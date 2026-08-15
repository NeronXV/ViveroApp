begin;

create table public.roles (
    id smallint generated always as identity primary key,
    name text not null unique check (name in ('SALES', 'CASHIER', 'INVENTORY', 'MANAGER', 'ADMIN', 'OWNER')),
    display_name text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.permissions (
    name text primary key check (name ~ '^[A-Z][A-Z0-9_]{2,63}$'),
    description text not null,
    created_at timestamptz not null default now()
);

create table public.role_permissions (
    role_id smallint not null references public.roles(id) on delete cascade,
    permission_name text not null references public.permissions(name) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (role_id, permission_name)
);

create table public.branches (
    id uuid primary key default gen_random_uuid(),
    code text not null unique check (char_length(trim(code)) between 2 and 24),
    name text not null check (char_length(trim(name)) between 2 and 120),
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    full_name text not null check (char_length(trim(full_name)) between 2 and 160),
    avatar_path text,
    branch_id uuid references public.branches(id) on delete set null,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.user_roles (
    user_id uuid primary key references public.profiles(id) on delete cascade,
    role_id smallint not null references public.roles(id) on delete restrict,
    assigned_by uuid references auth.users(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index role_permissions_permission_idx on public.role_permissions(permission_name, role_id);
create index profiles_branch_id_idx on public.profiles(branch_id);
create index profiles_active_idx on public.profiles(is_active) where is_active;
create index user_roles_role_id_idx on public.user_roles(role_id);

insert into public.roles (name, display_name) values
    ('SALES', 'Ventas'),
    ('CASHIER', 'Cajero'),
    ('INVENTORY', 'Inventario'),
    ('MANAGER', 'Gerente'),
    ('ADMIN', 'Administrador'),
    ('OWNER', 'Propietario');

insert into public.permissions (name, description) values
    ('VIEW_CATALOG', 'Consultar el catalogo disponible'),
    ('SCAN_PRODUCTS', 'Escanear codigos de producto'),
    ('CREATE_SALES', 'Crear y enviar comandas a caja'),
    ('VIEW_OWN_SALES', 'Consultar ventas creadas por la propia persona'),
    ('OPERATE_CASHIER', 'Atender la bandeja y operaciones de caja'),
    ('VIEW_BRANCH_SALES', 'Consultar ventas de la sucursal asignada'),
    ('VIEW_ALL_SALES', 'Consultar ventas de todas las sucursales'),
    ('MANAGE_PRODUCTS', 'Crear y mantener productos e imagenes'),
    ('MANAGE_PRICES', 'Actualizar precios de productos'),
    ('MANAGE_DISCOUNTS', 'Autorizar y administrar descuentos'),
    ('MANAGE_INVENTORY', 'Recibir mercancia y ajustar existencias'),
    ('VIEW_INVENTORY_ALERTS', 'Consultar alertas de inventario'),
    ('VIEW_REPORTS', 'Consultar reportes autorizados'),
    ('MANAGE_BRANCHES', 'Administrar sucursales'),
    ('MANAGE_USERS', 'Administrar perfiles de personal'),
    ('ASSIGN_ROLES', 'Asignar roles respetando la jerarquia'),
    ('VIEW_AUDIT', 'Consultar la auditoria completa'),
    ('MANAGE_SETTINGS', 'Administrar configuracion general');

insert into public.role_permissions (role_id, permission_name)
select r.id, assigned.permission_name
from (values
    ('SALES', 'VIEW_CATALOG'),
    ('SALES', 'SCAN_PRODUCTS'),
    ('SALES', 'CREATE_SALES'),
    ('SALES', 'VIEW_OWN_SALES'),
    ('CASHIER', 'VIEW_CATALOG'),
    ('CASHIER', 'OPERATE_CASHIER'),
    ('INVENTORY', 'VIEW_CATALOG'),
    ('INVENTORY', 'SCAN_PRODUCTS'),
    ('INVENTORY', 'MANAGE_PRODUCTS'),
    ('INVENTORY', 'MANAGE_INVENTORY'),
    ('INVENTORY', 'VIEW_INVENTORY_ALERTS'),
    ('MANAGER', 'VIEW_CATALOG'),
    ('MANAGER', 'SCAN_PRODUCTS'),
    ('MANAGER', 'CREATE_SALES'),
    ('MANAGER', 'VIEW_OWN_SALES'),
    ('MANAGER', 'OPERATE_CASHIER'),
    ('MANAGER', 'VIEW_BRANCH_SALES'),
    ('MANAGER', 'MANAGE_PRODUCTS'),
    ('MANAGER', 'MANAGE_PRICES'),
    ('MANAGER', 'MANAGE_DISCOUNTS'),
    ('MANAGER', 'MANAGE_INVENTORY'),
    ('MANAGER', 'VIEW_INVENTORY_ALERTS'),
    ('MANAGER', 'VIEW_REPORTS')
) as assigned(role_name, permission_name)
join public.roles r on r.name = assigned.role_name;

insert into public.role_permissions (role_id, permission_name)
select r.id, p.name
from public.roles r
cross join public.permissions p
where r.name in ('ADMIN', 'OWNER');

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at = pg_catalog.now();
    return new;
end;
$$;

revoke all on function public.set_updated_at() from public;

create trigger roles_set_updated_at before update on public.roles
for each row execute function public.set_updated_at();
create trigger branches_set_updated_at before update on public.branches
for each row execute function public.set_updated_at();
create trigger profiles_set_updated_at before update on public.profiles
for each row execute function public.set_updated_at();
create trigger user_roles_set_updated_at before update on public.user_roles
for each row execute function public.set_updated_at();

create or replace function public.has_permission(required_permission pg_catalog.text)
returns pg_catalog.boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.user_roles ur
        join public.profiles p on p.id = ur.user_id
        join public.role_permissions rp on rp.role_id = ur.role_id
        where ur.user_id = auth.uid()
          and p.is_active
          and rp.permission_name = required_permission
    );
$$;

revoke all on function public.has_permission(pg_catalog.text) from public;
grant execute on function public.has_permission(pg_catalog.text) to authenticated;

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.profiles (id, full_name)
    values (
        new.id,
        pg_catalog.coalesce(
            pg_catalog.nullif(pg_catalog.btrim(new.raw_user_meta_data ->> 'full_name'), ''),
            pg_catalog.split_part(pg_catalog.coalesce(new.email, 'Usuario'), '@', 1)
        )
    );
    return new;
end;
$$;

revoke all on function public.handle_new_user() from public;

create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

create or replace function public.bootstrap_first_owner(p_user_id pg_catalog.uuid)
returns pg_catalog.void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner_role_id pg_catalog.int2;
begin
    lock table public.user_roles in access exclusive mode;

    if exists (select 1 from public.user_roles) then
        raise exception using errcode = '42501', message = 'Bootstrap already completed';
    end if;

    if not exists (
        select 1 from public.profiles p where p.id = p_user_id and p.is_active
    ) then
        raise exception using errcode = '22023', message = 'Target profile is unavailable';
    end if;

    select r.id into strict v_owner_role_id
    from public.roles r
    where r.name = 'OWNER';

    insert into public.user_roles (user_id, role_id, assigned_by)
    values (p_user_id, v_owner_role_id, p_user_id);
end;
$$;

revoke all on function public.bootstrap_first_owner(pg_catalog.uuid) from public;
grant execute on function public.bootstrap_first_owner(pg_catalog.uuid) to service_role;

create or replace function public.assign_user_role(
    p_user_id pg_catalog.uuid,
    p_role_name pg_catalog.text
)
returns pg_catalog.void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_actor_role pg_catalog.text;
    v_target_role pg_catalog.text;
    v_requested_role_id pg_catalog.int2;
begin
    if v_actor_id is null or not public.has_permission('ASSIGN_ROLES') then
        raise exception using errcode = '42501', message = 'Role assignment is not allowed';
    end if;

    lock table public.user_roles in share row exclusive mode;

    select r.name into strict v_actor_role
    from public.user_roles ur
    join public.roles r on r.id = ur.role_id
    where ur.user_id = v_actor_id;

    if v_actor_role not in ('ADMIN', 'OWNER') then
        raise exception using errcode = '42501', message = 'Role assignment is not allowed';
    end if;

    if not exists (
        select 1 from public.profiles p where p.id = p_user_id and p.is_active
    ) then
        raise exception using errcode = '22023', message = 'Target profile is unavailable';
    end if;

    select r.id into strict v_requested_role_id
    from public.roles r
    where r.name = p_role_name;

    select r.name into v_target_role
    from public.user_roles ur
    join public.roles r on r.id = ur.role_id
    where ur.user_id = p_user_id;

    if v_actor_role = 'ADMIN' and (p_role_name = 'OWNER' or v_target_role = 'OWNER') then
        raise exception using errcode = '42501', message = 'ADMIN cannot grant or modify OWNER';
    end if;

    if v_target_role = 'OWNER'
       and p_role_name <> 'OWNER'
       and (select pg_catalog.count(*)
            from public.user_roles ur
            join public.roles r on r.id = ur.role_id
            where r.name = 'OWNER') <= 1 then
        raise exception using errcode = '42501', message = 'The last OWNER cannot be reassigned';
    end if;

    insert into public.user_roles (user_id, role_id, assigned_by)
    values (p_user_id, v_requested_role_id, v_actor_id)
    on conflict (user_id) do update
    set role_id = excluded.role_id,
        assigned_by = excluded.assigned_by,
        updated_at = pg_catalog.now();
end;
$$;

revoke all on function public.assign_user_role(pg_catalog.uuid, pg_catalog.text) from public;
grant execute on function public.assign_user_role(pg_catalog.uuid, pg_catalog.text) to authenticated;

alter table public.roles enable row level security;
alter table public.permissions enable row level security;
alter table public.role_permissions enable row level security;
alter table public.branches enable row level security;
alter table public.profiles enable row level security;
alter table public.user_roles enable row level security;

create policy roles_read_authenticated on public.roles
for select to authenticated using (true);

create policy permissions_read_authenticated on public.permissions
for select to authenticated using (true);

create policy role_permissions_read_authenticated on public.role_permissions
for select to authenticated using (true);

create policy branches_read_authenticated on public.branches
for select to authenticated
using (
    is_active
    or public.has_permission('MANAGE_BRANCHES')
    or public.has_permission('VIEW_REPORTS')
);

create policy profiles_read_self_or_management on public.profiles
for select to authenticated
using (id = auth.uid() or public.has_permission('MANAGE_USERS'));

create policy profiles_update_personal_fields on public.profiles
for update to authenticated
using (id = auth.uid() and is_active)
with check (id = auth.uid() and is_active);

create policy user_roles_read_self_or_management on public.user_roles
for select to authenticated
using (user_id = auth.uid() or public.has_permission('MANAGE_USERS'));

grant select on public.roles, public.permissions, public.role_permissions, public.branches to authenticated;
grant select on public.profiles to authenticated;
grant update (full_name, avatar_path) on public.profiles to authenticated;
grant select on public.user_roles to authenticated;

comment on column public.profiles.full_name is 'Campo personal editable por la propia persona.';
comment on column public.profiles.avatar_path is 'Campo personal editable por la propia persona.';
comment on column public.profiles.branch_id is 'Campo protegido; solo se modifica mediante administracion autorizada.';
comment on column public.profiles.is_active is 'Campo protegido; solo se modifica mediante administracion autorizada.';

commit;
