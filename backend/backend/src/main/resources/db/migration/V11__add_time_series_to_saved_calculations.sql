alter table saved_calculations
    add column if not exists time_series jsonb;

update saved_calculations
set time_series = '{}'::jsonb
where time_series is null;

alter table saved_calculations
    alter column time_series set not null;
