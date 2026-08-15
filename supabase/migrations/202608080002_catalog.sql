begin;

create table public.categories (
    id uuid primary key default gen_random_uuid(),
    name text not null unique check (char_length(trim(name)) between 2 and 100),
    description text,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.products (
    id uuid primary key default gen_random_uuid(),
    internal_code text not null unique check (char_length(trim(internal_code)) between 2 and 40),
    barcode text unique check (barcode is null or char_length(trim(barcode)) between 4 and 128),
    common_name text not null check (char_length(trim(common_name)) between 2 and 160),
    scientific_name text,
    description text not null default '',
    category_id uuid not null references public.categories(id) on delete restrict,
    price_cents bigint not null check (price_cents >= 0),
    wholesale_price_cents bigint check (wholesale_price_cents is null or wholesale_price_cents >= 0),
    unit text not null check (unit in ('pieza', 'maceta', 'charola', 'bolsa', 'kg')),
    minimum_stock numeric(14,3) not null default 0 check (minimum_stock >= 0),
    watering_advice text not null default '',
    light_type text not null default '',
    recommended_climate text not null default '',
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.product_images (
    id uuid primary key default gen_random_uuid(),
    product_id uuid not null references public.products(id) on delete cascade,
    storage_path text not null unique check (char_length(trim(storage_path)) between 3 and 500),
    alt_text text,
    sort_order smallint not null default 0 check (sort_order >= 0),
    is_primary boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index categories_active_name_idx on public.categories(is_active, name);
create index products_category_active_idx on public.products(category_id, is_active);
create index products_common_name_lower_idx on public.products(lower(common_name));
create index products_scientific_name_lower_idx on public.products(lower(scientific_name));
create index product_images_product_order_idx on public.product_images(product_id, sort_order);
create unique index product_images_one_primary_idx on public.product_images(product_id) where is_primary;

create trigger categories_set_updated_at before update on public.categories
for each row execute function public.set_updated_at();
create trigger products_set_updated_at before update on public.products
for each row execute function public.set_updated_at();
create trigger product_images_set_updated_at before update on public.product_images
for each row execute function public.set_updated_at();

create or replace function public.enforce_product_price_permission()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if auth.uid() is not null
       and (
           new.price_cents is distinct from old.price_cents
           or new.wholesale_price_cents is distinct from old.wholesale_price_cents
       )
       and not public.has_permission('MANAGE_PRICES') then
        raise exception using errcode = '42501', message = 'Price management is not allowed';
    end if;

    return new;
end;
$$;

revoke all on function public.enforce_product_price_permission() from public;

create trigger products_enforce_price_permission before update on public.products
for each row execute function public.enforce_product_price_permission();

alter table public.categories enable row level security;
alter table public.products enable row level security;
alter table public.product_images enable row level security;

create policy categories_read on public.categories
for select to authenticated
using (is_active or public.has_permission('MANAGE_PRODUCTS'));

create policy categories_manage on public.categories
for all to authenticated
using (public.has_permission('MANAGE_PRODUCTS'))
with check (public.has_permission('MANAGE_PRODUCTS'));

create policy products_read on public.products
for select to authenticated
using (is_active or public.has_permission('MANAGE_PRODUCTS'));

create policy products_manage on public.products
for all to authenticated
using (public.has_permission('MANAGE_PRODUCTS'))
with check (public.has_permission('MANAGE_PRODUCTS'));

create policy product_images_read on public.product_images
for select to authenticated
using (exists (
    select 1 from public.products p
    where p.id = product_images.product_id
      and (p.is_active or public.has_permission('MANAGE_PRODUCTS'))
));

create policy product_images_manage on public.product_images
for all to authenticated
using (public.has_permission('MANAGE_PRODUCTS'))
with check (public.has_permission('MANAGE_PRODUCTS'));

grant select on public.categories, public.products, public.product_images to authenticated;
grant insert, update, delete on public.categories to authenticated;
grant insert, update, delete on public.products to authenticated;
grant insert, update, delete on public.product_images to authenticated;

commit;
