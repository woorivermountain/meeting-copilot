create extension if not exists pgcrypto;

create type system_role as enum ('owner', 'member');
create type action_status as enum ('todo', 'doing', 'done');
create type subscription_status as enum ('trialing', 'active', 'past_due', 'canceled', 'unpaid', 'incomplete');

create table users (
  id uuid primary key default gen_random_uuid(),
  email text not null unique,
  name varchar(20) not null,
  password_hash text not null,
  created_at timestamptz not null default now()
);

create table teams (
  id uuid primary key default gen_random_uuid(),
  name varchar(80) not null,
  owner_id uuid not null references users(id),
  invite_code_hash text,
  created_at timestamptz not null default now()
);

create table team_members (
  team_id uuid not null references teams(id) on delete cascade,
  user_id uuid not null references users(id) on delete cascade,
  system_role system_role not null default 'member',
  primary key (team_id, user_id)
);

create table meetings (
  id uuid primary key default gen_random_uuid(),
  team_id uuid not null references teams(id) on delete cascade,
  title varchar(120),
  started_at timestamptz not null default now(),
  ended_at timestamptz,
  created_by uuid not null references users(id)
  -- Intentionally no transcript/audio column (REQ-N-002).
);

create table meeting_participants (
  meeting_id uuid not null references meetings(id) on delete cascade,
  user_id uuid not null references users(id),
  participant_name_snapshot varchar(80) not null,
  meeting_role_label varchar(20) not null,
  primary key (meeting_id, user_id)
);

create table agenda_items (
  id uuid primary key default gen_random_uuid(),
  meeting_id uuid not null references meetings(id) on delete cascade,
  seq integer not null check (seq > 0),
  title varchar(120) not null,
  planned_min integer check (planned_min > 0),
  actual_min integer check (actual_min >= 0),
  unique (meeting_id, seq)
);

create table decisions (
  id uuid primary key default gen_random_uuid(),
  meeting_id uuid not null references meetings(id) on delete cascade,
  what text not null,
  why text,
  confidence integer check (confidence between 0 and 100),
  reviewed_by uuid not null references users(id),
  reviewed_at timestamptz not null,
  supersedes_id uuid references decisions(id),
  status varchar(16) not null default 'active',
  decided_at timestamptz not null default now()
);

create table action_items (
  id uuid primary key default gen_random_uuid(),
  meeting_id uuid not null references meetings(id) on delete cascade,
  assignee_id uuid references users(id),
  assignee_name_snapshot varchar(80),
  from_decision_id uuid references decisions(id),
  what text not null,
  due_date date,
  status action_status not null default 'todo',
  reviewed_by uuid not null references users(id),
  reviewed_at timestamptz not null,
  created_at timestamptz not null default now()
);

create table open_issues (
  id uuid primary key default gen_random_uuid(),
  meeting_id uuid not null references meetings(id) on delete cascade,
  question text not null,
  confidence integer check (confidence between 0 and 100),
  reviewed_by uuid not null references users(id),
  reviewed_at timestamptz not null,
  resolved_by_id uuid references decisions(id)
);

create table wrapup_confirmations (
  meeting_id uuid primary key references meetings(id) on delete cascade,
  reviewed_by uuid not null references users(id),
  reviewed_at timestamptz not null
);

create table billing_subscriptions (
  team_id uuid primary key references teams(id) on delete cascade,
  stripe_customer_id text not null unique,
  stripe_subscription_id text not null unique,
  stripe_product_id text not null,
  status subscription_status not null,
  seats integer not null check (seats >= 1),
  current_period_end timestamptz,
  updated_at timestamptz not null default now()
);

create table usage_ledger (
  id bigserial primary key,
  team_id uuid not null references teams(id) on delete cascade,
  meeting_id uuid references meetings(id) on delete set null,
  event_type varchar(32) not null,
  model varchar(80),
  input_tokens integer not null default 0,
  output_tokens integer not null default 0,
  latency_ms integer,
  estimated_cost_usd numeric(12, 6) not null default 0,
  created_at timestamptz not null default now()
  -- No prompt, transcript, or generated content fields by design.
);

create index meetings_team_started_idx on meetings(team_id, started_at desc);
create index action_items_status_idx on action_items(meeting_id, status);
create index usage_ledger_team_created_idx on usage_ledger(team_id, created_at desc);
