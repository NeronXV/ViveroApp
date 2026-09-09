begin;

create index products_active_internal_code_scan_idx
on public.products (pg_catalog.lower(pg_catalog.btrim(internal_code)))
where is_active;

create index products_active_barcode_scan_idx
on public.products (pg_catalog.lower(pg_catalog.btrim(barcode)))
where is_active and barcode is not null;

create or replace function public.get_product_by_scan_code(
    p_code pg_catalog.text
)
returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_branch_id pg_catalog.uuid;
    v_code pg_catalog.text := pg_catalog.btrim(p_code);
    v_product_id pg_catalog.uuid;
    v_is_ambiguous pg_catalog.bool;
    v_product pg_catalog.jsonb;
begin
    if v_actor_id is null or not public.has_permission('VIEW_CATALOG') then
        raise exception using errcode = '42501', message = 'PRODUCT_SCAN_UNAUTHORIZED';
    end if;

    if v_code is null
       or pg_catalog.char_length(v_code) < 2
       or pg_catalog.char_length(v_code) > 128
       or v_code ~ '[[:cntrl:]]' then
        raise exception using errcode = '22023', message = 'PRODUCT_SCAN_CODE_INVALID';
    end if;

    select p.id
    into v_product_id
    from public.products p
    where p.is_active
      and (
          pg_catalog.lower(pg_catalog.btrim(p.internal_code)) = pg_catalog.lower(v_code)
          or pg_catalog.lower(pg_catalog.btrim(p.barcode)) = pg_catalog.lower(v_code)
      )
    order by p.id
    limit 1;

    if v_product_id is not null then
        select exists(
            select 1
            from public.products p
            where p.is_active
              and p.id <> v_product_id
              and (
                  pg_catalog.lower(pg_catalog.btrim(p.internal_code)) = pg_catalog.lower(v_code)
                  or pg_catalog.lower(pg_catalog.btrim(p.barcode)) = pg_catalog.lower(v_code)
              )
        )
        into v_is_ambiguous;

        if v_is_ambiguous then
            raise exception using errcode = 'P0001', message = 'PRODUCT_SCAN_CODE_AMBIGUOUS';
        end if;
    end if;

    select pr.branch_id
    into v_branch_id
    from public.profiles pr
    join public.branches b on b.id = pr.branch_id and b.is_active
    where pr.id = v_actor_id and pr.is_active;

    select pg_catalog.jsonb_build_object(
        'product', pg_catalog.jsonb_build_object(
            'id', p.id,
            'internal_code', p.internal_code,
            'barcode', p.barcode,
            'common_name', p.common_name,
            'scientific_name', p.scientific_name,
            'description', p.description,
            'category_id', p.category_id,
            'price_cents', p.price_cents,
            'wholesale_price_cents', p.wholesale_price_cents,
            'unit', p.unit,
            'minimum_stock', p.minimum_stock,
            'watering_advice', p.watering_advice,
            'light_type', p.light_type,
            'recommended_climate', p.recommended_climate,
            'is_active', p.is_active,
            'created_at', p.created_at,
            'updated_at', p.updated_at,
            'images', coalesce(images.items, '[]'::pg_catalog.jsonb)
        ),
        'category', pg_catalog.jsonb_build_object(
            'id', c.id,
            'name', c.name,
            'is_active', c.is_active
        ),
        'pricing', pg_catalog.jsonb_build_object(
            'productId', p.id,
            'listPriceCents', pricing.list_price_cents,
            'effectivePriceCents', pricing.effective_price_cents,
            'activePromotion', case
                when pricing.promotion_id is null then null
                else pg_catalog.jsonb_build_object(
                    'id', pricing.promotion_id,
                    'name', pricing.promotion_name,
                    'discountPercent', pricing.discount_percent
                )
            end
        ),
        'stockAvailable', case
            when v_branch_id is null then null
            else coalesce(balance.total_quantity, 0)
        end,
        'stockKnown', v_branch_id is not null
    )
    into v_product
    from public.products p
    join public.categories c on c.id = p.category_id and c.is_active
    cross join lateral public.resolve_catalog_product_price(p.id) pricing
    left join public.inventory_balances balance
      on balance.product_id = p.id
     and balance.branch_id = v_branch_id
    left join lateral (
        select pg_catalog.jsonb_agg(
            pg_catalog.jsonb_build_object(
                'id', pi.id,
                'product_id', pi.product_id,
                'storage_path', pi.storage_path,
                'alt_text', pi.alt_text,
                'sort_order', pi.sort_order,
                'is_primary', pi.is_primary
            )
            order by pi.is_primary desc, pi.sort_order, pi.id
        ) as items
        from public.product_images pi
        where pi.product_id = p.id
    ) images on true
    where p.id = v_product_id and p.is_active;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'item', v_product
    );
end;
$$;

alter function public.get_product_by_scan_code(pg_catalog.text) owner to postgres;

revoke all on function public.get_product_by_scan_code(pg_catalog.text)
from public, anon, authenticated, service_role;
grant execute on function public.get_product_by_scan_code(pg_catalog.text)
to authenticated;

comment on function public.get_product_by_scan_code(pg_catalog.text) is
'Resolves one active catalog product by internal code or barcode, with authoritative pricing and inventory limited to the authenticated actor active branch.';

commit;
