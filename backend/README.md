# Tournament Management Backend

This folder is the backend foundation for the Tournament Management System.

The product is at a very early startup stage, so the backend should begin with a small but future-ready core instead of sport-specific modules. The main idea is:

```text
Generic Tournament Engine
  + Sport Configuration
  + Dynamic Registration Forms
  + Scoring and Ranking Strategies
```

Current stack:

- Java 21
- Spring Boot 3.5.16
- Gradle
- PostgreSQL
- Spring Security with JWT
- Flyway
- JPA/Hibernate
- Bean Validation
- Docker Compose

Sprint 1 (identity, organization tree, JWT auth):

- `GET /api/v1/health`
- `POST /api/v1/auth/register` bootstrap endpoint for the current frontend signup flow
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/invite-accept`
- `GET /api/v1/auth/me`
- `POST /api/v1/users/invite`
- `POST /api/v1/organization-units`
- `GET /api/v1/organization-units`
- `GET /api/v1/organization-units/{id}`
- `GET /api/v1/organization-units/{id}/tree`
- `PATCH /api/v1/organization-units/{id}`
- `DELETE /api/v1/organization-units/{id}`

Sprint 2 (scoped RBAC):

- `POST /api/v1/users/{userId}/role-assignments`
- `GET /api/v1/users/{userId}/role-assignments`
- `DELETE /api/v1/users/{userId}/role-assignments/{assignmentId}`

Every endpoint above is now guarded by a fine-grained permission evaluated against scope
(`GLOBAL`, `ORGANIZATION`, `TOURNAMENT`, `COMPETITION`). An `ORGANIZATION` grant covers the whole
subtree beneath that unit. The permission catalog and the seven system roles are seeded by
`V4__seed_rbac.sql`; `POST /api/v1/auth/register` makes the registrant `TENANT_ADMIN` of the
organization unit it creates.

The canonical design source is now the root [`Arch & context documents`](../Arch%20&%20context%20documents) folder. Older backend-local architecture notes were removed to avoid contradictory docs.

## Run Locally

Prerequisites:

- **A JDK between 17 and 24** to run Gradle itself. Java 25 does **not** work — Gradle 8.14 rejects
  its class file version. Java 21 (Temurin LTS) is the recommended choice since it matches the
  project's compile toolchain.
- **Docker**, for the local database and for the Testcontainers integration tests.

Gradle itself does not need to be installed — use the wrapper (`./gradlew`), which pins the exact
version. If your machine has no Java 21, the foojay toolchain resolver in `settings.gradle`
downloads one for compilation automatically; only the JDK that *runs* Gradle must already exist.

Start PostgreSQL:

```bash
cd backend
docker compose up -d
```

Start the backend:

```bash
cd backend
./gradlew bootRun
```

Run with the `dev` profile to load a demo tenant tree (SAI → Haryana/Punjab → district units) with
one user per role, all using the password `StrongPass123`:

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=dev'
```

The seeded accounts and the tree they belong to are listed in
[`TEST_ACCOUNTS.md`](../TEST_ACCOUNTS.md).

Run the tests. The 44 integration tests start a throwaway PostgreSQL 16 container, so Docker must be
running; the 5 `JwtServiceTest` cases are pure unit tests and run without it:

```bash
cd backend
./gradlew test
```

The backend runs at:

```text
http://localhost:8080
```

Check health:

```bash
curl http://localhost:8080/api/v1/health
```

Create the first workspace and user:

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Samarth Gulia","email":"samarth@example.com","password":"StrongPass123","organizationName":"Techspo Infinity","organizationType":"PRIVATE_ORGANIZER"}'
```

## Frontend Connection

The Next.js frontend reads this environment variable:

```text
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
```

Copy the root `.env.local.example` to `.env.local` before running the frontend.
