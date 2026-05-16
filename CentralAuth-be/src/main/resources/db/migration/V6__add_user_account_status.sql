alter table users add column account_status varchar(32);

update users
set account_status = case
    when email_verified = false then 'UNVERIFIED'
    when enabled = false then 'DISABLED'
    else 'ACTIVE'
end;

alter table users alter column account_status set not null;
alter table users alter column account_status set default 'UNVERIFIED';

alter table users add constraint users_account_status_check
    check (account_status in ('ACTIVE', 'DISABLED', 'LOCKED', 'UNVERIFIED'));

create index users_account_status_idx on users (account_status);
