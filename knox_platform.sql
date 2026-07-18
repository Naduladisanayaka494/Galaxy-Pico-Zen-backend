-- =====================================================================
--  KNOX PLATFORM — control plane for schema-per-tenant Galaxy
--
--  `knox` is the platform schema. It answers three questions that must be
--  answerable BEFORE any tenant schema is selected:
--     1. who is this person?        -> knox.tenant_users
--     2. which tenant/schema?       -> knox.tenants
--     3. are they paid up?          -> knox.subscriptions
--
--  Each tenant's business data lives in its own `tenant_<slug>` schema,
--  stamped from the galaxy template. Shared enum types live in `public`.
--
--  It also holds the KNOX Client Manager (§16): KNOX's own agency clients and
--  their billing. Those tables come first because knox.tenants references
--  knox.clients. KNOX staff identity (knox.platform_users) is defined at the
--  end — a standalone table with no FK to or from anything else here.
--
--  Install order:
--     1. platform_bootstrap.sql   (schemas, shared enums, touch_updated_at())
--     2. knox_platform.sql        <- this file
--     3. src/main/resources/db/tenant_template.sql, run into `galaxy` (the
--        dev reference copy). Provisioning stamps the same file per tenant —
--        it does not clone galaxy at runtime.
-- =====================================================================

SET search_path TO knox, public;

-- ---------------------------------------------------------------------
-- Types
-- ---------------------------------------------------------------------
CREATE TYPE knox.tenant_status AS ENUM (
    'provisioning',   -- schema being created; no logins yet
    'active',
    'suspended',      -- non-payment; login rejected, data retained
    'archived'        -- schema retained but cold
);

CREATE TYPE knox.subscription_status AS ENUM (
    'trialing', 'active', 'past_due', 'cancelled'
);

CREATE TYPE knox.tenant_user_status AS ENUM ('active', 'disabled');


-- =====================================================================
-- KNOX Client Manager (§16) — KNOX's own agency clients & their billing.
-- Defined before the tenant tables because knox.tenants.client_id references
-- knox.clients. Distinct from the tenant billing above: a client is someone
-- KNOX invoices; a tenant is a provisioned Galaxy schema.
-- =====================================================================

CREATE TYPE knox.knox_plan AS ENUM (
    'monthly_2k', 'monthly_5k', 'yearly_2k', 'yearly_5k', 'unlimited'
);
CREATE TYPE knox.knox_status       AS ENUM ('active', 'trial', 'blocked');
CREATE TYPE knox.knox_setup_option AS ENUM ('full', 'installment_4');

CREATE TABLE knox.plans (
    plan             knox_plan     PRIMARY KEY,
    subscription_fee NUMERIC(14,2),          -- per period; NULL for unlimited (per-order)
    per_order_fee    NUMERIC(14,2),          -- unlimited plan only (Rs. 7/order)
    setup_fee        NUMERIC(14,2) NOT NULL,
    is_yearly        BOOLEAN       NOT NULL DEFAULT FALSE
);

-- KNOX agency pricing (§16.5). Reference data, so seeded here with the schema.
INSERT INTO knox.plans (plan, subscription_fee, per_order_fee, setup_fee, is_yearly) VALUES
    ('monthly_2k',  2000.00,  NULL, 20000.00, FALSE),
    ('monthly_5k',  5000.00,  NULL, 20000.00, FALSE),
    ('yearly_2k',  20000.00,  NULL, 40000.00, TRUE),
    ('yearly_5k',  50000.00,  NULL, 70000.00, TRUE),
    ('unlimited',   NULL,     7.00, 20000.00, FALSE)
ON CONFLICT (plan) DO NOTHING;

CREATE TABLE knox.clients (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_name  TEXT        NOT NULL,
    contact_person TEXT,
    phone          TEXT,
    email          TEXT,
    plan           knox_plan   NOT NULL REFERENCES knox.plans(plan),
    status         knox_status NOT NULL DEFAULT 'trial',
    start_date     DATE        NOT NULL,        -- drives 7-day trial + renewal
    setup_option   knox_setup_option NOT NULL DEFAULT 'full',
    on_trial       BOOLEAN     NOT NULL DEFAULT TRUE,
    notes          TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Setup fee: one row (full pay) or four installment rows (§16.4).
CREATE TABLE knox.setup_fee_installments (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id     BIGINT   NOT NULL REFERENCES knox.clients(id) ON DELETE CASCADE,
    installment_no SMALLINT NOT NULL CHECK (installment_no BETWEEN 1 AND 4),
    amount        NUMERIC(14,2) NOT NULL,
    is_paid       BOOLEAN  NOT NULL DEFAULT FALSE,
    paid_at       TIMESTAMPTZ,
    UNIQUE (client_id, installment_no)
);

-- Subscription billing periods; amount editable for unlimited/pay-per-order (§16.4).
CREATE TABLE knox.subscription_periods (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id   BIGINT   NOT NULL REFERENCES knox.clients(id) ON DELETE CASCADE,
    period_start DATE    NOT NULL,               -- month or year start
    amount      NUMERIC(14,2),                   -- NULL = "Not entered" (unlimited plan)
    is_paid     BOOLEAN  NOT NULL DEFAULT FALSE,
    paid_at     TIMESTAMPTZ,
    UNIQUE (client_id, period_start)
);

-- Internal announcement images + text notifications (§16.6)
CREATE TABLE knox.announcements (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    image_url TEXT     NOT NULL,
    position  SMALLINT NOT NULL CHECK (position BETWEEN 1 AND 5),
    UNIQUE (position)
);

CREATE TABLE knox.notifications (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title      TEXT        NOT NULL,
    message    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- ---------------------------------------------------------------------
-- tenants — the routing table. One row per Galaxy install.
-- ---------------------------------------------------------------------
CREATE TABLE knox.tenants (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- slug drives schema_name and is the only user-facing tenant handle.
    -- The CHECK is a security boundary, not validation sugar: schema_name
    -- is interpolated into `SET search_path`, which cannot be parameterised.
    slug          TEXT NOT NULL UNIQUE
                  CONSTRAINT tenants_slug_safe CHECK (slug ~ '^[a-z][a-z0-9_]{2,30}$'),
    schema_name   TEXT NOT NULL UNIQUE
                  CONSTRAINT tenants_schema_safe CHECK (schema_name ~ '^[a-z][a-z0-9_]{2,40}$'),

    -- The regex above stops injection but happily accepts 'public' or 'knox'.
    -- A tenant whose schema_name pointed at the control plane would aim every
    -- tenant-scoped query at knox itself, so reserved names are blocked outright.
    CONSTRAINT tenants_schema_not_reserved CHECK (
        schema_name NOT IN ('public', 'knox', 'information_schema',
                            'pg_catalog', 'pg_toast', 'pg_temp')
        AND schema_name NOT LIKE 'pg\_%'
    ),

    business_name TEXT NOT NULL,
    status        knox.tenant_status NOT NULL DEFAULT 'provisioning',

    -- KNOX's CRM record. Nullable: not every agency client is a Galaxy tenant,
    -- and a tenant may be provisioned before the client row is filled in.
    client_id     BIGINT REFERENCES knox.clients(id) ON DELETE SET NULL,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tenants_status ON knox.tenants (status);
CREATE INDEX idx_tenants_client ON knox.tenants (client_id);


-- ---------------------------------------------------------------------
-- tenant_users — platform identity directory.
--
-- Credentials live HERE, not in tenant_<slug>.users, because login must
-- resolve the tenant before any schema is selected. The tenant-side users
-- row keeps profile/role/commission and all 7 of its inbound FKs.
--
-- email is globally unique: one email = one tenant. If a person ever needs
-- membership in two tenants, drop this constraint and add a tenant-picker
-- step to login (the JWT can no longer be minted from email+password alone).
-- ---------------------------------------------------------------------
CREATE TABLE knox.tenant_users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     BIGINT NOT NULL REFERENCES knox.tenants(id) ON DELETE CASCADE,

    email         TEXT NOT NULL,
    password_hash TEXT NOT NULL,

    -- id of the matching row in tenant_<slug>.users. Not an FK: PostgreSQL
    -- cannot reference a table whose schema is chosen at runtime.
    local_user_id BIGINT,

    status        knox.tenant_user_status NOT NULL DEFAULT 'active',
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (tenant_id, local_user_id)
);

CREATE UNIQUE INDEX uq_tenant_users_email_lower ON knox.tenant_users (lower(email));
CREATE INDEX idx_tenant_users_tenant ON knox.tenant_users (tenant_id);

-- ---------------------------------------------------------------------
-- refresh_tokens — DB-backed, revocable session state for tenant logins.
-- The JWT access token stays stateless (never touches this table); this is
-- only consulted on /api/auth/refresh, which is rare compared to normal
-- request traffic.
--
-- token_hash, never the raw token: same principle as password_hash — a DB
-- leak must not hand out live, usable tokens.
--
-- family_id groups one login's whole rotation chain. Every refresh revokes
-- the token just used and issues a new one in the same family. If a
-- REVOKED token is ever presented again, that's a replay of a stolen
-- token — the entire family gets killed (see RefreshTokenService), not
-- just the one token, forcing a full re-login on that session.
-- ---------------------------------------------------------------------
CREATE TABLE knox.refresh_tokens (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_user_id BIGINT NOT NULL REFERENCES knox.tenant_users(id) ON DELETE CASCADE,
    token_hash     TEXT NOT NULL,
    family_id      UUID NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    revoked_at     TIMESTAMPTZ,
    replaced_by    BIGINT REFERENCES knox.refresh_tokens(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_refresh_tokens_hash ON knox.refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_family ON knox.refresh_tokens (family_id);
-- Cleanup job scans by expiry; only live rows need to be fast to skip.
CREATE INDEX idx_refresh_tokens_active ON knox.refresh_tokens (tenant_user_id) WHERE revoked_at IS NULL;


-- ---------------------------------------------------------------------
-- galaxy_plans — Galaxy the product's own tiers (basic/nova/stellar) and
-- their limits. Distinct from knox.plans, which is KNOX's agency pricing for
-- billing its clients (knox_plan enum) — a different business entirely.
--
-- This used to be a per-tenant table (plans/subscription/invoices, one copy
-- per tenant_<slug>). It described "what this business pays Galaxy", which
-- under schema-per-tenant is platform data, not tenant data: a tenant has no
-- business reading or writing its own billing truth. It now lives here, once,
-- and knox.subscriptions.plan references it directly.
-- ---------------------------------------------------------------------
CREATE TABLE knox.galaxy_plans (
    plan              public.billing_plan  PRIMARY KEY,
    monthly_price     NUMERIC(14,2),         -- NULL for Stellar (custom)
    max_warehouses    INTEGER,
    max_products      INTEGER,
    max_orders_month  INTEGER,
    max_users         INTEGER
);

INSERT INTO knox.galaxy_plans (plan, monthly_price, max_warehouses, max_products, max_orders_month, max_users) VALUES
    ('basic',   2000.00, 1,    500,  100, 2),
    ('nova',    5000.00, 3,    NULL, 500, 4),
    ('stellar', NULL,    NULL, NULL, NULL, NULL)
ON CONFLICT (plan) DO NOTHING;


-- ---------------------------------------------------------------------
-- subscriptions — single source of truth for billing state.
-- knox.subscription_periods remains the paid-history ledger.
-- ---------------------------------------------------------------------
CREATE TABLE knox.subscriptions (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id            BIGINT NOT NULL REFERENCES knox.tenants(id) ON DELETE CASCADE,

    plan                 public.billing_plan NOT NULL REFERENCES knox.galaxy_plans(plan),
    status               knox.subscription_status NOT NULL DEFAULT 'trialing',

    started_at           DATE NOT NULL DEFAULT CURRENT_DATE,
    trial_ends_at        DATE,
    current_period_start DATE,
    current_period_end   DATE,
    cancelled_at         TIMESTAMPTZ,

    outstanding          NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (outstanding >= 0),

    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT subscription_period_order
        CHECK (current_period_end IS NULL
               OR current_period_start IS NULL
               OR current_period_end >= current_period_start),
    CONSTRAINT subscription_cancelled_shape
        CHECK ((status = 'cancelled') = (cancelled_at IS NOT NULL))
);

-- At most one live subscription per tenant; cancelled ones accumulate as history.
CREATE UNIQUE INDEX uq_subscriptions_live
    ON knox.subscriptions (tenant_id)
    WHERE status <> 'cancelled';

CREATE INDEX idx_subscriptions_status ON knox.subscriptions (status);


-- ---------------------------------------------------------------------
-- updated_at triggers (function lives in public, see platform_bootstrap.sql)
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_tenants_touch       BEFORE UPDATE ON knox.tenants
    FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at();
CREATE TRIGGER trg_tenant_users_touch  BEFORE UPDATE ON knox.tenant_users
    FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at();
CREATE TRIGGER trg_subscriptions_touch BEFORE UPDATE ON knox.subscriptions
    FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at();


-- =====================================================================
-- KNOX staff identity — the platform's own admins.
--
-- Distinct from knox.tenant_users on purpose: a tenant_user is scoped to
-- exactly one tenant, and every token minted for one carries a tenant_id.
-- KNOX staff operate ACROSS tenants (provisioning, client billing), so they
-- cannot be represented by a tenant-scoped identity without giving some
-- tenant's token cross-tenant reach.
--
-- Tokens issued for these users carry NO tenant_id and never bind a tenant
-- schema; they only reach the knox control plane.
-- =====================================================================

CREATE TABLE knox.platform_users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,
    full_name     TEXT        NOT NULL,
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Case-insensitive, matching the tenant_users convention.
CREATE UNIQUE INDEX uq_platform_users_email_lower ON knox.platform_users (lower(email));

CREATE TRIGGER trg_platform_users_touch BEFORE UPDATE ON knox.platform_users
    FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at();

-- ---------------------------------------------------------------------
-- Platform settings — single row (id=1), mirrors the tenant-side
-- business_settings pattern. Currently just the assumed profit margin
-- the Client Manager dashboard uses to turn revenue into "Estimated
-- Profit"; there's no real cost tracking to compute an actual margin from.
-- ---------------------------------------------------------------------
CREATE TABLE knox.platform_settings (
    id                     SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    profit_margin_percent  NUMERIC(5,2) NOT NULL DEFAULT 65.00
                           CHECK (profit_margin_percent >= 0 AND profit_margin_percent <= 100),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO knox.platform_settings (id) VALUES (1) ON CONFLICT (id) DO NOTHING;

CREATE TRIGGER trg_platform_settings_touch BEFORE UPDATE ON knox.platform_settings
    FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at();

SET search_path TO galaxy, public;
