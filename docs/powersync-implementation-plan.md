# Todolist → Supabase + PowerSync: Implementation Plan

**Status:** Decisions locked; no app code changed yet
**Date:** 2026-09-03

---

## 1. Locked decisions

| Decision | Choice | Notes |
|---|---|---|
| Cloud backend | **Supabase** (Postgres + Auth) | your existing project |
| Sync model | **Offline-first** | local DB = UI source of truth, works offline, converges across devices |
| Sync engine | **Path A — PowerSync** | managed/self-hosted PowerSync instance linked to Supabase Postgres |
| Client data access | **Room integration** (`com.powersync:room`) | typed queries close to today's SQL |
| Auth UX | **Email + password** | Supabase Auth native flow |
| Usernames | Display-only `username` in `profiles` | captured at registration for display in batch lists; no longer a credential |
| Primary keys | **uuid** (client-generated) | required for offline writes |
| Conflict resolution | PowerSync built-in (LWW on `updated_at`) | `updated_at bigint` on mutable tables |
| Business rules | Move into **Postgres triggers/functions (RPCs)** | so every device behaves identically |

## 2. Reference docs

- Detailed PowerSync vs hand-rolled comparison: `supabase-offline-first-comparison.md`
- Original DB schema (SQLite): `app/src/main/java/com/example/todolist/data/db/TodoDbHelper.kt`

---

## 3. Target architecture

```
┌─────────────────────────────┐  Room/SQL  ┌───────────────────────┐  replicate   ┌─────────────────────────┐
│ Todolist Android (offline)  │ ◄────────► │  PowerSync SQLite DB   │ ◄───────────► │  Supabase project       │
│  - reads local DB always    │            │  (app tables)          │   + writes    │  - Postgres = 6 tables  │
│  - writes local first       │            │  + SupabaseConnector   │               │  - Supabase Auth (JWT)  │
└─────────────────────────────┘            └───────────────────────┘               └─────────────────────────┘
        │  ViewModels → Repositories → PowerSyncDatabase
        └─ supabase-kt auth: signUp / signInWithPassword / resetPassword
```

---

## 4. Supabase Postgres schema (draft — to be finalized before writing migrations)

Key deltas vs current SQLite schema:
- `id uuid primary key default gen_random_uuid()` on every table
- `updated_at bigint not null` on all mutable tables (LWW)
- `users` → replaced by `auth.users` + `profiles`
- password/salt columns dropped (Supabase Auth manages credentials)
- enums stay as `text` + `check`; booleans become real `boolean`; millis stay `bigint`

### `profiles`
| column | type | notes |
|---|---|---|
| id | uuid pk | `references auth.users(id) on delete cascade` |
| username | text | display name, nullable, not unique-enforced (emails are the unique id) |
| role | text | `ADMIN` / `USER` + check |
| batch_id | uuid null | references `batches(id)` on delete set null |
| created_at | bigint | |
| updated_at | bigint | |

*(A trigger on `auth.users` insert creates the profile and mirrors `role` into JWT `raw_app_meta_data`.)*

### Other tables — same shape as SQLite, retyped
- `batches(id uuid pk, name text not null unique, created_at bigint, updated_at bigint)`
- `batch_tasks(id uuid pk, title, description, due_date bigint, priority text, batch_id uuid ref batches on delete cascade, created_at, updated_at)`
- `user_tasks(id uuid pk, user_id uuid ref profiles on delete cascade, task_id uuid ref batch_tasks on delete cascade, status text, completed_at bigint null, has_proof boolean, last_notified_due bigint, last_notified_overdue bigint, unique(user_id, task_id), updated_at)`
- `personal_tasks(id uuid pk, user_id uuid ref profiles cascade, title, description, due_date, priority, status, created_at, completed_at, has_proof, last_notified_*, updated_at)`
- `notifications(id uuid pk, user_id uuid ref profiles cascade, message, type, task_title, created_at, read boolean)`

### Business-rule functions (RPC/trigger) to port from Android to Postgres
- `create_batch_task(...)` → insert + insert `user_tasks` per member (transaction)
- `join_batch(user_id, batch_id)` → assign existing batch tasks
- `leave_batch(user_id)` → detach
- `delete_batch(id)` → cascade + members to "No Batch"
- `delete_batch_task(id)` → cascade

## 5. Sync Streams (PowerSync side) — draft

```yaml
config:
  edition: 3
streams:
  own_profile:
    auto_subscribe: true
    query: SELECT * FROM profiles WHERE id = auth.user_id()
  personal_tasks:
    auto_subscribe: true
    query: SELECT * FROM personal_tasks WHERE user_id = auth.user_id()
  my_batch_tasks:            # user: tasks assigned to them + the assignment state
    auto_subscribe: true
    query: |
      SELECT bt.*, b.name AS batch_name
      FROM batch_tasks bt
      JOIN batches b ON b.id = bt.batch_id
      WHERE b.id = (SELECT batch_id FROM profiles WHERE id = auth.user_id())
  my_user_tasks:
    auto_subscribe: true
    query: SELECT * FROM user_tasks WHERE user_id = auth.user_id()
  my_notifications:
    auto_subscribe: true
    query: SELECT * FROM notifications WHERE user_id = auth.user_id()
  admin_overview:            # admins see every batch, task, assignment
    auto_subscribe: false    # subscribe only for ADMIN users
    query: |
      SELECT * FROM batches
      UNION ALL SELECT * FROM batch_tasks
      UNION ALL SELECT * FROM user_tasks
```
*(Table `updated_at` enables PowerSync's LWW conflict strategy; verify current Sync Streams syntax + role-claim access against the PowerSync docs at setup time.)*

---

## 6. App-side change list (implementation phase)

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | add powersync + supabase-kt + serialization versions/artifacts |
| `app/build.gradle.kts` | add dependencies, `BuildConfig` fields (SUPABASE_URL, ANON_KEY, POWERSYNC_URL) |
| `app/src/main/AndroidManifest.xml` | add `INTERNET` permission |
| `data/db/TodoDbHelper.kt` | **delete**; replaced by PowerSync schema/init (`PowerSync.kt`) |
| `data/PasswordHasher.kt` | **delete** |
| `data/model/Models.kt` | ids → String/UUID; `User` becomes profile model (drop hash/salt) |
| `data/repo/*` (5) | rewrite over PowerSync DB (suspend/Flow via Room or `watch`) |
| `data/auth/SupabaseAuth.kt` (new) | signUp/signIn/reset/session restore via supabase-kt |
| `util/SessionManager.kt` | cache profile only (role/username/batch) from local DB |
| `TaskApp.kt` | init `SupabaseClient` + `PowerSyncDatabase` + repos |
| `ui/auth/*` + `AuthViewModel` | email+password screens; drop username-is-credential logic |
| `ui/user/UserViewModel.kt`, `ui/admin/AdminViewModel.kt` | call sites → suspend/Flow; observe local DB |
| `notification/ReminderWorker.kt` | per signed-in user (decision pending: §8) |
| progress/chart queries | unchanged SQL semantics, run against local PowerSync DB |

---

## 7. Phased rollout

1. **Prereqs (user):** create PowerSync instance linked to the Supabase project; collect 3 public values → `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `POWERSYNC_URL`. *(No secrets in chat/repo; anon key is public by design.)*
2. **Backend SQL:** write `supabase/migrations/*.sql` (schema, auth trigger, RLS where relevant, business-rule functions, seed) → apply via SQL Editor or `supabase db push`.
3. **Sync Streams YAML:** finalize against live docs → deploy to PowerSync instance.
4. **App deps + init** (`AppContainer`, BuildConfig, INTERNET permission).
5. **Auth rework** (supabase-kt; email+password screens; profiles read).
6. **Repository rewrite** table by table (batches → batch tasks → personal → notifications).
7. **ViewModels** adopt suspend/Flow.
8. **Reminder worker** rescope (decision §8).
9. **Two-device test:** user+admin, offline writes, conflicts, reconnect.

---

## 8. Remaining open item: notifications/reminders

- **Option 1 (default for v1):** keep WorkManager daily worker on the device, scoped to the signed-in user's data (works when that device runs).
- **Option 2 (server push):** Supabase pg_cron/Edge Function computes due/overdue → inserts `notifications` (synced via PowerSync) + optional FCM push so *any* device gets reminded.
- Decision can be deferred until after core sync works.

## 9. What still must NOT happen

- No `service_role` key or DB password in repo/chat/logs.
- No tables left with RLS off while shipping an anon key.
- No schema edits applied ad hoc without a migration file + review.

