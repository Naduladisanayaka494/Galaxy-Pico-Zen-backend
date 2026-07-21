package com.knox.galaxy.tenancy;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

/**
 * Applies {@code db/migration/tenant/*} to one tenant schema via its own
 * Flyway instance.
 *
 * <p>Spring Boot's auto-configured Flyway (see application.properties) only
 * ever manages one schema — the platform one. Schema-per-tenant means a
 * fresh {@link Flyway} instance per call, each pointed at exactly one
 * tenant_&lt;slug&gt; schema. Flyway creates its own
 * {@code flyway_schema_history} table inside that schema, so every tenant
 * independently and durably tracks which migrations it has had applied —
 * that is what makes double-applying a migration to a schema structurally
 * impossible, and "did every tenant get this change" an answerable question
 * instead of something kept in a person's head.
 */
@Service
public class TenantMigrationService {

    private static final Logger log = LoggerFactory.getLogger(TenantMigrationService.class);
    private static final String LOCATIONS = "classpath:db/migration/tenant";

    private final DataSource dataSource;

    public TenantMigrationService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Brings one tenant schema to the latest tenant migration.
     *
     * <p>Safe to call repeatedly and safe to call on a schema that predates
     * this migration system: baselineOnMigrate only triggers when the target
     * schema already has objects in it but no history table yet (every
     * tenant stamped by the old raw-SQL {@code stampTemplateAndSeed}), in
     * which case V1 is marked applied without being re-run and only V2
     * onward actually executes. A genuinely empty schema (brand-new tenant)
     * runs every version for real.
     */
    public void migrate(String schemaName) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations(LOCATIONS)
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .baselineDescription("Baseline: schema as stamped by tenant_template.sql before Flyway")
                .load()
                .migrate();
        log.info("Tenant schema {} migrated to latest", schemaName);
    }
}
