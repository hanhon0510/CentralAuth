create table audit_logs (
    id uuid primary key,
    event_type varchar(80) not null,
    user_id uuid,
    email varchar(320),
    client_ip varchar(64),
    reason varchar(120),
    occurred_at timestamp with time zone not null,
    consumed_at timestamp with time zone not null default current_timestamp,
    kafka_topic varchar(255) not null,
    kafka_key varchar(320),
    payload_json text not null,
    created_at timestamp with time zone not null default current_timestamp
);

create index audit_logs_event_type_occurred_at_idx on audit_logs (event_type, occurred_at);
create index audit_logs_user_id_occurred_at_idx on audit_logs (user_id, occurred_at);
create index audit_logs_email_occurred_at_idx on audit_logs (email, occurred_at);
create index audit_logs_occurred_at_idx on audit_logs (occurred_at);
