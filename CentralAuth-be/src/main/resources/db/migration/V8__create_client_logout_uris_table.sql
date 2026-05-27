create table client_logout_uris (
    client_id varchar(120) not null,
    logout_uri varchar(2048) not null,
    created_at timestamp with time zone not null default current_timestamp,
    constraint client_logout_uris_pk primary key (client_id, logout_uri),
    constraint client_logout_uris_client_fk foreign key (client_id) references clients (client_id) on delete cascade
);
