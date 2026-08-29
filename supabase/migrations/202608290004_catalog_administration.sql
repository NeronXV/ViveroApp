begin;

-- 1. Mutaciones de Categorias

create or replace function public.upsert_category(
    p_id pg_catalog.uuid default null,
    p_name pg_catalog.text default null,
    p_description pg_catalog.text default null,
    p_is_active pg_catalog.bool default true
)
returns public.categories
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_name pg_catalog.text := pg_catalog.nullif(pg_catalog.btrim(p_name), '');
    v_category public.categories%rowtype;
begin
    if v_actor_id is null or not public.has_permission('MANAGE_PRODUCTS') then
        raise exception using errcode = '42501', message = 'Category management is not allowed';
    end if;

    if v_name is null or pg_catalog.char_length(v_name) not between 2 and 100 then
        raise exception using errcode = '22023', message = 'Category name is invalid';
    end if;

    insert into public.categories (id, name, description, is_active)
    values (
        pg_catalog.coalesce(p_id, pg_catalog.gen_random_uuid()),
        v_name,
        p_description,
        pg_catalog.coalesce(p_is_active, true)
    )
    on conflict (id) do update
    set name = excluded.name,
        description = excluded.description,
        is_active = excluded.is_active,
        updated_at = pg_catalog.now()
    returning * into v_category;

    return v_category;
end;
$$;

-- 2. Mutaciones de Productos

create or replace function public.upsert_product(
    p_id pg_catalog.uuid default null,
    p_internal_code pg_catalog.text default null,
    p_barcode pg_catalog.text default null,
    p_common_name pg_catalog.text default null,
    p_scientific_name pg_catalog.text default null,
    p_description pg_catalog.text default null,
    p_category_id pg_catalog.uuid default null,
    p_price_cents pg_catalog.bigint default null,
    p_wholesale_price_cents pg_catalog.bigint default null,
    p_unit pg_catalog.text default null,
    p_minimum_stock pg_catalog.numeric default null,
    p_watering_advice pg_catalog.text default null,
    p_light_type pg_catalog.text default null,
    p_recommended_climate pg_catalog.text default null,
    p_is_active pg_catalog.bool default true
)
returns public.products
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_internal_code pg_catalog.text := pg_catalog.upper(pg_catalog.btrim(p_internal_code));
    v_common_name pg_catalog.text := pg_catalog.btrim(p_common_name);
    v_product public.products%rowtype;
begin
    -- Validar permisos basicos
    if v_actor_id is null or not public.has_permission('MANAGE_PRODUCTS') then
        raise exception using errcode = '42501', message = 'Product management is not allowed';
    end if;

    -- Validar precio si cambio (MANAGE_PRICES requerido)
    if p_id is not null then
        select * into v_product from public.products where id = p_id;
        if found and (
            p_price_cents is distinct from v_product.price_cents
            or p_wholesale_price_cents is distinct from v_product.wholesale_price_cents
        ) and not public.has_permission('MANAGE_PRICES') then
            raise exception using errcode = '42501', message = 'Price management is not allowed';
        end if;
    elsif not public.has_permission('MANAGE_PRICES') then
        -- Para nuevos productos, se requiere permiso de precios para establecerlos
        raise exception using errcode = '42501', message = 'Price management is required for new products';
    end if;

    -- Validaciones de datos
    if v_internal_code is null or pg_catalog.char_length(v_internal_code) not between 2 and 40 then
        raise exception using errcode = '22023', message = 'Internal code is invalid';
    end if;

    if v_common_name is null or pg_catalog.char_length(v_common_name) not between 2 and 160 then
        raise exception using errcode = '22023', message = 'Common name is invalid';
    end if;

    if p_category_id is null or not exists (select 1 from public.categories where id = p_category_id) then
        raise exception using errcode = '22023', message = 'Category is invalid or missing';
    end if;

    if p_price_cents is null or p_price_cents < 0 then
        raise exception using errcode = '22023', message = 'Price is invalid';
    end if;

    if p_unit is null or p_unit not in ('pieza', 'maceta', 'charola', 'bolsa', 'kg') then
        raise exception using errcode = '22023', message = 'Unit is invalid';
    end if;

    insert into public.products (
        id, internal_code, barcode, common_name, scientific_name,
        description, category_id, price_cents, wholesale_price_cents,
        unit, minimum_stock, watering_advice, light_type,
        recommended_climate, is_active
    )
    values (
        pg_catalog.coalesce(p_id, pg_catalog.gen_random_uuid()),
        v_internal_code,
        pg_catalog.nullif(pg_catalog.btrim(p_barcode), ''),
        v_common_name,
        p_scientific_name,
        pg_catalog.coalesce(p_description, ''),
        p_category_id,
        p_price_cents,
        p_wholesale_price_cents,
        p_unit,
        pg_catalog.coalesce(p_minimum_stock, 0),
        pg_catalog.coalesce(p_watering_advice, ''),
        pg_catalog.coalesce(p_light_type, ''),
        pg_catalog.coalesce(p_recommended_climate, ''),
        pg_catalog.coalesce(p_is_active, true)
    )
    on conflict (id) do update
    set internal_code = excluded.internal_code,
        barcode = excluded.barcode,
        common_name = excluded.common_name,
        scientific_name = excluded.scientific_name,
        description = excluded.description,
        category_id = excluded.category_id,
        price_cents = excluded.price_cents,
        wholesale_price_cents = excluded.wholesale_price_cents,
        unit = excluded.unit,
        minimum_stock = excluded.minimum_stock,
        watering_advice = excluded.watering_advice,
        light_type = excluded.light_type,
        recommended_climate = excluded.recommended_climate,
        is_active = excluded.is_active,
        updated_at = pg_catalog.now()
    returning * into v_product;

    return v_product;
exception
    when unique_violation then
        raise exception using errcode = '23505', message = 'Code or barcode is already in use';
end;
$$;

-- 3. Mutaciones de Imagenes (Administracion de metadatos)

create or replace function public.set_product_image_primary(
    p_image_id pg_catalog.uuid
)
returns pg_catalog.void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_product_id pg_catalog.uuid;
begin
    if v_actor_id is null or not public.has_permission('MANAGE_PRODUCTS') then
        raise exception using errcode = '42501', message = 'Image management is not allowed';
    end if;

    select product_id into v_product_id from public.product_images where id = p_image_id;

    if not found then
        raise exception using errcode = '22023', message = 'Image not found';
    end if;

    update public.product_images set is_primary = false where product_id = v_product_id;
    update public.product_images set is_primary = true where id = p_image_id;
end;
$$;

-- 4. Privilegios

revoke all on function public.upsert_category, public.upsert_product, public.set_product_image_primary from public, anon, authenticated;
grant execute on function public.upsert_category, public.upsert_product, public.set_product_image_primary to authenticated;

commit;
