create table users (
    id uuid primary key,
    email varchar(320) not null,
    password_hash varchar(255) not null,
    display_name varchar(120),
    enabled boolean not null default true,
    email_verified boolean not null default false,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    constraint users_email_key unique (email)
);

create index users_enabled_idx on users (enabled);
