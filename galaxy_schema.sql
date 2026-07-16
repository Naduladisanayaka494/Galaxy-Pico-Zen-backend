-- =====================================================================
--  GALAXY — Stock & Order Management System
--  PostgreSQL schema (server-connected version)
--  Derived from: Galaxy_User_Guide.pdf  (v1.1, June 2026 — KNOX Digital)
--
--  Target: PostgreSQL 13+
--  Money : NUMERIC(14,2) in the business currency (default LKR)
--  Notes : Single-business deployment (one install = one client).
--          To make it multi-tenant, add a business_id FK to every table
--          and scope unique constraints by business_id.
--
--  Sections
--    0. Schemas & extensions
--    1. Enumerated types
--    2. Settings & reference data  (business, cities, delivery/payment/discount)
--    3. Staff (users, roles, permissions, commission, leave)
--    4. Inventory (warehouses, products, images, stock, movements)
--    5. Customers & Orders (orders, items, status history)
--    6. Finance
--    7. Notifications / announcements / feedback
--    8. Billing (subscription & invoices)
--    9. Views & helper functions
--   10. KNOX Client Manager  (separate internal tool — schema `knox`)
-- =====================================================================


-- =====================================================================
-- 0. SCHEMAS & EXTENSIONS
-- =====================================================================
CREATE SCHEMA IF NOT EXISTS galaxy;
CREATE SCHEMA IF NOT EXISTS knox;

SET search_path TO galaxy, public;

-- citext gives case-insensitive usernames/emails; comment out if unavailable.
CREATE EXTENSION IF NOT EXISTS citext;


-- =====================================================================
-- 1. ENUMERATED TYPES
-- =====================================================================

-- The 7 staff roles (§11.2)
CREATE TYPE user_role AS ENUM (
    'owner', 'admin', 'manager', 'sales', 'stock_keeper', 'delivery', 'accountant'
);

-- The 7 order statuses (§8.5)
CREATE TYPE order_status AS ENUM (
    'processing', 'ready_to_ship', 'delivering', 'delivered',
    'cancelled', 'returned', 'refunded'
);

-- Stock History event types (§5.5)
CREATE TYPE stock_movement_type AS ENUM (
    'initial_stock', 'refill', 'transfer'
);

-- Discount code kinds (§12.1)
CREATE TYPE discount_type AS ENUM ('percentage', 'fixed');

-- Commission calculation methods (§11.2)
CREATE TYPE commission_method AS ENUM ('product_percentage', 'per_product_fixed');

-- Finance line kinds & source (§10.5)
CREATE TYPE finance_kind   AS ENUM ('revenue', 'expense');
CREATE TYPE finance_source AS ENUM ('auto', 'manual');

-- Notification / alert types (§14.6 + §12.5)
CREATE TYPE notification_type AS ENUM (
    'low_stock', 'transfer_complete', 'order_delivered',
    'new_order', 'order_status_update', 'user_added', 'product_added'
);

-- Feedback types (§14.3)
CREATE TYPE feedback_type AS ENUM ('complaint', 'recommendation', 'question');

-- Access level used by the role-permission matrix (§11.4)
CREATE TYPE access_level AS ENUM ('full', 'view', 'no_price', 'none');

-- Galaxy subscription plans (§13.1)
CREATE TYPE billing_plan AS ENUM ('basic', 'nova', 'stellar');


-- =====================================================================
-- 2. SETTINGS & REFERENCE DATA
-- =====================================================================

-- Single-row business configuration (§12). id is pinned to 1.
CREATE TABLE business_settings (
    id                    SMALLINT     PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    business_name         TEXT         NOT NULL,
    category              TEXT,                 -- Retail, Wholesale, ... (§12.1, 10 options)
    address               TEXT,
    phone                 TEXT,
    logo_url              TEXT,
    -- Time & currency (§12.2 / §12.3)
    time_zone             TEXT         NOT NULL DEFAULT 'Asia/Colombo',
    time_format_24h       BOOLEAN      NOT NULL DEFAULT TRUE,
    currency_code         TEXT         NOT NULL DEFAULT 'LKR',
    -- Theme (§12.4)
    theme_dark            BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Notifications (§12.5)
    low_stock_threshold   INTEGER      NOT NULL DEFAULT 5 CHECK (low_stock_threshold >= 0),
    email_alerts          BOOLEAN      NOT NULL DEFAULT FALSE,
    alert_new_order       BOOLEAN      NOT NULL DEFAULT TRUE,
    alert_status_update   BOOLEAN      NOT NULL DEFAULT TRUE,
    alert_user_added      BOOLEAN      NOT NULL DEFAULT TRUE,
    alert_product_added   BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Sri Lankan cities dropdown (§9.4) — Province → District → Town.
CREATE TABLE cities (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    province  TEXT NOT NULL,
    district  TEXT NOT NULL,
    name      TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (district, name)
);

-- Delivery methods configured in settings (§12.1 / §9.2)
CREATE TABLE delivery_methods (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name      TEXT           NOT NULL,
    charge    NUMERIC(14,2)  NOT NULL DEFAULT 0 CHECK (charge >= 0),
    is_active BOOLEAN        NOT NULL DEFAULT TRUE,
    UNIQUE (name)
);

-- Payment methods (tags) (§12.1)
CREATE TABLE payment_methods (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name      TEXT    NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Discount codes (§12.1 / §9.2)
CREATE TABLE discount_codes (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       TEXT           NOT NULL UNIQUE,
    kind       discount_type  NOT NULL,
    value      NUMERIC(14,2)  NOT NULL CHECK (value >= 0),  -- percent (0-100) or fixed LKR
    is_active  BOOLEAN        NOT NULL DEFAULT TRUE,
    CHECK (kind <> 'percentage' OR value <= 100)
);


-- =====================================================================
-- 3. STAFF — users, permissions, commission, leave
-- =====================================================================

CREATE TABLE users (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name     TEXT        NOT NULL,
    last_name      TEXT        NOT NULL,
    email          CITEXT      UNIQUE,
    username       CITEXT      NOT NULL UNIQUE,
    password_hash  TEXT        NOT NULL,           -- store a hash, never plaintext
    role           user_role   NOT NULL,
    phone          TEXT,
    avatar_url     TEXT,
    is_active      BOOLEAN     NOT NULL DEFAULT TRUE,   -- Status On/Off toggle (§11.1)
    last_login_at  TIMESTAMPTZ,
    -- Commission configuration (§11.2). Live totals are computed, not stored.
    commission_enabled     BOOLEAN           NOT NULL DEFAULT FALSE,
    commission_method      commission_method,
    commission_percent     NUMERIC(6,3) CHECK (commission_percent >= 0),   -- e.g. 5.000 = 5%
    commission_unit_amount NUMERIC(14,2) CHECK (commission_unit_amount >= 0), -- LKR per unit
    commission_min_units   INTEGER,               -- min qty cap before commission applies
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT commission_shape CHECK (
        NOT commission_enabled
        OR (commission_method = 'product_percentage' AND commission_percent IS NOT NULL)
        OR (commission_method = 'per_product_fixed'  AND commission_unit_amount IS NOT NULL)
    )
);

-- Static role → feature access matrix (§11.4). Reference/lookup data.
CREATE TABLE role_permissions (
    role     user_role    NOT NULL,
    feature  TEXT         NOT NULL,   -- e.g. 'item_stock_view', 'billing'
    access   access_level NOT NULL,
    PRIMARY KEY (role, feature)
);

-- Leave requests (§14.4 — Coming Soon; modelled for completeness)
CREATE TABLE leave_requests (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    from_date   DATE        NOT NULL,
    to_date     DATE        NOT NULL,
    reason      TEXT        NOT NULL,
    status      TEXT        NOT NULL DEFAULT 'submitted',  -- submitted/approved/rejected
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (to_date >= from_date)
);


-- =====================================================================
-- 4. INVENTORY — warehouses, products, images, stock, movements
-- =====================================================================

CREATE TABLE warehouses (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       TEXT     NOT NULL,
    code       TEXT     NOT NULL UNIQUE,          -- short id, e.g. "NOR" (§7.3)
    location   TEXT,                              -- e.g. "Colombo 03"
    type       TEXT,                              -- Distribution / Retail / Storage
    manager    TEXT,                              -- optional person responsible (§7.6)
    capacity   INTEGER  CHECK (capacity >= 0),    -- used for fill % (§7.3)
    is_active  BOOLEAN  NOT NULL DEFAULT TRUE,    -- On/Off toggle (§7.3)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Optional product categories (shown in warehouse detail table, §7.5)
CREATE TABLE categories (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE products (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_code       TEXT          NOT NULL UNIQUE,      -- SKU, auto-gen or manual (§6.2)
    name               TEXT          NOT NULL,
    description        TEXT,
    category_id        BIGINT        REFERENCES categories(id) ON DELETE SET NULL,
    -- Self-reference for price-batch / variant siblings sharing a parent code (§9.1 note)
    parent_product_id  BIGINT        REFERENCES products(id) ON DELETE SET NULL,
    purchase_price     NUMERIC(14,2) NOT NULL CHECK (purchase_price > 0),  -- §6 required
    selling_price      NUMERIC(14,2) NOT NULL CHECK (selling_price  > 0),
    -- Product-specific low-stock threshold; falls back to business default if NULL (§6)
    low_stock_threshold INTEGER      CHECK (low_stock_threshold >= 0),
    is_active          BOOLEAN       NOT NULL DEFAULT TRUE,  -- On/Off, hides from Place Order (§6.4)
    added_date         DATE          NOT NULL DEFAULT CURRENT_DATE,   -- Add Date (§5)
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_products_name    ON products (lower(name));
CREATE INDEX idx_products_parent  ON products (parent_product_id);
CREATE INDEX idx_products_active  ON products (is_active);

-- Up to 5 images per product; exactly one default (§6.1)
CREATE TABLE product_images (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id BIGINT   NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    url        TEXT     NOT NULL,
    position   SMALLINT NOT NULL CHECK (position BETWEEN 1 AND 5),
    is_default BOOLEAN  NOT NULL DEFAULT FALSE,
    UNIQUE (product_id, position)
);
-- Enforce at most one default image per product.
CREATE UNIQUE INDEX uq_product_default_image
    ON product_images (product_id) WHERE is_default;

-- Stock held per product per warehouse (§5 warehouse breakdown).
-- available = on_hand - reserved (On Hand vs Available, §7.5).
CREATE TABLE inventory (
    product_id   BIGINT  NOT NULL REFERENCES products(id)   ON DELETE CASCADE,
    warehouse_id BIGINT  NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
    on_hand      INTEGER NOT NULL DEFAULT 0 CHECK (on_hand  >= 0),
    reserved     INTEGER NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    PRIMARY KEY (product_id, warehouse_id),
    CHECK (reserved <= on_hand)
);
CREATE INDEX idx_inventory_warehouse ON inventory (warehouse_id);

-- Immutable audit log of every stock change (§5.5 / §10.5 Stock movement history).
-- INITIAL_STOCK & REFILL: warehouse_to set. TRANSFER: both from/to set.
CREATE TABLE stock_movements (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id         BIGINT              NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    type               stock_movement_type NOT NULL,
    quantity           INTEGER             NOT NULL CHECK (quantity > 0),
    warehouse_from_id  BIGINT REFERENCES warehouses(id) ON DELETE SET NULL,
    warehouse_to_id    BIGINT REFERENCES warehouses(id) ON DELETE SET NULL,
    purchase_price     NUMERIC(14,2),      -- price snapshot at time of event
    selling_price      NUMERIC(14,2),
    created_by         BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ         NOT NULL DEFAULT now(),
    CHECK (
        (type = 'transfer' AND warehouse_from_id IS NOT NULL AND warehouse_to_id IS NOT NULL)
        OR (type IN ('initial_stock','refill') AND warehouse_to_id IS NOT NULL)
    )
);
CREATE INDEX idx_stock_moves_product ON stock_movements (product_id, created_at DESC);
CREATE INDEX idx_stock_moves_time    ON stock_movements (created_at DESC);


-- =====================================================================
-- 5. CUSTOMERS & ORDERS
-- =====================================================================

-- Customers are identified by phone number (§10.3).
CREATE TABLE customers (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       TEXT   NOT NULL,
    phone      TEXT   NOT NULL UNIQUE,
    email      CITEXT,
    city_id    BIGINT REFERENCES cities(id) ON DELETE SET NULL,
    address    TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE orders (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_code          TEXT         NOT NULL UNIQUE,        -- e.g. "ORD-001" (§8.6)
    customer_id         BIGINT       NOT NULL REFERENCES customers(id),
    placed_by           BIGINT       REFERENCES users(id) ON DELETE SET NULL,  -- Added by (§8.6)
    status              order_status NOT NULL DEFAULT 'processing',
    ordered_at          TIMESTAMPTZ  NOT NULL DEFAULT now(), -- Date & Time (§8.6)
    -- Chosen options (snapshotted so later settings edits don't rewrite history)
    delivery_method_id  BIGINT       REFERENCES delivery_methods(id) ON DELETE SET NULL,
    payment_method_id   BIGINT       REFERENCES payment_methods(id)  ON DELETE SET NULL,
    discount_code_id    BIGINT       REFERENCES discount_codes(id)   ON DELETE SET NULL,
    delivery_charge     NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (delivery_charge >= 0),
    discount_amount     NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    -- Reason recorded when cancelled/returned/refunded (§8.4)
    status_reason       TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_orders_status   ON orders (status);
CREATE INDEX idx_orders_customer ON orders (customer_id);
CREATE INDEX idx_orders_date     ON orders (ordered_at DESC);
CREATE INDEX idx_orders_placedby ON orders (placed_by);

-- Order line items. Prices are snapshotted at order time (custom unit price
-- editable per order, §9.1; purchase price kept for profit calc).
CREATE TABLE order_items (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id       BIGINT        NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id     BIGINT        NOT NULL REFERENCES products(id),
    quantity       INTEGER       NOT NULL CHECK (quantity > 0),
    unit_price     NUMERIC(14,2) NOT NULL CHECK (unit_price >= 0),      -- selling, possibly edited
    purchase_price NUMERIC(14,2) NOT NULL CHECK (purchase_price >= 0),  -- cost snapshot
    -- Generated line total keeps reads simple and consistent.
    line_total     NUMERIC(14,2) GENERATED ALWAYS AS (unit_price * quantity) STORED
);
CREATE INDEX idx_order_items_order   ON order_items (order_id);
CREATE INDEX idx_order_items_product ON order_items (product_id);

-- Full audit trail of status changes (§8.5 Change status + reason).
CREATE TABLE order_status_history (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id    BIGINT       NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    from_status order_status,
    to_status   order_status NOT NULL,
    reason      TEXT,
    changed_by  BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    changed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_status_hist ON order_status_history (order_id, changed_at);


-- =====================================================================
-- 6. FINANCE  (§10.5)
-- =====================================================================

-- Auto rows (product revenue/cost, commission) are derived from orders; they
-- may be materialised here per month or computed via views. Manual rows are
-- user-entered expenses/revenue.
CREATE TABLE finance_entries (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    period_month  DATE          NOT NULL,          -- first day of the month
    kind          finance_kind  NOT NULL,          -- revenue | expense
    source        finance_source NOT NULL,         -- auto | manual
    description   TEXT          NOT NULL,
    amount        NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    created_by    BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_finance_month ON finance_entries (period_month);


-- =====================================================================
-- 7. NOTIFICATIONS / ANNOUNCEMENTS / FEEDBACK
-- =====================================================================

-- Alert bell feed (§14.6). Optional links to the entity that raised it.
CREATE TABLE notifications (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type        notification_type NOT NULL,
    title       TEXT        NOT NULL,
    body        TEXT,
    is_read     BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Optional targets for the "›" navigation
    order_id    BIGINT REFERENCES orders(id)   ON DELETE CASCADE,
    product_id  BIGINT REFERENCES products(id) ON DELETE CASCADE,
    -- Optional recipient; NULL = system-wide
    user_id     BIGINT REFERENCES users(id)    ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_unread ON notifications (is_read, created_at DESC);

-- Dashboard announcement slider images (§4) and settings slots.
CREATE TABLE announcements (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    image_url  TEXT     NOT NULL,
    position   SMALLINT NOT NULL CHECK (position BETWEEN 1 AND 5),
    is_active  BOOLEAN  NOT NULL DEFAULT TRUE,
    UNIQUE (position)
);

-- Help page feedback form (§14.3)
CREATE TABLE feedback (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type        feedback_type NOT NULL,
    message     TEXT          NOT NULL,
    submitted_by BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);


-- =====================================================================
-- 8. BILLING — Galaxy subscription (§13)
-- =====================================================================

-- Plan catalogue with limits (§13.1). NULL = unlimited.
CREATE TABLE plans (
    plan              billing_plan  PRIMARY KEY,
    monthly_price     NUMERIC(14,2),         -- NULL for Stellar (custom)
    max_warehouses    INTEGER,
    max_products      INTEGER,
    max_orders_month  INTEGER,
    max_users         INTEGER
);

-- The single active subscription for this business (§13.1 / §13.2).
CREATE TABLE subscription (
    id              SMALLINT     PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    plan            billing_plan NOT NULL REFERENCES plans(plan),
    started_at      DATE         NOT NULL,
    next_renewal    DATE,
    outstanding     NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (outstanding >= 0),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Payment history / invoices (§13.3)
CREATE TABLE invoices (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_no     TEXT          NOT NULL UNIQUE,        -- e.g. "INV-0041"
    period         TEXT          NOT NULL,               -- billing month label
    plan           billing_plan  NOT NULL,
    amount         NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    method         TEXT          NOT NULL,               -- Cash / Bank transfer
    invoice_date   DATE          NOT NULL,
    status         TEXT          NOT NULL DEFAULT 'paid', -- paid / pending
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);


-- =====================================================================
-- 9. VIEWS & HELPER FUNCTIONS
-- =====================================================================

-- Total available quantity per product across all warehouses (§5 Available Qty).
CREATE VIEW product_stock_totals AS
SELECT p.id AS product_id,
       COALESCE(SUM(i.on_hand), 0)             AS total_on_hand,
       COALESCE(SUM(i.on_hand - i.reserved),0) AS total_available,
       p.purchase_price,
       p.selling_price,
       (p.selling_price - p.purchase_price)              AS profit_each,
       COALESCE(SUM(i.on_hand),0) * p.purchase_price     AS stock_value_purchase,
       COALESCE(SUM(i.on_hand),0) * p.selling_price      AS stock_value_selling
FROM products p
LEFT JOIN inventory i ON i.product_id = p.id
GROUP BY p.id;

-- Order financial roll-up (items + delivery − discount, plus profit). §9.3
CREATE VIEW order_totals AS
SELECT o.id AS order_id,
       COALESCE(SUM(oi.line_total), 0)                              AS items_total,
       COALESCE(SUM(oi.purchase_price * oi.quantity), 0)            AS purchase_total,
       o.delivery_charge,
       o.discount_amount,
       COALESCE(SUM(oi.line_total),0) + o.delivery_charge - o.discount_amount AS grand_total,
       COALESCE(SUM(oi.line_total - oi.purchase_price * oi.quantity), 0)      AS profit
FROM orders o
LEFT JOIN order_items oi ON oi.order_id = o.id
GROUP BY o.id;

-- updated_at auto-touch trigger
CREATE OR REPLACE FUNCTION touch_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_products_touch      BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER trg_orders_touch        BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER trg_users_touch         BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();


-- =====================================================================
-- 10. KNOX CLIENT MANAGER  (separate internal tool — §16)
--     Tracks KNOX's own agency clients & their billing.
--     Kept in its own schema; not part of the Galaxy retail system.
-- =====================================================================
SET search_path TO knox, public;

CREATE TYPE knox_plan AS ENUM (
    'monthly_2k', 'monthly_5k', 'yearly_2k', 'yearly_5k', 'unlimited'
);
CREATE TYPE knox_status       AS ENUM ('active', 'trial', 'blocked');
CREATE TYPE knox_setup_option AS ENUM ('full', 'installment_4');

CREATE TABLE knox.plans (
    plan             knox_plan     PRIMARY KEY,
    subscription_fee NUMERIC(14,2),          -- per period; NULL for unlimited (per-order)
    per_order_fee    NUMERIC(14,2),          -- unlimited plan only (Rs. 7/order)
    setup_fee        NUMERIC(14,2) NOT NULL,
    is_yearly        BOOLEAN       NOT NULL DEFAULT FALSE
);

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

SET search_path TO galaxy, public;
-- =====================================================================
--  END OF SCHEMA
-- =====================================================================
