package com.knox.galaxy.tenancy;

import com.knox.galaxy.model.Tenant;
import com.knox.galaxy.repository.TenantRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps a token's tenant id to the schema name to put on the search_path.
 *
 * <p>The mapping is cached because it is consulted on every authenticated
 * request and a tenant's schema name never changes. Tenant <em>status</em> is
 * deliberately not cached or checked here — see {@code AuthService}, which
 * checks it at login. Consequence: suspending a tenant does not kill sessions
 * already holding a token; it takes effect when that token expires.
 */
@Service
public class TenantResolver {

    private final TenantRepository tenantRepository;
    private final Map<Long, String> schemaByTenantId = new ConcurrentHashMap<>();

    public TenantResolver(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    /**
     * @throws IllegalArgumentException if the id matches no tenant. Callers must
     *         let this fail the request rather than falling back to a default
     *         schema — a wrong default here is a cross-tenant data leak.
     */
    public String schemaFor(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("No tenant id on token");
        }
        String cached = schemaByTenantId.get(tenantId);
        if (cached != null) {
            return cached;
        }
        // Look up on the platform search_path; knox entities are schema-qualified.
        String previous = TenantContext.getSchema();
        TenantContext.clear();
        try {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown tenant: " + tenantId));
            schemaByTenantId.put(tenantId, tenant.getSchemaName());
            return tenant.getSchemaName();
        } finally {
            if (previous != null) {
                TenantContext.setSchema(previous);
            }
        }
    }

    /** Call after provisioning or renaming so the next request re-reads the row. */
    public void evict(Long tenantId) {
        schemaByTenantId.remove(tenantId);
    }

    public void evictAll() {
        schemaByTenantId.clear();
    }
}
