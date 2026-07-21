# Running the backend

## Prerequisites

- **JDK 17**, with `JAVA_HOME` pointing at it. The Maven wrapper (`mvnw`/`mvnw.cmd`)
  uses `JAVA_HOME`, not whatever `java` resolves to on `PATH`.
- A **PostgreSQL** database you can reach and have DDL rights on. There is no
  bundled/local Postgres and no Docker Compose here — this connects to
  whatever `DB_URL` points at, local or remote (the live dev database is on
  RDS).

## 1. Configure

```
cp .env.example .env
```

Fill in `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` and `JWT_SECRET` (32+
random characters — this is the entire trust boundary between tenants and
between tenant/platform tokens; never ship the checked-in default anywhere
real). Everything else in `.env.example` has a documented default.

**Spring Boot does not read `.env` files by itself** — `application.properties`
opts into it explicitly via `spring.config.import=optional:file:.env`, so a
plain `.env` sitting next to `pom.xml` is picked up automatically by
`mvnw spring-boot:run`, no shell export needed.

## 2. First-time database setup

There is no manual SQL to run by hand anymore. On first boot against a
**genuinely empty** database, two independent things build the schema for
you:

1. **Platform schema** (`knox` + `public`) — Spring Boot's auto-configured
   Flyway runs `db/migration/platform/V1__baseline.sql` for real, creating
   `knox`, every control-plane table, and the shared `public` enum types.
2. **`galaxy` reference schema** — this one is *not* automatic. `galaxy` is
   kept purely as a live, human-browsable copy of "what a tenant schema looks
   like"; Hibernate's `ddl-auto=validate` checks every JPA entity against it
   at boot, before any tenant is bound. Create and stamp it once:

   ```sql
   CREATE SCHEMA galaxy;
   ```

   Then run the tenant migrations into it the same way a tenant gets built —
   either point a throwaway `POST /api/platform/tenants` call's schema at it
   temporarily, or simplest: run `db/migration/tenant/V1__init.sql` then
   `V2__seed.sql` by hand into `galaxy` once (`SET search_path TO galaxy,
   public;` first, since those files are deliberately unqualified). See
   [ARCHITECTURE.md](ARCHITECTURE.md#migrations-flyway) for why this schema
   exists and isn't managed the same way tenants are.

If you're pointing at the existing shared dev database instead of a fresh
one, none of this applies — `knox`, `public`, and `galaxy` already exist, and
Flyway's `baseline-on-migrate` recognizes the schema is already built and
tracks forward from there instead of re-running anything.

## 3. First KNOX staff login

Set `PLATFORM_BOOTSTRAP_EMAIL` and `PLATFORM_BOOTSTRAP_PASSWORD` in `.env`
before the very first boot. `PlatformAdminBootstrap` only ever creates an
account when `knox.platform_users` is empty, so it's safe to leave these set
across restarts — it will never touch an existing account — but clear them
once you actually have an admin, so a stale value in someone else's `.env`
can't create a second one. Without this, you'd need to insert a
`knox.platform_users` row (bcrypt password hash) by hand to log in at all.

## 4. Run

```
./mvnw.cmd spring-boot:run
```

Starts on `SERVER_PORT` (default `8080`; override with
`SERVER_PORT=8081 ./mvnw.cmd spring-boot:run` if something else owns 8080).

A clean boot log looks like, in order: Flyway validating/migrating `knox`,
Hibernate validating every entity against `galaxy`, the Spring Security
filter chain being assembled, Tomcat starting, then
`TenantMigrationRunner` looping every row in `knox.tenants` bringing each
schema current, then the platform-admin bootstrap check.

## Adding a schema change later

Never hand-edit `galaxy` or a tenant schema directly again. Add a new
`V<n>__description.sql` file to `db/migration/platform/` (control plane) or
`db/migration/tenant/` (business schema) and restart:

- Platform changes apply automatically on the next boot (auto-configured
  Flyway).
- Tenant changes apply automatically to **every** tenant on the next boot
  (`TenantMigrationRunner`) and to every **new** tenant from creation
  (`TenantProvisioningService` runs the full migration set).
- Remember to mirror structural changes into `galaxy` by hand too (see
  [ARCHITECTURE.md](ARCHITECTURE.md#the-galaxy-schema)) — it is not looped
  over by the tenant runner.

## Common gotchas

- **"relation does not exist" at boot** — almost always means `galaxy` is
  missing or behind; Hibernate validates against it, not against any tenant.
- **A new tenant fails to provision** — check the app log for which Flyway
  migration failed; `TenantProvisioningService.rollbackSchema` drops the
  half-built schema and the `knox.tenants` row automatically so a retry
  starts clean.
- **Email never arrives for a new tenant** — `MAIL_PASSWORD` must be a 16-char
  Google App Password with **no spaces**; Google's UI displays it grouped in
  4s for readability, but the real credential has none.
- **CORS rejected in the browser** — `galaxy.cors.allowed-origins` is an
  explicit allow-list (never `*`, since it's combined with credentialed
  cookies). Add your frontend's origin to `CORS_ALLOWED_ORIGINS` if you're
  running it somewhere other than `http://localhost:5173`.
