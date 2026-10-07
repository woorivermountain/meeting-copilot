alter table app_knowledge_boxes add column created_by uuid references app_users(id);
create table knowledge_grants (box_id uuid not null references app_knowledge_boxes(id), user_id uuid not null references app_users(id), primary key(box_id,user_id));
