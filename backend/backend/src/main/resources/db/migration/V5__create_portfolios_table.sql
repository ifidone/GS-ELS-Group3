create table if not exists portfolios (
    id bigserial primary key,
    uid varchar(128) not null references users(uid) on delete cascade,
    name varchar(255) not null,
    description text,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_portfolios_uid_created_at
    on portfolios (uid, created_at desc);
