package com.knox.galaxy.tenancy;

import org.hibernate.MultiTenancyStrategy;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Hands Hibernate the Spring-managed tenancy beans. Registering the instances
 * (rather than class names) is what lets the connection provider be injected
 * with the application's DataSource.
 */
@Configuration
public class MultiTenancyConfig implements HibernatePropertiesCustomizer {

    private final SchemaMultiTenantConnectionProvider connectionProvider;
    private final TenantIdentifierResolver tenantIdentifierResolver;

    public MultiTenancyConfig(SchemaMultiTenantConnectionProvider connectionProvider,
                              TenantIdentifierResolver tenantIdentifierResolver) {
        this.connectionProvider = connectionProvider;
        this.tenantIdentifierResolver = tenantIdentifierResolver;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT, MultiTenancyStrategy.SCHEMA);
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
    }
}
