# 14 — Architectural Decision Records

| | |
|---|---|
| **Version** | 1.0 |
| **Status** | Approved |
| **Date** | 2026-07-26 |
| **Owner** | Samarth |
| **Depends on** | ARCHITECTURE_BRIEF.md (frozen), 02–09 |

ADR log for the Tournament Management Platform. Format per ADR: Context / Decision / Alternatives considered / Consequences / Status. Superseding an ADR requires a new ADR that references the old one; ADRs are never edited retroactively.

## Index

| ADR | Title | Status |
|---|---|---|
| ADR-001 | Modular monolith over microservices | Accepted |
| ADR-002 | PostgreSQL shared-schema multi-tenancy | Accepted |
| ADR-003 | OrganizationUnit hierarchy over flat tenant | Accepted |
| ADR-004 | Tournament → Competition naming | Accepted |
| ADR-005 | Polymorphic Participant | Accepted |
| ADR-006 | Dynamic forms (JSONB) in MVP | Accepted |
| ADR-007 | Simple Registration status + separate Approval Workflow engine | Accepted |
| ADR-008 | JSON-based SportConfiguration + Strategy pattern | Accepted |
| ADR-009 | Scoped RBAC (IAM-style) over plain roles | Accepted |
| ADR-010 | Slug-based public URLs | Accepted |
| ADR-011 | UUID v7 primary keys | Accepted |
| ADR-012 | JWT stateless auth | Accepted |
| ADR-013 | Flyway for schema migrations | Accepted |
| ADR-014 | Audit via AOP interceptor writing JSONB before/after | Accepted |
| ADR-015 | MATCH as a resolve-only scope type | Accepted |
| ADR-016 | Sport-neutral leaderboard metric names | Accepted |
| ADR-017 | Unpartitioned audit_log, and snapshot providers that cannot throw | Accepted |

Decision clusters worth reading together: tenancy (002, 003, 009), domain shape (004, 005, 006, 007, 008), platform plumbing (001, 011, 012, 013, 014), fixtures and results (008, 015, 016), audit (014, 017).

---

## ADR-001 — Modular monolith over microservices

**Context.** V1 is built by a solo developer with AI assistance. The domain (identity, organization, tournaments, registrations, fixtures, results, workflow, documents, audit) is broad but the team is minimal, deployment must stay simple, and domain boundaries are still being validated against real tenants.

**Decision.** Single Spring Boot 3 deployable, structured as a modular monolith: modules `identity, organization, tournament, registration, fixture, result, workflow, document, audit, common` under `com.acme.tms.*`, with module boundaries enforced by ArchUnit (services-only cross-module calls, no cycles) and cross-module signaling via after-commit application events.

**Alternatives considered.**
- *Microservices per domain:* operational cost (service discovery, distributed tracing, per-service CI/CD, distributed transactions across Registration↔Workflow) is unjustifiable for one developer; wrong boundaries would be frozen into network contracts before the domain is proven.
- *Unstructured monolith:* fastest today, but every consulting-grade horror story starts here; no enforced seams means no future extraction path.

**Consequences.** One artifact to build, test, deploy, and debug; in-process calls keep approval/registration flows transactional and fast. Module seams + event-based signaling preserve a realistic extraction path (workflow and document are the likeliest first candidates) if scale demands it. Cost: discipline is required to keep boundaries honest — hence ArchUnit gates in CI. Scaling is whole-app horizontal scaling behind a load balancer (stateless by ADR-012).

**Status.** Accepted — 2026-07-26.

---

## ADR-002 — PostgreSQL shared-schema multi-tenancy over schema-per-tenant / DB-per-tenant

**Context.** Tenants range from SAI-scale federations to single clubs; expected tenant count is high, per-tenant data volume mostly modest. Solo-dev operations must stay simple; Flyway migrations must not multiply per tenant.

**Decision.** One PostgreSQL 16 database, one schema. Every tenant-owned row carries `organization_unit_id`; isolation is enforced by service-layer scope checks plus a Hibernate filter, with the tenant being the root `OrganizationUnit`.

**Alternatives considered.**
- *Schema-per-tenant:* stronger isolation, but migrations run N times, connection-pool/schema routing complexity, cross-tenant platform queries (SUPER_ADMIN views, platform analytics) become painful; onboarding a tenant becomes a DDL operation.
- *Database-per-tenant:* maximum isolation and per-tenant tuning, but operationally absurd at hundreds of small tenants for a one-person team.
- *Postgres Row-Level Security:* attractive defense-in-depth; deferred, not rejected — the `organization_unit_id` column strategy is RLS-compatible and RLS can be layered on later (tracked in 11_FUTURE_ENHANCEMENTS).

**Consequences.** One migration path, trivial tenant onboarding (insert a root OrganizationUnit), easy platform-wide queries. Risk concentrates in application-level isolation: a missed scope check is a cross-tenant leak — mitigated by the mandatory Hibernate filter, `@RequiresPermission` + `ScopeEvaluator` on every endpoint, and the table-driven authz matrix test (13 §10). Noisy-neighbor risk accepted for V1; the largest tenants can be revisited under a hybrid model later.

**Status.** Accepted — 2026-07-26.

---

## ADR-003 — OrganizationUnit hierarchy over flat tenant

**Context.** Real customers are hierarchical: SAI → state associations → district associations; colleges under universities; academies with branches. Tournaments are organized at any level, and officials at a level need visibility over their subtree.

**Decision.** Self-referencing `OrganizationUnit` tree (`id, parentOrganizationUnitId, name, slug, type, status`; types `FEDERATION, STATE_ASSOCIATION, DISTRICT_ASSOCIATION, ACADEMY, COLLEGE, CLUB, PRIVATE_ORGANIZER`). The root node (parent = null) is the tenant; every scoped entity references the OrganizationUnit that owns it, and ORGANIZATION-scoped access covers the whole subtree.

**Alternatives considered.**
- *Flat tenant + free-form "department" field:* cannot express delegation (Haryana admin manages Haryana + districts, not Punjab) without reinventing a tree in RBAC anyway.
- *Fixed three-level model (federation/state/district):* matches SAI but breaks for clubs, colleges and private organizers who are one- or two-level.

**Consequences.** One model covers a national federation and a single club (tree of depth 1). Delegated administration falls out of scope resolution naturally. Cost: subtree queries (recursive CTE) on hot paths need caching (Redis-cached subtree closure, invalidated on tree changes); tree mutations (move subtree) need cycle guards. RBAC (ADR-009) is designed against this tree.

**Status.** Accepted — 2026-07-26.

---

## ADR-004 — Tournament → Competition naming

**Context.** A real-world event like "Khelo India 2027" contains many contests ("Football U16", "100m Race"). Early drafts used "Event", which collides with domain events, calendar events, and analytics events, and "TournamentEvent", which is clumsy and ambiguous.

**Decision.** The container is `Tournament`; each contest within it is `Competition`. These names are frozen — code, DB (`tournament`, `competition` tables), APIs (`/tournaments/{id}/competitions`) and docs use them exclusively.

**Alternatives considered.** *Event / TournamentEvent:* overloaded terminology causing real confusion in code search and conversation. *Championship/Discipline (sport-federation vocabulary):* precise for federations but alien to colleges and private organizers.

**Consequences.** Unambiguous vocabulary across docs, schema, code, and UI. Sport configuration, registration forms, fixtures, and leaderboards all attach at the Competition level, which matches reality (one tournament mixes team round-robins and timed athletics finals). Minor mismatch with some customers' vocabulary ("event") is handled in UI copy/i18n, never in identifiers.

**Status.** Accepted — 2026-07-26.

---

## ADR-005 — Polymorphic Participant

**Context.** Depending on the competition, the registering party is an individual (100m runner), a team (football squad), or an organization (a district entering an overall championship). Registrations, fixtures, matches, results and leaderboards must treat all three uniformly.

**Decision.** Single `Participant` entity with `participantType ∈ {INDIVIDUAL, TEAM, ORGANIZATION}`; team composition via the `TeamMember` join entity. `Registration`, `MatchParticipant`, `Result` and `LeaderboardEntry` reference `Participant` regardless of type. `SportConfiguration.participantType` declares what a competition accepts, enforced at registration time.

**Alternatives considered.**
- *Separate IndividualEntry / TeamEntry / OrgEntry entities:* triples every downstream relationship (three FK variants on Match, Result, Leaderboard) and forces `switch(type)` through the engine — exactly what the strategy architecture forbids.
- *JPA inheritance hierarchy (joined/single-table subclasses):* heavier ORM machinery for little gain; the varying part is small and type-specific data largely lives in dynamic-form answers anyway.

**Consequences.** Fixture generators and leaderboard strategies are written once against `Participant`. Type-specific validation (roster rules for TEAM via `TeamMember` + `SportConfiguration.rules`) concentrates at registration. Cost: some fields are meaningful only for some types — guarded by service-level validation rather than DB constraints; acceptable for V1.

**Status.** Accepted — 2026-07-26.

---

## ADR-006 — Dynamic forms (JSONB) in MVP

**Context.** Every organizer wants different registration data (age proofs, jersey numbers, coach contacts, medical declarations). Hard-coding fields means a schema migration per customer request — untenable for a white-label SaaS. Original phasing pushed dynamic forms to phase 4; the architecture review pulled them into MVP because retrofitting them would rewrite the registration module.

**Decision.** `RegistrationFormDefinition` stores a versioned JSON schema per Competition; `RegistrationResponse` stores JSONB answers linked to a Registration and pinned to the definition version it answered. Publishing a definition freezes it; edits create version n+1. Server validates answers against the pinned version.

**Alternatives considered.**
- *Fixed field set + "custom fields" bag:* the bag becomes the real system within months, minus versioning and validation.
- *EAV tables:* queryable but miserable — reconstruction joins, weak typing, poor performance; Postgres JSONB gives storage + indexing (GIN when needed) without EAV pain.
- *Defer to phase 4:* rejected; the Registration aggregate, approval inbox rendering, and API contracts all depend on the form model existing.

**Consequences.** Organizers self-serve form changes with zero deployments. Versioning guarantees old submissions render/validate against the schema they answered (a hard correctness requirement, tested first per roadmap Sprint 4). Costs: answers are opaque to SQL-level constraints (validation is application-side), and ad-hoc reporting over answers needs JSONB queries — accepted for MVP, reporting tooling deferred to 11.

**Status.** Accepted — 2026-07-26.

---

## ADR-007 — Simple Registration status + separate configurable Approval Workflow engine

**Context.** SAI-style tenants need multi-level approvals (district → state → federation); a private club approves in one click. Encoding levels into the registration status (`PENDING_L1`, `PENDING_L2`, …) couples every tenant's process into one enum and changes with every new tenant configuration.

**Decision.** `Registration.status` stays `PENDING, APPROVED, REJECTED, WITHDRAWN`. All approval-process complexity lives in a separate tenant-configurable engine: `ApprovalWorkflow` (per OrganizationUnit + entityType), `ApprovalStep` (level, roleCode, approvalRequired), `ApprovalInstance` (currentLevel, status ∈ `IN_PROGRESS, APPROVED, REJECTED, CANCELLED`), `ApprovalAction` (audit of each decision). "Pending at level 2" is workflow state, never registration status. Terminal instance state drives the registration status transition.

**Alternatives considered.**
- *Extended status enum:* combinatorial explosion, migration per process change, leaks one tenant's process into all tenants' code.
- *Embedded BPMN engine (Camunda/Flowable):* powerful but massive dependency for linear-level approvals; steep operational and learning cost for a solo dev.
- *Hard-coded two-level approval:* fails the "1 level or 3 levels with zero code changes" requirement immediately.

**Consequences.** Registration consumers (fixtures, participant lists, public counts) read one simple enum. Tenants configure depth per entity type without deployments; the engine is generic (`entityType` + `entityId`) and reusable for future approvable entities (venue requests, result disputes). Costs: two coordinated state machines (instance ↔ registration) require careful transactional wiring and concurrency control (optimistic locking, roadmap Sprint 5); an inbox query layer must join workflow state to role/scope.

**Status.** Accepted — 2026-07-26.

---

## ADR-008 — JSON-based SportConfiguration + Strategy pattern

**Context.** Launch sports are Football (TEAM/ROUND_ROBIN/POINTS) and Athletics-100m (INDIVIDUAL/NONE/TIME); Chess (SWISS) must be addable without touching core code. Sport-conditional logic (`if sport == FOOTBALL`) metastasizes and makes every new sport a core change.

**Decision.** `SportConfiguration` holds a JSONB config `{ sport, participantType, fixtureGenerator, resultEvaluator, leaderboardStrategy, rules{} }`. Behavior is resolved at runtime via `FixtureGeneratorFactory`, `ResultEvaluatorFactory`, `LeaderboardStrategyFactory` over closed strategy-key enums (`ROUND_ROBIN, SINGLE_ELIMINATION, DOUBLE_ELIMINATION, SWISS, NONE`; `POINTS, WIN_LOSS, TIME, DISTANCE, SCORE`; `POINTS_TABLE, LOWEST_TIME, HIGHEST_DISTANCE, HIGHEST_SCORE, BRACKET`). Sport conditionals outside the factories are banned (code-review rule 13 §13.7). Sport-specific tunables (points-per-win, roster size, tie-breakers) live in `rules{}` and are interpreted by the selected strategies.

**Alternatives considered.**
- *Subclass-per-sport (FootballCompetition…):* new sport = new code + schema churn; combinatorial with formats.
- *Fully scriptable rules engine (embedded scripting/DSL):* maximum flexibility, but a security/sandboxing and debuggability swamp; unjustified when strategy keys cover the launch matrix.
- *Hard-coded per-sport services:* fastest for two sports, fails the Chess test by definition.

**Consequences.** New sport = new `SportConfiguration` row (existing strategies) or one new strategy class + factory registration — no core edits, proven by the fake-SWISS dispatch test (roadmap Sprint 6). Config validation must be rigorous at save time (unknown keys fail fast) since JSONB bypasses schema checks. `rules{}` interpretation contracts must be documented per strategy in 06 to avoid silent misconfiguration.

**Status.** Accepted — 2026-07-26.

---

## ADR-009 — Scoped RBAC (IAM-style) over plain roles

**Context.** "Admin" is meaningless without asking *of what*: the Haryana admin must manage Haryana and its districts but never Punjab; a competition official records results for one competition only. Plain global roles cannot express this; per-resource ACLs don't scale administratively.

**Decision.** Role assignments carry scope: `UserRoleAssignment { userId, roleId, scopeType, scopeId }`, `scopeType ∈ {GLOBAL, ORGANIZATION, TOURNAMENT, COMPETITION}`; ORGANIZATION scope covers the whole OrganizationUnit subtree. Roles bundle fine-grained permission strings (`tournament:create`, `registration:approve`) via `Role`/`Permission`/`RolePermission`. Seed roles: `SUPER_ADMIN (GLOBAL), TENANT_ADMIN (ORGANIZATION), ORG_OFFICIAL (ORGANIZATION), TOURNAMENT_ADMIN (TOURNAMENT), COMPETITION_OFFICIAL (COMPETITION), PARTICIPANT_USER, PUBLIC_VIEWER`. Enforcement: `@RequiresPermission` + `ScopeEvaluator` resolving scope against the org tree.

**Alternatives considered.**
- *Plain role-per-user:* cannot bind authority to a subtree/tournament; ends in tenant-forked role lists.
- *Per-object ACLs (Spring ACL):* precise but administratively unmanageable at federation scale; row explosion.
- *External policy engine (OPA/ReBAC à la Zanzibar):* the right shape at huge scale, operational overkill for V1; the assignment model maps onto ReBAC later if needed.

**Consequences.** One assignment grants a coherent slice of authority; delegation matches the org hierarchy (ADR-003) by construction. Same user can hold different roles in different scopes. Costs: every request performs permission + scope resolution — subtree results are Redis-cached with invalidation on tree change; the permission catalog needs governance (single seeded source, doc-first additions per 13). Authorization correctness is guarded by the table-driven role × endpoint × scope matrix test.

**Status.** Accepted — 2026-07-26.

---

## ADR-010 — Slug-based public URLs

**Context.** Tournaments are promoted on posters, WhatsApp and social media; spectators and participants arrive without accounts. URLs must be human-readable, dictatable, and stable, and must not leak internal identifiers.

**Decision.** Public pages live at `/t/{tournament-slug}` (e.g. `/t/haryana-games-2027`). Slugs are unique platform-wide, generated from the tournament name with collision suffixing, editable while `DRAFT`, and **immutable after publish** (DB uniqueness + service guard). Public APIs resolve by slug; admin APIs use UUIDs.

**Alternatives considered.**
- *UUID URLs:* stable and trivial but hostile on a poster and useless for SEO.
- *Tenant-scoped slugs (`/{org}/{slug}`):* allows duplicate tournament slugs across tenants but produces longer URLs and couples URLs to the org tree, which can be restructured; platform-wide uniqueness is cheap at realistic volumes.
- *Mutable slugs with redirect history:* nicer for typo fixes but adds a redirect table and cache complexity; immutability after publish is simpler and protects shared links — rename remains possible via display name.

**Consequences.** Marketing-friendly, memorable URLs; public read endpoints are cacheable by slug. Slug becomes a public contract — hence the publish-time freeze, surfaced explicitly in the creation wizard (12 §2.5). Custom tenant domains later (11) prefix these paths without changing them.

**Status.** Accepted — 2026-07-26.

---

## ADR-011 — UUID v7 primary keys

**Context.** Keys must be safe to expose in APIs, generatable app-side (batch inserts, offline fixture generation), and non-enumerable across tenants. Random UUID v4 fragments B-tree indexes; bigserial leaks volume and ordering and complicates any future sharding or data merge.

**Decision.** UUID v7 (time-ordered) primary keys on all entities, generated in the application (`common` generator), stored as native Postgres `uuid`.

**Alternatives considered.** *bigserial:* smallest and fastest but enumerable (competitor scraping, IDOR aids) and DB-coupled generation. *UUID v4:* non-enumerable but random insert points cause index bloat/page splits at scale. *ULID/KSUID/Snowflake:* equivalent time-ordered properties but non-standard types or extra infrastructure; UUID v7 is standardized (RFC 9562) and fits the `uuid` column type natively.

**Consequences.** Index-friendly monotonic-ish inserts, app-side generation, safe public exposure. 16 bytes per key/FK (accepted). Timestamp bits leak coarse creation time — acceptable for this domain. Keys sort roughly by creation, which is convenient but **cursor pagination still orders by explicit columns**, never by ID as a semantic timestamp.

**Status.** Accepted — 2026-07-26.

---

## ADR-012 — JWT stateless auth

**Context.** One SPA + public pages, horizontally scaled stateless app nodes (ADR-001), Redis available. Auth must carry enough identity to drive scoped RBAC without a DB hit per request for the common case.

**Decision.** Spring Security with JWT: short-lived access token (~15 min; claims: `sub`, session id, minimal identity) + longer-lived refresh token with rotation, refresh state and revocation list kept in Redis. Logout/suspension revokes refresh tokens; access tokens die by expiry. Permissions/scopes are **not** baked into the access token — they are resolved server-side (Redis-cached) so role changes apply within minutes, not token lifetime.

**Alternatives considered.**
- *Server-side sessions (sticky or Redis-backed):* fine for one app, but couples every request to session storage and complicates future non-browser clients (federation integrations, mobile).
- *Opaque tokens + introspection:* strong revocation but adds an introspection hop per request — effectively sessions with extra steps at this scale.
- *Full OIDC provider (Keycloak/Auth0):* likely future direction for SSO tenants (see 11), but heavy to operate/pay for on day one; the JWT contract is designed to be replaceable by an OIDC issuer later.

**Consequences.** Stateless request path, trivial horizontal scaling, standard `Authorization: Bearer` contract. Revocation latency bounded by access-token TTL (≤15 min) — acceptable given server-side permission resolution already handles authority changes. Refresh rotation + reuse detection in Redis mitigates token theft. Costs: careful clock/expiry handling and the usual JWT discipline (strong signing key management, no sensitive claims).

**Status.** Accepted — 2026-07-26.

---

## ADR-013 — Flyway for schema migrations

**Context.** Shared-schema multi-tenancy (ADR-002) makes the single schema the most critical shared asset; its evolution must be versioned, reviewable, repeatable across dev/CI/prod, and safe for rolling deploys. Hibernate `ddl-auto` is unacceptable outside throwaway prototypes.

**Decision.** Flyway with versioned SQL migrations (`V<n>__desc.sql`, immutable once merged — rules in 13 §12), executed automatically on application startup/deploy, checksum-validated. Repeatable `R__` migrations only for idempotent reference data. Testcontainers-based tests run the full migration chain on every CI build.

**Alternatives considered.** *Liquibase:* comparable capability, XML/YAML changelog abstraction and rollback support we don't need — plain SQL is more transparent and AI-assistant-friendly. *Hibernate ddl-auto:* non-deterministic, no history, destructive surprises. *Manual SQL runbooks:* human-dependent ordering — precisely the failure mode migrations exist to remove.

**Consequences.** Schema state is a pure function of the migration chain; every environment converges identically; CI proves the chain from empty DB on every build. Immutability forces fix-forward discipline and expand→migrate→contract for breaking changes (compatible with rolling deploys). Rollbacks are handled by forward-fixes plus backups, not down-migrations — accepted trade-off.

**Status.** Accepted — 2026-07-26.

---

## ADR-014 — Audit via AOP interceptor writing JSONB before/after

**Context.** Federations and government bodies require answerable history: who approved which registration, who edited a result, from where. Audit is MVP, day one (brief §10). Scattering manual `auditService.log(...)` calls guarantees gaps; DB triggers capture rows but lose the acting user and business action.

**Decision.** Service-layer AOP interceptor (annotation-driven pointcut on mutating service methods) writes `AuditLog { actorId, action, entityType, entityId, beforeState (JSONB), afterState (JSONB), organizationUnitId, ipAddress, timestamp }`. Before/after states are DTO-level snapshots (not raw entities, avoiding lazy-loading traps), serialized to JSONB. Actor/IP/correlation come from MDC/security context. Audit rows are append-only — no update/delete path exists in code; coverage is enforced by an architecture test asserting every mutating service method is intercepted.

**Alternatives considered.**
- *DB triggers / CDC (Debezium):* complete at the row level but blind to actor and business intent ("approve registration" vs. three row updates); CDC infra is post-MVP overkill.
- *Hibernate Envers:* entity versioning, but per-entity `_AUD` table sprawl, awkward cross-entity action semantics, and tight ORM coupling.
- *Manual audit calls per service method:* precise wording per action but historically the most gap-prone approach; forbidden as the primary mechanism (allowed only to enrich specific actions).
- *Event-sourcing:* audit for free but wholesale architectural change unjustified by requirements.

**Consequences.** Uniform, hard-to-forget audit coverage with business-level actions and structured before/after diffs queryable via JSONB. Costs: one extra JSONB write per mutation in-transaction (accepted: an audit row without its mutation, or vice versa, is worse than the latency); snapshot serialization must exclude sensitive fields (password hashes, token material) via an explicit exclusion list; `audit_log` growth requires time-based partitioning/archival before it becomes a problem (tracked in 11).

**Status.** Accepted — 2026-07-26.

---

## ADR-015 — MATCH as a resolve-only scope type

**Context.** Doc 08 §12 addresses match operations flat — `POST /matches/{matchId}/result`, `/schedule`, `/start` — while authority over a match comes from its competition. `@RequiresPermission` resolves its scope id from a request parameter before the method runs, so a match-id path variable cannot be checked against `ScopeType.COMPETITION`: the ids are different entities. The four scopes in doc 05 §3 had no way to express "addressed by match, governed by competition".

**Decision.** Add `MATCH` to `ScopeType` as a **target only, never a grant**. `MatchScopeResolver` implements `ScopeOwnershipResolver` and reports the match's owning organization unit plus its competition and tournament as parent scopes, so an existing COMPETITION, TOURNAMENT or ORGANIZATION grant satisfies a MATCH target with no new role assignments and no change to the seeded catalog. The `ck_ura_scope_type` and `ck_role_default_scope_type` check constraints are deliberately left listing only the original four, so the database refuses a MATCH-scoped grant outright.

**Alternatives considered.**
- *Nest every match endpoint under its competition* (`/competitions/{id}/matches/{matchId}/result`): satisfies the existing scopes but contradicts doc 08's flat item-operation convention and makes every client carry a competition id it does not otherwise need.
- *Resolve the competition inside the controller and check manually:* moves authorization out of the annotation and back into method bodies — exactly what `PermissionAspect` exists to prevent, and unenforceable by the authz matrix test.
- *Reuse COMPETITION and pass the competition id in the body:* the id would then be caller-supplied and unverified against the match, which is a privilege-escalation hole.

**Consequences.** Match endpoints get the same declarative check as everything else, and no role, seed or migration changes. The enum now contains a value that is legal as a target and illegal as a grant — an asymmetry the enum itself documents, and which the DB constraints enforce rather than trusting convention. Future entity-addressed-but-inherited scopes (a result correction, say) follow the same pattern.

**Status.** Accepted — 2026-08-07.

---

## ADR-016 — Sport-neutral leaderboard metric names

**Context.** Doc 08 §13.1 sketches the points-table response with `goalDifference`. `PointsTableLeaderboard` is the same class that ranks a football league, a Swiss chess event and any future league sport (ADR-008); baking football's vocabulary into its output would put a sport-specific concept in the one place the strategy pattern exists to keep sport-free — and the grep gate added in Sprint 6 would flag the literal anyway.

**Decision.** Emitted metrics are neutral: `played`, `won`, `drawn`, `lost`, `points`, `scoreFor`, `scoreAgainst`, `scoreDifference`. Tenants keep their own vocabulary in configuration — `rules.tiebreakers` accepts `GOAL_DIFFERENCE` and `GOALS_FOR` as aliases of `SCORE_DIFFERENCE` and `SCORE_FOR`, resolving to the same comparator — so a football organizer configures the table in football's words while the engine learns none of them. Presentation labels ("GD", "GF") belong to the frontend, which already knows which sport it is rendering.

**Alternatives considered.**
- *Follow doc 08 literally:* one sport's nouns in a shared strategy, and a second sport would either inherit nonsense keys or force a per-sport branch.
- *Per-sport metric key maps in configuration:* solves naming but adds a schema surface and a failure mode (a typo silently renaming a column) for what is a display concern.
- *Emit both neutral and aliased keys:* doubles the payload and leaves clients guessing which is canonical.

**Consequences.** `metrics` is strategy-shaped and clients render what they are given rather than assuming a sport, which is what lets `LOWEST_TIME` (`bestValue`, `unit`, `attempts`) and `POINTS_TABLE` share one response type. Doc 08 §13.1's example payload is superseded by this ADR. An unrecognized tiebreaker name ranks nobody rather than throwing, so a typo in a tenant's config cannot take a live standings page down mid-tournament.

**Status.** Accepted — 2026-08-07.

---

## ADR-017 — Unpartitioned `audit_log`, and snapshot providers that cannot throw

**Context.** ADR-014 fixed the mechanism (service-layer AOP, JSONB before/after). Building it surfaced two things that document 04's table spec and the ADR's sketch did not settle.

**Decision.**

*The table is not partitioned in V1.* Doc 04 describes `audit_log` as partitioned with a composite `(id, timestamp)` primary key. ADR-014's own consequences already defer time-based partitioning and archival to doc 11, and a partitioned table with no partition-maintenance job is worse than an unpartitioned one: inserts begin failing the moment the clock passes the last declared partition, and the first symptom is a business operation rolling back. The primary key is therefore plain `id`; partitioning arrives in the same migration that introduces the job that maintains it. `ip_address` is `varchar(45)` rather than `inet` for a smaller reason — nothing in V1 queries by subnet, and `inet` needs a custom Hibernate type to bind from a String.

*An `AuditSnapshotProvider` must not throw, must not read the caller, and must not filter by permission.* Two failure modes forced this, both found by tests rather than by reasoning:

- A `ResourceNotFoundException` raised inside a nested `@Transactional` service method marks the caller's transaction **rollback-only before the audit aspect can catch it**. The aspect's defensive `try/catch` looks like it makes snapshot failures harmless; it does not. A provider that throws takes down the very operation it was recording. Providers therefore check existence against a repository first and only then call a read that can no longer throw.
- The first draft of the User provider reused `RoleAssignmentService.list`, which filters by the caller's own permissions. That would have made the recorded history depend on **who triggered it** — the same grant, recorded twice, producing two different snapshots. A snapshot describes the row, never the reader.

**Alternatives considered.** *Running snapshots in `REQUIRES_NEW`* would isolate failures, but the "after" snapshot must see the caller's uncommitted changes, so a separate transaction cannot read the state being audited. *Serializing the method's own return value* instead of a provider snapshot avoids the loading problem, but records what the API happened to return rather than the entity's state, and would have put a raw invite token into an append-only table.

**Consequences.** Audit rows are transactional with the mutation they describe, and a provider bug can no longer roll one back. The constraint on providers is a real one that a future implementer will not infer, so it is stated on the port itself. `audit_log` growth remains tracked in doc 11; the read path is indexed for the two queries that exist (subtree-by-time, entity history). Coverage is enforced by `AuditCoverageTest`, which fails the build when a mutating service method is neither annotated nor explicitly exempt — the exemption list is small, and a second test fails if an entry on it stops naming a real method.

**Status.** Accepted — 2026-08-07.
