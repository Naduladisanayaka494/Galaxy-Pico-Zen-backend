package com.knox.galaxy.tenancy;

/**
 * Holds the schema the current thread's queries must run against, plus the
 * tenant id that selected it.
 *
 * <p>Set by {@code JwtAuthenticationFilter} from the token's tenant claim and
 * cleared in a finally block once the request completes. When unset, queries
 * run against {@link #PLATFORM}, where only the schema-qualified {@code knox}
 * entities resolve — tenant tables are absent there, so a missing tenant
 * context fails loudly instead of silently reading the wrong rows.
 */
public final class TenantContext {

    /** Search path used when no tenant is bound: login, provisioning, admin. */
    public static final String PLATFORM = "public";

    private static final ThreadLocal<String> CURRENT_SCHEMA = new ThreadLocal<>();
    private static final ThreadLocal<Long> CURRENT_TENANT_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setSchema(String schema) {
        CURRENT_SCHEMA.set(schema);
    }

    public static void bind(Long tenantId, String schema) {
        CURRENT_TENANT_ID.set(tenantId);
        CURRENT_SCHEMA.set(schema);
    }

    public static String getSchema() {
        return CURRENT_SCHEMA.get();
    }

    public static String getSchemaOrPlatform() {
        String schema = CURRENT_SCHEMA.get();
        return schema != null ? schema : PLATFORM;
    }

    public static Long getTenantId() {
        return CURRENT_TENANT_ID.get();
    }

    public static Long requireTenantId() {
        Long tenantId = CURRENT_TENANT_ID.get();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant bound to this request");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT_SCHEMA.remove();
        CURRENT_TENANT_ID.remove();
    }
}
