package com.knox.galaxy.tenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Tells Hibernate which schema the session being opened belongs to.
 * Consulted once per Session, so {@link TenantContext} must already be set
 * before any repository call.
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.getSchemaOrPlatform();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
