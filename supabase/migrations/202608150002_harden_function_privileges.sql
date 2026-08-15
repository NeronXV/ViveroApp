begin;

revoke all on function public.assign_user_branch(pg_catalog.uuid, pg_catalog.uuid)
from public, anon, authenticated;
revoke all on function public.assign_user_role(pg_catalog.uuid, pg_catalog.text)
from public, anon, authenticated;
revoke all on function public.bootstrap_first_owner(pg_catalog.uuid)
from public, anon, authenticated;
revoke all on function public.create_branch(pg_catalog.text, pg_catalog.text)
from public, anon, authenticated;
revoke all on function public.enforce_product_price_permission()
from public, anon, authenticated;
revoke all on function public.handle_new_user()
from public, anon, authenticated;
revoke all on function public.has_permission(pg_catalog.text)
from public, anon, authenticated;
revoke all on function public.set_branch_active(pg_catalog.uuid, pg_catalog.bool)
from public, anon, authenticated;
revoke all on function public.set_updated_at()
from public, anon, authenticated;
revoke all on function public.submit_sale_to_cashier(
    pg_catalog.uuid, pg_catalog.text, pg_catalog.jsonb, pg_catalog.uuid
)
from public, anon, authenticated;
revoke all on function public.update_branch(
    pg_catalog.uuid, pg_catalog.text, pg_catalog.text
)
from public, anon, authenticated;

grant execute on function public.has_permission(pg_catalog.text)
to authenticated;
grant execute on function public.submit_sale_to_cashier(
    pg_catalog.uuid, pg_catalog.text, pg_catalog.jsonb, pg_catalog.uuid
)
to authenticated;
grant execute on function public.assign_user_role(pg_catalog.uuid, pg_catalog.text)
to authenticated;
grant execute on function public.create_branch(pg_catalog.text, pg_catalog.text)
to authenticated;
grant execute on function public.update_branch(
    pg_catalog.uuid, pg_catalog.text, pg_catalog.text
)
to authenticated;
grant execute on function public.set_branch_active(pg_catalog.uuid, pg_catalog.bool)
to authenticated;
grant execute on function public.assign_user_branch(pg_catalog.uuid, pg_catalog.uuid)
to authenticated;

grant execute on function public.bootstrap_first_owner(pg_catalog.uuid)
to service_role;

commit;
