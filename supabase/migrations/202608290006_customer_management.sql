begin;

-- 1. Tabla de Clientes
create table public.customers (
    id uuid primary key default gen_random_uuid(),
    full_name text not null check (char_length(trim(full_name)) between 2 and 160),
    email text unique check (email is null or email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    phone text check (phone is null or char_length(trim(phone)) between 8 and 20),
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- 2. Índices y Seguridad
create index customers_full_name_lower_idx on public.customers(lower(full_name));
create index customers_email_idx on public.customers(email) where email is not null;

alter table public.customers enable row level security;

-- Políticas: Lectura para personal activo, Escritura para roles autorizados
create policy customers_read on public.customers
for select to authenticated
using (is_active or public.has_permission('MANAGE_USERS'));

create trigger customers_set_updated_at before update on public.customers
for each row execute function public.set_updated_at();

-- 3. RPCs Administrativos

-- Crear o actualizar cliente
create or replace function public.upsert_customer(
    p_id pg_catalog.uuid default null,
    p_full_name pg_catalog.text default null,
    p_email pg_catalog.text default null,
    p_phone pg_catalog.text default null,
    p_is_active pg_catalog.bool default true
)
returns public.customers
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_full_name pg_catalog.text := pg_catalog.btrim(p_full_name);
    v_email pg_catalog.text := pg_catalog.lower(pg_catalog.btrim(p_email));
    v_customer public.customers%rowtype;
begin
    if v_actor_id is null or not (public.has_permission('CREATE_SALES') or public.has_permission('MANAGE_USERS')) then
        raise exception using errcode = '42501', message = 'Customer management is not allowed';
    end if;

    if v_full_name is null or pg_catalog.char_length(v_full_name) not between 2 and 160 then
        raise exception using errcode = '22023', message = 'Customer name is invalid';
    end if;

    insert into public.customers (id, full_name, email, phone, is_active)
    values (
        coalesce(p_id, pg_catalog.gen_random_uuid()),
        v_full_name,
        nullif(v_email, ''),
        nullif(pg_catalog.btrim(p_phone), ''),
        coalesce(p_is_active, true)
    )
    on conflict (id) do update
    set full_name = excluded.full_name,
        email = excluded.email,
        phone = excluded.phone,
        is_active = excluded.is_active,
        updated_at = pg_catalog.now()
    returning * into v_customer;

    return v_customer;
exception
    when unique_violation then
        raise exception using errcode = '23505', message = 'Email is already registered';
end;
$$;

-- Buscar clientes para asociación (versión segura para UI)
create or replace function public.search_customers(
    p_query pg_catalog.text,
    p_limit pg_catalog.int4 default 10
) returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_query pg_catalog.text := pg_catalog.lower(pg_catalog.btrim(p_query));
begin
    if auth.uid() is null then
        raise exception using errcode = '42501', message = 'Unauthorized';
    end if;

    return (
        select coalesce(pg_catalog.jsonb_agg(pg_catalog.jsonb_build_object(
            'id', c.id,
            'fullName', c.full_name,
            'email', c.email,
            'phone', c.phone
        )), '[]'::jsonb)
        from public.customers c
        where c.is_active
          and (
              pg_catalog.lower(c.full_name) like '%' || v_query || '%'
              or c.email like '%' || v_query || '%'
              or c.phone like '%' || v_query || '%'
          )
        limit p_limit
    );
end;
$$;

-- 4. Privilegios
revoke all on table public.customers from public, anon, authenticated;
grant select on table public.customers to authenticated;

revoke all on function public.upsert_customer, public.search_customers from public, anon, authenticated;
grant execute on function public.upsert_customer, public.search_customers to authenticated;

commit;
