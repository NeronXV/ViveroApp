begin;

-- Supabase default privileges may grant service_role execution when functions
-- are created. These two presentation RPCs are client-only entry points.
revoke all on function public.get_my_access_context() from public, anon, service_role;
revoke all on function public.get_my_branch_catalog_inventory() from public, anon, service_role;

grant execute on function public.get_my_access_context() to authenticated;
grant execute on function public.get_my_branch_catalog_inventory() to authenticated;

commit;
