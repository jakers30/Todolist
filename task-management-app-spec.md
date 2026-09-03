# Task Management App — Specification

## Features

### 1. Login & Registration
- Login screen (username/password)
- Role-based redirect: Admin view vs User view
- Users can register their own account
- During registration, user selects which batch to join from the list of existing batches
- Batches are not fixed to two — Admin can create additional batches at any time
- Forgot password feature (password reset flow)

### 2. Admin View
- Create task (title, description, due date, priority)
- Select batch to send task to (from the dynamic list of batches)
- Submit task — sent to **everyone** in the selected batch
- Edit existing task (edited task stays in its original batch)
- Delete a task the admin submitted — deleting removes it from all users' views in that batch

### 3. Batch Separation
- Batches are dynamic (Admin can create new ones, not limited to 2)
- Tasks only appear in the batch they were submitted to
- No cross-visibility between batches

### 4. Personal Task Space
- Separate tab/section from batch tasks
- User can create their own tasks here
- Fully private — only visible to that user, not even Admin can view it
- User can delete their own personal tasks

### 5. Task Status Board
- Three columns/tabs: Accepted | Ongoing | Completed
- User moves task between these states
- Applies to both batch tasks and personal tasks
- Tasks remain visible even after their due date passes (overdue tasks do not disappear, just flagged as overdue)

### 6. Filtering & Views
- Filter by task source: Personal tasks vs Admin-sent (batch) tasks
- Filter/sort by status, due date, or priority

### 7. Progress Tracking
- Progress tracking available for both Admins and Users, but kept as **separate views**:
  - **Admin Progress Tracker** — tracks completion of batch tasks (e.g., how many users in a batch completed a given task, per-batch completion rates)
  - **User Progress Tracker** — tracks the individual's own personal task completion, separate from batch task progress
- Visualized using graphs/charts for easier readability (in addition to or instead of plain percentage/count summaries)

### 8. Notifications
- Due-date based reminders
- Alerts for overdue tasks

### 9. Proof of Completion
- Optional image attachment when marking a task as Completed
- Use a placeholder image for now (actual image picker/upload to be implemented later)
- No admin approval/acceptance needed — attaching proof is just for the user's own record

---

## Development
- Use Kotlin for development
- Use temporary storage like SQLite for now (local-only, single-device)
- **Future plan:** replace SQLite with a real backend database to support cross-device sync — this is a planned phase, not part of the current build
- **Architecture pattern:** MVVM (Model-View-ViewModel)
  - `Model` — data classes + Room/SQLite (Repository pattern to abstract data source, so swapping SQLite for a remote DB later is easier)
  - `ViewModel` — holds UI state, survives config changes, exposes data via LiveData/StateFlow
  - `View` — Activities/Fragments or Compose screens, observes ViewModel state only
  - Use a Repository layer between ViewModel and data source now, specifically to make the future backend swap less painful
- Passwords: hash before storing locally (e.g., bcrypt/argon2 or at minimum salted SHA-256) even though it's local storage for now — do **not** store plaintext passwords, since this habit carries over once a real database is added
- Minimum SDK: target a low-ish minSdk (e.g., API 24 / Android 7.0) so it runs on most Android devices in use; use a recent targetSdk for compliance

---

## UI
- Must use Google Fonts, Google Icons
- Content loading transition when loading information
- Empty states for all lists/boards (e.g., "No tasks yet", "No batches created", "All caught up!")
- Clean UI that can be modified later
- Easily switch between dark & light mode
- Follow the 60-30-10 color rule
- Use graphs (bar/pie/line as appropriate) for progress tracking visualizations

---

## Open Questions / To Decide Later
- Cross-device sync is a target goal, but current phase is local SQLite only — confirm this phased approach is acceptable before dev starts
- Should Admin be able to move a user from one batch to another after registration?
- Can a user belong to more than one batch, or just one?
- Should there be more than one Admin account, and if so, do all Admins see all batches?
- Notification lead time — fixed (e.g. 1 day before due) or user-configurable?
