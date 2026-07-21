package com.knox.galaxy.tenancy;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

/**
 * Points each pooled connection at the calling tenant's schema.
 *
 * <p>Two invariants keep tenants apart, and both matter:
 *
 * <ol>
 *   <li><b>Every</b> connection handed out has its search_path set. A connection
 *       is never used with whatever path the previous borrower left behind.</li>
 *   <li>Every connection returned to the pool is reset. Belt-and-braces against
 *       any path that bypasses invariant 1.</li>
 * </ol>
 *
 * <p>The schema name is interpolated into SQL because {@code SET search_path}
 * takes an identifier, not a bind parameter. It is therefore re-validated here
 * against {@link #SAFE_SCHEMA} even though {@code knox.tenants} already
 * constrains it — this is the last gate before the string reaches the database,
 * and it must not rely on callers having done the right thing.
 */
@Component
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider {

    private static final Pattern SAFE_SCHEMA = Pattern.compile("^[a-z][a-z0-9_]{2,40}$");

    private final DataSource dataSource;

    /**
     * Schema used for Hibernate's startup schema validation, which runs before
     * any request has bound a tenant. Must be a schema carrying the full tenant
     * table set, or {@code ddl-auto=validate} fails at boot.
     */
    private final String templateSchema;

    public SchemaMultiTenantConnectionProvider(
            DataSource dataSource,
            @Value("${galaxy.tenancy.template-schema:galaxy}") String templateSchema) {
        this.dataSource = dataSource;
        this.templateSchema = templateSchema;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        Connection connection = dataSource.getConnection();
        applySearchPath(connection, templateSchema);
        return connection;
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        resetAndClose(connection);
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        try {
            applySearchPath(connection, tenantIdentifier);
        } catch (SQLException | RuntimeException e) {
            // Never return a connection to the pool whose search_path we could
            // not establish — the next borrower would inherit an unknown path.
            connection.close();
            throw e;
        }
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        resetAndClose(connection);
    }

    private void applySearchPath(Connection connection, String schema) throws SQLException {
        if (schema == null || !SAFE_SCHEMA.matcher(schema).matches()) {
            throw new IllegalArgumentException("Refusing unsafe schema identifier: " + schema);
        }
        try (Statement statement = connection.createStatement()) {
            // public trails so shared enums and touch_updated_at() resolve.
            statement.execute("SET search_path TO \"" + schema + "\", public");
        }
    }

    private void resetAndClose(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO public");
        } catch (SQLException e) {
            // Reset failed, so this connection's path is unknown. Closing it
            // without returning it to the pool is the only safe outcome.
            connection.close();
            throw e;
        }
        connection.close();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        // Aggressive release would return the connection mid-transaction and
        // re-acquire it later, losing the search_path we just set.
        return false;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean isUnwrappableAs(Class unwrapType) {
        return MultiTenantConnectionProvider.class.isAssignableFrom(unwrapType)
                || DataSource.class.isAssignableFrom(unwrapType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> unwrapType) {
        if (MultiTenantConnectionProvider.class.isAssignableFrom(unwrapType)) {
            return (T) this;
        }
        if (DataSource.class.isAssignableFrom(unwrapType)) {
            return (T) dataSource;
        }
        throw new UnsupportedOperationException("Cannot unwrap to " + unwrapType);
    }
}
