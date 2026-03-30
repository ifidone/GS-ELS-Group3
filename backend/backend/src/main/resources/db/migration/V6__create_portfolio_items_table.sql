create table if not exists portfolio_items (
    portfolio_id bigint not null references portfolios(id) on delete cascade,
    calculation_id bigint not null references saved_calculations(id) on delete cascade,
    created_at timestamp not null default current_timestamp,
    primary key (portfolio_id, calculation_id)
);

create index if not exists idx_portfolio_items_portfolio_id
    on portfolio_items (portfolio_id);

create index if not exists idx_portfolio_items_calculation_id
    on portfolio_items (calculation_id);
