-- =====================================================================
--  Delivery pricing enums (Flyway V3, platform schema)
--
--  These two types live in `public` alongside discount_type and the rest
--  (platform V1__baseline.sql, PART 1) because every tenant schema needs
--  them: a type created from a tenant migration would be created once per
--  tenant and blow up on the second one.
--
--  Platform Flyway runs at boot before TenantMigrationRunner, so the
--  tenant-side V3__delivery_pricing.sql can rely on them existing.
--
--    delivery_charge_kind — is the number a flat amount or a percent of
--                           the order's items subtotal?
--    delivery_region_type — how finely a method prices by region. Shared
--                           by delivery_methods.rate_scope ('flat' means
--                           one charge everywhere) and by the region rows
--                           in delivery_method_rates, which may only be
--                           'province' or 'district'.
-- =====================================================================

CREATE TYPE public.delivery_charge_kind AS ENUM ('fixed', 'percentage');

CREATE TYPE public.delivery_region_type AS ENUM ('flat', 'province', 'district');
