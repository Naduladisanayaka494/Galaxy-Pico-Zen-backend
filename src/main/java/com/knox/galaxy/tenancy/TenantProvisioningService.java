package com.knox.galaxy.tenancy;

import com.knox.galaxy.model.*;
import com.knox.galaxy.repository.SubscriptionRepository;
import com.knox.galaxy.repository.TenantRepository;
import com.knox.galaxy.repository.TenantUserRepository;
import com.knox.galaxy.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Creates a tenant: its schema, its tables, its reference data and its owner.
 *
 * <p>Structure is STAMPED from {@code db/tenant_template.sql} — plain
 * {@code CREATE TABLE} statements run with the new schema on the search_path.
 * There is deliberately no runtime cloning of the {@code galaxy} schema (no
 * catalog introspection, no PL/pgSQL): every tenant is built the same
 * reviewable way a fresh database is built. The cost of this is that galaxy
 * and tenant_template.sql are two things a human keeps in sync by hand —
 * change one, mirror it in the other — rather than galaxy alone being able to
 * define a tenant.
 *
 * <p>Reference rows come from {@code db/tenant_seed.sql}: without it a tenant
 * would start with an empty role_permissions matrix and nobody could do
 * anything.
 *
 * <p>Not atomic. PostgreSQL can roll back DDL, but this spans the platform
 * schema and a brand-new tenant schema across several Hibernate sessions
 * (each pinned to one schema), so a failure midway is cleaned up by hand in
 * {@link #rollbackSchema}. A tenant left in {@code provisioning} status cannot
 * be logged into, so a half-built tenant is inert rather than dangerous.
 */
@Service
public class TenantProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningService.class);

    private static final Pattern SAFE_SLUG = Pattern.compile("^[a-z][a-z0-9_]{2,30}$");
    private static final String SCHEMA_PREFIX = "tenant_";

    /** Mirrors knox.tenants' CHECK. A schema here would shadow the control plane. */
    private static final java.util.Set<String> RESERVED = java.util.Set.of(
            "public", "knox", "galaxy", "information_schema", "pg_catalog", "pg_toast", "pg_temp");

    private final JdbcTemplate jdbcTemplate;
    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantResolver tenantResolver;
    private final com.knox.galaxy.repository.RoleRepository roleRepository;

    public TenantProvisioningService(JdbcTemplate jdbcTemplate,
                                     TenantRepository tenantRepository,
                                     TenantUserRepository tenantUserRepository,
                                     SubscriptionRepository subscriptionRepository,
                                     UserRepository userRepository,
                                     PasswordEncoder passwordEncoder,
                                     TenantResolver tenantResolver,
                                     com.knox.galaxy.repository.RoleRepository roleRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantRepository = tenantRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantResolver = tenantResolver;
        this.roleRepository = roleRepository;
    }

    public Tenant provision(ProvisionTenantCommand command) {
        String slug = command.getSlug() == null ? "" : command.getSlug().trim().toLowerCase();
        if (!SAFE_SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException(
                    "Slug must be 3-31 chars, lowercase letters/digits/underscore, starting with a letter");
        }
        String schemaName = SCHEMA_PREFIX + slug;
        if (RESERVED.contains(slug) || RESERVED.contains(schemaName)) {
            throw new IllegalArgumentException("Reserved name: " + slug);
        }
        if (tenantRepository.existsBySlug(slug) || tenantRepository.existsBySchemaName(schemaName)) {
            throw new IllegalArgumentException("Tenant already exists: " + slug);
        }
        if (tenantUserRepository.existsByEmailIgnoreCase(command.getOwnerEmail())) {
            // Checked before any DDL: one email maps to exactly one tenant, so
            // this would fail at the very last step otherwise.
            throw new IllegalArgumentException("Email already registered: " + command.getOwnerEmail());
        }

        TenantContext.clear();

        Tenant tenant = new Tenant();
        tenant.setSlug(slug);
        tenant.setSchemaName(schemaName);
        tenant.setBusinessName(command.getBusinessName());
        tenant.setClientId(command.getClientId());
        tenant.setStatus(TenantStatus.provisioning);
        tenant = tenantRepository.save(tenant);

        try {
            createSchema(schemaName);
            stampTemplateAndSeed(schemaName);
            seedBusinessSettings(schemaName, command.getBusinessName());

            User owner = createOwner(schemaName, command);
            createDirectoryEntry(tenant.getId(), command, owner.getId());
            createSubscription(tenant.getId(), command.getPlan());

            tenant.setStatus(TenantStatus.active);
            tenant.setUpdatedAt(LocalDateTime.now());
            tenant = tenantRepository.save(tenant);

            tenantResolver.evict(tenant.getId());
            log.info("Provisioned tenant {} into schema {}", slug, schemaName);
            return tenant;

        } catch (RuntimeException e) {
            log.error("Provisioning failed for {}; rolling back", slug, e);
            rollbackSchema(schemaName, tenant.getId());
            throw e;
        }
    }

    private void createSchema(String schemaName) {
        // schemaName is derived from a slug already matched against SAFE_SLUG,
        // and CREATE SCHEMA takes an identifier that cannot be bound.
        jdbcTemplate.execute("CREATE SCHEMA \"" + schemaName + "\"");
    }

    /**
     * Runs tenant_template.sql (structure) then tenant_seed.sql (reference
     * rows) with the new schema first on the search_path, so their unqualified
     * statements land there while shared enum types still resolve from public.
     * Same connection, same search_path setting, because the seed's INSERTs
     * depend on tables the template just created.
     */
    private void stampTemplateAndSeed(String schemaName) {
        String template = readClasspath("db/tenant_template.sql");
        String seed = readClasspath("db/tenant_seed.sql");
        jdbcTemplate.execute((java.sql.Connection connection) -> {
            try {
                try (java.sql.Statement statement = connection.createStatement()) {
                    statement.execute("SET search_path TO \"" + schemaName + "\", public");
                    statement.execute(template);
                    statement.execute(seed);
                }
            } finally {
                // This connection goes back to a pool shared with tenant traffic.
                try (java.sql.Statement reset = connection.createStatement()) {
                    reset.execute("SET search_path TO public");
                }
            }
            return null;
        });
    }

    private String readClasspath(String path) {
        try (InputStreamReader reader = new InputStreamReader(
                new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path + " from classpath", e);
        }
    }

    /** business_settings is pinned to one row; without it the tenant's settings screen has nothing to read. */
    private void seedBusinessSettings(String schemaName, String businessName) {
        jdbcTemplate.update(
                "INSERT INTO \"" + schemaName + "\".business_settings (id, business_name) VALUES (1, ?)",
                businessName);
    }

    private User createOwner(String schemaName, ProvisionTenantCommand command) {
        TenantContext.setSchema(schemaName);
        try {
            User owner = new User();
            owner.setUsername(command.getOwnerUsername());
            owner.setFirstName(command.getOwnerFirstName());
            owner.setLastName(command.getOwnerLastName());
            owner.setEmail(command.getOwnerEmail());
            // Resolves against this tenant's own roles table — tenant_seed.sql
            // just seeded it (stampTemplateAndSeed runs before createOwner),
            // and 'owner' is the one role every tenant is guaranteed to have.
            owner.setRole(roleRepository.findByName("owner")
                    .orElseThrow(() -> new IllegalStateException(
                            schemaName + ".roles has no 'owner' row; tenant_seed.sql did not run")));
            owner.setPasswordHash(passwordEncoder.encode(command.getOwnerPassword()));
            owner.setCreatedAt(LocalDateTime.now());
            owner.setUpdatedAt(LocalDateTime.now());
            return userRepository.save(owner);
        } finally {
            TenantContext.clear();
        }
    }

    private void createDirectoryEntry(Long tenantId, ProvisionTenantCommand command, Long localUserId) {
        TenantUser tenantUser = new TenantUser();
        tenantUser.setTenantId(tenantId);
        tenantUser.setEmail(command.getOwnerEmail());
        tenantUser.setPasswordHash(passwordEncoder.encode(command.getOwnerPassword()));
        tenantUser.setLocalUserId(localUserId);
        tenantUser.setStatus(TenantUserStatus.active);
        tenantUserRepository.save(tenantUser);
    }

    private void createSubscription(Long tenantId, BillingPlan plan) {
        Subscription subscription = new Subscription();
        subscription.setTenantId(tenantId);
        subscription.setPlan(plan == null ? BillingPlan.basic : plan);
        subscription.setStatus(SubscriptionStatus.trialing);
        subscription.setStartedAt(LocalDate.now());
        subscription.setTrialEndsAt(LocalDate.now().plusDays(7));
        subscriptionRepository.save(subscription);
    }

    /**
     * Best-effort cleanup. Failures are logged, not rethrown: the caller is
     * already handling the original error and that one is more useful.
     */
    private void rollbackSchema(String schemaName, Long tenantId) {
        TenantContext.clear();
        try {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS \"" + schemaName + "\" CASCADE");
        } catch (RuntimeException e) {
            log.error("Could not drop schema {} during rollback; needs manual cleanup", schemaName, e);
        }
        try {
            tenantRepository.deleteById(tenantId);
        } catch (RuntimeException e) {
            log.error("Could not delete tenant row {} during rollback", tenantId, e);
        }
        tenantResolver.evict(tenantId);
    }
}
