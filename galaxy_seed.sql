-- =====================================================================
--  GALAXY — reference / seed data
--  Run after galaxy_schema.sql. Values taken verbatim from the guide.
-- =====================================================================
SET search_path TO galaxy, public;

-- ---------------------------------------------------------------------
-- Galaxy subscription plans (§13.1). NULL limit = unlimited.
-- ---------------------------------------------------------------------
INSERT INTO plans (plan, monthly_price, max_warehouses, max_products, max_orders_month, max_users) VALUES
    ('basic',   2000.00, 1,    500,  100, 2),
    ('nova',    5000.00, 3,    NULL, 500, 4),
    ('stellar', NULL,    NULL, NULL, NULL, NULL)
ON CONFLICT (plan) DO NOTHING;

-- ---------------------------------------------------------------------
-- Role → feature access matrix (§11.4)
--   full = ✓ | none = ✕ | no_price = "no price"
--   Role order in each block: owner, admin, manager, sales,
--                             stock_keeper, delivery, accountant
-- ---------------------------------------------------------------------
INSERT INTO role_permissions (role, feature, access) VALUES
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
ON CONFLICT (role, feature) DO NOTHING;

-- ---------------------------------------------------------------------
-- Default business row (§12). Edit for the real client during setup (§15).
-- ---------------------------------------------------------------------
INSERT INTO business_settings (id, business_name)
VALUES (1, 'My Business')
ON CONFLICT (id) DO NOTHING;


-- =====================================================================
-- KNOX Client Manager plans & pricing (§16.5)
-- =====================================================================
SET search_path TO knox, public;

INSERT INTO knox.plans (plan, subscription_fee, per_order_fee, setup_fee, is_yearly) VALUES
    ('monthly_2k',  2000.00,  NULL, 20000.00, FALSE),
    ('monthly_5k',  5000.00,  NULL, 20000.00, FALSE),
    ('yearly_2k',  20000.00,  NULL, 40000.00, TRUE),
    ('yearly_5k',  50000.00,  NULL, 70000.00, TRUE),
    ('unlimited',   NULL,     7.00, 20000.00, FALSE)
ON CONFLICT (plan) DO NOTHING;

SET search_path TO galaxy, public;
