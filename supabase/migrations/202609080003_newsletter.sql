begin;
create table public.newsletter_subscribers (
    id uuid primary key default gen_random_uuid(),
    email text not null unique check(length(email)<=254 and email ~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'),
    confirmation_token uuid not null unique default gen_random_uuid(),
    unsubscribe_token uuid not null unique default gen_random_uuid(),
    confirmed_at timestamptz,
    unsubscribed_at timestamptz,
    requested_at timestamptz not null default now()
);
create table public.newsletter_campaigns (
    id uuid primary key,
    subject text not null check(length(btrim(subject)) between 3 and 150),
    body text not null check(length(btrim(body)) between 10 and 10000),
    created_by uuid not null references public.profiles(id),
    created_at timestamptz not null default now()
);
create table public.newsletter_deliveries (
    campaign_id uuid not null references public.newsletter_campaigns(id),
    subscriber_id uuid not null references public.newsletter_subscribers(id),
    first_attempt_at timestamptz,
    sent_at timestamptz,
    skipped_at timestamptz,
    primary key(campaign_id,subscriber_id)
);
alter table public.newsletter_subscribers enable row level security;
alter table public.newsletter_campaigns enable row level security;
alter table public.newsletter_deliveries enable row level security;
revoke all on table public.newsletter_subscribers,public.newsletter_campaigns,public.newsletter_deliveries from public,anon,authenticated;
grant select,insert,update on table public.newsletter_subscribers,public.newsletter_campaigns,public.newsletter_deliveries to service_role;

-- Only the mail function obtains confirmation tokens; public clients cannot enumerate email addresses.
create or replace function public.prepare_newsletter_subscription(p_email text)
returns jsonb language plpgsql security definer set search_path=''
as $$
declare v_row public.newsletter_subscribers%rowtype; v_email text:=lower(btrim(p_email));
begin
    if v_email is null or length(v_email)>254 or v_email !~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$' then
        raise exception using errcode='22023',message='NEWSLETTER_EMAIL_INVALID';
    end if;
    perform pg_catalog.pg_advisory_xact_lock(808003);
    select * into v_row from public.newsletter_subscribers where email=v_email for update;
    if found and v_row.confirmed_at is not null and v_row.unsubscribed_at is null then return null; end if;
    if found and v_row.requested_at > now()-interval '10 minutes' then return null; end if;
    if (select count(*) from public.newsletter_subscribers where requested_at>now()-interval '1 hour') >= 100 then
        raise exception using errcode='P0001',message='NEWSLETTER_RATE_LIMITED';
    end if;
    insert into public.newsletter_subscribers(email) values(v_email)
    on conflict(email) do update set confirmation_token=gen_random_uuid(),requested_at=now()
    returning * into v_row;
    return jsonb_build_object('id',v_row.id,'email',v_row.email,'token',v_row.confirmation_token);
end;
$$;
revoke all on function public.prepare_newsletter_subscription(text) from public,anon,authenticated;
grant execute on function public.prepare_newsletter_subscription(text) to service_role;

create or replace function public.confirm_newsletter_subscription(p_token uuid)
returns void language plpgsql security definer set search_path=''
as $$
begin
    update public.newsletter_subscribers set confirmed_at=now(),unsubscribed_at=null,confirmation_token=gen_random_uuid()
    where confirmation_token=p_token and requested_at>now()-interval '24 hours';
    if not found then raise exception using errcode='P0001',message='NEWSLETTER_TOKEN_INVALID'; end if;
end;
$$;
revoke all on function public.confirm_newsletter_subscription(uuid) from public,anon,authenticated;
grant execute on function public.confirm_newsletter_subscription(uuid) to anon,authenticated;
create or replace function public.unsubscribe_newsletter(p_token uuid)
returns void language sql security definer set search_path=''
as $$ update public.newsletter_subscribers set unsubscribed_at=now() where unsubscribe_token=p_token; $$;
revoke all on function public.unsubscribe_newsletter(uuid) from public,anon,authenticated;
grant execute on function public.unsubscribe_newsletter(uuid) to anon,authenticated;

create or replace function public.create_newsletter_campaign(p_id uuid,p_subject text,p_body text)
returns jsonb language plpgsql security definer set search_path=''
as $$
declare v_row public.newsletter_campaigns%rowtype; v_created bool;
begin
    if auth.uid() is null or not public.has_permission('MANAGE_SETTINGS') then
        raise exception using errcode='42501',message='NEWSLETTER_UNAUTHORIZED';
    end if;
    insert into public.newsletter_campaigns(id,subject,body,created_by)
    values(p_id,btrim(p_subject),btrim(p_body),auth.uid()) on conflict(id) do nothing;
    v_created:=found;
    select * into strict v_row from public.newsletter_campaigns where id=p_id;
    if v_row.created_by<>auth.uid() or v_row.subject<>btrim(p_subject) or v_row.body<>btrim(p_body) then
        raise exception using errcode='P0001',message='IDEMPOTENCY_CONFLICT';
    end if;
    if v_created then
    insert into public.newsletter_deliveries(campaign_id,subscriber_id)
    select p_id,id from public.newsletter_subscribers where confirmed_at is not null and unsubscribed_at is null
    on conflict do nothing;
    end if;
    return jsonb_build_object('id',p_id,'recipients',(select count(*) from public.newsletter_deliveries where campaign_id=p_id));
end;
$$;
revoke all on function public.create_newsletter_campaign(uuid,text,text) from public,anon,authenticated;
grant execute on function public.create_newsletter_campaign(uuid,text,text) to authenticated;

create or replace function public.get_newsletter_campaigns()
returns jsonb language plpgsql stable security definer set search_path=''
as $$
begin
    if auth.uid() is null or not public.has_permission('MANAGE_SETTINGS') then
        raise exception using errcode='42501',message='NEWSLETTER_UNAUTHORIZED';
    end if;
    return coalesce((select jsonb_agg(jsonb_build_object('id',c.id,'subject',c.subject,'body',c.body,'createdAt',c.created_at,
        'recipients',(select count(*) from public.newsletter_deliveries d where d.campaign_id=c.id),
        'sent',(select count(*) from public.newsletter_deliveries d where d.campaign_id=c.id and d.sent_at is not null),
        'skipped',(select count(*) from public.newsletter_deliveries d where d.campaign_id=c.id and d.skipped_at is not null))
        order by c.created_at desc) from (select * from public.newsletter_campaigns order by created_at desc limit 50)c),'[]'::jsonb);
end;
$$;
revoke all on function public.get_newsletter_campaigns() from public,anon,authenticated;
grant execute on function public.get_newsletter_campaigns() to authenticated;
commit;
