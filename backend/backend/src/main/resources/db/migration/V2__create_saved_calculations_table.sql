create table if not exists saved_calculations (
    id bigserial primary key,
    uid varchar(128) not null references users(uid) on delete cascade,
    ticker varchar(20) not null,
    initial_investment numeric(18, 4) not null,
    years numeric(10, 2) not null,
    beta numeric(12, 6) not null,
    expected_return numeric(12, 6) not null,
    future_value numeric(18, 4) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_saved_calculations_uid_created_at
    on saved_calculations (uid, created_at desc);
