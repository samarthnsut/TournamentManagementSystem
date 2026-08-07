# Tournament Management System — Project Context

Multi-tenant SaaS for running sports tournaments (federations, state/district associations,
academies, private organizers). Spring Boot modular monolith + PostgreSQL behind a statically
exported Next.js frontend.

## Layout

```
/                          Next.js 14 App Router frontend (the repo root IS the frontend)
├── app/                   routes
├── components/            React components; components/ui/ holds the primitives
├── lib/api/               typed fetch wrappers, one file per backend module
├── styles/globals.css     Tailwind + the .btn-gradient / .glass-panel / .text-gradient-brand classes
├── backend/               Spring Boot 3.5 / Java 21 / Gradle
├── Arch & context documents/   15 design docs — the canonical spec (see below)
├── TEST_ACCOUNTS.md       seeded dev logins
└── out/                   committed static export; do not hand-edit
```

## Running it

```bash
cd backend && docker compose up -d && ./gradlew bootRun --args='--spring.profiles.active=dev'
```

```bash
npm run dev
```

- Frontend <http://localhost:3000/TournamentManagementSystem>, backend <http://localhost:8080>.
- Needs `.env.local` (copy `.env.local.example`) with `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1`.
- Gradle must **run** on a JDK 17–24. **Java 25 breaks it** (Gradle 8.14 rejects the class file version).
- Docker is required for the DB and for the Testcontainers integration tests (`./gradlew test`).
- `npm run dev` writes to `.next-dev` via `NEXT_DIST_DIR`, deliberately — `next build` and `next dev`
  sharing `.next` used to blow up the running dev server (see the comment in `next.config.js`).
- Dev logins are in [TEST_ACCOUNTS.md](TEST_ACCOUNTS.md); `haryana.admin@example.com` /
  `StrongPass123` is the usual one. They only exist under the `dev` profile.

## Backend architecture

Package root `com.acme.tms`, one package per module: `identity`, `organization`, `tournament`,
`registration`, `fixture`, `result`, `workflow`, `common`. Inside each: `api/` (controllers),
`service/`, `domain/` (JPA entities), `dto/` (records), `repository/`.

- **Multi-tenancy** is a shared schema scoped by `OrganizationUnit`, which is a tree
  (federation → state → district). There is no `Tenant` entity.
- **RBAC** is scoped, IAM-style. Controllers carry
  `@RequiresPermission(value = "tournament:create", scope = ScopeType.ORGANIZATION, scopeIdParam = "request.organizationUnitId")`;
  `PermissionAspect` + `ScopeEvaluator` resolve it. Scopes: `GLOBAL`, `ORGANIZATION`, `TOURNAMENT`,
  `COMPETITION`. An `ORGANIZATION` grant covers the entire subtree below that unit. The permission
  catalog and the seven system roles are seeded in `V4__seed_rbac.sql`.
  `scopeIdParam` is SpEL over parameter names — that is why `-parameters` is set in `build.gradle`.
- **Auth** is JWT (access + refresh), 15 min / 30 day TTLs. `POST /api/v1/auth/register` is a
  *bootstrap* endpoint: it creates a new organization and makes the registrant its `TENANT_ADMIN`.
  There is no athlete self-registration path yet.
- **Sport configuration** is a JSONB blob validated against a JSON Schema, driving Strategy-pattern
  factories (`FixtureGeneratorFactory`, `ResultEvaluatorFactory`, `LeaderboardStrategyFactory`).
  Adding a sport must never mean adding an `if` on sport type.
- **Migrations** are Flyway, `backend/src/main/resources/db/migration`, `ddl-auto: validate`.
  Schema changes go in a new `V{n}__*.sql` — never edit an applied migration.
- **Errors** are RFC-7807 problem+json from `GlobalExceptionHandler`; the frontend `ApiError` mirrors
  that shape.

## Frontend conventions

- `output: 'export'` with `basePath: '/TournamentManagementSystem'` — it deploys to GitHub Pages.
  **Consequences to respect:** no server components doing data fetching, no route handlers, no
  middleware, and **no dynamic path segments for runtime ids**. Detail pages read the id from the
  query string (`/dashboard/tournament?id=…`) and wrap it in `<Suspense>` because `useSearchParams`
  demands it. Follow that pattern rather than adding `[id]` routes.
- Every call goes through `lib/api/client.ts` so the bearer token, the error shape, and 401 handling
  stay in one place. Pass `auth: false` for public endpoints.
- Session lives in `localStorage` under `tms_auth`; `lib/api/session.ts` owns it and fires a
  `tms:auth-changed` event so the current tab notices sign-in, not just other tabs.
- `useAuth()` exposes `can(permission)`, which **falls open** when permissions are unknown — the API
  is the real authority, so hiding controls on a failed `/auth/me` would just make the app look
  broken. Never treat `can()` or `RequireAuth` as a security boundary.
- Styling is Tailwind only, dark theme, gradient accents. Colors come from the `dark.*` / `accent.*`
  tokens in `tailwind.config.js` — use those, not raw hex.
- Reach for `components/ui/*` (`Button`, `Input`, `Card`, `Badge`, `Select`, `StatusBadge`) before
  writing new markup.

## The design docs

`Arch & context documents/` is the canonical spec — `ARCHITECTURE_BRIEF.md` is frozen and every
other doc conforms to its entity names and enums. Most useful by task:

| Working on | Read |
|---|---|
| entities, invariants | `02_DOMAIN_MODEL.md` |
| schema, DDL | `04_DATABASE_DESIGN.md` |
| permissions, org tree | `05_RBAC_AND_ORGANIZATION.md` |
| sports, fixtures, leaderboards | `06_SPORT_CONFIGURATION_ENGINE.md` |
| approvals | `07_APPROVAL_WORKFLOW_ENGINE.md` |
| endpoints, payloads | `08_API_CONTRACTS.md` |
| sprint scope + DoD | `10_DEVELOPMENT_ROADMAP.md` |

House rule from the docs: a PR that changes a decision, entity, enum, endpoint, or the schema updates
the affected doc **in the same PR**. ADRs in `14_ARCHITECTURAL_DECISIONS.md` are append-only —
supersede, never rewrite.

## Where the build actually is

Sprints 1–5 are built: identity + org tree + JWT, scoped RBAC, sport config engine +
tournament/competition lifecycle + public `/t/{slug}` pages, dynamic registration forms + entries,
and the tenant-configurable approval workflow with an inbox. The frontend is wired to all of it.

Sprint 6 (fixtures, matches, results, leaderboards) is built: round-robin and direct-final
generation, the match lifecycle, `POINTS`/`TIME` evaluators, and `POINTS_TABLE`/`LOWEST_TIME`
boards recomputed transactionally with each result. The organizer UI is wired to all of it —
`components/competition/` holds the fixtures panel, the result-entry form and the standings table,
mounted on the competition page once a competition leaves `OPEN`.

The public `/t/{slug}` pages now show fixtures and standings too, via the anonymous routes
`/api/v1/public/t/{slug}/competitions/{id}/{fixtures,leaderboard}` (08 §13A). They take the slug
**and** the competition id and check the pairing in `PublicAccessService` — a competition id on its
own must never reach an unpublished tournament — and every failure is a 404, never a 403. The
public fixture payload is narrower than the organizer's on purpose (no venue, version, fixture id
or seed); the leaderboard is the same read, so the two boards cannot drift.

The result-entry UI dispatches on the competition's `resultEvaluator` / `fixtureGenerator` keys,
never on `sportCode` — that is what makes one form serve a football scoreline and an eight-lane
sprint. Keep it that way.

Leaderboards are materialized in `leaderboard_entry` and rewritten wholesale on every result; the
Redis read-through in doc 03 is not built and is not needed for correctness. `ScopeType.MATCH` is a
resolve-only scope (ADR-015) — never grant it.

Sprint 7 is under way. The **audit trail is built**: `@Audited` on a mutating service method (the
annotation lives in `common/audit` so no module depends on `audit`), an AOP aspect that writes a
row inside the caller's transaction, and per-module `AuditSnapshotProvider`s supplying the
before/after state. `AuditCoverageTest` fails the build if a mutating service method is neither
annotated nor on a short, justified exemption list — so a new mutation cannot silently escape the
trail.

Two rules about snapshot providers that are easy to get wrong and are enforced by nothing but
review, so read them before writing one (ADR-017): a provider **must not throw** — an exception from
a nested `@Transactional` call marks the caller's transaction rollback-only *before* the aspect can
catch it, taking down the operation being recorded — and it **must not read the caller or filter by
permission**, or the same action recorded by two actors produces two different histories.

The **document module** is built too: two-phase presigned upload (08 §14), MinIO locally via
`docker compose`, real S3 in production — only `app.storage.endpoint` differs. Bytes never pass
through the app. Two rules it enforces that are easy to lose: the object key is composed by the
server and namespaced by organization unit (a client-supplied key is a path traversal), and the
mime/size allow-list is re-checked against the *stored object* at attach, because a presigned PUT
signs the content type but not the length. `AttachableEntityResolver` is both the owner lookup and
the allow-list of what a file may hang off.

Every backend module now has a screen. `/dashboard/settings/*` holds people & roles, venues, sport
configurations and approval workflows; `/dashboard/audit` is the trail viewer; `DocumentPanel` is a
drop-in attachment list mounted on the tournament page. Two backend gaps had to be closed first —
venues had a table and an entity but no controller *and* no permissions in the catalog (V14 seeds
`venue:*`), and there was no way to list users, only to invite one.

`ScopeType.VENUE` joins `MATCH` as a resolve-only scope (ADR-015): the endpoint is addressed by
venue id, the authority comes from the owning unit, and the role tables still refuse both as grants.

Still to come in Sprint 7: rate limiting, request-size limits, security headers, and the
dependency/container scans.

### Docs that describe intent, not the code

`13_CODING_STANDARDS.md` is partly aspirational. The code does **not** currently use Lombok,
MapStruct, `mapper/` packages, Spotless, Checkstyle, or ArchUnit — controllers use plain constructor
injection and services return DTOs directly. Match the surrounding code, not the doc, unless you are
deliberately introducing the tooling.

## Conventions

- Commits: `type(scope): lowercase summary` — `feat(backend):`, `fix(web):`, `docs:`, `ci:`.
- Branches: `feat/**`, `fix/**`; both trigger backend CI.
- CI: `.github/workflows/backend.yml` runs `./gradlew build` on JDK 21 for `backend/**` changes;
  `nextjs.yml` builds and deploys the site to GitHub Pages from `main`.
- Comments in this codebase explain *why* something is the way it is, not what the line does. Keep
  that register.
