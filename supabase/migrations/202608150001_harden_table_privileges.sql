begin;

revoke all privileges on table
    public.branches,
    public.categories,
    public.permissions,
    public.product_images,
    public.products,
    public.profiles,
    public.role_permissions,
    public.roles,
    public.sale_items,
    public.sale_status_history,
    public.sales,
    public.user_roles
from anon, authenticated, public;

grant select on table
    public.branches,
    public.categories,
    public.permissions,
    public.product_images,
    public.products,
    public.profiles,
    public.role_permissions,
    public.roles,
    public.sale_items,
    public.sale_status_history,
    public.sales,
    public.user_roles
to authenticated;

grant update (full_name, avatar_path)
on public.profiles
to authenticated;

grant insert, update, delete on table
    public.categories,
    public.product_images,
    public.products
to authenticated;

commit;
