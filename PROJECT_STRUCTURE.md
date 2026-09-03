# TaskBoard (Todolist) — Project Structure

> Task management Android app: batch tasks from an Admin + fully private personal tasks,
> built with Kotlin + MVVM + SQLite (local-only, backend planned for a future phase).

---

## 1. Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | **Kotlin** (AGP 9 built-in Kotlin, no separate KGP) | Kotlin 2.3.20 |
| Build system | Gradle (wrapper) | 9.5.0 |
| Android Gradle Plugin | AGP | 9.3.1 |
| Min / Target SDK | `minSdk 24` (Android 7.0), `targetSdk 37` | — |
| UI | **Views + Fragments**, Material 3 (`Theme.Material3.DayNight`) | material 1.14.0 |
| Architecture | **MVVM** — ViewModel + LiveData + Repository | lifecycle 2.11.0 |
| Navigation | AndroidX **Navigation Component** (route-based IDs) | navigation 2.10.0 |
| Local storage | **SQLite** (`SQLiteOpenHelper`), custom DAO-style helpers | — |
| Async | **Kotlin Coroutines** (`Dispatchers.IO` + `viewModelScope`) | coroutines 1.10.2 |
| Background | **WorkManager** (daily due/overdue reminder worker) | work 2.11.2 |
| Charts | **MPAndroidChart** (pie/donut + bar charts) | v3.1.0 (JitPack) |
| View binding | **ViewBinding** (generated binding classes) | — |
| Fonts | **Google Font "Poppins"** (bundled TTFs) | — |
| Icons | **Google Material icons** (custom vector drawables) | — |
| DI | Manual service locator (`AppContainer`) — no framework | — |
| Tests | JUnit 4 (unit), Espresso (instrumented, scaffold) | 4.13.2 / 3.7.0 |

### Key build files
- `settings.gradle.kts` — repos: `google()`, `mavenCentral()`, `jitpack.io` (MPAndroidChart)
- `gradle/libs.versions.toml` — central version catalog
- `app/build.gradle.kts` — Android config + dependencies + `viewBinding = true`

---

## 2. System / Architecture

### 2.1 MVVM layers

```
┌─────────────────────────────────────────────────────────────┐
│  VIEW (View layer)                                           │
│  MainActivity + Fragments  (XML layouts, RecyclerAdapters)   │
│  observes ViewModel state only; emits user actions           │
└──────────────────────────┬──────────────────────────────────┘
                           │ LiveData / Events
┌──────────────────────────▼──────────────────────────────────┐
│  VIEWMODEL (ViewModel layer)                                 │
│  AuthViewModel / AdminViewModel / UserViewModel              │
│  holds UI state, survives config changes, launches coroutines│
└──────────────────────────┬──────────────────────────────────┘
                           │ suspend calls (Dispatchers.IO)
┌──────────────────────────▼──────────────────────────────────┐
│  REPOSITORY (data layer — the swap point for a backend)      │
│  AuthRepository, BatchRepository, TaskRepository,            │
│  PersonalTaskRepository, NotificationRepository              │
└──────────────────────────┬──────────────────────────────────┘
                           │ SQL via helpers (queryList / CursorRow)
┌──────────────────────────▼──────────────────────────────────┐
│  DATA SOURCE                                                │
│  TodoDbHelper (SQLiteOpenHelper)  →  taskboard.db            │
│  + PasswordHasher (salted iterated SHA-256)                  │
└─────────────────────────────────────────────────────────────┘
```

- **Dependency graph**: `TaskApp` (Application) creates an `AppContainer` holding the
  `TodoDbHelper` + all repositories + `SessionManager` + `ThemeManager`. Fragments obtain
  repositories via `viewModelFactory { initializer { ... } }` (no DI framework).
- **Repository pattern** is deliberate: swapping SQLite for a remote backend later only
  touches the repository internals — ViewModels/UI stay unchanged.

### 2.2 Single-Activity + Navigation graph

- One `MainActivity` hosts a `NavHostFragment` + `DrawerLayout` (burger menu) +
  `BottomNavigationView` (role-dependent menu).
- Screens are Fragments wired through `res/navigation/nav_graph.xml`.
- Flow: **Welcome** → **Login** / **Register** / **Forgot password** →
  role-based home (**Admin** or **User**).

### 2.3 Authentication & session

- Passwords hashed with salted, iterated SHA-256 (`PasswordHasher`) — never stored plaintext.
- Login → `SessionManager` persists `userId/username/role` in SharedPreferences.
- Returning logged-in users skip Welcome and go straight to their home screen.

---

## 3. Database Schema (SQLite — `taskboard.db`)

| Table | Purpose | Key columns |
|---|---|---|
| `users` | Accounts | id, username (unique), password_hash, salt, role (ADMIN/USER), batch_id |
| `batches` | Dynamic groups | id, name (unique), created_at |
| `batch_tasks` | Admin-created tasks sent to a batch | id, title, description, due_date, priority, batch_id, created_at, updated_at |
| `user_tasks` | Assignment of a batch task to one user | id, user_id, task_id, status (ACCEPTED/ONGOING/COMPLETED), completed_at, has_proof, last_notified_due, last_notified_overdue |
| `personal_tasks` | Fully private user tasks | id, user_id, title, description, due_date, priority, status, created_at, completed_at, has_proof, last_notified_due, last_notified_overdue |
| `notifications` | In-app reminder records | id, user_id, message, type (DUE_SOON/OVERDUE), task_title, created_at, read |

- FK cascades: deleting a batch task removes every `user_tasks` row; deleting a user cascades
  their personal tasks/assignments/notifications.
- Seeded on first launch: **Batch A**, the admin account, and the demo user (see §5).
---

## 4. Source Tree (annotated)

```
Todolist/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/libs.versions.toml            # version catalog
└── app/
    ├── build.gradle.kts                 # viewBinding, deps, min/target SDK
    └── src/main/
        ├── AndroidManifest.xml          # POST_NOTIFICATIONS perm, MainActivity
        ├── java/com/example/todolist/
        │   ├── TaskApp.kt               # Application + AppContainer (service locator)
        │   ├── MainActivity.kt          # drawer, bottom nav, keyboard handling, session restore
        │   ├── data/
        │   │   ├── PasswordHasher.kt    # salted iterated SHA-256
        │   │   ├── db/TodoDbHelper.kt   # schema, seed, queryList/CursorRow helpers
        │   │   ├── model/Models.kt      # User, Batch, BatchTask, UserTask, PersonalTask, ...
        │   │   └── repo/                # Auth, Batch, Task, PersonalTask, Notification
        │   ├── notification/ReminderWorker.kt  # daily WorkManager check → system + in-app alerts
        │   ├── ui/
        │   │   ├── common/              # ChartUtils, NavAnim, Events, NotificationsDialog
        │   │   ├── auth/                # Login, Register, ForgotPassword + AuthViewModel
        │   │   ├── admin/               # Tasks, TaskForm, Batches, Progress + AdminViewModel
        │   │   ├── user/                # Board, Personal, Progress + UserViewModel + adapters
        │   │   └── welcome/WelcomeFragment.kt
        │   └── util/                    # SessionManager, ThemeManager, DateUtils
        └── res/
            ├── anim/                    # fragment enter/exit/pop transitions
            ├── drawable/                # ~30 Material vector icons + badge/circle shapes
            ├── font/                    # Poppins Regular/Medium/SemiBold/Bold TTFs
            ├── layout/                  # 19 layouts (activities, fragments, items, dialogs)
            ├── menu/                    # bottom nav (admin/user) + drawer menu
            ├── navigation/nav_graph.xml
            └── values/ + values-night/  # 60-30-10 palette, Material3 themes, strings
```

### Screen map
| Screen | Fragment | Role |
|---|---|---|
| Welcome | `WelcomeFragment` | entry point — Login / Exit |
| Login / Register / Forgot | `auth/*` | auth |
| Admin — Tasks / Batches / Progress | `admin/AdminTasks/Batches/Progress` | Admin bottom tabs |
| Admin — Task form | `admin/AdminTaskFormFragment` | create/edit batch task |
| User — Board / Personal / Progress | `user/UserBoard/Personal/Progress` | User bottom tabs |

---

## 5. Default Accounts (seeded on first launch)

| Role | Username | Password | Notes |
|---|---|---|---|
| **Admin** | **`admin`** | **`admin123`** | Full admin view — create batches & tasks, see progress |
| **User** | `user` | `user123` | Lives in **Batch A**; also the demo "normal user" |

> These are defined in `TodoDbHelper.seed()`. They only exist until the DB file is deleted
> (`taskboard.db` in app internal storage) — uninstalling the app wipes them.

---

## 6. Key Behaviors / Rules

- **Batch tasks** are copied to every member of a batch at creation; new members receive
  all existing batch tasks at registration.
- **Deleting a batch task** removes it from all users' views (FK cascade).
- **Editing a batch task** keeps it in its original batch.
- **Personal tasks** are private (never visible to Admin); editable until **Completed**
  (Edit greyed out after completion, Delete always allowed).
- **Status board**: Accepted → Ongoing → Completed, with Undo (Ongoing → Accepted).
- **Overdue** tasks remain visible with an overdue flag/badge.
- **Notifications**: WorkManager daily check → due-soon/overdue system notifications +
  in-app records; proof-of-completion is a placeholder flag for now.

---

## 7. Build & Run

```bash
# build debug APK
gradlew.bat :app:assembleDebug
# install on a connected device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
# run unit tests
gradlew.bat :app:testDebugUnitTest
```

Output: `app/build/outputs/apk/debug/app-debug.apk`


