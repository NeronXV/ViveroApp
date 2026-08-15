begin;

create table public.roles (
    id smallint generated always as identity primary key,
    name text not null unique check (name in ('WORKER', 'CASHIER', 'INVENTORY', 'MANAGER', 'ADMIN', 'OWNER')),
    display_name text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
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

create index profiles_branch_id_idx on public.profiles(branch_id);
create index profiles_active_idx on public.profiles(is_active) where is_active;
create index user_roles_role_id_idx on public.user_roles(role_id);

insert into public.roles (name, display_name) values
    ('WORKER', 'Trabajador'),
    ('CASHIER', 'Cajero'),
    ('INVENTORY', 'Inventario'),
    ('MANAGER', 'Gerente'),
    ('ADMIN', 'Administrador'),
    ('OWNER', 'Propietario');

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger roles_set_updated_at before update on public.roles
for each row execute function public.set_updated_at();
create trigger branches_set_updated_at before update on public.branches
for each row execute function public.set_updated_at();
create trigger profiles_set_updated_at before update on public.profiles
for each row execute function public.set_updated_at();
create trigger user_roles_set_updated_at before update on public.user_roles
for each row execute function public.set_updated_at();

create or replace function public.has_any_role(required_roles text[])
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.user_roles ur
        join public.roles r on r.id = ur.role_id
        join public.profiles p on p.id = ur.user_id
        where ur.user_id = auth.uid()
          and p.is_active
          and r.name = any(required_roles)
    );
$$;

revoke all on function public.has_any_role(text[]) from public;
grant execute on function public.has_any_role(text[]) to authenticated;

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
        coalesce(nullif(trim(new.raw_user_meta_data ->> 'full_name'), ''), split_part(coalesce(new.email, 'Usuario'), '@', 1))
    );
    return new;
end;
$$;

create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

alter table public.roles enable row level security;
alter table public.branches enable row level security;
alter table public.profiles enable row level security;
alter table public.user_roles enable row level security;

create policy roles_read_authenticated on public.roles
for select to authenticated using (true);

create policy branches_read_authenticated on public.branches
for select to authenticated using (is_active or public.has_any_role(array['MANAGER', 'ADMIN', 'OWNER']));

create policy profiles_read_self_or_management on public.profiles
for select to authenticated
using (id = auth.uid() or public.has_any_role(array['MANAGER', 'ADMIN', 'OWNER']));

create policy profiles_update_self on public.profiles
for update to authenticated
using (id = auth.uid() and is_active)
with check (id = auth.uid() and is_active);

create policy profiles_manage_admin on public.profiles
for update to authenticated
using (public.has_any_role(array['ADMIN']))
with check (public.has_any_role(array['ADMIN']));

create policy user_roles_read_self_or_management on public.user_roles
for select to authenticated
using (user_id = auth.uid() or public.has_any_role(array['MANAGER', 'ADMIN', 'OWNER']));

create policy user_roles_insert_admin on public.user_roles
for insert to authenticated
with check (public.has_any_role(array['ADMIN']));

create policy user_roles_update_admin on public.user_roles
for update to authenticated
using (public.has_any_role(array['ADMIN']))
with check (public.has_any_role(array['ADMIN']));

create policy user_roles_delete_admin on public.user_roles
for delete to authenticated
using (public.has_any_role(array['ADMIN']));

grant select on public.roles, public.branches to authenticated;
grant select, update on public.profiles to authenticated;
grant select, insert, update, delete on public.user_roles to authenticated;

commit;
