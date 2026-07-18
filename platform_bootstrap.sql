-- =====================================================================
--  GALAXY — platform bootstrap. Run ONCE per database, before anything else.
--
--  Creates the shared objects that every tenant schema resolves through
--  `public` on its search_path. Nothing tenant-specific belongs here.
--
--  Install order:
--     1. platform_bootstrap.sql   <- this file
--     2. knox_platform.sql        (control plane: tenants, tenant_users, subscriptions)
--     3. src/main/resources/db/tenant_template.sql
--        — not run by hand; TenantProvisioningService stamps it per tenant.
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS knox;

-- =====================================================================
-- 1. SHARED ENUMERATED TYPES
--     In `public` rather than a tenant schema: `public` trails every tenant's
--     search_path, so one definition serves all of them. Per-schema copies
--     would mean N copies to keep in step, and columnDefinition in the JPA
--     entities would resolve to whichever copy the search_path found first.
-- =====================================================================

CREATE TYPE public.user_role AS ENUM (
    'owner', 'admin', 'manager', 'sales', 'stock_keeper', 'delivery', 'accountant'
);

CREATE TYPE public.order_status AS ENUM (
    'processing', 'ready_to_ship', 'delivering', 'delivered',
    'cancelled', 'returned', 'refunded'
);

CREATE TYPE public.stock_movement_type AS ENUM (
    'initial_stock', 'refill', 'transfer'
);

CREATE TYPE public.discount_type AS ENUM ('percentage', 'fixed');

CREATE TYPE public.commission_method AS ENUM ('product_percentage', 'per_product_fixed');

CREATE TYPE public.finance_kind   AS ENUM ('revenue', 'expense');
CREATE TYPE public.finance_source AS ENUM ('auto', 'manual');

CREATE TYPE public.notification_type AS ENUM (
    'low_stock', 'transfer_complete', 'order_delivered',
    'new_order', 'order_status_update', 'user_added', 'product_added'
);

CREATE TYPE public.feedback_type AS ENUM ('complaint', 'recommendation', 'question');

CREATE TYPE public.access_level AS ENUM ('full', 'view', 'no_price', 'none');

CREATE TYPE public.billing_plan AS ENUM ('basic', 'nova', 'stellar');


-- =====================================================================
-- 2. SHARED TRIGGER FUNCTION
--     Per-tenant triggers all bind to this single definition.
-- =====================================================================

CREATE OR REPLACE FUNCTION public.touch_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
