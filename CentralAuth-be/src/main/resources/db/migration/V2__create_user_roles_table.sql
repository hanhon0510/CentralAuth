create table user_roles (
    user_id uuid not null,
    role varchar(64) not null,
    created_at timestamp with time zone not null default current_timestamp,
    constraint user_roles_pk primary key (user_id, role),
    constraint user_roles_user_id_fk foreign key (user_id) references users (id) on delete cascade
);

create index user_roles_role_idx on user_roles (role);
