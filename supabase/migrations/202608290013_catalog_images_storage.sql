begin;

-- Catalog images storage write policies: authenticated with MANAGE_PRODUCTS can insert/update/delete
-- Public read remains via bucket public=true (no separate select grant needed for anon, but we add explicit read for consistency)

do $$
begin
    if not exists (select 1 from pg_policies where schemaname='storage' and tablename='objects' and policyname='catalog-images public read') then
        create policy "catalog-images public read"
            on storage.objects for select
            to public, authenticated, anon
            using (bucket_id = 'catalog-images');
    end if;

    if not exists (select 1 from pg_policies where schemaname='storage' and tablename='objects' and policyname='catalog-images insert') then
        create policy "catalog-images insert"
            on storage.objects for insert
            to authenticated
            with check (bucket_id = 'catalog-images' and public.has_permission('MANAGE_PRODUCTS'));
    end if;

    if not exists (select 1 from pg_policies where schemaname='storage' and tablename='objects' and policyname='catalog-images update') then
        create policy "catalog-images update"
            on storage.objects for update
            to authenticated
            using (bucket_id = 'catalog-images' and public.has_permission('MANAGE_PRODUCTS'))
            with check (bucket_id = 'catalog-images' and public.has_permission('MANAGE_PRODUCTS'));
    end if;

    if not exists (select 1 from pg_policies where schemaname='storage' and tablename='objects' and policyname='catalog-images delete') then
        create policy "catalog-images delete"
            on storage.objects for delete
            to authenticated
            using (bucket_id = 'catalog-images' and public.has_permission('MANAGE_PRODUCTS'));
    end if;
end;
$$;

-- Ensure authenticated can use storage (no extra grants needed beyond policies)
-- Keep product_images RLS as is: authenticated with MANAGE_PRODUCTS can insert/update/delete via has_permission

commit;
