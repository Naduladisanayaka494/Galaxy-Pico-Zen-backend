package com.knox.galaxy.tenancy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps the {@code galaxy} template schema on the latest tenant migration.
 *
 * <p>{@link TenantMigrationRunner} reaches every real tenant, but it runs as an
 * {@code ApplicationRunner} — after the context is up. The template schema
 * cannot wait that long: it is what Hibernate's {@code ddl-auto=validate}
 * checks while the EntityManagerFactory is being built, so a tenant migration
 * that adds a table or column (V3__delivery_pricing.sql was the first) would
 * fail validation at boot with the template still one version behind.
 *
 * <p>Hooking it onto the platform Flyway run puts it in the one window that
 * works: Spring Boot orders {@code FlywayMigrationInitializer} before the
 * EntityManagerFactory, so by the time validation looks at {@code galaxy} the
 * schema already matches the entities. New tenants are unaffected — their
 * schemas are created empty and migrated in full by
 * {@link TenantProvisioningService}.
 *
 * <p>One visible side effect: the template picks up V2__seed.sql's default
 * roles and permissions, where before it held structure only. Nothing reads
 * data out of the template (provisioning builds each tenant from the migrations,
 * never by cloning galaxy), so those rows are inert, and the seed is idempotent.
 */
@Configuration
public class TenantTemplateMigrationConfig {

    private static final Logger log = LoggerFactory.getLogger(TenantTemplateMigrationConfig.class);

    @Bean
    public FlywayMigrationStrategy tenantTemplateMigrationStrategy(
            TenantMigrationService tenantMigrationService,
            @Value("${galaxy.tenancy.template-schema:galaxy}") String templateSchema) {
        return flyway -> {
            flyway.migrate(); // platform schema, exactly as the default strategy does
            try {
                tenantMigrationService.migrate(templateSchema);
            } catch (RuntimeException e) {
                // Let boot continue to Hibernate's own validation rather than
                // dying here: its error names the exact missing table or column,
                // which is far more useful than a Flyway stack trace.
                log.error("Could not migrate the template schema {}; ddl-auto=validate may now fail",
                        templateSchema, e);
            }
        };
    }
}
