create table budget(
    codename varchar(255) primary key,
    daily_limit integer
);

create table category(
    codename varchar(255) primary key,
    name varchar(255),
    is_base_expense boolean,
    aliases text
);

create table expense(
    id integer primary key,
    user_id integer,
    amount integer,
    created datetime,
    category_codename integer,
    raw_text text,
    FOREIGN KEY(category_codename) REFERENCES category(codename)
);

insert into category(codename, name, is_base_expense, aliases)
values
    ('products', 'продукты', true, 'еда'),
    ('dinner', 'обед', true, 'вкусвилл,вкусвил,шаурма,шава,шавуха'),
    ('cafe', 'кафе', true, 'фастфут,мак,бургеркинг,булочка,kfc,кфс'),
    ('coffee', 'кофе', false, ''),
    ('transport', 'общ. транспорт', false, 'метро,автобус,такси'),
    ('phone', 'телефон', false, 'йота,связь'),
    ('internet', 'интернет', false, 'инет,inet'),
    ('subscriptions', 'подписки', false, 'подписка'),
    ('other', 'прочее', false, '');

insert into budget(codename, daily_limit) values ('base', 500);
