alter table app_meetings add column deleted_at timestamp with time zone;
alter table app_meetings add column deleted_by uuid references app_users(id);
create index meetings_trash_idx on app_meetings(team_id,deleted_at);
