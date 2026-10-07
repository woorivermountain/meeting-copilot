-- A team is the workspace security boundary; department labels do not grant access.
create table knowledge_boxes (
  id uuid primary key default gen_random_uuid(),
  team_id uuid not null references teams(id) on delete cascade,
  name varchar(80) not null,
  department varchar(80) not null,
  unique (team_id, id)
);
create table knowledge_agents (
  id uuid primary key default gen_random_uuid(),
  team_id uuid not null references teams(id) on delete cascade,
  name varchar(80) not null,
  department varchar(80) not null,
  unique (team_id, id)
);
create table knowledge_agent_boxes (
  team_id uuid not null,
  agent_id uuid not null,
  box_id uuid not null,
  primary key (agent_id, box_id),
  foreign key (team_id, agent_id) references knowledge_agents(team_id, id) on delete cascade,
  foreign key (team_id, box_id) references knowledge_boxes(team_id, id) on delete cascade
);
create table knowledge_documents (
  id uuid primary key default gen_random_uuid(),
  box_id uuid not null references knowledge_boxes(id) on delete cascade,
  title varchar(160) not null,
  content text not null check (length(content) between 1 and 30000),
  version integer not null default 1,
  approved_by uuid not null references users(id),
  updated_at timestamptz not null default now()
);
create table knowledge_audit (
  id uuid primary key default gen_random_uuid(),
  team_id uuid not null references teams(id) on delete cascade,
  actor_id uuid not null references users(id),
  operation varchar(40) not null,
  resource_id uuid,
  created_at timestamptz not null default now()
  -- Do not log document content, questions, or meeting transcripts.
);
create index knowledge_documents_box_idx on knowledge_documents(box_id);
create index knowledge_audit_team_idx on knowledge_audit(team_id, created_at desc);
