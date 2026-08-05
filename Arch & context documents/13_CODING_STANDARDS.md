# 13 — Coding Standards

| | |
|---|---|
| **Version** | 1.0 |
| **Status** | Approved |
| **Date** | 2026-07-26 |
| **Owner** | Samarth |
| **Depends on** | ARCHITECTURE_BRIEF.md, 03_HLD, 08_API_CONTRACTS, 09_LLD |

Java 21 / Spring Boot 3.x standards for the TMS modular monolith. Enforced by Spotless + Checkstyle + ArchUnit tests in CI wherever tooling can enforce; the rest is enforced in code review (§10).

---

## 1. Package Layout

Root: `com.acme.tms`. Modules exactly as frozen in the brief: `identity`, `organization`, `tournament`, `registration`, `fixture`, `result`, `workflow`, `document`, `audit`, `common`.

Inside each module:

```
com.acme.tms.<module>
├── api/          # @RestController classes + Request/Response DTOs
├── service/      # business logic, transaction boundaries
├── domain/       # JPA entities, enums, domain value objects
├── repository/   # Spring Data repositories
├── mapper/       # entity <-> DTO mappers (MapStruct)
└── config/       # module-local Spring configuration
```

Rules:
- Cross-module calls go through **service** interfaces only — never another module's repository or entity internals. Enforced by ArchUnit.
- `common` holds only genuinely shared code: `TmsException` hierarchy, problem+json handler, UUID v7 generator, pagination helpers, audit annotations. If two modules need it, it may go to `common`; if one might someday, it stays put.
- No cyclic module dependencies (ArchUnit-verified). Expected direction: everything may depend on `common`; `tournament`/`registration`/`fixture`/`result`/`workflow`/`document`/`audit` may depend on `identity`/`organization`, not vice versa.

## 2. Naming

- **Classes:** `PascalCase`. Controllers `<Resource>Controller`, services `<Resource>Service` (interface only when there are ≥2 implementations — otherwise a concrete class), repositories `<Entity>Repository`, mappers `<Entity>Mapper`.
- **DTOs:** suffix `Request` / `Response` — `CreateTournamentRequest`, `TournamentResponse`, `RegistrationSubmitRequest`. Never `DTO` in the name. DTOs are Java `record`s.
- **Entities:** exact canonical names from the brief (`Competition`, never `TournamentEvent`; `OrganizationUnit`, never `Tenant`). Table names snake_case per doc 04.
- **Enums:** `UPPER_SNAKE_CASE` values matching the brief's canonical enums verbatim.
- **Strategies:** implementations named `<Key><Role>` — `RoundRobinFixtureGenerator`, `LowestTimeLeaderboardStrategy` — registered in `FixtureGeneratorFactory` / `ResultEvaluatorFactory` / `LeaderboardStrategyFactory` under their brief-defined keys.
- **Methods:** verbs; boolean accessors `is`/`has`/`can`. No abbreviations except universally understood (`id`, `url`, `dto` in local vars).
- **Constants:** `static final`, `UPPER_SNAKE_CASE`; magic numbers banned outside constants and test fixtures.

Canonical example of the naming rules working together:

```java
// api/
@RestController
@RequestMapping("/api/v1/tournaments")
@RequiredArgsConstructor
class TournamentController {

    private final TournamentService tournamentService;
    private final TournamentMapper tournamentMapper;

    @PostMapping
    @RequiresPermission("tournament:create")
    ResponseEntity<TournamentResponse> create(@Valid @RequestBody CreateTournamentRequest request) {
        Tournament tournament = tournamentService.create(request);
        return ResponseEntity
            .created(location(tournament.getId()))
            .body(tournamentMapper.toResponse(tournament));
    }
}

// api/ — DTOs are records with the Request/Response suffix
record CreateTournamentRequest(
    @NotBlank String name,
    @NotNull UUID organizationUnitId,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate) {}
```

## 3. DTO vs Entity Rules

- **Entities never cross the API boundary.** Controllers accept and return DTOs only; ArchUnit forbids `domain.*` types in `api.*` method signatures.
- Mapping lives in `mapper/` (MapStruct, `componentModel = "spring"`, `unmappedTargetPolicy = ERROR` so new fields can't be silently dropped). No mapping logic in controllers or services beyond calling the mapper.
- DTOs are immutable records; entities are mutable JPA classes. Never reuse a class for both.
- JSONB payloads (`SportConfiguration.config`, `RegistrationResponse.answers`, `AuditLog.beforeState/afterState`) map to typed value objects where the structure is known, `JsonNode` where it's tenant-defined — never raw `String` passed around.
- Partial updates: dedicated `Update<Resource>Request`; no generic patch maps.

## 4. Dependency Injection

- **Constructor injection only.** No `@Autowired` on fields or setters. Single constructor → no annotation needed.
- Dependencies are `private final`. A constructor exceeding ~6 dependencies signals a class that needs splitting.
- No `@Lazy` to break cycles — a cycle is a design bug; fix the design.

## 5. Lombok Policy

Restricted allow-list:
- **Allowed:** `@RequiredArgsConstructor` (services/controllers), `@Getter` on entities, `@Builder` on entities where construction is complex, `@Slf4j`.
- **Forbidden:** `@Data`, `@Setter` (class-level), `@EqualsAndHashCode` and `@ToString` on JPA entities (lazy-loading and identity pitfalls — write explicit `equals`/`hashCode` on the UUID id per 09), `@SneakyThrows`, `@Value` (use records), `val`/`var` via Lombok (use Java `var` judiciously for obvious local types).
- DTO records need no Lombok at all.

## 6. Exception Handling

- **Never swallow exceptions.** No empty catch blocks; no catch-and-log-and-continue unless the operation is explicitly best-effort (e.g., cache warm) and the comment says why.
- Use the `TmsException` hierarchy from `common`:
  - `TmsException` (abstract, carries error code + HTTP status)
  - `ResourceNotFoundException` → 404, `ValidationException` → 400, `AuthenticationException` → 401, `ScopeAccessDeniedException` → 403, `ConflictException` → 409 (slug collisions, optimistic-lock losses, illegal lifecycle transitions), `ExternalServiceException` → 502.
- One `@RestControllerAdvice` in `common` translates everything to RFC-7807 problem+json (per API conventions): `type`, `title`, `status`, `detail`, `instance`, `errors[]` for field violations, plus `correlationId`. No stack traces, entity internals, or SQL in responses.
- Don't catch what you can't handle — let it propagate to the advice. Catching to add context: wrap in a `TmsException` subtype with the original as cause; never log **and** rethrow (one or the other).
- No exceptions for control flow. Existence checks return `Optional`; `orElseThrow(() -> new ResourceNotFoundException(...))` at the service boundary.

```java
// Good — service boundary, typed exception, no leakage of persistence details
public Tournament publish(UUID tournamentId) {
    Tournament tournament = tournamentRepository.findById(tournamentId)
        .orElseThrow(() -> new ResourceNotFoundException("Tournament", tournamentId));
    if (tournament.getStatus() != TournamentStatus.DRAFT) {
        throw new ConflictException(
            "TOURNAMENT_ILLEGAL_TRANSITION",
            "Tournament %s cannot move from %s to PUBLISHED".formatted(tournamentId, tournament.getStatus()));
    }
    tournament.publish(); // domain method enforces slug freeze
    return tournament;
}

// Bad — swallowed exception, string status, control-flow catch
try {
    tournament.publish();
} catch (Exception e) {
    log.error("publish failed", e); // and then silently continuing — forbidden
}
```

## 7. Logging

- SLF4J API only (via `@Slf4j`); Logback with JSON encoder — **structured JSON** in all deployed environments.
- Every log line carries MDC context: `correlationId` (from the request filter, propagated to async work), `userId`, `organizationUnitId` when resolved.
- **No PII in logs.** Never log names, emails, phone numbers, dynamic-form answers, tokens, or passwords. Log UUIDs and enum values instead ("registration `id` moved to `APPROVED`", not the participant's details). JSONB payloads are never logged wholesale.
- Levels: `ERROR` = needs human attention (alertable); `WARN` = anomalous but self-handled; `INFO` = domain events (lifecycle transitions, approvals, fixture generation); `DEBUG` = local diagnosis, off in prod. No `System.out`, no `printStackTrace` (Checkstyle-banned).
- Parameterized messages only: `log.info("Tournament {} published", id)` — no string concatenation.
- Business-grade traceability belongs in `AuditLog`, not application logs; don't duplicate audit data at INFO.

```java
// Good
log.info("Registration {} transitioned to {} via approval instance {}",
    registrationId, RegistrationStatus.APPROVED, instanceId);

// Bad — PII, concatenation, wrong level
log.error("Approved registration for " + participant.getFullName()
    + " (" + participant.getEmail() + ")");
```

Example structured output (Logback JSON encoder):

```json
{
  "timestamp": "2026-07-26T10:14:03.201Z",
  "level": "INFO",
  "logger": "c.a.t.workflow.service.ApprovalService",
  "message": "Approval instance 018f... advanced to level 2",
  "correlationId": "b7c1e2a4-...",
  "userId": "018f6a...",
  "organizationUnitId": "018f2b..."
}
```

## 8. REST Conventions

Match 08_API_CONTRACTS exactly:
- Base path `/api/v1`; plural kebab-case resource nouns: `/api/v1/tournaments`, `/api/v1/organization-units`, `/api/v1/registrations`.
- Nesting only for true composition: `/api/v1/tournaments/{id}/competitions`; otherwise top-level with filters.
- Verbs via HTTP methods; lifecycle transitions as sub-resources, not enum PATCHes: `POST /api/v1/tournaments/{id}/publish`, `.../registrations/{id}/withdraw`, `.../approval-instances/{id}/actions`.
- JSON camelCase; timestamps ISO-8601 UTC; IDs are UUID strings; enums serialized as their canonical values.
- Pagination: cursor-based `?cursor=&limit=` returning `{ items, nextCursor }` — no offset/page params anywhere.
- Status codes: 201 + `Location` on create, 200 on read/update, 204 on delete, RFC-7807 for all errors. Public endpoints (slug pages, leaderboards) live under `/api/v1/public/**` and require no auth.

## 9. Validation & Transactions

**Validation placement:**
- **Syntactic** (shape, format, required, ranges): Bean Validation annotations on Request records + `@Valid` in controllers.
- **Business** (state, uniqueness, scope, cross-field, dynamic-form answers vs. pinned `RegistrationFormDefinition` version, `SportConfiguration` strategy keys): in services, throwing `ValidationException`/`ConflictException`. Never in controllers, never only in the frontend.
- Scope/permission checks (`@RequiresPermission` + `ScopeEvaluator`) run before business validation.

**Transactions:**
- `@Transactional` on service methods only — never controllers or repositories; `readOnly = true` for queries.
- One use-case = one transaction. No cross-module transactions spanning service calls unless deliberately designed in 09; keep transactions short — no S3, HTTP, or presigned-URL work inside a transaction.
- Optimistic locking (`@Version`) on entities with concurrent writes: `ApprovalInstance`, `Registration`, `Match`, `Result`. Lost updates surface as `ConflictException`.
- Events published to other modules go via Spring application events after commit (`@TransactionalEventListener(phase = AFTER_COMMIT)`).

## 10. Testing Strategy

- **Unit tests** — services and strategies, mocked collaborators (Mockito). **Coverage gate: >70% line coverage on `service/` and strategy packages** (JaCoCo, CI-enforced). Strategy implementations (fixture generators, evaluators, leaderboards) are table-driven against worked examples from 06.
- **Repository tests** — Testcontainers PostgreSQL (real Postgres, never H2 — JSONB, CTEs and constraints must be real). Cover tree queries, JSONB round-trips, unique constraints, soft-delete filters.
- **Contract/API tests** — `@SpringBootTest` + MockMvc/RestAssured against the contracts in 08: status codes, problem+json shape, pagination envelope, authz matrix (role × endpoint × scope table-driven test).
- **Architecture tests** — ArchUnit: module boundaries, no entities in `api/`, constructor injection, no banned Lombok.
- **Naming:** `methodName_condition_expectedResult`. Examples:
  - `submitRegistration_whenTournamentNotRegistrationOpen_throwsValidationException`
  - `generateFixtures_withFiveTeams_createsTenRoundRobinMatches`
  - `approve_whenFinalLevelApproves_setsRegistrationStatusApproved`
  - `create_whenSlugAlreadyExists_throwsConflictException`
  - `evaluate_lowestTimeStrategy_ranksAscendingByTime`
- Fixtures via test-data builders in `src/test/.../support`; no shared mutable test state; every bug fix lands with a regression test.

Test pyramid targets (indicative, not dogma):

| Layer | Tooling | Speed | Volume |
|---|---|---|---|
| Unit (services, strategies) | JUnit 5 + Mockito | ms | most tests |
| Repository | Testcontainers PostgreSQL | ~s | per query/constraint of interest |
| Contract/API | SpringBootTest + RestAssured | ~s | per endpoint + authz matrix |
| Architecture | ArchUnit | ms | one suite, grows with rules |

## 11. Git Workflow

- **Trunk-based:** `main` is always releasable; short-lived branches (≤2 days) — `feat/<scope>-<desc>`, `fix/...`, `chore/...`; merge via PR, squash-merge, delete branch. No long-lived develop/release branches.
- **Conventional commits:** `type(scope): summary` with types `feat|fix|chore|docs|test|refactor|perf|ci` and scope = module name — e.g. `feat(workflow): advance approval instance on final level approval`. Imperative mood, ≤72-char subject.
- Even solo, every change goes through a PR (self-review + AI review + green CI).

**PR checklist (template):**
- [ ] CI green (build, tests, coverage gate, static analysis, ArchUnit)
- [ ] Flyway migration included for any schema change; migration is immutable-safe (§12)
- [ ] Entity/enum names match ARCHITECTURE_BRIEF.md verbatim
- [ ] New endpoints match 08_API_CONTRACTS; `@RequiresPermission` present
- [ ] No PII in logs; audit interception covers new mutations
- [ ] Tests follow naming convention; regression test for any bug fix

## 12. Flyway Migration Rules

- Location `db/migration`; naming `V<n>__<snake_case_description>.sql` — sequential integer, two underscores: `V7__create_registration_tables.sql`. Repeatable migrations `R__<desc>.sql` only for idempotent reference/seed data.
- **Migrations are immutable once merged to `main`.** Never edit or delete a merged migration — fix-forward with a new `V<n+1>`. Checksum validation on; a checksum failure fails the deploy.
- One concern per migration; both DDL and its indexes together. Every tenant-owned table includes `organization_unit_id`, UUID v7 PK, audit columns (`created_at, created_by, updated_at, updated_by`) and `deleted_at` where doc 04 specifies.
- Migrations must be backward-compatible with the currently running app version (expand → migrate → contract for renames/drops; destructive steps ship at least one release after the code stops using the column).
- No environment-specific data in versioned migrations; dev/demo seeds live in a separate location activated by profile.

Illustrative migration chain (early sprints):

```
db/migration/
├── V1__baseline.sql                       # extensions, helpers
├── V2__create_user_and_organization_unit.sql
├── V3__create_rbac_tables.sql
├── V4__seed_permission_catalog_and_roles.sql
├── V5__create_sport_and_tournament_tables.sql
├── V6__create_registration_and_form_tables.sql
├── V7__create_approval_workflow_tables.sql
└── V8__create_fixture_match_result_tables.sql
```

## 13. Code Review Checklist

Reviewer (human or AI-assisted) verifies:
1. **Correctness:** business rules match docs 02/06/07; lifecycle transitions legal per canonical enums; edge cases (empty, duplicate, concurrent) handled.
2. **Boundaries:** module dependency direction respected; no entity leakage; no cross-module repository access.
3. **Security:** permission annotation on every new endpoint; scope enforced (no cross-tenant reads via ID guessing); input validated server-side; no secrets/PII in code or logs.
4. **Data:** migration correct, immutable-safe, indexed; JSONB used only where the brief prescribes; soft-delete respected in queries.
5. **Errors:** `TmsException` subtypes, no swallowing, problem+json intact.
6. **Tests:** meaningful assertions (not coverage theater), naming convention, negative cases present.
7. **No sport conditionals:** any `if`/`switch` on a sport or strategy key outside the factories is an automatic change-request (Strategy pattern per ADR-008).
8. **Simplicity:** no speculative abstraction; solves the ticket, nothing more.
