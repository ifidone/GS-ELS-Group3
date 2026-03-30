drop index if exists ux_portfolios_uid_name_lower;

create unique index if not exists ux_portfolios_name_lower
    on portfolios (lower(name));
