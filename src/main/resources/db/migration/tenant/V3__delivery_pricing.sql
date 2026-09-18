-- =====================================================================
--  Delivery method pricing: percentage charges and per-region rates
--
--  Two things a delivery method could not express before:
--
--   1. charge_kind — the charge may now be a percent of the order's items
--      subtotal instead of a flat amount, the same choice discount_codes
--      already offers. `charge` keeps holding the number either way; when
--      the kind is 'percentage' it reads as 0-100, hence the CHECK that
--      mirrors the one on discount_codes.
--
--   2. rate_scope + delivery_method_rates — a courier that charges more to
--      reach Jaffna than Nugegoda. 'flat' (the default every existing row
--      keeps) means one charge everywhere and no rate rows are consulted;
--      'province' or 'district' means the matching rate row wins and
--      delivery_methods.charge is the fallback for regions with no row.
--
--  Rate rows carry their own region_type rather than inheriting the
--  method's scope, so flipping a method between province- and
--  district-wise in Settings leaves the other grid's numbers on record
--  instead of destroying them.
--
--  Deliberately UNQUALIFIED for the tenant tables (TenantMigrationService
--  points Flyway's search_path at the one tenant schema); the enum types
--  are public., created by the platform migration of the same version.
-- =====================================================================

ALTER TABLE delivery_methods
    ADD COLUMN charge_kind public.delivery_charge_kind NOT NULL DEFAULT 'fixed',
    ADD COLUMN rate_scope  public.delivery_region_type NOT NULL DEFAULT 'flat';

-- A percentage charge is 0-100, exactly like discount_codes.value.
ALTER TABLE delivery_methods
    ADD CONSTRAINT delivery_methods_percentage_range
    CHECK (charge_kind <> 'percentage' OR charge <= 100);

CREATE TABLE delivery_method_rates (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    delivery_method_id BIGINT NOT NULL
                       REFERENCES delivery_methods(id) ON DELETE CASCADE,
    region_type        public.delivery_region_type NOT NULL,
    region_name        TEXT   NOT NULL,
    charge             NUMERIC(14,2) NOT NULL CHECK (charge >= 0),
    -- 'flat' is a scope, never a region a rate row can name.
    CHECK (region_type <> 'flat'),
    UNIQUE (delivery_method_id, region_type, region_name)
);

CREATE INDEX idx_delivery_rates_method ON delivery_method_rates (delivery_method_id);
