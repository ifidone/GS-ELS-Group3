create unique index if not exists ux_portfolios_uid_name_lower
    on portfolios (uid, lower(name));
