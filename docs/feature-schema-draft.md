# Feature Schema Draft — Attachments, Categories, Storage

**Status:** DRAFT — for review only. Nothing implemented.
**Date:** 2026-09-03
**Target:** New Supabase/Postgres project (PowerSync migration target).
**Related:** `docs/powersync-implementation-plan.md`, `docs/supabase-setup-runbook.md`

---

## Summary of DB impact

| Change | Object | Type |
|---|---|---|
| 1 new table | `attachments` | handles **both** attach points, kept distinct by constraint |
| 2 new columns | `batch_tasks.category`, `personal_tasks.category` | task categorization |
| 1 new bucket | `attachments` (Supabase Storage) | file bytes live here, metadata lives in the table |
| Publication + 1 Sync Stream | `powersync` pub / `attachments` stream | make metadata synced (not the bytes) |

Everything else in the feature list (calendar view, month stats, batch label chip,
draggable containers, number alignment) is **UI-only — no schema change.**

---

## 1. New table: `public.attachments`

One table, but the **two attach points are structurally impossible to merge** via the
`attach_type` + "exactly one target" check:

```sql
create table public.attachments (
  id               uuid primary key default gen_random_uuid(),
  -- who uploaded it (admin for RESOURCE, the user for PROOF)
  owner_id         uuid not null references public.profiles(id) on delete cascade,

  -- WHICH attach point this belongs to (the two features stay separate here)
  attach_type      text not null check (
                     attach_type in ('BATCH_TASK_RESOURCE', 'USER_TASK_PROOF', 'PERSONAL_TASK_PROOF')
                   ),

  -- exactly one of these is set, matching attach_type (enforced below)
  batch_task_id    uuid references public.batch_tasks(id) on delete cascade,
  user_task_id     uuid references public.user_tasks(id) on delete cascade,
  personal_task_id uuid references public.personal_tasks(id) on delete cascade,

  -- file metadata (bytes live in Storage, NOT here)
  file_name        text not null,
  mime_type        text not null,
  storage_path     text not null,          -- Storage object key, e.g. 'user-tasks/<uuid>/<uuid>.jpg'
  size_bytes       bigint not null default 0,
  created_at       bigint not null,
  updated_at       bigint not null,

  constraint attachments_exactly_one_target check (
    (attach_type = 'BATCH_TASK_RESOURCE' and batch_task_id is not null
        and user_task_id is null and personal_task_id is null)
    or
    (attach_type = 'USER_TASK_PROOF' and user_task_id is not null
        and batch_task_id is null and personal_task_id is null)
    or
    (attach_type = 'PERSONAL_TASK_PROOF' and personal_task_id is not null
        and batch_task_id is null and user_task_id is null)
  )
);

-- lookups
create index idx_attachments_batch_task    on public.attachments(batch_task_id);
create index idx_attachments_user_task     on public.attachments(user_task_id);
create index idx_attachments_personal_task on public.attachments(personal_task_id);
```

**How each feature maps:**
- **Admin resource** → row with `attach_type = 'BATCH_TASK_RESOURCE'`, `batch_task_id = <task>` (file stored on Admin upload).
- **User proof of completion** → `attach_type = 'USER_TASK_PROOF'`, `user_task_id = <assignment>` — set only when the user marks a batch task Completed.
- *(Optional parity)* **Personal task proof** → `PERSONAL_TASK_PROOF`.

`ON DELETE CASCADE` from each target means removing a task/batch cleans up its rows
(file cleanup handled by a delete function — see §4).

---

## 2. New columns: `category`

Plain text, nullable, per task type:

```sql
alter table public.batch_tasks
  add column if not exists category text;      -- e.g. 'Math', 'Lab', 'Revision'

alter table public.personal_tasks
  add column if not exists category text;

-- helpful for the calendar/filters later
create index if not exists idx_batch_tasks_category    on public.batch_tasks(category);
create index if not exists idx_personal_tasks_category on public.personal_tasks(category);
```

> **Decision to make:** free-text (`category text`) keeps scope small and is enough for
> filter-by-category. A managed, color-coded list (a `task_categories` table) is only worth
> it if admins must define a fixed set per batch. Recommended: start free-text.

---

## 3. Sync wiring (PowerSync)

Since existing streams use `SELECT *`, the new `category` columns **sync automatically** —
no YAML change.

The `attachments` **table** needs:
1. Adding to the publication:
   ```sql
   alter publication powersync add table public.attachments;
   ```
2. A new Sync Stream so devices only get what they're allowed to see:

```yaml
  attachments:
    auto_subscribe: true
    query: |
      SELECT a.*
      FROM attachments a
      LEFT JOIN user_tasks ut ON ut.id = a.user_task_id
      LEFT JOIN personal_tasks pt ON pt.id = a.personal_task_id
      LEFT JOIN batch_tasks bt ON bt.id = COALESCE(a.batch_task_id, ut.task_id)
      LEFT JOIN batches b ON b.id = bt.batch_id
      WHERE a.owner_id = auth.user_id()              -- own uploads (proofs)
         OR b.id = (SELECT batch_id FROM profiles WHERE id = auth.user_id())  -- own batch's task resources
```
   *(Admin visibility of proofs is handled separately in an admin stream later — open work.)*

**Files are NOT synced by PowerSync** — only these metadata rows. The app fetches the
actual file from Storage on demand.

---

## 4. Storage bucket rules (Supabase Storage)

```sql
-- bucket (private: only authenticated clients / signed URLs can fetch)
insert into storage.buckets (id, name, public)
values ('attachments', 'attachments', false);
```

**Object path convention** (keeps ownership checkable):
```
attachments/
  batch-tasks/{taskId}/{attachmentId}.{ext}              ← Admin RESOURCE
  user-tasks/{userId}/{userTaskId}/{attachmentId}.{ext}  ← proof
  personal-tasks/{userId}/{personalTaskId}/{attachmentId}.{ext}
```

**RLS policy sketches on `storage.objects`** (refined during implementation):
- **INSERT** — `auth.role() = 'authenticated'` and the path prefix contains `auth.uid()`
  (users can only upload into their own folder) **OR** uploader is an ADMIN posting under a
  `batch-tasks/` path.
- **SELECT** — owner of the row **or** member of the batch that owns the task resource
  **or** admin of that batch.
- **DELETE** — owner **or** admin (so cleanup can cascade when tasks/batches are deleted).

---

## 5. Open questions before implementation

1. **Bucket visibility:** private + signed URLs (recommended, proofs are personal) vs.
   public-read (simpler, weaker privacy)?
2. **Max file size / accepted types:** images only for proofs? PDF + images for admin
   resources? Size cap (e.g., 5–10 MB)?
3. **Proof retention:** if a user *leaves a batch* or a task is *deleted*, should their
   completion proofs be deleted too (current cascade design) or archived?
4. **Category:** free-text (recommended) vs. managed list per batch?
5. **Admin visibility of proofs:** confirm admins should be able to **view proof images**
   (affects RLS + the admin stream).

