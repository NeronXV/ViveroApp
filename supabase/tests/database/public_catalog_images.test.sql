begin;

create extension if not exists pgtap with schema extensions;

select extensions.plan(18);

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
              'anon'::pg_catalog.name,
              'authenticated'::pg_catalog.name
          ]
    ),
    'catalog migration grants no object write policy to client roles'
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

select * from extensions.finish();
rollback;
