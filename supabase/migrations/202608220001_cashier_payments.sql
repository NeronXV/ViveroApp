begin;

create type public.payment_method as enum ('CASH', 'CARD', 'TRANSFER');

create table public.sale_payment_claims (
    id pg_catalog.uuid primary key default gen_random_uuid(),
    sale_id pg_catalog.uuid not null references public.sales(id) on delete restrict,
    cashier_id pg_catalog.uuid not null references public.profiles(id) on delete restrict,
    branch_id pg_catalog.uuid not null references public.branches(id) on delete restrict,
    claim_token pg_catalog.uuid not null unique default gen_random_uuid(),
    created_at pg_catalog.timestamptz not null default pg_catalog.now(),
    expires_at pg_catalog.timestamptz not null,
    renewed_at pg_catalog.timestamptz,
    renewal_count pg_catalog.int4 not null default 0 check (renewal_count >= 0),
    released_at pg_catalog.timestamptz,
    consumed_at pg_catalog.timestamptz,
    closed_reason pg_catalog.text check (
        closed_reason is null or closed_reason in ('RELEASED', 'EXPIRED', 'CONFIRMED')
    ),
    check (expires_at > created_at),
    check (released_at is null or consumed_at is null),
    check (
        (released_at is null and consumed_at is null and closed_reason is null)
        or (released_at is not null and consumed_at is null and closed_reason in ('RELEASED', 'EXPIRED'))
        or (released_at is null and consumed_at is not null and closed_reason = 'CONFIRMED')
    )
);

create unique index sale_payment_claims_one_open_per_sale_idx
on public.sale_payment_claims(sale_id)
where released_at is null and consumed_at is null;

create index sale_payment_claims_expiration_idx
on public.sale_payment_claims(expires_at)
where released_at is null and consumed_at is null;

create index sale_payment_claims_cashier_created_idx
on public.sale_payment_claims(cashier_id, created_at desc);

create table public.sale_payments (
    id pg_catalog.uuid primary key default gen_random_uuid(),
    sale_id pg_catalog.uuid not null unique references public.sales(id) on delete restrict,
    branch_id pg_catalog.uuid not null references public.branches(id) on delete restrict,
    cashier_id pg_catalog.uuid not null references public.profiles(id) on delete restrict,
    claim_id pg_catalog.uuid not null unique references public.sale_payment_claims(id) on delete restrict,
    idempotency_key pg_catalog.uuid not null unique,
    method public.payment_method not null,
    amount_due_cents pg_catalog.int8 not null check (amount_due_cents > 0),
    requested_amount_received_cents pg_catalog.int8,
    amount_received_cents pg_catalog.int8 not null check (amount_received_cents >= amount_due_cents),
    change_cents pg_catalog.int8 not null check (change_cents >= 0),
    reference pg_catalog.text,
    created_at pg_catalog.timestamptz not null default pg_catalog.now(),
    check (change_cents = amount_received_cents - amount_due_cents),
    check (
        reference is null or (
            pg_catalog.char_length(reference) <= 120
            and reference !~ '[[:cntrl:]]'
        )
    ),
    check (
        (method = 'CASH'
            and requested_amount_received_cents is not null
            and amount_received_cents = requested_amount_received_cents
            and reference is null)
        or (method = 'CARD'
            and requested_amount_received_cents is null
            and amount_received_cents = amount_due_cents
            and change_cents = 0
            and pg_catalog.char_length(coalesce(reference, '')) <= 64
            and coalesce(reference, '') !~ '^[0-9]{3,4}$'
            and pg_catalog.regexp_replace(coalesce(reference, ''), '[^0-9]', '', 'g') !~ '^[0-9]{13,19}$')
        or (method = 'TRANSFER'
            and requested_amount_received_cents is null
            and amount_received_cents = amount_due_cents
            and change_cents = 0
            and reference is not null
            and pg_catalog.char_length(reference) between 1 and 120)
    )
);

create index sale_payments_branch_created_idx
on public.sale_payments(branch_id, created_at desc);

create index sale_payments_cashier_created_idx
on public.sale_payments(cashier_id, created_at desc);

alter table public.sale_payment_claims enable row level security;
alter table public.sale_payments enable row level security;

-- These tables are RPC-only for client roles. RLS remains enabled with no client
-- policies, so even an accidental future SELECT grant would remain deny-by-default.
revoke all privileges on table public.sale_payment_claims, public.sale_payments
from public, anon, authenticated;
grant all privileges on table public.sale_payment_claims, public.sale_payments
to service_role;

create or replace function public.claim_sale_for_payment(
    p_sale_id pg_catalog.uuid,
    p_claim_token pg_catalog.uuid default null
) returns pg_catalog.jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_branch_id pg_catalog.uuid;
    v_sale public.sales%rowtype;
    v_claim public.sale_payment_claims%rowtype;
    v_now pg_catalog.timestamptz := pg_catalog.clock_timestamp();
    v_renewed pg_catalog.bool := false;
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
    for update;

    if not found or v_sale.branch_id <> v_branch_id then
        raise exception using errcode = 'P0001', message = 'SALE_UNAVAILABLE';
    end if;

    if v_sale.status <> 'SENT_TO_CASHIER' then
        raise exception using errcode = 'P0001', message = 'SALE_STATUS_INVALID';
    end if;

    select c.* into v_claim
    from public.sale_payment_claims c
    where c.sale_id = v_sale.id
      and c.released_at is null
      and c.consumed_at is null
    for update;

    if found then
        if v_claim.expires_at <= v_now then
            if p_claim_token is not null then
                raise exception using errcode = 'P0001', message = 'CLAIM_EXPIRED';
            end if;

            update public.sale_payment_claims c
            set released_at = v_now,
                closed_reason = 'EXPIRED'
            where c.id = v_claim.id;
        else
            if v_claim.cashier_id <> v_actor_id or v_claim.branch_id <> v_branch_id then
                raise exception using errcode = 'P0001', message = 'CLAIM_UNAVAILABLE';
            end if;

            if p_claim_token is null then
                return pg_catalog.jsonb_build_object(
                    'sale_id', v_claim.sale_id,
                    'branch_id', v_claim.branch_id,
                    'cashier_id', v_claim.cashier_id,
                    'claim_token', v_claim.claim_token,
                    'created_at', v_claim.created_at,
                    'expires_at', v_claim.expires_at,
                    'server_time', v_now,
                    'renewed', false
                );
            end if;

            if v_claim.claim_token <> p_claim_token then
                raise exception using errcode = 'P0001', message = 'CLAIM_NOT_OWNED';
            end if;

            update public.sale_payment_claims c
            set expires_at = v_now + pg_catalog.make_interval(mins => 5),
                renewed_at = v_now,
                renewal_count = c.renewal_count + 1
            where c.id = v_claim.id
            returning c.* into v_claim;
            v_renewed := true;

            return pg_catalog.jsonb_build_object(
                'sale_id', v_claim.sale_id,
                'branch_id', v_claim.branch_id,
                'cashier_id', v_claim.cashier_id,
                'claim_token', v_claim.claim_token,
                'created_at', v_claim.created_at,
                'expires_at', v_claim.expires_at,
                'server_time', v_now,
                'renewed', v_renewed
            );
        end if;
    elsif p_claim_token is not null then
        raise exception using errcode = 'P0001', message = 'CLAIM_NOT_OWNED';
    end if;

    insert into public.sale_payment_claims (
        sale_id, cashier_id, branch_id, expires_at
    ) values (
        v_sale.id, v_actor_id, v_branch_id,
        v_now + pg_catalog.make_interval(mins => 5)
    ) returning * into v_claim;

    return pg_catalog.jsonb_build_object(
        'sale_id', v_claim.sale_id,
        'branch_id', v_claim.branch_id,
        'cashier_id', v_claim.cashier_id,
        'claim_token', v_claim.claim_token,
        'created_at', v_claim.created_at,
        'expires_at', v_claim.expires_at,
        'server_time', v_now,
        'renewed', false
    );
exception
    when unique_violation then
        raise exception using errcode = 'P0001', message = 'CLAIM_UNAVAILABLE';
end;
$$;

create or replace function public.release_sale_payment_claim(
    p_sale_id pg_catalog.uuid,
    p_claim_token pg_catalog.uuid
) returns pg_catalog.jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_branch_id pg_catalog.uuid;
    v_sale public.sales%rowtype;
    v_claim public.sale_payment_claims%rowtype;
    v_now pg_catalog.timestamptz := pg_catalog.clock_timestamp();
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
    for update;

    if not found or v_sale.branch_id <> v_branch_id then
        raise exception using errcode = 'P0001', message = 'SALE_UNAVAILABLE';
    end if;

    select c.* into v_claim
    from public.sale_payment_claims c
    where c.sale_id = v_sale.id
      and c.released_at is null
      and c.consumed_at is null
    for update;

    if not found
       or v_claim.cashier_id <> v_actor_id
       or v_claim.branch_id <> v_branch_id
       or v_claim.claim_token <> p_claim_token then
        raise exception using errcode = 'P0001', message = 'CLAIM_NOT_OWNED';
    end if;

    update public.sale_payment_claims c
    set released_at = v_now,
        closed_reason = case when c.expires_at <= v_now then 'EXPIRED' else 'RELEASED' end
    where c.id = v_claim.id
    returning c.* into v_claim;

    return pg_catalog.jsonb_build_object(
        'sale_id', v_claim.sale_id,
        'claim_token', v_claim.claim_token,
        'released_at', v_claim.released_at,
        'closed_reason', v_claim.closed_reason
    );
end;
$$;

create or replace function public.confirm_sale_payment(
    p_sale_id pg_catalog.uuid,
    p_claim_token pg_catalog.uuid,
    p_idempotency_key pg_catalog.uuid,
    p_method public.payment_method,
    p_amount_received_cents pg_catalog.int8 default null,
    p_reference pg_catalog.text default null
) returns pg_catalog.jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_branch_id pg_catalog.uuid;
    v_sale public.sales%rowtype;
    v_claim public.sale_payment_claims%rowtype;
    v_payment public.sale_payments%rowtype;
    v_now pg_catalog.timestamptz := pg_catalog.clock_timestamp();
    v_reference pg_catalog.text := nullif(pg_catalog.btrim(p_reference), '');
    v_amount_received pg_catalog.int8;
    v_change pg_catalog.int8;
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
    for update;

    if not found or v_sale.branch_id <> v_branch_id then
        raise exception using errcode = 'P0001', message = 'SALE_UNAVAILABLE';
    end if;

    if p_idempotency_key is null then
        raise exception using errcode = 'P0001', message = 'IDEMPOTENCY_KEY_INVALID';
    end if;

    if p_method is null then
        raise exception using errcode = 'P0001', message = 'PAYMENT_METHOD_INVALID';
    end if;

    select sp.* into v_payment
    from public.sale_payments sp
    where sp.idempotency_key = p_idempotency_key
    for update;

    if found then
        if v_payment.sale_id <> v_sale.id
           or v_payment.cashier_id <> v_actor_id
           or v_payment.branch_id <> v_branch_id
           or v_payment.method <> p_method
           or v_payment.requested_amount_received_cents is distinct from p_amount_received_cents
           or v_payment.reference is distinct from v_reference then
            raise exception using errcode = 'P0001', message = 'IDEMPOTENCY_CONFLICT';
        end if;

        return pg_catalog.jsonb_build_object(
            'idempotent_replay', true,
            'sale', pg_catalog.jsonb_build_object(
                'id', v_sale.id,
                'folio', v_sale.folio,
                'branch_id', v_sale.branch_id,
                'status', v_sale.status,
                'total_cents', v_sale.total_cents
            ),
            'payment', pg_catalog.jsonb_build_object(
                'id', v_payment.id,
                'sale_id', v_payment.sale_id,
                'cashier_id', v_payment.cashier_id,
                'idempotency_key', v_payment.idempotency_key,
                'method', v_payment.method,
                'amount_due_cents', v_payment.amount_due_cents,
                'amount_received_cents', v_payment.amount_received_cents,
                'change_cents', v_payment.change_cents,
                'reference', v_payment.reference,
                'created_at', v_payment.created_at
            )
        );
    end if;

    if exists (select 1 from public.sale_payments sp where sp.sale_id = v_sale.id) then
        raise exception using errcode = 'P0001', message = 'SALE_ALREADY_PAID';
    end if;

    if v_sale.status <> 'SENT_TO_CASHIER' then
        raise exception using errcode = 'P0001', message = 'SALE_STATUS_INVALID';
    end if;

    if v_sale.total_cents <= 0 then
        raise exception using errcode = 'P0001', message = 'SALE_TOTAL_INVALID';
    end if;

    select c.* into v_claim
    from public.sale_payment_claims c
    where c.sale_id = v_sale.id
      and c.released_at is null
      and c.consumed_at is null
    for update;

    if not found then
        raise exception using errcode = 'P0001', message = 'CLAIM_REQUIRED';
    end if;

    if v_claim.cashier_id <> v_actor_id
       or v_claim.branch_id <> v_branch_id
       or v_claim.claim_token <> p_claim_token then
        raise exception using errcode = 'P0001', message = 'CLAIM_NOT_OWNED';
    end if;

    if v_claim.expires_at <= v_now then
        raise exception using errcode = 'P0001', message = 'CLAIM_EXPIRED';
    end if;

    if v_reference is not null and (
        pg_catalog.char_length(v_reference) > 120
        or v_reference ~ '[[:cntrl:]]'
    ) then
        raise exception using errcode = 'P0001', message = 'PAYMENT_DATA_INVALID';
    end if;

    case p_method
        when 'CASH' then
            if p_amount_received_cents is null
               or p_amount_received_cents < v_sale.total_cents then
                raise exception using errcode = 'P0001', message = 'CASH_AMOUNT_INSUFFICIENT';
            end if;
            if v_reference is not null then
                raise exception using errcode = 'P0001', message = 'PAYMENT_DATA_INVALID';
            end if;
            v_amount_received := p_amount_received_cents;
            v_change := p_amount_received_cents - v_sale.total_cents;
        when 'CARD' then
            if p_amount_received_cents is not null
               or pg_catalog.char_length(coalesce(v_reference, '')) > 64
               or coalesce(v_reference, '') ~ '^[0-9]{3,4}$'
               or pg_catalog.regexp_replace(coalesce(v_reference, ''), '[^0-9]', '', 'g') ~ '^[0-9]{13,19}$' then
                raise exception using errcode = 'P0001', message = 'PAYMENT_DATA_INVALID';
            end if;
            v_amount_received := v_sale.total_cents;
            v_change := 0;
        when 'TRANSFER' then
            if p_amount_received_cents is not null or v_reference is null then
                raise exception using errcode = 'P0001', message = 'TRANSFER_REFERENCE_REQUIRED';
            end if;
            v_amount_received := v_sale.total_cents;
            v_change := 0;
    end case;

    insert into public.sale_payments (
        sale_id, branch_id, cashier_id, claim_id, idempotency_key, method,
        amount_due_cents, requested_amount_received_cents,
        amount_received_cents, change_cents, reference
    ) values (
        v_sale.id, v_branch_id, v_actor_id, v_claim.id, p_idempotency_key, p_method,
        v_sale.total_cents, p_amount_received_cents,
        v_amount_received, v_change, v_reference
    ) returning * into v_payment;

    update public.sales s
    set status = 'PAID'
    where s.id = v_sale.id and s.status = 'SENT_TO_CASHIER'
    returning s.* into v_sale;

    if not found then
        raise exception using errcode = 'P0001', message = 'SALE_STATUS_INVALID';
    end if;

    insert into public.sale_status_history (
        sale_id, previous_status, new_status, changed_by, observation
    ) values (
        v_sale.id, 'SENT_TO_CASHIER', 'PAID', v_actor_id, 'Payment confirmed by cashier'
    );

    update public.sale_payment_claims c
    set consumed_at = v_now,
        closed_reason = 'CONFIRMED'
    where c.id = v_claim.id;

    return pg_catalog.jsonb_build_object(
        'idempotent_replay', false,
        'sale', pg_catalog.jsonb_build_object(
            'id', v_sale.id,
            'folio', v_sale.folio,
            'branch_id', v_sale.branch_id,
            'status', v_sale.status,
            'total_cents', v_sale.total_cents
        ),
        'payment', pg_catalog.jsonb_build_object(
            'id', v_payment.id,
            'sale_id', v_payment.sale_id,
            'cashier_id', v_payment.cashier_id,
            'idempotency_key', v_payment.idempotency_key,
            'method', v_payment.method,
            'amount_due_cents', v_payment.amount_due_cents,
            'amount_received_cents', v_payment.amount_received_cents,
            'change_cents', v_payment.change_cents,
            'reference', v_payment.reference,
            'created_at', v_payment.created_at
        )
    );
exception
    when unique_violation then
        raise exception using errcode = 'P0001', message = 'IDEMPOTENCY_CONFLICT';
end;
$$;

revoke all on function public.claim_sale_for_payment(
    pg_catalog.uuid, pg_catalog.uuid
) from public, anon, authenticated;
revoke all on function public.release_sale_payment_claim(
    pg_catalog.uuid, pg_catalog.uuid
) from public, anon, authenticated;
revoke all on function public.confirm_sale_payment(
    pg_catalog.uuid, pg_catalog.uuid, pg_catalog.uuid, public.payment_method,
    pg_catalog.int8, pg_catalog.text
) from public, anon, authenticated;

grant execute on function public.claim_sale_for_payment(
    pg_catalog.uuid, pg_catalog.uuid
) to authenticated, service_role;
grant execute on function public.release_sale_payment_claim(
    pg_catalog.uuid, pg_catalog.uuid
) to authenticated, service_role;
grant execute on function public.confirm_sale_payment(
    pg_catalog.uuid, pg_catalog.uuid, pg_catalog.uuid, public.payment_method,
    pg_catalog.int8, pg_catalog.text
) to authenticated, service_role;

comment on table public.sale_payment_claims is
'Auditable server-timed payment reservations. Client access is RPC-only.';
comment on table public.sale_payments is
'Canonical immutable MVP payment records. Client access is RPC-only.';
comment on column public.sale_payments.reference is
'Short external operation reference only. Never store PAN, CVV, or banking secrets.';

commit;
