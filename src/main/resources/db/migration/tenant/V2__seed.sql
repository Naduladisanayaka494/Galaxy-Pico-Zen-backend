-- =====================================================================
--  GALAXY — per-tenant reference data
--
--  Runs immediately after V1__init.sql in the same tenant Flyway instance.
--
--  Why this is a file and not rows copied from galaxy: `galaxy` is kept
--  empty as a pure structural reference, so there is no data there to
--  clone. Without this file a new tenant would come up with an EMPTY
--  role_permissions table — i.e. no one can do anything.
--
--  Both INSERTs are idempotent (ON CONFLICT DO NOTHING), which matters for
--  the tenants baselined at V1 (every tenant that predates this migration
--  system): baselining marks V1 as done without running it, but V2 still
--  actually executes against them — safe, since it only adds rows that are
--  missing, never duplicates or overwrites ones already there.
--
--  Deliberately UNQUALIFIED: TenantMigrationService sets search_path to the
--  target tenant schema via Flyway's schemas(...) config.
--  Values follow the Galaxy user guide (§11.4 role matrix).
--  Both plan catalogues are platform data and are NOT seeded here: KNOX's own
--  agency pricing (knox.plans) and Galaxy's own tiers (knox.galaxy_plans) both
--  live in db/migration/platform/V1__baseline.sql.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Starting roles. Per-tenant and editable from here on — this is just the
-- default set a new tenant gets, not a fixed platform list. 'owner' is
-- is_system=true and must stay that way (see the comment on the roles table
-- in V1__init.sql); the rest are ordinary rows a tenant can rename, delete,
-- or add siblings to once role management exists.
-- ---------------------------------------------------------------------
INSERT INTO roles (name, is_system) VALUES
    ('owner', TRUE),
    ('admin', FALSE),
    ('manager', FALSE),
    ('sales', FALSE),
    ('stock_keeper', FALSE),
    ('delivery', FALSE),
    ('accountant', FALSE)
ON CONFLICT ((lower(name))) DO NOTHING;

-- ---------------------------------------------------------------------
-- Role → feature access matrix (§11.4) for the roles seeded just above.
--   full = ✓ | none = ✕ | no_price = "no price"
--   Role order in each block: owner, admin, manager, sales,
--                             stock_keeper, delivery, accountant
-- ---------------------------------------------------------------------
-- Role names, not IDs: roles.id is an identity column, so the actual
-- integers aren't something a seed file should hardcode. Resolved via a join
-- at insert time instead.
INSERT INTO role_permissions (role_id, feature, access)
SELECT r.id, v.feature, v.access::access_level
FROM (VALUES
  -- Item Stock — view
  ('owner','item_stock_view','full'),('admin','item_stock_view','full'),
  ('manager','item_stock_view','full'),('sales','item_stock_view','no_price'),
  ('stock_keeper','item_stock_view','full'),('delivery','item_stock_view','none'),
  ('accountant','item_stock_view','full'),
  -- Item Stock — edit/delete
  ('owner','item_stock_edit','full'),('admin','item_stock_edit','full'),
  ('manager','item_stock_edit','full'),('sales','item_stock_edit','none'),
  ('stock_keeper','item_stock_edit','full'),('delivery','item_stock_edit','none'),
  ('accountant','item_stock_edit','none'),
  -- Add Product & Refill
  ('owner','add_product','full'),('admin','add_product','full'),
  ('manager','add_product','full'),('sales','add_product','none'),
  ('stock_keeper','add_product','full'),('delivery','add_product','none'),
  ('accountant','add_product','none'),
  -- Warehouses
  ('owner','warehouses','full'),('admin','warehouses','full'),
  ('manager','warehouses','full'),('sales','warehouses','none'),
  ('stock_keeper','warehouses','full'),('delivery','warehouses','none'),
  ('accountant','warehouses','none'),
  -- Place Order
  ('owner','place_order','full'),('admin','place_order','full'),
  ('manager','place_order','full'),('sales','place_order','full'),
  ('stock_keeper','place_order','none'),('delivery','place_order','none'),
  ('accountant','place_order','none'),
  -- Manage Orders — view
  ('owner','orders_view','full'),('admin','orders_view','full'),
  ('manager','orders_view','full'),('sales','orders_view','full'),
  ('stock_keeper','orders_view','none'),('delivery','orders_view','full'),
  ('accountant','orders_view','full'),
  -- Manage Orders — edit/cancel
  ('owner','orders_edit','full'),('admin','orders_edit','full'),
  ('manager','orders_edit','full'),('sales','orders_edit','none'),
  ('stock_keeper','orders_edit','none'),('delivery','orders_edit','none'),
  ('accountant','orders_edit','none'),
  -- Manage Orders — status update
  ('owner','orders_status','full'),('admin','orders_status','full'),
  ('manager','orders_status','full'),('sales','orders_status','full'),
  ('stock_keeper','orders_status','none'),('delivery','orders_status','full'),
  ('accountant','orders_status','none'),
  -- Reports
  ('owner','reports','full'),('admin','reports','full'),
  ('manager','reports','full'),('sales','reports','none'),
  ('stock_keeper','reports','none'),('delivery','reports','none'),
  ('accountant','reports','full'),
  -- Users
  ('owner','users','full'),('admin','users','full'),
  ('manager','users','none'),('sales','users','none'),
  ('stock_keeper','users','none'),('delivery','users','none'),
  ('accountant','users','none'),
  -- Settings — Business info
  ('owner','settings_business','full'),('admin','settings_business','full'),
  ('manager','settings_business','none'),('sales','settings_business','none'),
  ('stock_keeper','settings_business','none'),('delivery','settings_business','none'),
  ('accountant','settings_business','none'),
  -- Settings — Time & Currency
  ('owner','settings_time_currency','full'),('admin','settings_time_currency','full'),
  ('manager','settings_time_currency','none'),('sales','settings_time_currency','none'),
  ('stock_keeper','settings_time_currency','none'),('delivery','settings_time_currency','none'),
  ('accountant','settings_time_currency','none'),
  -- Settings — Notifications (everyone)
  ('owner','settings_notifications','full'),('admin','settings_notifications','full'),
  ('manager','settings_notifications','full'),('sales','settings_notifications','full'),
  ('stock_keeper','settings_notifications','full'),('delivery','settings_notifications','full'),
  ('accountant','settings_notifications','full'),
  -- Settings — Theme (everyone)
  ('owner','settings_theme','full'),('admin','settings_theme','full'),
  ('manager','settings_theme','full'),('sales','settings_theme','full'),
  ('stock_keeper','settings_theme','full'),('delivery','settings_theme','full'),
  ('accountant','settings_theme','full'),
  -- Billing
  ('owner','billing','full'),('admin','billing','none'),
  ('manager','billing','none'),('sales','billing','none'),
  ('stock_keeper','billing','none'),('delivery','billing','none'),
  ('accountant','billing','full')
) AS v(role_name, feature, access)
JOIN roles r ON r.name = v.role_name
ON CONFLICT (role_id, feature) DO NOTHING;
