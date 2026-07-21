# Galaxy-Pico-Zen-backend

Spring Boot API for Galaxy: schema-per-tenant PostgreSQL multi-tenancy, JWT
auth, the KNOX control plane, and the KNOX Client Manager API. See the
[monorepo-root README](../README.md) for how this fits alongside
`Galaxy-client` and `Galaxy-frontend`.

- **[docs/RUNNING.md](docs/RUNNING.md)** — prerequisites, first-time database
  setup, running the app, common gotchas.
- **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** — schema-per-tenant model
  and how a request gets bound to a tenant, the auth architecture
  (access/refresh tokens, rotation, CSRF, rate limiting), per-tenant roles,
  the Flyway migration setup, package layout, and the route map.

Quick start:

```
cp .env.example .env   # fill in DB_URL / DB_USERNAME / DB_PASSWORD / JWT_SECRET
./mvnw.cmd spring-boot:run
```
