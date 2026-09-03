# Conversation Log — Supabase + PowerSync Migration & Feature Planning

**Date:** 2026-09-03
**Project:** Todolist (Kotlin, MVVM, SQLite → migrating to Supabase + PowerSync)
**Purpose:** Session record so work can be resumed even if the original chat is lost.
**Security:** No passwords, keys, or secrets in this file — placeholders only.

---

## 1. Where this started

- Requested to run the app on a phone (TECNO CE7j, wireless adb).
- Reviewed the current **SQLite schema** (`TodoDbHelper.kt`): 6 tables —
  `users`, `batches`, `batch_tasks`, `user_tasks`, `personal_tasks`, `notifications`.
- User wants to move the database to **Supabase for syncing** (originally "don't do
  anything yet" → planning → explicit go-ahead).

---

## 2. Decisions locked (for the migration)

| Decision | Choice |
|---|---|
| Cloud backend | **Supabase** (Postgres + Auth) |
| Sync model | **Offline-first** (local DB = UI source of truth) |
| Sync engine | **PowerSync** (Path A) with Room integration |
| Auth UX | **Email + password** (Supabase Auth native) |
| Username | Display-only `username` in `profiles` (no longer a credential) |
| Primary keys | **uuid** (client-generated, for offline writes) |
| Conflict resolution | PowerSync built-in (LWW via `updated_at bigint`) |
| Business rules | Move to **Postgres functions/triggers/RPCs** |
| Firebase references | **Disregarded** — not in this project's plan |
| Deletes | Propagate via PowerSync → Supabase Postgres ("delete-to-web") |

---

## 3. What was completed on the backend (fresh project)

A brand-new Supabase project was set up (previous one was abandoned mid-setup to get a
clean start). All backend steps **succeeded and were verified**:

1. **Tables** — 6 tables created in `public` (`profiles`, `batches`, `batch_tasks`,
   `user_tasks`, `personal_tasks`, `notifications`), all `uuid` PKs + `updated_at bigint`.
2. **RLS** — enabled on all 6 tables with **no policies** (closed by default via REST).
3. **Role** — `powersync` role (LOGIN + REPLICATION + BYPASSRLS) with its own password.
4. **Grants** — full privileges on tables/sequences + default privileges for `powersync`.
5. **Publication** — `powersync` publication containing all 6 tables.
6. **Connectivity** — PowerSync instance connected via the **direct** connection
   (`db.<project-ref>.supabase.co:5432`), user `powersync`.
   - The shared pooler/Supavisor was tried first and correctly rejected
     ("Supavisor does not support logical replication").
   - Requires the **IPv4 add-on** because new projects' direct connections are IPv6-only
     (PowerSync Cloud cannot reach IPv6).
7. **Sync Streams** — deployed & validated (see §5): per-user sync of own profile,
   batch, batch tasks, user tasks, personal tasks, notifications. Alias warning fixed
   (source tables must NOT be aliased or they sync under the alias name).

### Known-good connection string shape (do NOT store real password here)
```
postgresql://powersync:<POWERSYNC_DB_PASSWORD>@db.<project-ref>.supabase.co:5432/postgres
```

---

## 4. Docs created in this session

| File | Contents |
|---|---|
| `docs/supabase-offline-first-comparison.md` | PowerSync vs hand-rolled sync decision doc |
| `docs/powersync-implementation-plan.md` | Locked decisions, target architecture, schema/stream drafts, phased rollout |
| `docs/supabase-setup-runbook.md` | Clean ordered setup runbook + full SQL script + verification queries |
| `docs/feature-schema-draft.md` | Draft schema for new features (attachments/category/Storage) |

---

## 5. Sync Streams (deployed, validated)

```yaml
config:
  edition: 3

streams:
  own_profile:        SELECT * FROM profiles WHERE id = auth.user_id()
  my_batches:         SELECT batches.* FROM batches
                      JOIN profiles p ON p.batch_id = batches.id WHERE p.id = auth.user_id()
  my_batch_tasks:     SELECT batch_tasks.* FROM batch_tasks
                      JOIN batches ON batches.id = batch_tasks.batch_id
                      JOIN profiles p ON p.batch_id = batches.id WHERE p.id = auth.user_id()
  my_user_tasks:      SELECT * FROM user_tasks WHERE user_id = auth.user_id()
  my_personal_tasks:  SELECT * FROM personal_tasks WHERE user_id = auth.user_id()
  my_notifications:   SELECT * FROM notifications WHERE user_id = auth.user_id()
```
Rule learned: never alias the **source** table in a stream query (rows sync under the
alias name otherwise). Admin stream still TODO.

---

## 6. Feature-planning discussion (the large feature prompt)

A feature prompt was discussed (attachments, calendar icon, task categories, batch label,
delete-to-web, monthly stats, draggable stat containers, aligned numbers). Outcome:

- **Schema changes needed:** only two — a new `attachments` table (Admin task resources +
  User proof-of-completion kept structurally separate via `attach_type` + check constraint)
  and `category` columns on `batch_tasks`/`personal_tasks`. File bytes → Supabase Storage
  (private bucket `attachments`), metadata rows synced by PowerSync.
- **UI-only (no schema):** calendar view, month stats, batch label chip, draggable
  containers, aligned summary numbers.
- Full draft DDL + Storage rules + Sync Stream sketch: `docs/feature-schema-draft.md`.

### Open questions (from feature-schema-draft §5) — answer before implementation
1. Private bucket + signed URLs (recommended) vs public-read?
2. Max file size / allowed types (images only for proofs? PDF+images for resources?)
3. Proof retention when a user leaves a batch / task is deleted (delete vs archive)?
4. Category free-text (recommended) vs managed list?
5. Can admins view proof images (affects RLS + admin stream)?

---

## 7. App-side migration (NOT started — next big milestone)

Milestones when resumed (needs user's green light):
- **A. Foundation:** Gradle deps (`supabase-kt` auth, PowerSync `core-android`/
  `connector-supabase`/`room`, serialization), `INTERNET` permission, `AppContainer`
  init (Supabase client + PowerSync database replacing `TodoDbHelper`), BuildConfig
  for `SUPABASE_URL`, anon/publishable key, `POWERSYNC_URL`.
- **B. Auth rework:** email + password; `profiles` auto-create trigger on signup;
  seed admin/user; `SessionManager` caches profile only; retire `PasswordHasher`.
- **C. Repositories:** rewrite the 5 repos over PowerSync (suspend/Flow); ViewModels
  drop manual `Dispatchers.IO`.
- **D. Business-rule functions:** create-batch-task→assign members, join/leave batch,
  delete batch/task cascades — as Postgres RPCs/triggers.
- **E. ReminderWorker:** scope to signed-in user (decision pending).
- **F. Test:** two devices (user + admin), offline edits, conflicts, reconnect.

Config values still needed from user when starting: `SUPABASE_URL`,
publishable/anon key, `POWERSYNC_URL`.

---

## 8. How to resume

1. Open this project in VS Code.
2. Read `docs/conversation-log.md` + `docs/powersync-implementation-plan.md`.
3. Confirm backend is still running (restore Supabase project if auto-paused —
   free plan paused projects are deleted after ~7 days unless restored!).
4. Continue at §7 (Milestone A) once the user shares the 3 public config values.

---

## 9. Security reminders

- Never store the Supabase DB password, `powersync` password, or `service_role` key in
  the repo or chat.
- Old passwords used earlier in the session (`Hackathon6767`, `ps_dev_2026`) are
  considered **compromised** — rotate if reused anywhere.
- The anon/publishable key is safe to ship in-app (RLS is on); the IPv4 add-on on the
  Supabase project bills monthly and must be re-enabled before PowerSync can reconnect.


