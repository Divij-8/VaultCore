create table users(
    id uuid primary key,
    name varchar(150) not null,
    phone_number varchar(20) unique not null,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp
);

create table accounts(
    id uuid primary key,
    account_number varchar(20) unique not null,
    user_id uuid not null references users(id),
    account_type varchar(20) not null,
    status varchar(20) not null,
    created_at timestamptz not null default current_timestamp,
    check (account_type in ('USER', 'SYSTEM')),
    check (status in ('ACTIVE', 'CLOSED'))
);