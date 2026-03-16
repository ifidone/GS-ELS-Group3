create table if not exists users (
    uid varchar(128) primary key,
    email varchar(255),
    display_name varchar(255),
    photo_url text,
    auth_provider varchar(100),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);
