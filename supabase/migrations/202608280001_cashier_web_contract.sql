begin;

alter function public.claim_sale_for_payment(
    pg_catalog.uuid, pg_catalog.uuid
) owner to postgres;
alter function public.release_sale_payment_claim(
    pg_catalog.uuid, pg_catalog.uuid
) owner to postgres;
alter function public.confirm_sale_payment(
    pg_catalog.uuid, pg_catalog.uuid, pg_catalog.uuid, public.payment_method,
    pg_catalog.int8, pg_catalog.text
) owner to postgres;

create or replace function public.get_cashier_sales(
    p_limit pg_catalog.int4 default 25,
    p_after_created_at pg_catalog.timestamptz default null,
    p_after_id pg_catalog.uuid default null
) returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_branch_id pg_catalog.uuid;
    v_now pg_catalog.timestamptz := pg_catalog.statement_timestamp();
    v_items pg_catalog.jsonb;
    v_has_more pg_catalog.bool;
begin
    if v_actor_id is null or not public.has_permission('OPERATE_CASHIER') then
        raise exception using errcode = 'P0001', message = 'CASHIER_UNAUTHORIZED';
    end if;

    select p.branch_id into v_branch_id
    from public.profiles p
    join public.branches b on b.id = p.branch_id and b.is_active
    where p.id = v_actor_id and p.is_active;

    if v_branch_id is null then
        raise exception using errcode = 'P0001', message = 'CASHIER_UNAUTHORIZED';
    end if;

    if p_limit is null or p_limit < 1 or p_limit > 50 then
        raise exception using errcode = 'P0001', message = 'CASHIER_PAGE_LIMIT_INVALID';
    end if;

    if (p_after_created_at is null) <> (p_after_id is null) then
        raise exception using errcode = 'P0001', message = 'CASHIER_CURSOR_INVALID';
    end if;

    with candidate_rows as materialized (
        select
            s.id,
            s.folio,
            s.created_at,
            s.total_cents,
            s.status,
            (
                select pg_catalog.count(*)::pg_catalog.int4
                from public.sale_items si
                where si.sale_id = s.id
            ) as item_count,
            nullif(
                pg_catalog.regexp_replace(
                    pg_catalog.btrim(creator.full_name),
                    '[[:space:]]+', ' ', 'g'
                ),
                ''
            ) as created_by_label,
            claim.id as claim_id,
            claim.cashier_id as claim_cashier_id,
            claim.expires_at as claim_expires_at
        from public.sales s
        left join public.profiles creator on creator.id = s.created_by
        left join lateral (
            select c.id, c.cashier_id, c.expires_at
            from public.sale_payment_claims c
            where c.sale_id = s.id
              and c.released_at is null
              and c.consumed_at is null
            order by c.created_at desc, c.id desc
            limit 1
        ) claim on true
        where s.branch_id = v_branch_id
          and s.status = 'SENT_TO_CASHIER'
          and (
              p_after_created_at is null
              or s.created_at > p_after_created_at
              or (s.created_at = p_after_created_at and s.id > p_after_id)
          )
        order by s.created_at asc, s.id asc
        limit p_limit + 1
    ), page_rows as (
        select *
        from candidate_rows
        order by created_at asc, id asc
        limit p_limit
    )
    select
        coalesce(
            (
                select pg_catalog.jsonb_agg(
                    pg_catalog.jsonb_build_object(
                        'id', row_data.id,
                        'folio', row_data.folio,
                        'createdAt', row_data.created_at,
                        'totalCents', row_data.total_cents,
                        'itemCount', row_data.item_count,
                        'status', row_data.status::pg_catalog.text,
                        'createdByLabel', row_data.created_by_label,
                        'claimState', case
                            when row_data.claim_id is null
                              or row_data.claim_expires_at <= v_now then 'AVAILABLE'
                            when row_data.claim_cashier_id = v_actor_id then 'CLAIMED_BY_ME'
                            else 'CLAIMED_BY_OTHER'
                        end,
                        'claimExpiresAt', row_data.claim_expires_at,
                        'serverTime', v_now
                    )
                    order by row_data.created_at asc, row_data.id asc
                )
                from page_rows row_data
            ),
            '[]'::pg_catalog.jsonb
        ),
        (select pg_catalog.count(*) > p_limit from candidate_rows)
    into v_items, v_has_more;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'items', v_items,
        'page', pg_catalog.jsonb_build_object(
            'limit', p_limit,
            'hasMore', v_has_more,
            'nextCursor', case
                when v_has_more then pg_catalog.jsonb_build_object(
                    'createdAt', v_items -> (pg_catalog.jsonb_array_length(v_items) - 1) -> 'createdAt',
                    'id', v_items -> (pg_catalog.jsonb_array_length(v_items) - 1) -> 'id'
                )
                else null
            end
        )
    );
end;
$$;

create or replace function public.get_cashier_sale_detail(
    p_sale_id pg_catalog.uuid
) returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_branch_id pg_catalog.uuid;
    v_sale public.sales%rowtype;
    v_now pg_catalog.timestamptz := pg_catalog.statement_timestamp();
    v_created_by_label pg_catalog.text;
    v_claim_id pg_catalog.uuid;
    v_claim_cashier_id pg_catalog.uuid;
    v_claim_expires_at pg_catalog.timestamptz;
    v_item_count pg_catalog.int8;
    v_item_total pg_catalog.int8;
    v_invalid_item_count pg_catalog.int8;
    v_items pg_catalog.jsonb;
begin
    if v_actor_id is null or not public.has_permission('OPERATE_CASHIER') then
        raise exception using errcode = 'P0001', message = 'CASHIER_UNAUTHORIZED';
    end if;

    select p.branch_id into v_branch_id
    from public.profiles p
    join public.branches b on b.id = p.branch_id and b.is_active
    where p.id = v_actor_id and p.is_active;

    if v_branch_id is null then
        raise exception using errcode = 'P0001', message = 'CASHIER_UNAUTHORIZED';
    end if;

    select s.* into v_sale
    from public.sales s
    where s.id = p_sale_id
      and s.branch_id = v_branch_id
      and s.status = 'SENT_TO_CASHIER';

    if not found then
        raise exception using errcode = 'P0001', message = 'SALE_UNAVAILABLE';
    end if;

    select nullif(
        pg_catalog.regexp_replace(
            pg_catalog.btrim(creator.full_name), '[[:space:]]+', ' ', 'g'
        ),
        ''
    )
    into v_created_by_label
    from public.profiles creator
    where creator.id = v_sale.created_by;

    select c.id, c.cashier_id, c.expires_at
    into v_claim_id, v_claim_cashier_id, v_claim_expires_at
    from public.sale_payment_claims c
    where c.sale_id = v_sale.id
      and c.released_at is null
      and c.consumed_at is null
    order by c.created_at desc, c.id desc
    limit 1;

    select
        pg_catalog.count(*),
        coalesce(pg_catalog.sum(si.line_total_cents), 0),
        pg_catalog.count(*) filter (
            where si.quantity <= 0
               or si.unit_price_cents < 0
               or si.line_total_cents < 0
               or si.line_total_cents <> si.unit_price_cents * si.quantity
        ),
        coalesce(
            pg_catalog.jsonb_agg(
                pg_catalog.jsonb_build_object(
                    'id', si.id,
                    'productName', si.product_name,
                    'quantity', si.quantity,
                    'unitPriceCents', si.unit_price_cents,
                    'lineTotalCents', si.line_total_cents
                )
                order by si.created_at asc, si.id asc
            ),
            '[]'::pg_catalog.jsonb
        )
    into v_item_count, v_item_total, v_invalid_item_count, v_items
    from public.sale_items si
    where si.sale_id = v_sale.id;

    if v_item_count = 0
       or v_invalid_item_count > 0
       or v_item_total <> v_sale.subtotal_cents
       or v_sale.total_cents <> v_sale.subtotal_cents - v_sale.discount_cents then
        raise exception using errcode = 'P0001', message = 'SALE_DATA_INVALID';
    end if;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'sale', pg_catalog.jsonb_build_object(
            'id', v_sale.id,
            'folio', v_sale.folio,
            'createdAt', v_sale.created_at,
            'status', v_sale.status::pg_catalog.text,
            'totalCents', v_sale.total_cents,
            'itemCount', v_item_count,
            'createdByLabel', v_created_by_label,
            'claimState', case
                when v_claim_id is null or v_claim_expires_at <= v_now then 'AVAILABLE'
                when v_claim_cashier_id = v_actor_id then 'CLAIMED_BY_ME'
                else 'CLAIMED_BY_OTHER'
            end,
            'claimExpiresAt', v_claim_expires_at,
            'serverTime', v_now
        ),
        'items', v_items
    );
end;
$$;

create or replace function public.get_cashier_payment_result(
    p_sale_id pg_catalog.uuid,
    p_idempotency_key pg_catalog.uuid
) returns pg_catalog.jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_branch_id pg_catalog.uuid;
    v_now pg_catalog.timestamptz := pg_catalog.statement_timestamp();
    v_result record;
    v_item_count pg_catalog.int8;
    v_item_total pg_catalog.int8;
    v_invalid_item_count pg_catalog.int8;
    v_items pg_catalog.jsonb;
begin
    if v_actor_id is null or not public.has_permission('OPERATE_CASHIER') then
        raise exception using errcode = 'P0001', message = 'CASHIER_UNAUTHORIZED';
    end if;

    select p.branch_id into v_branch_id
    from public.profiles p
    join public.branches b on b.id = p.branch_id and b.is_active
    where p.id = v_actor_id and p.is_active;

    if v_branch_id is null then
        raise exception using errcode = 'P0001', message = 'CASHIER_UNAUTHORIZED';
    end if;

    select
        s.id as sale_id,
        s.folio,
        s.created_at as sale_created_at,
        s.subtotal_cents,
        s.discount_cents,
        s.total_cents,
        nullif(
            pg_catalog.regexp_replace(
                pg_catalog.btrim(creator.full_name), '[[:space:]]+', ' ', 'g'
            ),
            ''
        ) as created_by_label,
        b.name as branch_name,
        payment.method,
        payment.amount_received_cents,
        payment.change_cents,
        payment.reference,
        payment.created_at as payment_created_at
    into v_result
    from public.sale_payments payment
    join public.sales s on s.id = payment.sale_id
    join public.branches b on b.id = s.branch_id
    left join public.profiles creator on creator.id = s.created_by
    where s.id = p_sale_id
      and s.branch_id = v_branch_id
      and payment.branch_id = v_branch_id
      and payment.cashier_id = v_actor_id
      and payment.idempotency_key = p_idempotency_key
      and s.status = 'PAID';

    if not found then
        return pg_catalog.jsonb_build_object(
            'schemaVersion', 1,
            'status', 'NOT_FOUND',
            'serverTime', v_now
        );
    end if;

    select
        pg_catalog.count(*),
        coalesce(pg_catalog.sum(si.line_total_cents), 0),
        pg_catalog.count(*) filter (
            where si.quantity <= 0
               or si.unit_price_cents < 0
               or si.line_total_cents < 0
               or si.line_total_cents <> si.unit_price_cents * si.quantity
        ),
        coalesce(
            pg_catalog.jsonb_agg(
                pg_catalog.jsonb_build_object(
                    'id', si.id,
                    'productName', si.product_name,
                    'quantity', si.quantity,
                    'unitPriceCents', si.unit_price_cents,
                    'lineTotalCents', si.line_total_cents
                )
                order by si.created_at asc, si.id asc
            ),
            '[]'::pg_catalog.jsonb
        )
    into v_item_count, v_item_total, v_invalid_item_count, v_items
    from public.sale_items si
    where si.sale_id = v_result.sale_id;

    if v_item_count = 0
       or v_invalid_item_count > 0
       or v_item_total <> v_result.subtotal_cents
       or v_result.total_cents <> v_result.subtotal_cents - v_result.discount_cents then
        raise exception using errcode = 'P0001', message = 'PAYMENT_RESULT_INVALID';
    end if;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'status', 'SUCCEEDED',
        'sale', pg_catalog.jsonb_build_object(
            'id', v_result.sale_id,
            'folio', v_result.folio,
            'createdAt', v_result.sale_created_at,
            'totalCents', v_result.total_cents,
            'createdByLabel', v_result.created_by_label
        ),
        'items', v_items,
        'branch', pg_catalog.jsonb_build_object(
            'name', v_result.branch_name
        ),
        'payment', pg_catalog.jsonb_build_object(
            'method', v_result.method::pg_catalog.text,
            'amountReceivedCents', case
                when v_result.method = 'CASH' then v_result.amount_received_cents
                else null
            end,
            'changeCents', case
                when v_result.method = 'CASH' then v_result.change_cents
                else null
            end,
            'reference', case
                when v_result.method in ('CARD', 'TRANSFER') then v_result.reference
                else null
            end,
            'createdAt', v_result.payment_created_at
        ),
        'serverTime', v_now
    );
end;
$$;

alter function public.get_cashier_sales(
    pg_catalog.int4, pg_catalog.timestamptz, pg_catalog.uuid
) owner to postgres;
alter function public.get_cashier_sale_detail(pg_catalog.uuid) owner to postgres;
alter function public.get_cashier_payment_result(
    pg_catalog.uuid, pg_catalog.uuid
) owner to postgres;

revoke all on function public.get_cashier_sales(
    pg_catalog.int4, pg_catalog.timestamptz, pg_catalog.uuid
) from public, anon, authenticated, service_role;
revoke all on function public.get_cashier_sale_detail(pg_catalog.uuid)
from public, anon, authenticated, service_role;
revoke all on function public.get_cashier_payment_result(
    pg_catalog.uuid, pg_catalog.uuid
) from public, anon, authenticated, service_role;

grant execute on function public.get_cashier_sales(
    pg_catalog.int4, pg_catalog.timestamptz, pg_catalog.uuid
) to authenticated;
grant execute on function public.get_cashier_sale_detail(pg_catalog.uuid)
to authenticated;
grant execute on function public.get_cashier_payment_result(
    pg_catalog.uuid, pg_catalog.uuid
) to authenticated;

comment on function public.get_cashier_sales(
    pg_catalog.int4, pg_catalog.timestamptz, pg_catalog.uuid
) is 'Versioned FIFO cashier queue projection. Omits private sale and payment fields.';
comment on function public.get_cashier_sale_detail(pg_catalog.uuid) is
'Versioned authoritative read-only cashier sale detail projection. Creator label is current presentation metadata.';
comment on function public.get_cashier_payment_result(
    pg_catalog.uuid, pg_catalog.uuid
) is 'Versioned recovery contract for a cashier own canonical payment result. Creator label and branch name are current presentation metadata.';

commit;
