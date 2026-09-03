# Supabase + PowerSync — Clean Setup Runbook

**Goal:** From zero → a connected PowerSync instance for the Todolist app.
**Prereqs:** Supabase account, PowerSync Cloud account.
**Note:** All DB passwords stay out of chat and out of the repo. This doc uses placeholders.

---

## Phase 0 — Create the Supabase project

1. https://supabase.com/dashboard → **New project**.
2. Fill in: organization, **project name**, and **region closest to you**.
3. **Database password:** choose a strong one (≠ anything used before). Save it in your password manager. You will NOT need to paste it anywhere public.
4. After creation, note your **Project Ref** (in the dashboard URL `supabase.com/dashboard/project/<ref>`, also under *Project Settings → General*).
5. **Immediately enable the IPv4 add-on** (PowerSync Cloud cannot reach IPv6-only direct connections):
   - *Project Settings → Add-ons → IPv4 address → Enable* (small monthly fee; verify price).
   - Wait a few minutes, then verify in PowerShell:
     ```powershell
     Resolve-DnsName db.<your-project-ref>.supabase.co
     ```
     You want an **A (IPv4)** record to appear.

## Phase 1 — One clean SQL script (the entire backend)

Open **SQL Editor** → paste the full script from `supabase-setup-script.sql` (section below) →
replace `<POWERSYNC_DB_PASSWORD>` with a **new strong password for the `powersync` role** →
**Run**.

The script creates, in order:
1. All 6 tables (`batches`, `profiles`, `batch_tasks`, `user_tasks`, `personal_tasks`, `notifications`)
2. RLS enabled on all 6 (no policies = denied by default via REST)
3. The `powersync` role (LOGIN + REPLICATION + BYPASSRLS)
4. Schema/table/sequence grants + default privileges for `powersync`
5. The `powersync` publication containing the 6 tables

It is idempotent (safe to re-run).

## Phase 2 — Enable Supabase Auth (for app login later)

1. *Authentication → Sign In / Providers → Email* → **enable**.
2. For development, turn **off "Confirm email"** so test accounts work instantly.

## Phase 3 — Verify everything

Run the verification queries (section below). All should come back as described.

## Phase 4 — Connect PowerSync

1. https://dashboard.powersync.com → create a **new PowerSync instance**.
2. Use **Connect to Supabase / database** with the **direct** connection string:
   ```
   postgresql://powersync:<POWERSYNC_DB_PASSWORD>@db.<your-project-ref>.supabase.co:5432/postgres
   ```
   - Direct host only (`db.<ref>.supabase.co`), **never** the pooler.
   - Username `powersync` (no `.ref` suffix — that is Supavisor-only).
   - If SSL is required, append `?sslmode=require`.
3. Connect & test. This should succeed on the direct connection once the IPv4 add-on is live.

## Phase 5 — Deploy Sync Streams

Next step after connection succeeds (draft YAML in `powersync-implementation-plan.md` §5).

---

## Optional housekeeping (recommended by PowerSync docs)

On free/hobby projects, logical replication can grow the WAL and fill disk. After the CLI is
installed and `supabase login` done, run:

```powershell
supabase --experimental --project-ref <your-project-ref> postgres-config update --config max_wal_size=1GB
supabase --experimental --project-ref <your-project-ref> postgres-config update --config max_slot_wal_keep_size=1GB
```

## The full setup script

```sql
-- =====================================================================
-- Todolist + PowerSync — clean Supabase backend setup
-- Run once in the SQL Editor. Idempotent (safe to re-run).
-- Replace <POWERSYNC_DB_PASSWORD> before running.
-- =====================================================================

-- 1) Tables -----------------------------------------------------------
create table if not exists public.batches (
  id         uuid primary key default gen_random_uuid(),
  name       text not null unique,
  created_at bigint not null,
  updated_at bigint not null
);

create table if not exists public.profiles (
  id         uuid primary key references auth.users(id) on delete cascade,
  username   text,
  role       text not null default 'USER' check (role in ('ADMIN','USER')),
  batch_id   uuid references public.batches(id) on delete set null,
  created_at bigint not null,
  updated_at bigint not null
);

create table if not exists public.batch_tasks (
  id          uuid primary key default gen_random_uuid(),
  title       text not null,
  description text not null default '',
  due_date    bigint not null,
  priority    text not null check (priority in ('LOW','MEDIUM','HIGH')),
  batch_id    uuid not null references public.batches(id) on delete cascade,
  created_at  bigint not null,
  updated_at  bigint not null
);

create table if not exists public.user_tasks (
  id                    uuid primary key default gen_random_uuid(),
  user_id               uuid not null references public.profiles(id) on delete cascade,
  task_id               uuid not null references public.batch_tasks(id) on delete cascade,
  status                text not null default 'ACCEPTED' check (status in ('ACCEPTED','ONGOING','COMPLETED')),
  completed_at          bigint,
  has_proof             boolean not null default false,
  last_notified_due     bigint not null default 0,
  last_notified_overdue bigint not null default 0,
  updated_at            bigint not null,
  unique (user_id, task_id)
);

create table if not exists public.personal_tasks (
  id                    uuid primary key default gen_random_uuid(),
  user_id               uuid not null references public.profiles(id) on delete cascade,
  title                 text not null,
  description           text not null default '',
  due_date              bigint not null,
  priority              text not null check (priority in ('LOW','MEDIUM','HIGH')),
  status                text not null default 'ACCEPTED' check (status in ('ACCEPTED','ONGOING','COMPLETED')),
  created_at            bigint not null,
  completed_at          bigint,
  has_proof             boolean not null default false,
  last_notified_due     bigint not null default 0,
  last_notified_overdue bigint not null default 0,
  updated_at            bigint not null
);

create table if not exists public.notifications (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references public.profiles(id) on delete cascade,
  message    text not null,
  type       text not null,
  task_title text not null,
  created_at bigint not null,
  read       boolean not null default false
);

-- 2) Row Level Security (no policies = locked down via REST) ----------
alter table public.profiles        enable row level security;
alter table public.batches         enable row level security;
alter table public.batch_tasks     enable row level security;
alter table public.user_tasks      enable row level security;
alter table public.personal_tasks  enable row level security;
alter table public.notifications   enable row level security;

-- 3) PowerSync role ---------------------------------------------------
do $$
begin
  if not exists (select from pg_roles where rolname = 'powersync') then
    create role powersync with login replication bypassrls password '<POWERSYNC_DB_PASSWORD>';
  else
    alter role powersync with login replication bypassrls password '<POWERSYNC_DB_PASSWORD>';
  end if;
end $$;

-- 4) Grants -----------------------------------------------------------
grant usage on schema public to powersync;
grant all privileges on all tables in schema public to powersync;
grant all privileges on all sequences in schema public to powersync;
alter default privileges in schema public grant all privileges on tables to powersync;

-- 5) Publication ------------------------------------------------------
drop publication if exists powersync;
create publication powersync for table
  public.profiles,
  public.batches,
  public.batch_tasks,
  public.user_tasks,
  public.personal_tasks,
  public.notifications;
```

## Verification queries

```sql
-- role flags (want: all = t)
select rolname, rolcanlogin, rolreplication, rolbypassrls
from pg_roles where rolname = 'powersync';

-- tables + RLS (want: 6 rows, rls = t)
select c.relname, c.relrowsecurity as rls_enabled
from pg_class c
where c.relnamespace = 'public'::regnamespace
  and c.relkind = 'r'
  and c.relname in ('profiles','batches','batch_tasks','user_tasks','personal_tasks','notifications')
order by c.relname;

-- publication tables (want: 6 rows)
select pr.schemaname || '.' || pr.tablename as published_table
from pg_publication p
join pg_publication_tables pr on pr.pubname = p.pubname
where p.pubname = 'powersync'
order by pr.tablename;

-- grants (want: SELECT/INSERT/UPDATE/DELETE for the 6 tables)
select table_name, privilege_type
from information_schema.role_table_grants
where grantee = 'powersync' and table_schema = 'public'
order by table_name, privilege_type;
```

---
*Last updated: 2026-09-03. If a step throws an error, stop and report the exact message — don't patch around it.*


