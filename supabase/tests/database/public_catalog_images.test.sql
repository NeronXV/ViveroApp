begin;

create extension if not exists pgtap with schema extensions;

select extensions.plan(21);

select extensions.is(
    (
        select pg_catalog.count(*)
        from storage.buckets
        where id = 'catalog-images'
          and name = 'catalog-images'
    ),
    1::bigint,
    'catalog-images exists with matching id and name'
);

select extensions.is(
    (select public from storage.buckets where id = 'catalog-images'),
    true,
    'catalog-images is public'
);

select extensions.is(
    (select file_size_limit from storage.buckets where id = 'catalog-images'),
    5242880::bigint,
    'catalog-images limits each object to 5 MiB'
);

select extensions.is(
    (select allowed_mime_types from storage.buckets where id = 'catalog-images'),
    array['image/jpeg', 'image/png', 'image/webp', 'image/avif']::pg_catalog.text[],
    'catalog-images allows exactly the four approved MIME types'
);

select extensions.ok(
    not exists (
        select 1
        from pg_catalog.pg_policies p
        where p.schemaname = 'storage'
          and p.tablename = 'objects'
          and p.cmd in ('ALL', 'INSERT', 'UPDATE', 'DELETE')
          and p.roles && array[
              'public'::pg_catalog.name,
              'anon'::pg_catalog.name
          ]
    ),
    'catalog storage grants no object write policy to PUBLIC or anon'
);

select extensions.ok(
    (
        select c.convalidated
           and pg_catalog.pg_get_constraintdef(c.oid) like '%storage_path%'
        from pg_catalog.pg_constraint c
        where c.conrelid = 'public.product_images'::pg_catalog.regclass
          and c.conname = 'product_images_storage_path_catalog_object_key_check'
    ),
    'product image object-key constraint exists and is validated'
);

insert into public.categories (id, name, description, is_active)
values ('74000000-0000-0000-0000-000000000001', 'Imágenes', 'Pruebas', true);

insert into public.products (
    id, internal_code, common_name, description, category_id, price_cents,
    unit, minimum_stock, watering_advice, light_type, recommended_climate, is_active
) values (
    '75000000-0000-0000-0000-000000000001', 'IMAGE-TEST', 'Producto con imagen',
    'Prueba', '74000000-0000-0000-0000-000000000001', 1000,
    'pieza', 0, '', '', '', true
);

select extensions.lives_ok(
    $$
        insert into public.product_images (
            id, product_id, storage_path, alt_text, is_primary
        ) values (
            '76000000-0000-0000-0000-000000000001',
            '75000000-0000-0000-0000-000000000001',
            'products/example/catalog-photo.webp',
            'Imagen sintética',
            true
        )
    $$,
    'a valid relative image object key is accepted'
);

select extensions.throws_ok(
    $$insert into public.product_images (product_id, storage_path) values ('75000000-0000-0000-0000-000000000001', 'https://example.invalid/image.jpg')$$,
    '23514',
    'new row for relation "product_images" violates check constraint "product_images_storage_path_catalog_object_key_check"',
    'an absolute URL is rejected'
);

select extensions.throws_ok(
    $$insert into public.product_images (product_id, storage_path) values ('75000000-0000-0000-0000-000000000001', '../image.jpg')$$,
    '23514',
    'new row for relation "product_images" violates check constraint "product_images_storage_path_catalog_object_key_check"',
    'parent traversal is rejected'
);

select extensions.throws_ok(
    $$insert into public.product_images (product_id, storage_path) values ('75000000-0000-0000-0000-000000000001', 'products/./image.jpg')$$,
    '23514',
    'new row for relation "product_images" violates check constraint "product_images_storage_path_catalog_object_key_check"',
    'a current-directory segment is rejected'
);

select extensions.throws_ok(
    $$insert into public.product_images (product_id, storage_path) values ('75000000-0000-0000-0000-000000000001', '/products/image.jpg')$$,
    '23514',
    'new row for relation "product_images" violates check constraint "product_images_storage_path_catalog_object_key_check"',
    'a leading slash is rejected'
);

select extensions.throws_ok(
    $$insert into public.product_images (product_id, storage_path) values ('75000000-0000-0000-0000-000000000001', E'products\\image.jpg')$$,
    '23514',
    'new row for relation "product_images" violates check constraint "product_images_storage_path_catalog_object_key_check"',
    'a backslash is rejected'
);

select extensions.throws_ok(
    $$insert into public.product_images (product_id, storage_path) values ('75000000-0000-0000-0000-000000000001', E'products/image\n.jpg')$$,
    '23514',
    'new row for relation "product_images" violates check constraint "product_images_storage_path_catalog_object_key_check"',
    'a control character is rejected'
);

select extensions.throws_ok(
    $$insert into public.product_images (product_id, storage_path) values ('75000000-0000-0000-0000-000000000001', 'products/image.svg')$$,
    '23514',
    'new row for relation "product_images" violates check constraint "product_images_storage_path_catalog_object_key_check"',
    'an unapproved extension is rejected'
);

select extensions.throws_ok(
    $$insert into public.product_images (product_id, storage_path) values ('75000000-0000-0000-0000-000000000001', ' products/image.jpg')$$,
    '23514',
    'new row for relation "product_images" violates check constraint "product_images_storage_path_catalog_object_key_check"',
    'leading whitespace is rejected'
);

select extensions.throws_ok(
    $$insert into public.product_images (product_id, storage_path) values ('75000000-0000-0000-0000-000000000001', 'products/image.jpg ')$$,
    '23514',
    'new row for relation "product_images" violates check constraint "product_images_storage_path_catalog_object_key_check"',
    'trailing whitespace is rejected'
);

select extensions.throws_like(
    $$insert into public.product_images (product_id, storage_path) values ('75000000-0000-0000-0000-000000000001', '')$$,
    '%violates check constraint%',
    'an empty object key is rejected'
);

select extensions.throws_ok(
    $$insert into public.product_images (product_id, storage_path) values ('75000000-0000-0000-0000-000000000001', 'catalog-images/products/image.jpg')$$,
    '23514',
    'new row for relation "product_images" violates check constraint "product_images_storage_path_catalog_object_key_check"',
    'a bucket-prefixed object key is rejected'
);

-- The later catalog administration contract permits uploads only to staff with
-- MANAGE_PRODUCTS, and only inside the catalog-images bucket.
insert into auth.users (id, email, raw_user_meta_data) values
    ('77000000-0000-4000-8000-000000000001', 'image-owner@example.test', '{"full_name":"Image Owner"}'),
    ('77000000-0000-4000-8000-000000000002', 'image-sales@example.test', '{"full_name":"Image Sales"}');
insert into public.user_roles(user_id, role_id)
select actor.id, r.id from (values
    ('77000000-0000-4000-8000-000000000001'::uuid, 'OWNER'),
    ('77000000-0000-4000-8000-000000000002'::uuid, 'SALES')
) actor(id, role_name) join public.roles r on r.name = actor.role_name;
insert into storage.buckets(id, name) values ('restricted-test', 'restricted-test');

set local role authenticated;
set local request.jwt.claim.sub = '77000000-0000-4000-8000-000000000001';
select extensions.lives_ok(
    $$insert into storage.objects(bucket_id, name) values ('catalog-images', 'products/owner-photo.webp')$$,
    'staff with MANAGE_PRODUCTS can upload a catalog image'
);
select extensions.throws_ok(
    $$insert into storage.objects(bucket_id, name) values ('restricted-test', 'products/owner-photo.webp')$$,
    '42501', 'new row violates row-level security policy for table "objects"',
    'catalog permission does not authorize another storage bucket'
);
set local request.jwt.claim.sub = '77000000-0000-4000-8000-000000000002';
select extensions.throws_ok(
    $$insert into storage.objects(bucket_id, name) values ('catalog-images', 'products/sales-photo.webp')$$,
    '42501', 'new row violates row-level security policy for table "objects"',
    'staff without MANAGE_PRODUCTS cannot upload catalog images'
);
reset role;

select * from extensions.finish();
rollback;
