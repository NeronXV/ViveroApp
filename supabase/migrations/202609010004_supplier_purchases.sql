begin;

create table public.suppliers (
    id pg_catalog.uuid primary key default pg_catalog.gen_random_uuid(),
    code pg_catalog.text not null unique
        check (code = pg_catalog.upper(pg_catalog.btrim(code)) and code ~ '^[A-Z0-9][A-Z0-9-]{1,31}$'),
    name pg_catalog.text not null
        check (pg_catalog.char_length(pg_catalog.btrim(name)) between 2 and 160),
    is_active pg_catalog.bool not null default true,
    created_at pg_catalog.timestamptz not null default pg_catalog.now(),
    updated_at pg_catalog.timestamptz not null default pg_catalog.now()
);

create table public.supplier_presentations (
    id pg_catalog.uuid primary key default pg_catalog.gen_random_uuid(),
    supplier_id pg_catalog.uuid not null references public.suppliers(id) on delete restrict,
    code pg_catalog.text not null
        check (pg_catalog.char_length(pg_catalog.btrim(code)) between 1 and 24),
    display_name pg_catalog.text
        check (display_name is null or pg_catalog.char_length(pg_catalog.btrim(display_name)) between 2 and 80),
    nominal_size pg_catalog.numeric(10, 2)
        check (nominal_size is null or nominal_size > 0),
    size_unit pg_catalog.text
        check (size_unit is null or size_unit in ('cm', 'in', 'l', 'gal')),
    notes pg_catalog.text
        check (notes is null or pg_catalog.char_length(pg_catalog.btrim(notes)) between 2 and 240),
    created_at pg_catalog.timestamptz not null default pg_catalog.now(),
    updated_at pg_catalog.timestamptz not null default pg_catalog.now(),
    unique (supplier_id, code),
    check ((nominal_size is null) = (size_unit is null))
);

create table public.supplier_purchase_documents (
    id pg_catalog.uuid primary key default pg_catalog.gen_random_uuid(),
    supplier_id pg_catalog.uuid not null references public.suppliers(id) on delete restrict,
    branch_id pg_catalog.uuid not null references public.branches(id) on delete restrict,
    document_date pg_catalog.date not null,
    external_reference pg_catalog.text
        check (external_reference is null or pg_catalog.char_length(pg_catalog.btrim(external_reference)) between 1 and 80),
    payment_terms pg_catalog.text not null check (payment_terms in ('CASH', 'CREDIT', 'OTHER')),
    expected_total_cents pg_catalog.int8 not null check (expected_total_cents >= 0),
    currency pg_catalog.text not null default 'MXN' check (currency = 'MXN'),
    source_file_name pg_catalog.text
        check (source_file_name is null or pg_catalog.char_length(pg_catalog.btrim(source_file_name)) between 1 and 180),
    source_items pg_catalog.jsonb not null check (pg_catalog.jsonb_typeof(source_items) = 'array'),
    status pg_catalog.text not null default 'DRAFT'
        check (status in ('DRAFT', 'RECEIVED', 'CANCELLED')),
    idempotency_key pg_catalog.uuid not null unique,
    confirmation_key pg_catalog.uuid unique,
    created_by pg_catalog.uuid not null references auth.users(id) on delete restrict,
    received_by pg_catalog.uuid references auth.users(id) on delete restrict,
    received_at pg_catalog.timestamptz,
    created_at pg_catalog.timestamptz not null default pg_catalog.now(),
    updated_at pg_catalog.timestamptz not null default pg_catalog.now(),
    check (
        (status = 'RECEIVED' and confirmation_key is not null and received_by is not null and received_at is not null)
        or (status <> 'RECEIVED' and confirmation_key is null and received_by is null and received_at is null)
    )
);

create table public.supplier_purchase_items (
    id pg_catalog.uuid primary key default pg_catalog.gen_random_uuid(),
    purchase_id pg_catalog.uuid not null references public.supplier_purchase_documents(id) on delete restrict,
    line_number pg_catalog.int4 not null check (line_number > 0),
    raw_description pg_catalog.text not null
        check (pg_catalog.char_length(pg_catalog.btrim(raw_description)) between 2 and 240),
    supplier_container_code pg_catalog.text not null
        check (pg_catalog.char_length(pg_catalog.btrim(supplier_container_code)) between 1 and 24),
    suggested_common_name pg_catalog.text
        check (suggested_common_name is null or pg_catalog.char_length(pg_catalog.btrim(suggested_common_name)) between 2 and 160),
    suggested_presentation pg_catalog.text
        check (suggested_presentation is null or pg_catalog.char_length(pg_catalog.btrim(suggested_presentation)) between 2 and 120),
    quantity pg_catalog.int4 not null check (quantity > 0 and quantity <= 1000000),
    unit_cost_cents pg_catalog.int8 not null check (unit_cost_cents >= 0),
    line_total_cents pg_catalog.int8 not null check (line_total_cents >= 0),
    product_id pg_catalog.uuid references public.products(id) on delete restrict,
    resolution_status pg_catalog.text not null default 'UNMATCHED'
        check (resolution_status in ('UNMATCHED', 'AUTO_MATCHED', 'MATCHED', 'IGNORED')),
    resolved_by pg_catalog.uuid references auth.users(id) on delete restrict,
    resolved_at pg_catalog.timestamptz,
    created_at pg_catalog.timestamptz not null default pg_catalog.now(),
    updated_at pg_catalog.timestamptz not null default pg_catalog.now(),
    unique (purchase_id, line_number),
    check (line_total_cents = quantity::pg_catalog.int8 * unit_cost_cents),
    check (
        (resolution_status in ('AUTO_MATCHED', 'MATCHED') and product_id is not null)
        or (resolution_status in ('UNMATCHED', 'IGNORED') and product_id is null)
    ),
    check (
        (resolution_status = 'UNMATCHED' and resolved_by is null and resolved_at is null)
        or (resolution_status <> 'UNMATCHED' and resolved_by is not null and resolved_at is not null)
    )
);

create table public.supplier_product_aliases (
    id pg_catalog.uuid primary key default pg_catalog.gen_random_uuid(),
    supplier_id pg_catalog.uuid not null references public.suppliers(id) on delete restrict,
    alias_key pg_catalog.text not null,
    raw_description pg_catalog.text not null
        check (pg_catalog.char_length(pg_catalog.btrim(raw_description)) between 2 and 240),
    supplier_container_code pg_catalog.text not null
        check (pg_catalog.char_length(pg_catalog.btrim(supplier_container_code)) between 1 and 24),
    product_id pg_catalog.uuid not null references public.products(id) on delete restrict,
    updated_by pg_catalog.uuid not null references auth.users(id) on delete restrict,
    created_at pg_catalog.timestamptz not null default pg_catalog.now(),
    updated_at pg_catalog.timestamptz not null default pg_catalog.now(),
    unique (supplier_id, alias_key)
);

create unique index supplier_purchase_external_reference_idx
on public.supplier_purchase_documents(supplier_id, external_reference)
where external_reference is not null;

create index supplier_purchase_branch_status_date_idx
on public.supplier_purchase_documents(branch_id, status, document_date desc, id desc);

create index supplier_purchase_items_purchase_status_idx
on public.supplier_purchase_items(purchase_id, resolution_status, line_number);

create index supplier_product_aliases_product_idx
on public.supplier_product_aliases(product_id, supplier_id);

create trigger suppliers_set_updated_at before update on public.suppliers
for each row execute function public.set_updated_at();
create trigger supplier_presentations_set_updated_at before update on public.supplier_presentations
for each row execute function public.set_updated_at();
create trigger supplier_purchase_documents_set_updated_at before update on public.supplier_purchase_documents
for each row execute function public.set_updated_at();
create trigger supplier_purchase_items_set_updated_at before update on public.supplier_purchase_items
for each row execute function public.set_updated_at();
create trigger supplier_product_aliases_set_updated_at before update on public.supplier_product_aliases
for each row execute function public.set_updated_at();

alter table public.suppliers enable row level security;
alter table public.supplier_presentations enable row level security;
alter table public.supplier_purchase_documents enable row level security;
alter table public.supplier_purchase_items enable row level security;
alter table public.supplier_product_aliases enable row level security;

revoke all on table public.suppliers, public.supplier_presentations,
    public.supplier_purchase_documents, public.supplier_purchase_items,
    public.supplier_product_aliases
from public, anon, authenticated;

create or replace function public.upsert_supplier(
    p_code pg_catalog.text,
    p_name pg_catalog.text,
    p_id pg_catalog.uuid default null
)
returns pg_catalog.jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_code pg_catalog.text := pg_catalog.upper(pg_catalog.btrim(p_code));
    v_name pg_catalog.text := pg_catalog.btrim(p_name);
    v_supplier public.suppliers%rowtype;
begin
    if v_actor_id is null or not public.has_permission('MANAGE_INVENTORY') then
        raise exception using errcode = '42501', message = 'SUPPLIER_MANAGEMENT_UNAUTHORIZED';
    end if;

    if v_code is null or v_code !~ '^[A-Z0-9][A-Z0-9-]{1,31}$'
       or v_name is null or pg_catalog.char_length(v_name) not between 2 and 160 then
        raise exception using errcode = '22023', message = 'SUPPLIER_INPUT_INVALID';
    end if;

    insert into public.suppliers(id, code, name)
    values (coalesce(p_id, pg_catalog.gen_random_uuid()), v_code, v_name)
    on conflict (code) do update
    set name = excluded.name,
        is_active = true,
        updated_at = pg_catalog.now()
    returning * into v_supplier;

    if p_id is not null and v_supplier.id <> p_id then
        raise exception using errcode = '23505', message = 'SUPPLIER_CODE_IN_USE';
    end if;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'supplier', pg_catalog.jsonb_build_object(
            'id', v_supplier.id,
            'code', v_supplier.code,
            'name', v_supplier.name,
            'isActive', v_supplier.is_active
        )
    );
exception
    when unique_violation then
        raise exception using errcode = '23505', message = 'SUPPLIER_CODE_IN_USE';
end;
$$;

create or replace function public.create_supplier_purchase_draft(
    p_supplier_id pg_catalog.uuid,
    p_document_date pg_catalog.date,
    p_external_reference pg_catalog.text,
    p_payment_terms pg_catalog.text,
    p_expected_total_cents pg_catalog.int8,
    p_source_file_name pg_catalog.text,
    p_items pg_catalog.jsonb,
    p_idempotency_key pg_catalog.uuid
)
returns pg_catalog.jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_branch_id pg_catalog.uuid;
    v_reference pg_catalog.text := nullif(pg_catalog.btrim(p_external_reference), '');
    v_source_file pg_catalog.text := nullif(pg_catalog.btrim(p_source_file_name), '');
    v_purchase public.supplier_purchase_documents%rowtype;
    v_item pg_catalog.jsonb;
    v_line_number pg_catalog.int4;
    v_description pg_catalog.text;
    v_container_code pg_catalog.text;
    v_suggested_name pg_catalog.text;
    v_suggested_presentation pg_catalog.text;
    v_quantity pg_catalog.int4;
    v_unit_cost_cents pg_catalog.int8;
    v_calculated_total pg_catalog.int8 := 0;
    v_product_id pg_catalog.uuid;
    v_alias_key pg_catalog.text;
    v_inserted pg_catalog.bool := false;
begin
    if v_actor_id is null or not public.has_permission('MANAGE_INVENTORY') then
        raise exception using errcode = '42501', message = 'PURCHASE_MANAGEMENT_UNAUTHORIZED';
    end if;

    select p.branch_id into v_branch_id
    from public.profiles p
    join public.branches b on b.id = p.branch_id and b.is_active
    where p.id = v_actor_id and p.is_active;

    if v_branch_id is null or p_supplier_id is null or p_document_date is null
       or p_idempotency_key is null or p_expected_total_cents is null
       or p_expected_total_cents < 0
       or p_payment_terms not in ('CASH', 'CREDIT', 'OTHER')
       or pg_catalog.char_length(coalesce(v_reference, '')) > 80
       or pg_catalog.char_length(coalesce(v_source_file, '')) > 180
       or coalesce(v_source_file, '') ~ '[/\\]'
       or coalesce(v_source_file, '') ~ '[[:cntrl:]]'
       or not exists (
           select 1 from public.suppliers s where s.id = p_supplier_id and s.is_active
       )
       or p_items is null or pg_catalog.jsonb_typeof(p_items) <> 'array'
       or pg_catalog.jsonb_array_length(p_items) not between 1 and 250 then
        raise exception using errcode = '22023', message = 'PURCHASE_DRAFT_INVALID';
    end if;

    if exists (
        select 1
        from pg_catalog.jsonb_array_elements(p_items) item
        group by item.value ->> 'lineNumber'
        having pg_catalog.count(*) > 1
    ) then
        raise exception using errcode = '22023', message = 'PURCHASE_LINE_DUPLICATE';
    end if;

    for v_item in select value from pg_catalog.jsonb_array_elements(p_items)
    loop
        if pg_catalog.jsonb_typeof(v_item) <> 'object'
           or coalesce(v_item ->> 'lineNumber', '') !~ '^[1-9][0-9]{0,5}$'
           or coalesce(v_item ->> 'quantity', '') !~ '^[1-9][0-9]{0,6}$'
           or coalesce(v_item ->> 'unitCostCents', '') !~ '^[0-9]{1,12}$' then
            raise exception using errcode = '22023', message = 'PURCHASE_LINE_INVALID';
        end if;

        v_line_number := (v_item ->> 'lineNumber')::pg_catalog.int4;
        v_description := pg_catalog.btrim(v_item ->> 'rawDescription');
        v_container_code := pg_catalog.upper(pg_catalog.btrim(v_item ->> 'containerCode'));
        v_suggested_name := nullif(pg_catalog.btrim(v_item ->> 'suggestedCommonName'), '');
        v_suggested_presentation := nullif(pg_catalog.btrim(v_item ->> 'suggestedPresentation'), '');
        v_quantity := (v_item ->> 'quantity')::pg_catalog.int4;
        v_unit_cost_cents := (v_item ->> 'unitCostCents')::pg_catalog.int8;

        if v_description is null or pg_catalog.char_length(v_description) not between 2 and 240
           or v_description ~ '[[:cntrl:]]'
           or v_container_code is null or pg_catalog.char_length(v_container_code) not between 1 and 24
           or v_container_code ~ '[[:cntrl:]]'
           or v_quantity > 1000000
           or pg_catalog.char_length(coalesce(v_suggested_name, '')) > 160
           or pg_catalog.char_length(coalesce(v_suggested_presentation, '')) > 120 then
            raise exception using errcode = '22023', message = 'PURCHASE_LINE_INVALID';
        end if;

        v_calculated_total := v_calculated_total + v_quantity::pg_catalog.int8 * v_unit_cost_cents;
    end loop;

    if v_calculated_total <> p_expected_total_cents then
        raise exception using errcode = '22023', message = 'PURCHASE_TOTAL_MISMATCH';
    end if;

    insert into public.supplier_purchase_documents(
        supplier_id, branch_id, document_date, external_reference, payment_terms,
        expected_total_cents, source_file_name, source_items, idempotency_key, created_by
    ) values (
        p_supplier_id, v_branch_id, p_document_date, v_reference, p_payment_terms,
        p_expected_total_cents, v_source_file, p_items, p_idempotency_key, v_actor_id
    )
    on conflict (idempotency_key) do nothing
    returning * into v_purchase;

    v_inserted := found;
    if not v_inserted then
        select * into strict v_purchase
        from public.supplier_purchase_documents d
        where d.idempotency_key = p_idempotency_key;

        if v_purchase.supplier_id <> p_supplier_id
           or v_purchase.branch_id <> v_branch_id
           or v_purchase.document_date <> p_document_date
           or v_purchase.external_reference is distinct from v_reference
           or v_purchase.payment_terms <> p_payment_terms
           or v_purchase.expected_total_cents <> p_expected_total_cents
           or v_purchase.source_file_name is distinct from v_source_file
           or v_purchase.source_items <> p_items
           or v_purchase.created_by <> v_actor_id then
            raise exception using errcode = 'P0001', message = 'PURCHASE_IDEMPOTENCY_CONFLICT';
        end if;

        return pg_catalog.jsonb_build_object(
            'schemaVersion', 1,
            'idempotentReplay', true,
            'purchaseId', v_purchase.id,
            'status', v_purchase.status,
            'itemCount', pg_catalog.jsonb_array_length(v_purchase.source_items),
            'expectedTotalCents', v_purchase.expected_total_cents
        );
    end if;

    for v_item in select value from pg_catalog.jsonb_array_elements(p_items)
    loop
        v_line_number := (v_item ->> 'lineNumber')::pg_catalog.int4;
        v_description := pg_catalog.btrim(v_item ->> 'rawDescription');
        v_container_code := pg_catalog.upper(pg_catalog.btrim(v_item ->> 'containerCode'));
        v_suggested_name := nullif(pg_catalog.btrim(v_item ->> 'suggestedCommonName'), '');
        v_suggested_presentation := nullif(pg_catalog.btrim(v_item ->> 'suggestedPresentation'), '');
        v_quantity := (v_item ->> 'quantity')::pg_catalog.int4;
        v_unit_cost_cents := (v_item ->> 'unitCostCents')::pg_catalog.int8;
        v_alias_key := pg_catalog.lower(v_description) || '|' || pg_catalog.lower(v_container_code);

        select a.product_id into v_product_id
        from public.supplier_product_aliases a
        join public.products p on p.id = a.product_id and p.is_active
        where a.supplier_id = p_supplier_id and a.alias_key = v_alias_key;

        insert into public.supplier_presentations(supplier_id, code)
        values (p_supplier_id, v_container_code)
        on conflict (supplier_id, code) do nothing;

        insert into public.supplier_purchase_items(
            purchase_id, line_number, raw_description, supplier_container_code,
            suggested_common_name, suggested_presentation, quantity,
            unit_cost_cents, line_total_cents, product_id, resolution_status,
            resolved_by, resolved_at
        ) values (
            v_purchase.id, v_line_number, v_description, v_container_code,
            v_suggested_name, v_suggested_presentation, v_quantity,
            v_unit_cost_cents, v_quantity::pg_catalog.int8 * v_unit_cost_cents,
            v_product_id,
            case when v_product_id is null then 'UNMATCHED' else 'AUTO_MATCHED' end,
            case when v_product_id is null then null else v_actor_id end,
            case when v_product_id is null then null else pg_catalog.now() end
        );
    end loop;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'idempotentReplay', false,
        'purchaseId', v_purchase.id,
        'status', v_purchase.status,
        'itemCount', pg_catalog.jsonb_array_length(p_items),
        'expectedTotalCents', v_purchase.expected_total_cents
    );
exception
    when unique_violation then
        raise exception using errcode = '23505', message = 'PURCHASE_REFERENCE_IN_USE';
end;
$$;

create or replace function public.get_my_supplier_purchases(
    p_status pg_catalog.text default null,
    p_limit pg_catalog.int4 default 50
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
    v_items pg_catalog.jsonb;
begin
    if v_actor_id is null or not public.has_permission('MANAGE_INVENTORY') then
        raise exception using errcode = '42501', message = 'PURCHASE_MANAGEMENT_UNAUTHORIZED';
    end if;

    select p.branch_id into v_branch_id
    from public.profiles p
    join public.branches b on b.id = p.branch_id and b.is_active
    where p.id = v_actor_id and p.is_active;

    if v_branch_id is null or (p_status is not null and p_status not in ('DRAFT', 'RECEIVED', 'CANCELLED'))
       or p_limit is null or p_limit not between 1 and 100 then
        raise exception using errcode = '22023', message = 'PURCHASE_QUERY_INVALID';
    end if;

    select coalesce(
        pg_catalog.jsonb_agg(
            pg_catalog.jsonb_build_object(
                'id', d.id,
                'supplier', pg_catalog.jsonb_build_object('id', s.id, 'code', s.code, 'name', s.name),
                'documentDate', d.document_date,
                'externalReference', d.external_reference,
                'paymentTerms', d.payment_terms,
                'expectedTotalCents', d.expected_total_cents,
                'status', d.status,
                'itemCount', counts.item_count,
                'unmatchedCount', counts.unmatched_count,
                'createdAt', d.created_at,
                'receivedAt', d.received_at
            ) order by d.document_date desc, d.id desc
        ),
        '[]'::pg_catalog.jsonb
    ) into v_items
    from (
        select candidate.*
        from public.supplier_purchase_documents candidate
        where candidate.branch_id = v_branch_id
          and (p_status is null or candidate.status = p_status)
        order by candidate.document_date desc, candidate.id desc
        limit p_limit
    ) d
    join public.suppliers s on s.id = d.supplier_id
    cross join lateral (
        select
            pg_catalog.count(*)::pg_catalog.int4 as item_count,
            pg_catalog.count(*) filter (where i.resolution_status = 'UNMATCHED')::pg_catalog.int4 as unmatched_count
        from public.supplier_purchase_items i
        where i.purchase_id = d.id
    ) counts;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'branchId', v_branch_id,
        'items', v_items
    );
end;
$$;

create or replace function public.get_supplier_purchase(
    p_purchase_id pg_catalog.uuid
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
    v_result pg_catalog.jsonb;
begin
    if v_actor_id is null or not public.has_permission('MANAGE_INVENTORY') then
        raise exception using errcode = '42501', message = 'PURCHASE_MANAGEMENT_UNAUTHORIZED';
    end if;

    select p.branch_id into v_branch_id
    from public.profiles p
    join public.branches b on b.id = p.branch_id and b.is_active
    where p.id = v_actor_id and p.is_active;

    select pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'purchase', pg_catalog.jsonb_build_object(
            'id', d.id,
            'branchId', d.branch_id,
            'supplier', pg_catalog.jsonb_build_object('id', s.id, 'code', s.code, 'name', s.name),
            'documentDate', d.document_date,
            'externalReference', d.external_reference,
            'paymentTerms', d.payment_terms,
            'expectedTotalCents', d.expected_total_cents,
            'currency', d.currency,
            'sourceFileName', d.source_file_name,
            'status', d.status,
            'createdAt', d.created_at,
            'receivedAt', d.received_at,
            'items', coalesce(items.value, '[]'::pg_catalog.jsonb)
        )
    ) into v_result
    from public.supplier_purchase_documents d
    join public.suppliers s on s.id = d.supplier_id
    left join lateral (
        select pg_catalog.jsonb_agg(
            pg_catalog.jsonb_build_object(
                'id', i.id,
                'lineNumber', i.line_number,
                'rawDescription', i.raw_description,
                'containerCode', i.supplier_container_code,
                'supplierPresentation', pg_catalog.jsonb_build_object(
                    'displayName', presentation.display_name,
                    'nominalSize', presentation.nominal_size,
                    'sizeUnit', presentation.size_unit,
                    'notes', presentation.notes
                ),
                'suggestedCommonName', i.suggested_common_name,
                'suggestedPresentation', i.suggested_presentation,
                'quantity', i.quantity,
                'unitCostCents', i.unit_cost_cents,
                'lineTotalCents', i.line_total_cents,
                'productId', i.product_id,
                'resolutionStatus', i.resolution_status
            ) order by i.line_number
        ) as value
        from public.supplier_purchase_items i
        join public.supplier_presentations presentation
          on presentation.supplier_id = d.supplier_id
         and presentation.code = i.supplier_container_code
        where i.purchase_id = d.id
    ) items on true
    where d.id = p_purchase_id and d.branch_id = v_branch_id;

    if v_result is null then
        raise exception using errcode = '22023', message = 'PURCHASE_NOT_FOUND';
    end if;

    return v_result;
end;
$$;

create or replace function public.set_supplier_presentation(
    p_supplier_id pg_catalog.uuid,
    p_code pg_catalog.text,
    p_display_name pg_catalog.text,
    p_nominal_size pg_catalog.numeric default null,
    p_size_unit pg_catalog.text default null,
    p_notes pg_catalog.text default null
)
returns pg_catalog.jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_code pg_catalog.text := pg_catalog.upper(pg_catalog.btrim(p_code));
    v_display_name pg_catalog.text := nullif(pg_catalog.btrim(p_display_name), '');
    v_notes pg_catalog.text := nullif(pg_catalog.btrim(p_notes), '');
    v_presentation public.supplier_presentations%rowtype;
begin
    if v_actor_id is null or not public.has_permission('MANAGE_INVENTORY') then
        raise exception using errcode = '42501', message = 'PURCHASE_MANAGEMENT_UNAUTHORIZED';
    end if;

    if p_supplier_id is null
       or not exists (select 1 from public.suppliers s where s.id = p_supplier_id and s.is_active)
       or v_code is null or pg_catalog.char_length(v_code) not between 1 and 24
       or v_display_name is null or pg_catalog.char_length(v_display_name) not between 2 and 80
       or (p_nominal_size is null) <> (p_size_unit is null)
       or (p_nominal_size is not null and p_nominal_size <= 0)
       or (p_size_unit is not null and p_size_unit not in ('cm', 'in', 'l', 'gal'))
       or pg_catalog.char_length(coalesce(v_notes, '')) > 240 then
        raise exception using errcode = '22023', message = 'SUPPLIER_PRESENTATION_INVALID';
    end if;

    insert into public.supplier_presentations(
        supplier_id, code, display_name, nominal_size, size_unit, notes
    ) values (
        p_supplier_id, v_code, v_display_name, p_nominal_size, p_size_unit, v_notes
    )
    on conflict (supplier_id, code) do update
    set display_name = excluded.display_name,
        nominal_size = excluded.nominal_size,
        size_unit = excluded.size_unit,
        notes = excluded.notes,
        updated_at = pg_catalog.now()
    returning * into v_presentation;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'presentation', pg_catalog.jsonb_build_object(
            'id', v_presentation.id,
            'supplierId', v_presentation.supplier_id,
            'code', v_presentation.code,
            'displayName', v_presentation.display_name,
            'nominalSize', v_presentation.nominal_size,
            'sizeUnit', v_presentation.size_unit,
            'notes', v_presentation.notes
        )
    );
end;
$$;

create or replace function public.resolve_supplier_purchase_item(
    p_item_id pg_catalog.uuid,
    p_resolution pg_catalog.text,
    p_product_id pg_catalog.uuid default null,
    p_normalized_name pg_catalog.text default null,
    p_presentation pg_catalog.text default null
)
returns pg_catalog.jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_branch_id pg_catalog.uuid;
    v_item public.supplier_purchase_items%rowtype;
    v_purchase public.supplier_purchase_documents%rowtype;
    v_name pg_catalog.text := nullif(pg_catalog.btrim(p_normalized_name), '');
    v_presentation pg_catalog.text := nullif(pg_catalog.btrim(p_presentation), '');
    v_alias_key pg_catalog.text;
begin
    if v_actor_id is null or not public.has_permission('MANAGE_INVENTORY') then
        raise exception using errcode = '42501', message = 'PURCHASE_MANAGEMENT_UNAUTHORIZED';
    end if;

    select p.branch_id into v_branch_id
    from public.profiles p
    join public.branches b on b.id = p.branch_id and b.is_active
    where p.id = v_actor_id and p.is_active;

    select i.* into v_item
    from public.supplier_purchase_items i
    where i.id = p_item_id
    for update;

    if not found then
        raise exception using errcode = '22023', message = 'PURCHASE_ITEM_NOT_FOUND';
    end if;

    select * into strict v_purchase
    from public.supplier_purchase_documents d
    where d.id = v_item.purchase_id;

    if v_branch_id is null or v_purchase.branch_id <> v_branch_id or v_purchase.status <> 'DRAFT'
       or p_resolution not in ('MATCHED', 'IGNORED')
       or pg_catalog.char_length(coalesce(v_name, '')) > 160
       or pg_catalog.char_length(coalesce(v_presentation, '')) > 120
       or (p_resolution = 'MATCHED' and (
           p_product_id is null or not exists (
               select 1 from public.products p where p.id = p_product_id and p.is_active
           )
       ))
       or (p_resolution = 'IGNORED' and p_product_id is not null) then
        raise exception using errcode = '22023', message = 'PURCHASE_ITEM_RESOLUTION_INVALID';
    end if;

    update public.supplier_purchase_items
    set product_id = case when p_resolution = 'MATCHED' then p_product_id else null end,
        resolution_status = p_resolution,
        suggested_common_name = coalesce(v_name, suggested_common_name),
        suggested_presentation = coalesce(v_presentation, suggested_presentation),
        resolved_by = v_actor_id,
        resolved_at = pg_catalog.now(),
        updated_at = pg_catalog.now()
    where id = p_item_id
    returning * into v_item;

    if p_resolution = 'MATCHED' then
        v_alias_key := pg_catalog.lower(pg_catalog.btrim(v_item.raw_description))
            || '|' || pg_catalog.lower(pg_catalog.btrim(v_item.supplier_container_code));

        insert into public.supplier_product_aliases(
            supplier_id, alias_key, raw_description, supplier_container_code,
            product_id, updated_by
        ) values (
            v_purchase.supplier_id, v_alias_key, v_item.raw_description,
            v_item.supplier_container_code, p_product_id, v_actor_id
        )
        on conflict (supplier_id, alias_key) do update
        set product_id = excluded.product_id,
            raw_description = excluded.raw_description,
            supplier_container_code = excluded.supplier_container_code,
            updated_by = excluded.updated_by,
            updated_at = pg_catalog.now();
    end if;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'itemId', v_item.id,
        'resolutionStatus', v_item.resolution_status,
        'productId', v_item.product_id
    );
end;
$$;

create or replace function public.confirm_supplier_purchase(
    p_purchase_id pg_catalog.uuid,
    p_confirmation_key pg_catalog.uuid
)
returns pg_catalog.jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id pg_catalog.uuid := auth.uid();
    v_branch_id pg_catalog.uuid;
    v_purchase public.supplier_purchase_documents%rowtype;
    v_item public.supplier_purchase_items%rowtype;
    v_received_count pg_catalog.int4 := 0;
    v_received_quantity pg_catalog.int8 := 0;
begin
    if v_actor_id is null or not public.has_permission('MANAGE_INVENTORY') then
        raise exception using errcode = '42501', message = 'PURCHASE_MANAGEMENT_UNAUTHORIZED';
    end if;

    select p.branch_id into v_branch_id
    from public.profiles p
    join public.branches b on b.id = p.branch_id and b.is_active
    where p.id = v_actor_id and p.is_active;

    if v_branch_id is null or p_purchase_id is null or p_confirmation_key is null then
        raise exception using errcode = '22023', message = 'PURCHASE_CONFIRMATION_INVALID';
    end if;

    select * into v_purchase
    from public.supplier_purchase_documents d
    where d.id = p_purchase_id
    for update;

    if not found or v_purchase.branch_id <> v_branch_id then
        raise exception using errcode = '22023', message = 'PURCHASE_NOT_FOUND';
    end if;

    if v_purchase.status = 'RECEIVED' then
        if v_purchase.confirmation_key <> p_confirmation_key then
            raise exception using errcode = 'P0001', message = 'PURCHASE_CONFIRMATION_CONFLICT';
        end if;

        select
            pg_catalog.count(*)::pg_catalog.int4,
            coalesce(pg_catalog.sum(i.quantity), 0)::pg_catalog.int8
        into v_received_count, v_received_quantity
        from public.supplier_purchase_items i
        where i.purchase_id = p_purchase_id and i.resolution_status <> 'IGNORED';

        return pg_catalog.jsonb_build_object(
            'schemaVersion', 1,
            'idempotentReplay', true,
            'purchaseId', v_purchase.id,
            'status', v_purchase.status,
            'receivedItemCount', v_received_count,
            'receivedQuantity', v_received_quantity
        );
    end if;

    if v_purchase.status <> 'DRAFT'
       or exists (
           select 1 from public.supplier_purchase_items i
           where i.purchase_id = p_purchase_id and i.resolution_status = 'UNMATCHED'
       )
       or not exists (
           select 1 from public.supplier_purchase_items i
           where i.purchase_id = p_purchase_id and i.resolution_status in ('AUTO_MATCHED', 'MATCHED')
       ) then
        raise exception using errcode = 'P0001', message = 'PURCHASE_NOT_READY';
    end if;

    for v_item in
        select i.*
        from public.supplier_purchase_items i
        join public.products p on p.id = i.product_id and p.is_active
        where i.purchase_id = p_purchase_id
          and i.resolution_status in ('AUTO_MATCHED', 'MATCHED')
        order by i.line_number
    loop
        insert into public.inventory_movements(
            branch_id, product_id, movement_type, quantity, reference_id, notes, created_by
        ) values (
            v_branch_id, v_item.product_id, 'RECEPTION', v_item.quantity,
            v_item.id,
            'Compra de proveedor ' || v_purchase.id::pg_catalog.text
                || ', línea ' || v_item.line_number::pg_catalog.text,
            v_actor_id
        )
        on conflict do nothing;

        if not found then
            raise exception using errcode = 'P0001', message = 'PURCHASE_MOVEMENT_CONFLICT';
        end if;

        v_received_count := v_received_count + 1;
        v_received_quantity := v_received_quantity + v_item.quantity;
    end loop;

    if v_received_count <> (
        select pg_catalog.count(*)
        from public.supplier_purchase_items i
        where i.purchase_id = p_purchase_id
          and i.resolution_status in ('AUTO_MATCHED', 'MATCHED')
    ) then
        raise exception using errcode = 'P0001', message = 'PURCHASE_PRODUCT_UNAVAILABLE';
    end if;

    update public.supplier_purchase_documents
    set status = 'RECEIVED',
        confirmation_key = p_confirmation_key,
        received_by = v_actor_id,
        received_at = pg_catalog.now(),
        updated_at = pg_catalog.now()
    where id = p_purchase_id
    returning * into v_purchase;

    return pg_catalog.jsonb_build_object(
        'schemaVersion', 1,
        'idempotentReplay', false,
        'purchaseId', v_purchase.id,
        'status', v_purchase.status,
        'receivedItemCount', v_received_count,
        'receivedQuantity', v_received_quantity
    );
end;
$$;

alter function public.upsert_supplier(pg_catalog.text, pg_catalog.text, pg_catalog.uuid) owner to postgres;
alter function public.create_supplier_purchase_draft(
    pg_catalog.uuid, pg_catalog.date, pg_catalog.text, pg_catalog.text,
    pg_catalog.int8, pg_catalog.text, pg_catalog.jsonb, pg_catalog.uuid
) owner to postgres;
alter function public.get_my_supplier_purchases(pg_catalog.text, pg_catalog.int4) owner to postgres;
alter function public.get_supplier_purchase(pg_catalog.uuid) owner to postgres;
alter function public.set_supplier_presentation(
    pg_catalog.uuid, pg_catalog.text, pg_catalog.text, pg_catalog.numeric,
    pg_catalog.text, pg_catalog.text
) owner to postgres;
alter function public.resolve_supplier_purchase_item(
    pg_catalog.uuid, pg_catalog.text, pg_catalog.uuid, pg_catalog.text, pg_catalog.text
) owner to postgres;
alter function public.confirm_supplier_purchase(pg_catalog.uuid, pg_catalog.uuid) owner to postgres;

revoke all on function public.upsert_supplier(pg_catalog.text, pg_catalog.text, pg_catalog.uuid)
from public, anon, authenticated, service_role;
revoke all on function public.create_supplier_purchase_draft(
    pg_catalog.uuid, pg_catalog.date, pg_catalog.text, pg_catalog.text,
    pg_catalog.int8, pg_catalog.text, pg_catalog.jsonb, pg_catalog.uuid
) from public, anon, authenticated, service_role;
revoke all on function public.get_my_supplier_purchases(pg_catalog.text, pg_catalog.int4)
from public, anon, authenticated, service_role;
revoke all on function public.get_supplier_purchase(pg_catalog.uuid)
from public, anon, authenticated, service_role;
revoke all on function public.set_supplier_presentation(
    pg_catalog.uuid, pg_catalog.text, pg_catalog.text, pg_catalog.numeric,
    pg_catalog.text, pg_catalog.text
) from public, anon, authenticated, service_role;
revoke all on function public.resolve_supplier_purchase_item(
    pg_catalog.uuid, pg_catalog.text, pg_catalog.uuid, pg_catalog.text, pg_catalog.text
) from public, anon, authenticated, service_role;
revoke all on function public.confirm_supplier_purchase(pg_catalog.uuid, pg_catalog.uuid)
from public, anon, authenticated, service_role;

grant execute on function public.upsert_supplier(pg_catalog.text, pg_catalog.text, pg_catalog.uuid)
to authenticated;
grant execute on function public.create_supplier_purchase_draft(
    pg_catalog.uuid, pg_catalog.date, pg_catalog.text, pg_catalog.text,
    pg_catalog.int8, pg_catalog.text, pg_catalog.jsonb, pg_catalog.uuid
) to authenticated;
grant execute on function public.get_my_supplier_purchases(pg_catalog.text, pg_catalog.int4)
to authenticated;
grant execute on function public.get_supplier_purchase(pg_catalog.uuid)
to authenticated;
grant execute on function public.set_supplier_presentation(
    pg_catalog.uuid, pg_catalog.text, pg_catalog.text, pg_catalog.numeric,
    pg_catalog.text, pg_catalog.text
) to authenticated;
grant execute on function public.resolve_supplier_purchase_item(
    pg_catalog.uuid, pg_catalog.text, pg_catalog.uuid, pg_catalog.text, pg_catalog.text
) to authenticated;
grant execute on function public.confirm_supplier_purchase(pg_catalog.uuid, pg_catalog.uuid)
to authenticated;

comment on table public.supplier_purchase_documents is
'Auditable supplier purchase headers. DRAFT documents do not affect inventory; RECEIVED documents were applied atomically.';
comment on table public.supplier_purchase_items is
'Raw supplier lines preserve purchase cost separately from the customer sale price and must be matched or ignored before receipt.';
comment on table public.supplier_product_aliases is
'Learned supplier description and container mappings used to auto-match future draft purchases.';

commit;
