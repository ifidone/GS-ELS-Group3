create table if not exists mutual_funds (
    ticker varchar(20) primary key,
    name varchar(255) not null,
    category varchar(100) not null
);

insert into mutual_funds (ticker, name, category)
values
    ('VFIAX', 'Vanguard 500 Index Fund Admiral Shares', 'Large Blend'),
    ('VTSAX', 'Vanguard Total Stock Market Index Fund Admiral Shares', 'Large Blend'),
    ('FXAIX', 'Fidelity 500 Index Fund', 'Large Blend'),
    ('SWPPX', 'Schwab S&P 500 Index Fund', 'Large Blend'),
    ('VIGAX', 'Vanguard Growth Index Fund Admiral Shares', 'Large Growth'),
    ('VIMAX', 'Vanguard Mid-Cap Index Fund Admiral Shares', 'Mid-Cap Blend'),
    ('VSMAX', 'Vanguard Small-Cap Index Fund Admiral Shares', 'Small Blend'),
    ('VTMGX', 'Vanguard Developed Markets Index Fund Admiral Shares', 'International'),
    ('VEMAX', 'Vanguard Emerging Markets Stock Index Fund Admiral Shares', 'International'),
    ('VBTLX', 'Vanguard Total Bond Market Index Fund Admiral Shares', 'Intermediate Bond')
on conflict (ticker) do nothing;
