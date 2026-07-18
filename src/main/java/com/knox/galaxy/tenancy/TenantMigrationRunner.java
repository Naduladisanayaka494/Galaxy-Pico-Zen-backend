package com.knox.galaxy.tenancy;

import com.knox.galaxy.model.Tenant;
import com.knox.galaxy.model.TenantStatus;
import com.knox.galaxy.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Brings every existing tenant schema up to the latest tenant migration on
 * boot, after platform Flyway and Hibernate's ddl-auto=validate have already
 * run against {@code galaxy}.
 *
 * <p>This is what replaces hand-written one-off ALTER scripts run against
 * tenant schemas individually: adding a new file to {@code db/migration/tenant}
 * now reaches every tenant automatically on the next restart, each tracked
 * independently, instead of requiring someone to remember every schema name
 * and patch them by hand one at a time.
 *
 * <p>{@code provisioning}-status tenants are skipped: that status means
 * {@link TenantProvisioningService} is (or was, if it crashed) actively
 * building that schema itself via {@link TenantMigrationService}, so this
 * runner staying out of the way avoids two migration runs racing the same
 * schema.
 */
@Component
@Order(Integer.MAX_VALUE)
public class TenantMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantMigrationRunner.class);

    private final TenantRepository tenantRepository;
    private final TenantMigrationService tenantMigrationService;

    public TenantMigrationRunner(TenantRepository tenantRepository, TenantMigrationService tenantMigrationService) {
        this.tenantRepository = tenantRepository;
        this.tenantMigrationService = tenantMigrationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        TenantContext.clear();
        for (Tenant tenant : tenantRepository.findAll()) {
            if (tenant.getStatus() == TenantStatus.provisioning) {
                continue;
            }
            try {
                tenantMigrationService.migrate(tenant.getSchemaName());
            } catch (RuntimeException e) {
                // One tenant's migration failure must not take down app boot
                // or block the others — that would lock out every tenant
                // over one bad schema instead of just the one.
                log.error("Migration failed for tenant schema {}; it stays on its previous version",
                        tenant.getSchemaName(), e);
            }
        }
    }
}
