# TaskBoard (Todolist)

Android task-management app: **batch tasks** sent by an Admin to a batch, plus **fully
private personal tasks** that not even the Admin can see.

Built with **Kotlin + MVVM + Views/Fragments**. Storage is **local-only SQLite** today;
the app is being migrated to **Supabase (Postgres + Auth) + PowerSync** for offline-first
cross-device sync (see [Roadmap](#10-roadmap--supabase--powersync-migration)).

---

## 1. Status

| Phase | State |
|---|---|
| **v1 — local-only SQLite app** (admin + user flows, charts, reminders) | ✅ Implemented |
| **Backend setup** — Supabase project, 6 tables, RLS, `powersync` role, publication, Sync Streams | ✅ Done & verified |
| **App-side migration** to Supabase + PowerSync | ⛔ Not started (next milestone) |

The current app builds and runs entirely offline against `taskboard.db`; no network
permission is declared yet (`INTERNET` is added in migration Milestone A).

---

## 2. Features

**Auth & roles**
- Login / Register / Forgot-password screens, role-based redirect (**Admin** vs **User**)
- Passwords hashed before storage (salted, iterated SHA-256) — never plaintext
- Session persisted in `SharedPreferences`; returning users skip the Welcome screen
- Registration lets the user pick which batch to join from the Admin's live batch list

**Admin**
- Create tasks (title, description, due date, priority) and send them to a chosen batch
- Batches are dynamic — the Admin can add as many as they like
- Edit a task (it stays in its original batch) / delete a task (removed from every
  member's view via FK cascade)
- Progress dashboard: per-batch completion counts + charts, batch member list

**User**
- Status board with three columns — **Accepted → Ongoing → Completed** (with Undo)
- Batch tasks (from Admin) and **Personal tasks** in separate tabs
- Personal tasks are fully private, editable until completed, always deletable
- Overdue tasks stay visible and are flagged, never hidden
- Filter/sort by source, status, due date, priority
- Progress dashboard for personal tasks (pie/donut + bar charts)

**Notifications**
- WorkManager daily check for due-soon / overdue tasks → system notifications + in-app
  records (bell icon list); optional proof-of-completion image is a placeholder flag today

**UI**
- Material 3 (`Theme.Material3.DayNight`), Google Font **Poppins**, Material icons
- Dark/light mode switchable at runtime, 60-30-10 colour palette
- ViewBinding, loading overlays, empty states, fragment transitions

---

## 3. Tech stack

| Layer | Technology | Version |
|---|---|---|
| Language | Kotlin (AGP 9 built-in Kotlin, no separate KGP) | 2.3.20 |
| Build | Gradle wrapper / Android Gradle Plugin | 9.5.0 / 9.3.1 |
| SDKs | `minSdk 24` (Android 7.0), `targetSdk`/`compileSdk 37` | — |
| UI | Views + Fragments, Material 3 | material 1.14.0 |
| Architecture | MVVM — ViewModel + LiveData + Repository | lifecycle 2.11.0 |
| Navigation | AndroidX Navigation Component (route-based IDs) | 2.10.0 |
| Storage | SQLite via `SQLiteOpenHelper` (`taskboard.db`) | — |
| Async | Kotlin Coroutines (`Dispatchers.IO` + `viewModelScope`) | 1.10.2 |
| Background | WorkManager (daily reminder worker) | 2.11.2 |
| Charts | MPAndroidChart (pie/donut + bar) via JitPack | 3.1.0 |
| Binding | ViewBinding | — |
| Fonts / icons | Google Fonts "Poppins" (bundled) / Material vector drawables | — |
| DI | Manual service locator (`AppContainer`) — no framework | — |
| Tests | JUnit 4 + Espresso (scaffold only, no real tests yet) | 4.13.2 / 3.7.0 |

Dependencies are centralised in `gradle/libs.versions.toml`; repositories are declared in
`settings.gradle.kts` (`google()`, `mavenCentral()`, `jitpack.io`).

---

## 4. Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  VIEW            MainActivity + Fragments + RecyclerAdapters │
│                  observes ViewModel state only               │
└──────────────────────────┬──────────────────────────────────┘
                           │ LiveData / events
┌──────────────────────────▼──────────────────────────────────┐
│  VIEWMODEL       AuthViewModel / AdminViewModel /            │
│                  UserViewModel  (viewModelScope coroutines)  │
└──────────────────────────┬──────────────────────────────────┘
                           │ suspend calls (Dispatchers.IO)
┌──────────────────────────▼──────────────────────────────────┐
│  REPOSITORY      AuthRepository, BatchRepository,            │
│                  TaskRepository, PersonalTaskRepository,     │
│                  NotificationRepository   ← backend swap point│
└──────────────────────────┬──────────────────────────────────┘
                           │ SQL helpers (queryList / CursorRow)
┌──────────────────────────▼──────────────────────────────────┐
│  DATA SOURCE     TodoDbHelper (SQLiteOpenHelper) → taskboard.db│
│                  + PasswordHasher (salted iterated SHA-256)   │
└─────────────────────────────────────────────────────────────┘
```

- **Single-activity + Navigation graph**: `MainActivity` hosts a `NavHostFragment`, a
  `DrawerLayout` (burger menu) and a role-dependent `BottomNavigationView`.
  Flow: Welcome → Login / Register / Forgot password → Admin or User home.
- **Dependency graph**: `TaskApp` (Application) builds an `AppContainer` holding the
  `TodoDbHelper`, all five repositories, `SessionManager` and `ThemeManager`. Fragments get
  repositories through `viewModelFactory { initializer { ... } }` — no DI framework.
- **Why the repository layer matters**: swapping SQLite for Supabase/PowerSync only touches
  repository internals; ViewModels and UI stay as they are.

---

## 5. Project layout

```
Todolist/
├── docs/                                  # planning docs (see §9)
├── gradle/libs.versions.toml              # version catalog
├── settings.gradle.kts / build.gradle.kts / gradle.properties
└── app/
    ├── build.gradle.kts                   # viewBinding, deps, min/target SDK
    └── src/main/
        ├── AndroidManifest.xml            # POST_NOTIFICATIONS, MainActivity
        ├── java/com/example/todolist/
        │   ├── TaskApp.kt                 # Application + AppContainer (service locator)
        │   ├── MainActivity.kt            # drawer, bottom nav, session restore
        │   ├── data/
        │   │   ├── PasswordHasher.kt      # salted iterated SHA-256
        │   │   ├── db/TodoDbHelper.kt      # schema, seed, queryList/CursorRow helpers
        │   │   ├── model/Models.kt         # User, Batch, BatchTask, UserTask, PersonalTask…
        │   │   └── repo/                   # Auth, Batch, Task, PersonalTask, Notification
        │   ├── notification/ReminderWorker.kt
        │   ├── ui/
        │   │   ├── common/                 # ChartUtils, NavAnim, Events, NotificationsDialog
        │   │   ├── auth/                   # Login, Register, ForgotPassword + AuthViewModel
        │   │   ├── admin/                  # Tasks, TaskForm, Batches, Progress + AdminViewModel
        │   │   ├── user/                   # Board, Personal, Progress + UserViewModel + adapters
        │   │   └── welcome/WelcomeFragment.kt
        │   └── util/                       # SessionManager, ThemeManager, DateUtils
        └── res/
            ├── anim/ drawable/ font/ layout/ menu/ navigation/
            └── values/ + values-night/     # 60-30-10 palette, Material3 themes, strings
```

**Screen map**

| Screen | Fragment | Role |
|---|---|---|
| Welcome | `WelcomeFragment` | entry point — Login / Exit |
| Login / Register / Forgot | `ui/auth/*` | auth |
| Admin — Tasks / Batches / Progress | `ui/admin/AdminTasks`, `AdminBatches`, `AdminProgress` | Admin bottom tabs |
| Admin — Task form | `ui/admin/AdminTaskFormFragment` | create/edit a batch task |
| User — Board / Personal / Progress | `ui/user/UserBoard`, `UserPersonal`, `UserProgress` | User bottom tabs |

---

## 6. Database schema (SQLite — `taskboard.db`)

| Table | Purpose | Key columns |
|---|---|---|
| `users` | Accounts | id, username (unique), password_hash, salt, role (ADMIN/USER), batch_id |
| `batches` | Dynamic groups | id, name (unique), created_at |
| `batch_tasks` | Admin-created tasks sent to a batch | id, title, description, due_date, priority, batch_id, created_at, updated_at |
| `user_tasks` | Assignment of a batch task to one user | id, user_id, task_id, status (ACCEPTED/ONGOING/COMPLETED), completed_at, has_proof, last_notified_due, last_notified_overdue |
| `personal_tasks` | Fully private user tasks | id, user_id, title, description, due_date, priority, status, created_at, completed_at, has_proof, last_notified_due, last_notified_overdue |
| `notifications` | In-app reminder records | id, user_id, message, type (DUE_SOON/OVERDUE), task_title, created_at, read |

- **FK cascades**: deleting a batch task removes every matching `user_tasks` row; deleting a
  user cascades their personal tasks, assignments and notifications.
- **Seeded on first launch**: `Batch A`, the admin account and the demo user (see §7).
- Batch tasks are copied to every member of the batch at creation; members who register later
  receive all existing batch tasks.

---

## 7. Default accounts (seeded on first launch)

| Role | Username | Password | Notes |
|---|---|---|---|
| **Admin** | `admin` | `admin123` | Create batches & tasks, view progress |
| **User** | `user` | `user123` | Member of **Batch A**; the demo normal user |

> Defined in `TodoDbHelper.seed()`. They live only until `taskboard.db` is deleted —
> uninstalling the app wipes them. **Demo credentials only — never reuse on a real backend.**

---

## 8. Build & run

```bash
# build the debug APK
gradlew.bat :app:assembleDebug

# install on a connected device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk

# unit tests
gradlew.bat :app:testDebugUnitTest

# instrumented tests (device required)
gradlew.bat :app:connectedDebugAndroidTest
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

`local.properties` (SDK path) and `/build`, `.gradle`, `.idea/*` are git-ignored. Gradle uses
the configuration cache (`org.gradle.configuration-cache=true`).

---

## 9. Docs index

| File | What it covers |
|---|---|
| [`task-management-app-spec.md`](task-management-app-spec.md) | Original feature spec + open questions |
| [`PROJECT_STRUCTURE.md`](PROJECT_STRUCTURE.md) | Full annotated structure, stack and source tree |
| [`docs/conversation-log.md`](docs/conversation-log.md) | **Session log / resume point** for the migration work |
| [`docs/powersync-implementation-plan.md`](docs/powersync-implementation-plan.md) | Locked decisions, target architecture, schema & stream drafts, phased rollout |
| [`docs/supabase-offline-first-comparison.md`](docs/supabase-offline-first-comparison.md) | PowerSync vs hand-rolled sync decision record |
| [`docs/supabase-setup-runbook.md`](docs/supabase-setup-runbook.md) | Clean ordered backend setup runbook + SQL + verification queries |
| [`docs/feature-schema-draft.md`](docs/feature-schema-draft.md) | Draft schema for attachments / categories / Storage (not implemented) |

**To resume work, start with `docs/conversation-log.md`.**

---

## 10. Roadmap — Supabase + PowerSync migration

Locked decisions: **Supabase** (Postgres + Auth) as the backend, **offline-first** with the
local DB as the UI source of truth, **PowerSync** as the sync engine (Room integration),
**email + password** auth, `uuid` client-generated primary keys, and **LWW** conflict
resolution on `updated_at bigint`. Business rules move into Postgres functions/triggers/RPCs.

**Backend:** done and verified — 6 tables in `public` with RLS enabled, a `powersync` role
(LOGIN + REPLICATION + BYPASSRLS), grants, a `powersync` publication and deployed Sync
Streams (per-user profile / batch / batch tasks / user tasks / personal tasks /
notifications). The instance connects over the **direct** connection
(`db.<project-ref>.supabase.co:5432`), which requires the **IPv4 add-on** — the Supavisor
pooler does not support logical replication.

**App side: not started.** Milestones:

| # | Milestone | Summary |
|---|---|---|
| A | Foundation | Gradle deps (`supabase-kt`, PowerSync `core-android`/`connector-supabase`/`room`), `INTERNET` permission, `AppContainer` init, `BuildConfig` for `SUPABASE_URL` / anon key / `POWERSYNC_URL` |
| B | Auth rework | Email + password via supabase-kt, `profiles` auto-create trigger on signup, seed admin/user, retire `PasswordHasher` |
| C | Repositories | Rewrite the 5 repos over PowerSync (suspend/Flow); ViewModels drop manual `Dispatchers.IO` |
| D | Business rules | Create-batch-task → assign members, join/leave batch, delete cascades as Postgres RPCs/triggers |
| E | Reminders | Rescope `ReminderWorker` to the signed-in user (device-side vs. server push: decision pending) |
| F | Testing | Two devices (user + admin), offline edits, conflicts, reconnect |

Still to be decided before the attachments feature: bucket visibility (private + signed URLs
recommended), allowed file types/size caps, proof retention on leaving a batch, free-text vs.
managed categories, and whether admins can view proof images — see
`docs/feature-schema-draft.md` §5.

---

## 11. Security notes

- **Never commit secrets** — no database password, `powersync` password, `service_role` key
  or `.env` contents. Only the Supabase **anon/publishable** key is safe in-app (RLS is on;
  this is by design).
- RLS is enabled on all cloud tables with no policies = closed by default. Do not ship an
  anon key against a table with RLS disabled.
- Apply schema changes through reviewed migration files, never ad hoc.
- The migration log notes that earlier throwaway passwords (`Hackathon6767`, `ps_dev_2026`)
  are **considered compromised** — rotate them if they are reused anywhere.
- The seeded `admin` / `user` accounts in §7 exist only in the local demo DB.

---

## Repository

`origin` → <https://github.com/jakers30/Todolist.git> (branch `master`)
