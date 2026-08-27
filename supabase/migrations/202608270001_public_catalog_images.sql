begin;

do $$
declare
    v_bucket storage.buckets%rowtype;
begin
    select *
    into v_bucket
    from storage.buckets
    where id = 'catalog-images';

    if not found then
        insert into storage.buckets (
            id,
            name,
            public,
            file_size_limit,
            allowed_mime_types
        ) values (
            'catalog-images',
            'catalog-images',
            true,
            5242880,
            array['image/jpeg', 'image/png', 'image/webp', 'image/avif']::pg_catalog.text[]
        );
    elsif v_bucket.name <> 'catalog-images'
       or v_bucket.public is distinct from true
       or v_bucket.file_size_limit is distinct from 5242880
       or v_bucket.allowed_mime_types is distinct from
            array['image/jpeg', 'image/png', 'image/webp', 'image/avif']::pg_catalog.text[] then
        raise exception using
            errcode = '55000',
            message = 'The existing catalog-images bucket is incompatible with the public catalog contract';
    end if;
end;
$$;

alter table public.product_images
add constraint product_images_storage_path_catalog_object_key_check
check (
    storage_path = pg_catalog.btrim(storage_path)
    and storage_path !~ '^[[:space:]]|[[:space:]]$'
    and storage_path !~ '[[:cntrl:]]'
    and storage_path !~* '^[a-z][a-z0-9+.-]*:'
    and storage_path !~ '^/'
    and storage_path !~ '^catalog-images/'
    and pg_catalog.strpos(storage_path, pg_catalog.chr(92)) = 0
    and storage_path !~ '(^|/)[.]{1,2}(/|$)'
    and storage_path ~* '[.](jpe?g|png|webp|avif)$'
) not valid;

alter table public.product_images
validate constraint product_images_storage_path_catalog_object_key_check;

create or replace function public.get_public_catalog(
    p_search pg_catalog.text default null,
    p_category_id pg_catalog.uuid default null,
    p_limit pg_catalog.int4 default 24,
    p_after_name pg_catalog.text default null,
    p_after_id pg_catalog.uuid default null
)
returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_search pg_catalog.text;
    v_items pg_catalog.jsonb;
    v_categories pg_catalog.jsonb;
    v_has_more pg_catalog.bool;
    v_next_cursor pg_catalog.jsonb;
begin
    v_search := pg_catalog.btrim(p_search);

    if v_search = '' then
        v_search := null;
    end if;

    if v_search is not null and pg_catalog.char_length(v_search) > 80 then
        raise exception using
            errcode = '22023',
            message = 'La búsqueda no puede superar 80 caracteres.';
    end if;

    if v_search is not null and v_search ~ '[[:cntrl:]]' then
        raise exception using
            errcode = '22023',
            message = 'La búsqueda contiene caracteres no permitidos.';
    end if;

    if p_limit is null or p_limit < 1 or p_limit > 50 then
        raise exception using
            errcode = '22023',
            message = 'El límite debe estar entre 1 y 50.';
    end if;

    if (p_after_name is null) <> (p_after_id is null) then
        raise exception using
            errcode = '22023',
            message = 'El cursor debe incluir nombre e identificador.';
    end if;

    if p_after_name is not null then
        if pg_catalog.char_length(p_after_name) > 160
           or pg_catalog.char_length(pg_catalog.btrim(p_after_name)) = 0 then
            raise exception using
                errcode = '22023',
                message = 'El nombre del cursor no es válido.';
        end if;

        if p_after_name ~ '[[:cntrl:]]' then
            raise exception using
                errcode = '22023',
                message = 'El cursor contiene caracteres no permitidos.';
        end if;

        if p_after_name <> pg_catalog.lower(p_after_name) then
            raise exception using
                errcode = '22023',
                message = 'El nombre del cursor debe estar normalizado.';
        end if;
    end if;

    with filtered as (
        select
            p.id,
            p.common_name,
            p.scientific_name,
            p.description,
            p.price_cents,
            p.unit,
            p.watering_advice,
            p.light_type,
            p.recommended_climate,
            c.id as category_id,
            c.name as category_name,
            image.storage_path as image_storage_path,
            image.alt_text as image_alt_text,
            pg_catalog.lower(p.common_name) collate pg_catalog."C" as sort_name
        from public.products as p
        join public.categories as c
          on c.id = p.category_id
         and c.is_active = true
        left join lateral (
            select
                pi.storage_path,
                pi.alt_text
            from public.product_images as pi
            where pi.product_id = p.id
            order by pi.is_primary desc, pi.sort_order, pi.id
            limit 1
        ) as image on true
        where p.is_active = true
          and (p_category_id is null or p.category_id = p_category_id)
          and (
              v_search is null
              or pg_catalog.strpos(
                  pg_catalog.lower(p.common_name),
                  pg_catalog.lower(v_search)
              ) > 0
              or pg_catalog.strpos(
                  pg_catalog.lower(coalesce(p.scientific_name, '')),
                  pg_catalog.lower(v_search)
              ) > 0
              or pg_catalog.strpos(
                  pg_catalog.lower(p.description),
                  pg_catalog.lower(v_search)
              ) > 0
          )
          and (
              p_after_name is null
              or (pg_catalog.lower(p.common_name) collate pg_catalog."C")
                    > (p_after_name collate pg_catalog."C")
              or (
                  (pg_catalog.lower(p.common_name) collate pg_catalog."C")
                        = (p_after_name collate pg_catalog."C")
                  and p.id > p_after_id
              )
          )
    ),
    windowed as (
        select f.*
        from filtered as f
        order by f.sort_name collate pg_catalog."C", f.id
        limit (p_limit + 1)
    ),
    page_rows as (
        select w.*
        from windowed as w
        order by w.sort_name collate pg_catalog."C", w.id
        limit p_limit
    )
    select
        coalesce(
            (
                select pg_catalog.jsonb_agg(
                    pg_catalog.jsonb_build_object(
                        'id', row_data.id,
                        'name', row_data.common_name,
                        'scientificName', row_data.scientific_name,
                        'description', row_data.description,
                        'category', pg_catalog.jsonb_build_object(
                            'id', row_data.category_id,
                            'name', row_data.category_name
                        ),
                        'price', pg_catalog.jsonb_build_object(
                            'amountCents', row_data.price_cents,
                            'currency', 'MXN',
                            'unit', row_data.unit
                        ),
                        'care', pg_catalog.jsonb_build_object(
                            'wateringAdvice', row_data.watering_advice,
                            'lightType', row_data.light_type,
                            'recommendedClimate', row_data.recommended_climate
                        ),
                        'image', case
                            when row_data.image_storage_path is null then null
                            else pg_catalog.jsonb_build_object(
                                'bucketName', 'catalog-images',
                                'storagePath', row_data.image_storage_path,
                                'altText', row_data.image_alt_text
                            )
                        end,
                        'publicationStatus', 'LISTED'
                    )
                    order by row_data.sort_name collate pg_catalog."C", row_data.id
                )
                from page_rows as row_data
            ),
            '[]'::pg_catalog.jsonb
        ),
        (select pg_catalog.count(*) > p_limit from windowed),
        case
            when (select pg_catalog.count(*) > p_limit from windowed) then (
                select pg_catalog.jsonb_build_object(
                    'sortName', cursor_row.sort_name,
                    'id', cursor_row.id
                )
                from page_rows as cursor_row
                order by cursor_row.sort_name collate pg_catalog."C" desc, cursor_row.id desc
                limit 1
            )
            else null
        end
    into v_items, v_has_more, v_next_cursor;

    select coalesce(
        pg_catalog.jsonb_agg(
            pg_catalog.jsonb_build_object(
                'id', c.id,
                'name', c.name
            )
            order by pg_catalog.lower(c.name) collate pg_catalog."C", c.id
        ),
        '[]'::pg_catalog.jsonb
    )
    into v_categories
    from public.categories as c
    where c.is_active = true;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 2,
        'items', v_items,
        'categories', v_categories,
        'page', pg_catalog.jsonb_build_object(
            'limit', p_limit,
            'hasMore', v_has_more,
            'nextCursor', v_next_cursor
        )
    );
end;
$$;

revoke all on function public.get_public_catalog(
    pg_catalog.text,
    pg_catalog.uuid,
    pg_catalog.int4,
    pg_catalog.text,
    pg_catalog.uuid
) from public;

revoke all on function public.get_public_catalog(
    pg_catalog.text,
    pg_catalog.uuid,
    pg_catalog.int4,
    pg_catalog.text,
    pg_catalog.uuid
) from anon;

revoke all on function public.get_public_catalog(
    pg_catalog.text,
    pg_catalog.uuid,
    pg_catalog.int4,
    pg_catalog.text,
    pg_catalog.uuid
) from authenticated;

grant execute on function public.get_public_catalog(
    pg_catalog.text,
    pg_catalog.uuid,
    pg_catalog.int4,
    pg_catalog.text,
    pg_catalog.uuid
) to anon, authenticated;

comment on function public.get_public_catalog(
    pg_catalog.text,
    pg_catalog.uuid,
    pg_catalog.int4,
    pg_catalog.text,
    pg_catalog.uuid
) is 'Devuelve el catálogo público V2 en modo de solo lectura. Las imágenes usan claves relativas del bucket catalog-images; esta función no construye URLs ni concede acceso directo a tablas.';

commit;
