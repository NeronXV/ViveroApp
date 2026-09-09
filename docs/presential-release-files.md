# Archivos de la entrega presencial

Listas relativas a cada repositorio. No equivalen a autorización de commit: Android contiene cambios anteriores en algunos archivos compartidos.

## ViveroApp

- app/build.gradle.kts
- app/src/main/java/com/intutec/viveroapp/feature/auth/data/remote/SupabaseAuthRemoteDataSource.kt
- app/src/main/java/com/intutec/viveroapp/feature/cashier/data/remote/SupabaseCashierPaymentRemoteDataSource.kt
- app/src/main/java/com/intutec/viveroapp/feature/cashier/domain/model/CashierPayment.kt
- app/src/main/java/com/intutec/viveroapp/feature/reports/presentation/ReportsScreen.kt
- docs/presential-release.md
- supabase/config.toml
- supabase/functions/invite-staff/index.ts
- supabase/functions/newsletter/index.ts
- supabase/migrations/202609080001_presential_checkout.sql
- supabase/migrations/202609080002_cashier_closings_refunds.sql
- supabase/migrations/202609080003_newsletter.sql
- supabase/tests/database/cashier_closings_refunds.test.sql
- supabase/tests/database/gradual_inventory_trigger.test.sql
- supabase/tests/database/presential_release.test.sql
- supabase/tests/database/web_orders.test.sql
- supabase/tests/edge-functions.test.cjs
- supabase/tests/verify_migrations.ps1

## ViveroWeb

- docs/PRESENTIAL_RELEASE.md
- src/app/App.tsx
- src/app/router.tsx
- src/components/layout/SiteFooter.tsx
- src/features/access/access-rules.ts
- src/features/admin/AdminInventory.tsx
- src/features/admin/AdminOrders.tsx
- src/features/admin/AdminPage.tsx
- src/features/admin/InventoryActivation.tsx
- src/features/admin/StaffInvitation.tsx
- src/features/auth/LoginPage.tsx
- src/features/auth/PasswordRecoveryPage.tsx
- src/features/cashier/CashierOperations.tsx
- src/features/cashier/CashierPage.tsx
- src/features/cashier/cashier-operations-service.test.ts
- src/features/cashier/cashier-operations-service.ts
- src/features/cashier/cashier-service.ts
- src/features/newsletter/AdminNewsletter.tsx
- src/features/newsletter/NewsletterConfirmation.tsx
- src/features/newsletter/NewsletterSignup.tsx
- src/features/public-catalog/CareChat.tsx
- src/features/public-catalog/CareQuiz.tsx
- src/features/public-catalog/CartDrawer.tsx
- src/features/public-catalog/HomePage.tsx
- src/features/public-catalog/care-catalog.test.ts
- src/features/public-catalog/care-catalog.ts
- src/features/public-catalog/useCareCatalog.ts
- src/features/public-orders/web-order-parser.ts
- src/features/public-orders/web-order-service.ts
- src/features/public-orders/web-order-types.ts
