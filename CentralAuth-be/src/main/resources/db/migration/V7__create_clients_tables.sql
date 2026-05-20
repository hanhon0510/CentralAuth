create table clients (
    client_id varchar(120) primary key,
    client_name varchar(255) not null,
    active boolean not null default true,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp
);

create table client_redirect_uris (
    client_id varchar(120) not null,
    redirect_uri varchar(2048) not null,
    created_at timestamp with time zone not null default current_timestamp,
    constraint client_redirect_uris_pk primary key (client_id, redirect_uri),
    constraint client_redirect_uris_client_fk foreign key (client_id) references clients (client_id) on delete cascade
);

create table client_allowed_origins (
    client_id varchar(120) not null,
    origin varchar(512) not null,
    created_at timestamp with time zone not null default current_timestamp,
    constraint client_allowed_origins_pk primary key (client_id, origin),
    constraint client_allowed_origins_client_fk foreign key (client_id) references clients (client_id) on delete cascade
);

create index clients_active_idx on clients (active);
create index clients_created_at_idx on clients (created_at);
