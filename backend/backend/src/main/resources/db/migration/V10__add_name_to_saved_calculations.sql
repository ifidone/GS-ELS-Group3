alter table saved_calculations
    add column if not exists name varchar(255);

update saved_calculations
set name = ticker
where name is null;

alter table saved_calculations
    alter column name set not null;
