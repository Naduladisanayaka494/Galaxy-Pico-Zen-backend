# Backend architecture

Spring Boot (2.7, Java 17) API backing Galaxy, a multi-tenant stock/order
management product, plus KNOX's own internal Client Manager. One deployable,
one database, many tenants.

## The three identity domains

| Domain | Who | Table | Token carries |
|---|---|---|---|
| **Tenant user** | Someone at a business using Galaxy | `tenant_<slug>.users` (profile) + `knox.tenant_users` (credentials) | `tenantId`, tenant-local `userId` |
| **Platform / KNOX staff** | KNOX's own team, running the agency | `knox.platform_users` | nothing tenant-scoped — reaches only `knox`/`public` |
| **KNOX's agency clients** | KNOX's own customers (may or may not be a Galaxy tenant) | `knox.clients` | not a login identity — a CRM/billing record `POST /api/platform/clients` manages |

Platform staff and tenant users are deliberately separate identity systems,
not one table with a role flag: a platform token must never be able to reach
a tenant schema, and a tenant token must never see across tenants. Keeping
them structurally distinct makes "can this token see that data" a schema
question, enforceable by Postgres itself, rather than an application-level
check that a future endpoint could forget.

## Schema-per-tenant multi-tenancy

Every tenant's business data lives in its own Postgres schema,
`tenant_<slug>`, all built from the identical DDL (see
[Migrations](#migrations-flyway)). Control-plane data that must be readable
*before* a tenant is even known — who is this login, which tenant do they
belong to, are they paid up — lives in `knox`, a schema of its own. Shared
reference data used by every tenant schema (enum types, one trigger function)
lives in `public`, which trails every schema's search_path.

### How a request gets bound to a tenant

This is the part that makes the same JPA repositories/entities correctly
serve different tenants' data on different requests:

1. **Login** (`AuthService.login`) resolves `tenant_id` from
   `knox.tenant_users` by email, and bakes it into the JWT access token as a
   claim (`JwtTokenProvider`). The tenant is decided once, at login — nothing
   about routing, subdomain, or header decides it per-request afterward.
2. **Every request**, `JwtAuthenticationFilter` decodes the token, pulls out
   `tenantId`, and calls `TenantContext.bind(tenantId, schema)`.
3. `TenantResolver.schemaFor(tenantId)` turns the id into a schema name via
   `knox.tenants`, cached (`ConcurrentHashMap`) since it's looked up on every
   authenticated request but a tenant's schema name never changes.
4. `TenantContext` is a `ThreadLocal` holding `(tenantId, schema)` for the
   life of the request, cleared in `JwtAuthenticationFilter`'s `finally` —
   critical because Tomcat reuses threads, so a leftover value would leak
   into whichever request runs next on that thread.
5. Hibernate asks `TenantIdentifierResolver.resolveCurrentTenantIdentifier()`
   (its plug-in point for this) which schema to use, once per `Session`. It
   just reads `TenantContext`. No tenant bound → falls back to `"public"`,
   where tenant tables don't exist, so a request that somehow skipped tenant
   binding fails loudly instead of silently reading the wrong tenant.
6. `SchemaMultiTenantConnectionProvider` is where it actually becomes real:
   before handing out a pooled JDBC connection, it runs
   `SET search_path TO "<schema>", public`. Because connections are pooled
   and reused across tenants, every connection borrowed gets its path set
   fresh, and every connection returned to the pool gets reset to `public`
   first — belt-and-braces against a stale search_path leaking to the next
   borrower.

Net effect: `userRepository.findById(id)` resolves to
`tenant_acme.users` or `tenant_globex.users` depending purely on which
request called it, using the same `User` entity class either way.

### The `galaxy` schema

`galaxy` is a live, structurally-identical twin of a tenant schema, kept
purely as what Hibernate's `ddl-auto=validate` checks against at boot (before
any tenant is bound, there has to be *something* on the search_path for
Hibernate to validate entities against). It holds no real data and is never
looped over by tenant migrations or read by application code — it exists
only for schema validation and as a human-browsable reference. Consequence:
if you change `db/migration/tenant/`, you must also mirror the change into
`galaxy` by hand (or drop and rebuild it from the same files); they're two
independent copies of the same DDL, not a source-and-derivative pair.

## Auth architecture

### Tenant auth — access + refresh tokens

- **Access token**: stateless JWT, 15 minutes (`jwt.access-token-expiration`).
  Carries `tenantId` + local `userId`. Never touches the database to validate
  — that's the point of a JWT.
- **Refresh token**: opaque random value, 30 days (configurable), delivered
  as an **httpOnly, Secure, SameSite=Strict** cookie scoped to `/api/auth`.
  SHA-256-hashed before it's stored in `knox.refresh_tokens` — same principle
  as a password hash, a DB leak must not hand out live, usable tokens.
- **Rotation + reuse detection**: every `POST /api/auth/refresh` revokes the
  presented token and issues a new one in the same `family_id`. If a
  *revoked* token is ever presented again — a stolen-token replay — the
  entire family is revoked (`RefreshTokenService.revokeFamily`), forcing a
  full re-login, not just killing the one token.
- **CSRF (double-submit cookie)**: `/api/auth/refresh` and `/api/auth/logout`
  require a `galaxy_csrf_token` cookie value to be echoed back as an
  `X-XSRF-Token` header. The refresh cookie alone rides along with any
  cross-site request automatically (that's what cookies do); the CSRF cookie
  is deliberately **not** httpOnly so legitimate same-origin JS can read it
  and set the header, but an attacker's forged cross-site request can't —
  the browser's same-origin policy blocks a different origin's JS from
  reading it.
- **Rate limiting**: Bucket4j, 5 requests/minute per IP, on
  `/api/auth/login`, `/api/auth/refresh`, `/api/platform/auth/login`
  (`RateLimitFilter`).
- **Account/tenant disabled enforcement**: checked at login (`tenantUser`
  status, `tenant` status, `localUser.isActive()`), at every access-token
  request (`JwtAuthenticationFilter` checks `userDetails.isEnabled()`), and
  at refresh (mirrors login's three checks) — so disabling a user or
  suspending a tenant takes effect within one access-token lifetime (15 min)
  rather than surviving up to the full 30-day refresh window.

This is all deliberately scoped to **tenant** auth (`/api/auth/**`) only.

### Platform (KNOX staff) auth

`/api/platform/auth/login` still issues a single long-lived (24h) bearer JWT
with no refresh, no rotation, no cookie — the older, simpler model. This was
an explicit scope decision, not an oversight: platform staff are a much
smaller, internally-trusted user base than tenant end users, so the stronger
auth work was prioritized where it matters most. `Galaxy-client` (the KNOX
admin panel) stores this token in `localStorage`.

### Roles — per-tenant, not shared

`roles` is a table *inside every tenant schema*, not a shared enum or a
`public` table. Each tenant can rename, add, or delete roles to fit how they
actually run their team. `owner` is seeded with `is_system = TRUE` as a
marker that it must never be deleted or renamed (it's the one role every
tenant is guaranteed to have — provisioning and support tooling rely on it
existing) — enforcement is left for whenever a role-management CRUD endpoint
gets built; today the column just marks intent. `users.role_id` and
`role_permissions.role_id` are real FKs into this table.

## Migrations (Flyway)

Two independent Flyway setups, because schema-per-tenant means "one schema
per app" (Spring Boot's assumption) doesn't hold:

- **`db/migration/platform/`** — Spring Boot's auto-configured Flyway,
  targeting `knox` + `public`. Runs automatically at boot, before Hibernate's
  `ddl-auto=validate`.
- **`db/migration/tenant/`** — *not* auto-configured. `TenantMigrationService`
  builds a fresh `Flyway` instance per call, pointed at exactly one
  `tenant_<slug>` schema via `.schemas(schemaName)`. Flyway creates its own
  `flyway_schema_history` table **inside each tenant schema**, so every
  tenant independently and durably tracks which migrations it's had applied.
  That's what makes double-applying a migration to a schema structurally
  impossible, and "did every tenant get this change" an answerable query
  instead of something kept in a person's head.

Two call sites for the tenant runner:
- **New tenant**: `TenantProvisioningService.provision()` creates the empty
  schema, then runs the full tenant migration set against it — a new tenant
  starts on the same version every other tenant converges to, not a frozen
  snapshot file.
- **Existing tenants**: `TenantMigrationRunner` (an `ApplicationRunner`)
  loops every row in `knox.tenants` on boot and brings each schema current.
  `provisioning`-status tenants are skipped (that status means
  `TenantProvisioningService` is, or was mid-crash, already migrating that
  schema itself).

Adding a migration is now just: drop a new `V<n>__description.sql` file in
the right folder, restart. See [RUNNING.md](RUNNING.md#adding-a-schema-change-later).

## Package layout

```
com.knox.galaxy
├── config      — Spring Security, JWT, CORS, cookies, rate limiting
├── controller  — REST endpoints (see table below)
├── dto         — request/response shapes
├── model       — JPA entities
├── repository  — Spring Data repositories
├── service     — business logic
└── tenancy     — everything in "How a request gets bound to a tenant" above,
                  plus TenantProvisioningService / TenantMigrationService
```

## Route map

| Base path | Controller | Auth |
|---|---|---|
| `/api/auth/**` | `AuthController` | Tenant login/refresh/logout |
| `/api/users/**` | `UserController` | Tenant-scoped, requires tenant token |
| `/api/platform/auth/**` | `PlatformAuthController` | KNOX staff login |
| `/api/platform/clients/**` | `ClientController` | KNOX staff — Client Manager CRUD |
| `/api/platform/tenants/**` | `TenantAdminController` | KNOX staff — tenant provisioning |
| `/api/platform/plans/**` | `PlanController` | KNOX staff — KNOX agency + Galaxy plan catalogues |
| `/api/platform/settings/**` | `PlatformSettingsController` | KNOX staff — Client Manager dashboard settings |

## Outbound email

Gmail SMTP (`spring.mail.*`), used for one thing today: the welcome email a
new tenant owner gets from `TenantProvisioningService`, pointing them at
`galaxy.frontend.url` to log in. See `.env.example` for the app-password
setup gotcha (16 chars, no spaces — Google's UI display grouping is not the
real credential).
