create table ai_usage_events (
 id uuid primary key,
 team_id uuid not null references app_teams(id),
 user_id uuid not null references app_users(id),
 meeting_id uuid references app_meetings(id),
 feature varchar(32) not null check (feature in ('MEETING_ASK','MEETING_SUMMARY')),
 provider varchar(40) not null,
 model varchar(160) not null,
 input_tokens bigint not null,
 output_tokens bigint not null,
 total_tokens bigint not null,
 estimated boolean not null,
 created_at timestamp with time zone not null
);

create index ai_usage_team_user_created_idx on ai_usage_events(team_id,user_id,created_at);
