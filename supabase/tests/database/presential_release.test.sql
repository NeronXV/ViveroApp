begin;
create extension if not exists pgtap with schema extensions;
select extensions.plan(15);
select extensions.ok(not has_table_privilege('anon','public.sale_refunds','SELECT'), 'anonymous cannot read refunds');
select extensions.ok(not has_table_privilege('authenticated','public.cashier_closings','INSERT'), 'clients cannot forge closings');
select extensions.ok(not has_table_privilege('authenticated','public.newsletter_subscribers','SELECT'), 'staff cannot enumerate subscriber tokens');
select extensions.ok(not has_function_privilege('anon','public.send_web_order_to_cashier(uuid)','EXECUTE'), 'checkout requires authentication');
select extensions.ok(not has_function_privilege('authenticated','public.prepare_newsletter_subscription(text)','EXECUTE'), 'subscription preparation is service only');
select extensions.ok(has_function_privilege('service_role','public.prepare_newsletter_subscription(text)','EXECUTE'), 'mail service can prepare confirmation');
select extensions.ok(has_function_privilege('anon','public.confirm_newsletter_subscription(uuid)','EXECUTE'), 'confirmation is available to token holder');
set local role authenticated;
set local request.jwt.claims = '{}';
select extensions.throws_ok('select public.get_my_cashier_closing_preview()', '42501', 'CASHIER_UNAUTHORIZED', 'closing rejects missing identity');
select extensions.throws_ok($$select public.get_refundable_sale('VD-260908-TEST01')$$, '42501', 'REFUND_UNAUTHORIZED', 'refund lookup rejects missing identity');
select extensions.throws_ok('select public.get_newsletter_campaigns()', '42501', 'NEWSLETTER_UNAUTHORIZED', 'campaign list rejects missing identity');
reset role;
create temporary table subscription_fixture as
select public.prepare_newsletter_subscription(' Synthetic.Newsletter@example.test ') as payload;
select extensions.is((select payload->>'email' from subscription_fixture), 'synthetic.newsletter@example.test', 'email is normalized');
select extensions.is(public.prepare_newsletter_subscription('synthetic.newsletter@example.test'), null::jsonb, 'immediate repeated request is throttled');
select public.confirm_newsletter_subscription((select (payload->>'token')::uuid from subscription_fixture));
select extensions.ok((select confirmed_at is not null from public.newsletter_subscribers where email='synthetic.newsletter@example.test'), 'confirmation activates subscription');
select extensions.throws_ok(
 $$select public.confirm_newsletter_subscription((select (payload->>'token')::uuid from subscription_fixture))$$,
 'P0001','NEWSLETTER_TOKEN_INVALID','confirmation token is single use');
select public.unsubscribe_newsletter((select unsubscribe_token from public.newsletter_subscribers where email='synthetic.newsletter@example.test'));
select extensions.ok((select unsubscribed_at is not null from public.newsletter_subscribers where email='synthetic.newsletter@example.test'), 'unsubscribe stops future campaigns');
select extensions.finish();
rollback;
