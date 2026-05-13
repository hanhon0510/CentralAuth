create table refresh_tokens (
    id uuid primary key,
    user_id uuid not null,
    token_hash varchar(255) not null,
    issued_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    revoked boolean not null default false,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    constraint refresh_tokens_user_id_fk foreign key (user_id) references users (id) on delete cascade,
    constraint refresh_tokens_token_hash_key unique (token_hash),
    constraint refresh_tokens_expires_after_issued_check check (expires_at > issued_at)
);

create index refresh_tokens_user_id_idx on refresh_tokens (user_id);
create index refresh_tokens_expires_at_idx on refresh_tokens (expires_at);
create index refresh_tokens_revoked_idx on refresh_tokens (revoked);
