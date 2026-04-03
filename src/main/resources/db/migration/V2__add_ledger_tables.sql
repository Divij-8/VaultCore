create table transactions(
    id uuid primary key,
    reference_id varchar(50) unique not null,
    status varchar(20) not null,
    created_at timestamptz not null default current_timestamp,
    check (status in ('PENDING', 'COMPLETED', 'FAILED'))
);

create table ledger_entries(
    id uuid primary key,
    transaction_id uuid not null references transactions(id) on delete restrict ,
    account_id uuid not null references accounts(id) on delete restrict,
    amount decimal(19, 4) not null,
    entry_type varchar(20) not null,
    created_at timestamptz not null default current_timestamp,
    check (entry_type in ('DEBIT', 'CREDIT')),
    check(amount > 0)
);

CREATE INDEX idx_ledger_entries_account_id
    ON ledger_entries(account_id);

CREATE INDEX idx_ledger_entries_transaction_id
    ON ledger_entries(transaction_id);