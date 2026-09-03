# Supabase Migration — Offline-First: PowerSync vs Hand-Rolled Sync

**Status:** Decision document (no code changed yet)
**Date:** 2026-09-03
**App:** Todolist (`taskboard.db`, SQLite via `SQLiteOpenHelper`)
**Goal:** Move the cloud backend to Supabase with **offline-first sync** (works offline, converges across devices).

---

## 1. Context — what the app does today

- 6 local tables: `users`, `batches`, `batch_tasks`, `user_tasks`, `personal_tasks`, `notifications`.
- 5 synchronous repositories over `SQLiteOpenHelper`, consumed by ViewModels on `Dispatchers.IO`.
- Custom username/password auth (`PasswordHasher`: salted, iterated SHA-256).
- Business rules living in Android code:
  - creating a batch task auto-inserts a `user_tasks` row per batch member (transaction),
  - a user joining a batch auto-receives that batch's existing tasks,
  - deleting a batch/task cascades + resets members to "No Batch".
- WorkManager `ReminderWorker` scans all tasks daily and posts notifications.
- Manual DI in `AppContainer` (`TaskApp.kt`).

Both migration paths share a large common core (schema, RLS, Auth rework, business-rule move to Postgres). This document compares **only the sync-engine choice**.

---

## 2. The two candidate paths

### Path A — PowerSync engine
Keep local SQLite (via PowerSync, optionally with its **Room integration**). A **PowerSync instance**
(managed cloud or self-hosted Docker) connects to your Supabase Postgres and does the syncing for you.
Supabase stays as Auth + database. Sync scope is declared in YAML **Sync Streams** on the PowerSync side.

```
┌────────────┐  Room-style / raw SQL   ┌──────────────────┐  Postgres logical   ┌──────────────┐
│  Android   │ ◄──────────────────────► │ PowerSync (SQLite│ ◄─────────────────► │   Supabase   │
│    app     │  offline queue handled   │  + sync engine)  │   replication      │   Postgres   │
└────────────┘  inside the SDK          └──────────────────┘                     └──────────────┘
                                                │  token/connector (Supabase Auth JWT)
```

- Official artifacts: `com.powersync:core-android`, `com.powersync:connector-supabase`, `com.powersync:room`.
- Official reference demo exists: **android-supabase-todolist** (same domain as this app).

### Path B — Hand-rolled sync (supabase-kt + Room)
No extra infrastructure. You write a local **Room** cache and a **sync manager** yourself:
writes go to Room instantly + an **outbox**; a background sync pushes the outbox to Supabase;
a **Realtime `postgres_changes`** subscription pulls remote changes into Room; reconnect does a
cached `updated_at` catch-up. Conflict resolution is last-write-wins on `updated_at`.

```
┌────────────┐  writes→Room, read from Room   ┌──────────┐   PostgREST calls    ┌──────────────┐
│  Android   │ ───────────────►──────────────► │  Room    │  + outbox push      │   Supabase   │
│    app     │ ◄────────────────────────────── │ (SQLite) │  + Realtime pull    │   Postgres   │
└────────────┘        sync manager (yours)     └──────────┘                      └──────────────┘
```

---

## 3. Feature-by-feature comparison

| Dimension | Path A — PowerSync | Path B — Hand-rolled |
|---|---|---|
| Extra infrastructure | **Yes**: PowerSync instance (managed or self-hosted) | **No** |
| Offline write queue | Built-in (operations log) | You build (outbox table + worker) |
| Change ordering / retries | Built-in | You build |
| Real-time across devices | Built-in (updates while connected) | You build on Realtime `postgres_changes` |
| Conflict resolution | Built-in strategies (LWW / CRDT per table via Sync Streams config) | You implement LWW on `updated_at` |
| Deletes sync | Built-in (tombstones) | You design soft-delete + purge rules |
| Reconnect catch-up | Automatic | Manual: fetch rows with `updated_at > cursor` |
| Auth | Supabase Auth (JWT) handed to connector | Supabase Auth direct |
| RLS relevance | DB-side writes use PowerSync connector credentials; security mostly via Sync Streams per-user queries + your server rules | RLS is the primary security boundary; Realtime enforces it per subscriber |
| Client query style | Close to today's raw SQL; Room integration keeps typed queries | Room + Flow |
| New concepts to learn | Sync Streams YAML, PowerSync service ops | Supabase Realtime, RLS, serialization, offline protocol design |
| Third-party dependency | PowerSync (JourneyApps) | supabase-kt (open source) + your code |
| Risk of subtle sync bugs | Low (battle-tested engine) | **High** (ordering, races, deletes-vs-updates, clock skew, dupes) |
| Control/flexibility | Medium (constrained by engine model) | High (everything is yours) |

## 4. Deep dive on the dimensions that matter

### 4.1 Sync correctness (the #1 risk in Path B)
Hand-rolled sync must get these exactly right:
- **Ordering:** local ops must replay in a deterministic order (outbox sequence), including interleaved create→assign→complete flows.
- **Dedup:** Realtime can redeliver or race with your own outbox writes (you update locally then receive your own change back). Needs idempotent upserts and/or "ignore echo from self" logic.
- **Deletes:** Postgres deletes are invisible to RLS-filtered Realtime; you typically need soft deletes + a purge job, or Realtime with `replica identity full` (only for filtered delete events).
- **Catch-up gap:** while a device is offline, remote changes accumulate. On reconnect you must pull the delta reliably (`updated_at` cursor per table, with a fallback full re-fetch on clock/anomaly).
- **Clock skew:** LWW on device-generated `updated_at` breaks if clocks differ; server-assigned `updated_at` avoids it but complicates offline timestamps.

PowerSync removes this entire class of work; you configure priorities and strategies in Sync Streams.

### 4.2 Real-time behavior
- **A:** each connected client receives streamed changes from the PowerSync service automatically. Cross-device view updates are standard.
- **B:** you subscribe to `postgres_changes` per table on the `supabase_realtime` publication. RLS is enforced per subscriber, which is nice — but you still write the merge-into-Room code, subscription lifecycle (login/logout/refresh), and reconnect catch-up.

### 4.3 Security / RLS interplay
- **A:** the PowerSync instance talks to Postgres with elevated rights (your chosen role/key). Per-user filtering is expressed in Sync Streams YAML (`WHERE owner_id = auth.user_id()`), and writes are applied by your backend rulebook. Supabase RLS is less central because PowerSync, not the app, is the DB client.
- **B:** every client talks to PostgREST/Realtime with the user's JWT; **RLS policies are the security boundary** (see common migration §8). Cleaner separation of "app can only touch its own rows," but you must design policies carefully (avoid recursive policy-on-profiles traps; keep `role` in JWT claims).

### 4.4 Fit with this codebase
- **A:** repositories keep near-identical SQL; the **Room integration** means current MVVM/ViewModel call sites change least; `ReminderWorker` and progress queries (`GROUP BY`/`JOIN` stats) keep working against the local PowerSync DB. But note: shared admin aggregates over *all users' rows* must still be reshaped (RLS/stream scoping — see common §8) because PowerSync streams are user-scoped too.
- **B:** repositories become suspend Room DAO calls + network calls; more files change but no Room→engine adaptor needed.

### 4.5 Infrastructure, ops, cost
- **A managed (PowerSync Cloud):** zero ops, but a paid service with its own project/keys; another dashboard to administer; free tier availability should be verified at decision time.
- **A self-hosted:** free software (Docker) but you now run a sync service + DB replication setup — an ops burden on a dev phone project.
- **B:** nothing extra to run; Supabase free tier suffices; but the sync code is yours to maintain forever.

### 4.6 Vendor lock-in & exit
- **A:** the local DB stays SQLite/Room-shaped, so leaving PowerSync means replacing the engine behind the same queries. The Postgres/Supabase backend remains standard.
- **B:** no engine to leave; lock-in is to Supabase Realtime/postgrest idioms, which are standard HTTP/WebSocket + SQL.

## 5. Rough effort estimate (part-time, one developer)

| Work package | Path A — PowerSync | Path B — Hand-rolled |
|---|---|---|
| Supabase project + Postgres schema + RLS + seed | 2–3 days | 2–3 days |
| Auth rework (Supabase Auth, session, profile) | 2–3 days | 2–3 days |
| Move business rules to Postgres (functions/triggers/RPC) | 1–2 days | 1–2 days |
| Sync engine setup (instance + Sync Streams) | 1–2 days | — |
| Repo layer rewrite + ViewModel updates | 2–4 days | 3–5 days |
| Offline queue / outbox / sync manager | — (built-in) | 5–10 days |
| Realtime pull + reconnect catch-up | — (built-in) | 3–6 days |
| Conflict resolution & delete handling | — (built-in) | 2–4 days |
| Edge-case hardening, two-device testing | 2–3 days | 5–10 days |
| **Total (rough)** | **~2 weeks** | **~4–8 weeks** |

## 6. Risks

**Path A**
- PowerSync service availability/pricing (verify current free tier).
- Streams YAML scoping must be designed correctly for batch/role data — same mental model change as RLS, just in another syntax.
- Self-hosting means running one more server (replication user, WAL, upgrades).

**Path B**
- Offline sync correctness is genuinely hard; silent divergence bugs are easy to ship.
- Deletes + RLS-filtered Realtime require soft-delete architecture (schema and query changes everywhere).
- Supabase Realtime has per-table subscription limits / authorization cost per event; plan filters and scoped channels.
- Long-term maintenance burden lives in your code.

**Both**
- Existing local users (`admin`/`user`, custom SHA-256 hashes) cannot be imported into Supabase Auth — must re-register or be re-created via seed/admin.
- All client-side date math stays on epoch millis; keep `bigint` in Postgres to minimize churn.
- Notifications/reminders strategy is independent of this choice and still needs a decision.

## 7. Decision rubric (score 1–5; higher = better)

| Criterion (weight) | Path A | Path B |
|---|---|---|
| Development speed (5) | 5 | 2 |
| Offline correctness (5) | 5 | 2 |
| Simplicity of ongoing maintenance (4) | 4 | 2 |
| No extra infrastructure (3) | 2 | 5 |
| No vendor dependency (2) | 3 | 5 |
| Fit with existing MVVM/SQL code (3) | 5 | 4 |
| **Weighted total** | **~95** | **~62** |

> Verdict: unless "no third-party sync service" is a hard requirement, **Path A (PowerSync)** is the pragmatic recommendation; Path B is justified only if you want zero extra infra and can invest 4–8 weeks plus ongoing ownership of sync code.

## 8. Appendix — work that is common to BOTH paths (and needed regardless)

1. **Supabase project** + Postgres schema mirroring the 6 tables, with:
   - `uuid` primary keys (needed for any offline-first design),
   - `updated_at bigint` on mutable tables (`batch_tasks`, `user_tasks`, `personal_tasks`, `profiles`, `batches`, `notifications`),
   - soft-delete columns on user-writable tables if Path B is chosen.
2. **Auth**: Supabase Auth (email/password); `profiles` table keyed by `auth.uid()` holding `username`, `role`, `batch_id`; keep `role` mirrored into JWT `raw_app_meta_data` for cheap RLS claims. Retire `PasswordHasher`. Replace `SessionManager`'s role as source of truth (cache profile locally only).
3. **RLS policies**: users manage their own `personal_tasks`; batch members read/write their own `user_tasks`; admin stats served through server-side views/RPCs (do **not** give admins raw read on other users' rows via normal tables).
4. **Move business rules into Postgres** (functions + triggers or RPCs) so any device triggers identical behavior:
   - `create_batch_task` → insert + create `user_tasks` per member,
   - `join_batch` / `change_batch` → assign existing tasks / detach,
   - `delete_batch`, `delete_batch_task` → cascades + "No Batch" reset.
5. **`ReminderWorker`** becomes per signed-in user, or is replaced by a server-side scheduler (pg_cron / Edge Function + FCM push) — decision independent of Path A/B.
6. **Manifest**: add `INTERNET` permission.
7. **`AppContainer`**: single place to initialize the chosen stack; repositories constructed there.

## 9. Suggested next steps after you pick

1. (Decision) Choose Path A or B.
2. (Decision) Auth UX: email+password vs username-kept-for-login.
3. (Decision) Notifications: on-device worker vs server push.
4. Create the Supabase project and draft the Postgres schema/migrations (common appendix §8) — regardless of path.
5. Then implement in the agreed order from the earlier analysis (backend → deps → auth → repos → ViewModels → worker).

---

*Sources checked 2026-09-03:* `powersync-kotlin` GitHub README + demo (`android-supabase-todolist`) and Supabase Realtime `postgres_changes` docs. PowerSync free-tier/pricing and Sync Streams syntax should be re-verified at implementation time, as both evolve.



