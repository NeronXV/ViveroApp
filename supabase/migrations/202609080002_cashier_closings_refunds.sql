begin;

create table public.sale_refunds (
    id pg_catalog.uuid primary key default pg_catalog.gen_random_uuid(),
    sale_id pg_catalog.uuid not null unique references public.sales(id) on delete restrict,
    payment_id pg_catalog.uuid not null unique references public.sale_payments(id) on delete restrict,
    branch_id pg_catalog.uuid not null references public.branches(id) on delete restrict,
    refunded_by pg_catalog.uuid not null references public.profiles(id) on delete restrict,
    amount_cents pg_catalog.int8 not null check(amount_cents > 0),
    method public.payment_method not null,
    reason pg_catalog.text not null check(length(btrim(reason)) between 5 and 300),
    restock pg_catalog.bool not null,
    idempotency_key pg_catalog.uuid not null unique,
    created_at pg_catalog.timestamptz not null default pg_catalog.now()
);
create table public.cashier_closings (
    id pg_catalog.uuid primary key default pg_catalog.gen_random_uuid(),
    branch_id pg_catalog.uuid not null references public.branches(id) on delete restrict,
    cashier_id pg_catalog.uuid not null references public.profiles(id) on delete restrict,
    opening_cash_cents pg_catalog.int8 not null check(opening_cash_cents >= 0),
    cash_sales_cents pg_catalog.int8 not null,
    card_sales_cents pg_catalog.int8 not null,
    transfer_sales_cents pg_catalog.int8 not null,
    cash_refunds_cents pg_catalog.int8 not null,
    other_refunds_cents pg_catalog.int8 not null,
    counted_cash_cents pg_catalog.int8 not null check(counted_cash_cents >= 0),
    expected_cash_cents pg_catalog.int8 not null,
    difference_cents pg_catalog.int8 not null,
    idempotency_key pg_catalog.uuid not null unique,
    created_at pg_catalog.timestamptz not null default pg_catalog.now()
);
create table public.cashier_closing_payments (
    payment_id pg_catalog.uuid primary key references public.sale_payments(id) on delete restrict,
    closing_id pg_catalog.uuid not null references public.cashier_closings(id) on delete restrict
);
create table public.cashier_closing_refunds (
    refund_id pg_catalog.uuid primary key references public.sale_refunds(id) on delete restrict,
    closing_id pg_catalog.uuid not null references public.cashier_closings(id) on delete restrict
);
alter table public.sale_refunds enable row level security;
alter table public.cashier_closings enable row level security;
alter table public.cashier_closing_payments enable row level security;
alter table public.cashier_closing_refunds enable row level security;
revoke all on table public.sale_refunds, public.cashier_closings,
    public.cashier_closing_payments, public.cashier_closing_refunds from public, anon, authenticated;
create index refunds_branch_actor_idx on public.sale_refunds(branch_id, refunded_by, created_at);
create index closings_branch_actor_idx on public.cashier_closings(branch_id, cashier_id, created_at);

create or replace function public.get_refundable_sale(p_folio pg_catalog.text)
returns pg_catalog.jsonb language plpgsql stable security definer set search_path = ''
as $$
declare v_sale public.sales%rowtype; v_amount pg_catalog.int8;
begin
    if auth.uid() is null or not public.has_permission('OPERATE_CASHIER') or not public.has_permission('MANAGE_DISCOUNTS') then
        raise exception using errcode='42501',message='REFUND_UNAUTHORIZED';
    end if;
    select s.* into v_sale from public.sales s
    join public.profiles p on p.id=auth.uid() and p.branch_id=s.branch_id and p.is_active
    join public.branches b on b.id=s.branch_id and b.is_active
    where s.folio=btrim(p_folio) and s.status in ('PAID','DELIVERED');
    if not found then raise exception using errcode='P0001',message='REFUND_SALE_UNAVAILABLE'; end if;
    select amount_due_cents into strict v_amount from public.sale_payments where sale_id=v_sale.id;
    return pg_catalog.jsonb_build_object('folio',v_sale.folio,'amountCents',v_amount,
        'alreadyRefunded',exists(select 1 from public.sale_refunds where sale_id=v_sale.id));
end;
$$;
revoke all on function public.get_refundable_sale(text) from public,anon,authenticated;
grant execute on function public.get_refundable_sale(text) to authenticated;

-- Full refunds only. The operator records money returned in person; no bank charge is executed.
create or replace function public.refund_sale_in_person(
    p_folio pg_catalog.text, p_reason pg_catalog.text, p_method public.payment_method,
    p_restock pg_catalog.bool, p_money_returned pg_catalog.bool, p_idempotency_key pg_catalog.uuid
) returns pg_catalog.jsonb language plpgsql security definer set search_path = ''
as $$
declare
    v_actor pg_catalog.uuid := auth.uid(); v_branch pg_catalog.uuid;
    v_sale public.sales%rowtype; v_payment public.sale_payments%rowtype; v_refund public.sale_refunds%rowtype;
    v_reason pg_catalog.text := btrim(p_reason);
begin
    if v_actor is null or not public.has_permission('OPERATE_CASHIER')
       or not public.has_permission('MANAGE_DISCOUNTS') then
        raise exception using errcode='42501', message='REFUND_UNAUTHORIZED';
    end if;
    select p.branch_id into v_branch from public.profiles p join public.branches b on b.id=p.branch_id and b.is_active
    where p.id=v_actor and p.is_active;
    if v_branch is null then raise exception using errcode='42501',message='REFUND_UNAUTHORIZED'; end if;
    if p_money_returned is distinct from true or p_restock is null or p_method is null
       or p_idempotency_key is null or v_reason is null or length(v_reason) not between 5 and 300 then
        raise exception using errcode='22023',message='REFUND_DATA_INVALID';
    end if;
    select * into v_sale from public.sales where folio=btrim(p_folio) and branch_id=v_branch for update;
    if not found or v_sale.status not in ('PAID','DELIVERED') then
        raise exception using errcode='P0001',message='REFUND_SALE_UNAVAILABLE';
    end if;
    select * into v_refund from public.sale_refunds where sale_id=v_sale.id;
    if found then
        if v_refund.idempotency_key<>p_idempotency_key or v_refund.refunded_by<>v_actor
           or v_refund.reason<>v_reason or v_refund.method<>p_method or v_refund.restock<>p_restock then
            raise exception using errcode='P0001',message='REFUND_ALREADY_RECORDED';
        end if;
        return pg_catalog.jsonb_build_object('id',v_refund.id,'amountCents',v_refund.amount_cents,'idempotentReplay',true);
    end if;
    select * into strict v_payment from public.sale_payments where sale_id=v_sale.id;
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_branch::text,808));
    insert into public.sale_refunds(sale_id,payment_id,branch_id,refunded_by,amount_cents,method,reason,restock,idempotency_key)
    values(v_sale.id,v_payment.id,v_branch,v_actor,v_payment.amount_due_cents,p_method,v_reason,p_restock,p_idempotency_key)
    returning * into v_refund;
    if p_restock then
        -- Only put back stock actually deducted by this sale, even across activation dates.
        insert into public.inventory_movements(branch_id,product_id,movement_type,quantity,reference_id,created_by,notes)
        select v_branch,m.product_id,'ADJUSTMENT_ADD',-sum(m.quantity),v_refund.id,v_actor,
            'Devolución total ' || v_sale.folio || ': ' || v_reason
        from public.inventory_movements m where m.reference_id=v_sale.id and m.movement_type='SALE'
        group by m.product_id order by m.product_id;
    end if;
    return pg_catalog.jsonb_build_object('id',v_refund.id,'amountCents',v_refund.amount_cents,'idempotentReplay',false);
end;
$$;
revoke all on function public.refund_sale_in_person(text,text,public.payment_method,bool,bool,uuid) from public,anon,authenticated;
grant execute on function public.refund_sale_in_person(text,text,public.payment_method,bool,bool,uuid) to authenticated;

create or replace function public.get_my_cashier_closing_preview()
returns pg_catalog.jsonb language plpgsql stable security definer set search_path = ''
as $$
declare v_actor pg_catalog.uuid:=auth.uid(); v_branch pg_catalog.uuid; v_payments pg_catalog.jsonb; v_refunds pg_catalog.jsonb;
begin
    if v_actor is null or not public.has_permission('OPERATE_CASHIER') then
        raise exception using errcode='42501',message='CASHIER_UNAUTHORIZED';
    end if;
    select p.branch_id into v_branch from public.profiles p join public.branches b on b.id=p.branch_id and b.is_active
    where p.id=v_actor and p.is_active;
    if v_branch is null then raise exception using errcode='42501',message='CASHIER_UNAUTHORIZED'; end if;
    select pg_catalog.jsonb_build_object(
        'cash',coalesce(sum(p.amount_due_cents) filter(where p.method='CASH'),0),
        'card',coalesce(sum(p.amount_due_cents) filter(where p.method='CARD'),0),
        'transfer',coalesce(sum(p.amount_due_cents) filter(where p.method='TRANSFER'),0), 'count',count(*)
    ) into v_payments from public.sale_payments p
    where p.branch_id=v_branch and p.cashier_id=v_actor
      and not exists(select 1 from public.cashier_closing_payments c where c.payment_id=p.id);
    select pg_catalog.jsonb_build_object(
        'cash',coalesce(sum(r.amount_cents) filter(where r.method='CASH'),0),
        'other',coalesce(sum(r.amount_cents) filter(where r.method<>'CASH'),0), 'count',count(*)
    ) into v_refunds from public.sale_refunds r
    where r.branch_id=v_branch and r.refunded_by=v_actor
      and not exists(select 1 from public.cashier_closing_refunds c where c.refund_id=r.id);
    return pg_catalog.jsonb_build_object('payments',v_payments,'refunds',v_refunds,
        'lastClosing',(select pg_catalog.jsonb_build_object('id',c.id,'createdAt',c.created_at,
            'expectedCashCents',c.expected_cash_cents,'countedCashCents',c.counted_cash_cents,'differenceCents',c.difference_cents)
            from public.cashier_closings c where c.branch_id=v_branch and c.cashier_id=v_actor order by c.created_at desc,c.id desc limit 1));
end;
$$;
revoke all on function public.get_my_cashier_closing_preview() from public,anon,authenticated;
grant execute on function public.get_my_cashier_closing_preview() to authenticated;

create or replace function public.close_my_cashier(
    p_opening_cash_cents pg_catalog.int8, p_counted_cash_cents pg_catalog.int8, p_idempotency_key pg_catalog.uuid
) returns pg_catalog.jsonb language plpgsql security definer set search_path = ''
as $$
declare v_actor pg_catalog.uuid:=auth.uid(); v_branch pg_catalog.uuid; v_closing public.cashier_closings%rowtype;
    v_state pg_catalog.jsonb; v_payment_ids pg_catalog.uuid[]; v_refund_ids pg_catalog.uuid[];
    v_cash bigint; v_card bigint; v_transfer bigint; v_cash_refunds bigint; v_other_refunds bigint;
begin
    v_state:=public.get_my_cashier_closing_preview(); -- Enforce session, permission and active branch.
    select branch_id into v_branch from public.profiles where id=v_actor;
    if p_opening_cash_cents is null or p_counted_cash_cents is null or p_idempotency_key is null
       or p_opening_cash_cents<0 or p_counted_cash_cents<0 then
        raise exception using errcode='22023',message='CLOSING_DATA_INVALID';
    end if;
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_branch::text,808));
    select * into v_closing from public.cashier_closings where idempotency_key=p_idempotency_key;
    if found then
        if v_closing.cashier_id<>v_actor or v_closing.branch_id<>v_branch
          or v_closing.opening_cash_cents<>p_opening_cash_cents or v_closing.counted_cash_cents<>p_counted_cash_cents then
            raise exception using errcode='P0001',message='IDEMPOTENCY_CONFLICT';
        end if;
    else
        -- Assign exact committed operation IDs. A concurrent payment is never lost by timestamp boundaries.
        select coalesce(array_agg(p.id),'{}'::uuid[]),
            coalesce(sum(p.amount_due_cents) filter(where p.method='CASH'),0),
            coalesce(sum(p.amount_due_cents) filter(where p.method='CARD'),0),
            coalesce(sum(p.amount_due_cents) filter(where p.method='TRANSFER'),0)
        into v_payment_ids,v_cash,v_card,v_transfer from public.sale_payments p
        where p.branch_id=v_branch and p.cashier_id=v_actor
          and not exists(select 1 from public.cashier_closing_payments c where c.payment_id=p.id);
        select coalesce(array_agg(r.id),'{}'::uuid[]),
            coalesce(sum(r.amount_cents) filter(where r.method='CASH'),0),
            coalesce(sum(r.amount_cents) filter(where r.method<>'CASH'),0)
        into v_refund_ids,v_cash_refunds,v_other_refunds from public.sale_refunds r
        where r.branch_id=v_branch and r.refunded_by=v_actor
          and not exists(select 1 from public.cashier_closing_refunds c where c.refund_id=r.id);
        if cardinality(v_payment_ids)=0 and cardinality(v_refund_ids)=0 then
            raise exception using errcode='P0001',message='CLOSING_EMPTY';
        end if;
        insert into public.cashier_closings(branch_id,cashier_id,opening_cash_cents,cash_sales_cents,card_sales_cents,
            transfer_sales_cents,cash_refunds_cents,other_refunds_cents,counted_cash_cents,expected_cash_cents,difference_cents,idempotency_key)
        values(v_branch,v_actor,p_opening_cash_cents,v_cash,v_card,v_transfer,v_cash_refunds,v_other_refunds,
            p_counted_cash_cents,p_opening_cash_cents+v_cash-v_cash_refunds,
            p_counted_cash_cents-(p_opening_cash_cents+v_cash-v_cash_refunds),p_idempotency_key) returning * into v_closing;
        insert into public.cashier_closing_payments(payment_id,closing_id) select unnest(v_payment_ids),v_closing.id;
        insert into public.cashier_closing_refunds(refund_id,closing_id) select unnest(v_refund_ids),v_closing.id;
    end if;
    return pg_catalog.jsonb_build_object('id',v_closing.id,'createdAt',v_closing.created_at,
        'expectedCashCents',v_closing.expected_cash_cents,'countedCashCents',v_closing.counted_cash_cents,'differenceCents',v_closing.difference_cents);
end;
$$;
revoke all on function public.close_my_cashier(bigint,bigint,uuid) from public,anon,authenticated;
grant execute on function public.close_my_cashier(bigint,bigint,uuid) to authenticated;

-- A fully refunded order must never be handed out as a paid order.
create or replace function public.guard_web_order_checkout()
returns trigger language plpgsql security definer set search_path = ''
as $$
declare v_sale public.sales%rowtype;
begin
    if new.status is distinct from old.status then
        if new.status='COMPLETED' and not exists(select 1 from public.sales s
            join public.sale_payments p on p.sale_id=s.id where s.web_order_id=new.id and s.status in ('PAID','DELIVERED')
            and not exists(select 1 from public.sale_refunds r where r.sale_id=s.id)) then
            raise exception using errcode='P0001',message='WEB_ORDER_PAYMENT_REQUIRED';
        end if;
        if new.status='CANCELLED' then
            select * into v_sale from public.sales where web_order_id=new.id for update;
            if found then
                if v_sale.status in ('PAID','DELIVERED') or exists(select 1 from public.sale_payments where sale_id=v_sale.id)
                   or exists(select 1 from public.sale_payment_claims where sale_id=v_sale.id
                       and released_at is null and consumed_at is null and expires_at>now()) then
                    raise exception using errcode='P0001',message='WEB_ORDER_ALREADY_IN_CASHIER';
                end if;
                update public.sale_payment_claims set released_at=now(),closed_reason='EXPIRED'
                where sale_id=v_sale.id and released_at is null and consumed_at is null;
                update public.sales set status='CANCELLED' where id=v_sale.id;
                insert into public.sale_status_history(sale_id,previous_status,new_status,changed_by,observation)
                values(v_sale.id,v_sale.status,'CANCELLED',auth.uid(),'Pedido web cancelado antes de cobrar');
            end if;
        end if;
    end if;
    return new;
end;
$$;
revoke all on function public.guard_web_order_checkout() from public,anon,authenticated;
commit;
