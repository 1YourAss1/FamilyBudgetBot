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
    ('products', 'продукты', true, 'магазин, магаз'),
    ('health', 'здоровье', true, 'больница, аптека, лекарства, таблетки'),
    ('bills', 'платежи', true, 'квитанция, квитанции, камуналка, сад, садик, налог, налоги, страховка, общага'),
    ('food', 'еда', true, 'вкусвилл, вкусвил, шаурма, шава, шавуха, фастфут, мак, бургеркинг, булочка, kfc, кфс, кафе, кофе'),
    ('telecom', 'связь', true, 'телефон, йота, yota, инет, inet'),
    ('transport', 'транспорт', false, 'мара, метро, автобус, такси'),
    ('subscriptions', 'подписки', false, 'подписка'),
    ('car', 'машина', false, 'бензин, бенз, запчасти'),
    ('shopping', 'шоппинг', false, 'вб, одежда, ногти, маркет, озон, али, мегамаркет'),
    ('other', 'прочее', false, '');

insert into budget(codename, daily_limit) values ('base', 500);
