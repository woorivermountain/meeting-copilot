alter table app_knowledge_boxes add column sharing varchar(20) not null default 'PRIVATE' check (sharing in ('PRIVATE','TEAM_SHARED'));
create table app_knowledge_access_requests (
 id uuid primary key,
 box_id uuid not null references app_knowledge_boxes(id),
 requester_id uuid not null references app_users(id),
 status varchar(20) not null check (status in ('PENDING','APPROVED','DENIED')),
 created_at timestamp with time zone not null,
 decided_at timestamp with time zone,
 unique(box_id,requester_id)
);
