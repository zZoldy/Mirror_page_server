insert ignore into roles (name) values ('ROLE_SUPORTE'),('ROLE_REDACAO'),('ROLE_OPERACAO');

-- senha: admin123  (GERAR HASH BCRYPT!)
insert into users (username, password, enabled)
values ('admin', '{noop}Admin@2025', true);

insert into users (username, password, enabled)
values ('teste1', '{noop}123', true);
insert into users (username, password, enabled)
values ('teste2', '{noop}123', true);
insert into users (username, password, enabled)
values ('teste3', '{noop}123', true);


insert into user_roles (user_id, role_id)
select u.id, r.id from users u, roles r
where u.username='admin' and r.name='ROLE_SUPORTE';

insert into user_roles (user_id, role_id)
select u.id, r.id from users u, roles r
where u.username='teste1' and r.name='ROLE_REDACAO';

insert into user_roles (user_id, role_id)
select u.id, r.id from users u, roles r
where u.username='teste2' and r.name='ROLE_REDACAO';

insert into user_roles (user_id, role_id)
select u.id, r.id from users u, roles r
where u.username='teste3' and r.name='ROLE_OPERACAO';
