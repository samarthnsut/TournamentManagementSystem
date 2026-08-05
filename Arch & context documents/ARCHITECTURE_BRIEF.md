# Architecture Brief — Tournament Management Platform (FROZEN v1.0)

> Canonical source of truth for all design documents. Every doc in this folder MUST use these exact entity names, enums, statuses, and conventions. Date frozen: 2026-07-26.

## Product

Multi-tenant SaaS platform for organizing sports tournaments. Target customers: Sports Authority of India (SAI), national/state federations, district associations, academies, colleges, clubs, private organizers. Commercial white-label product.

## Tech Stack (agreed)

- Backend: Java 21, Spring Boot 3.x, modular monolith (NOT microservices in V1)
- Database: PostgreSQL 16 (single DB, shared-schema multi-tenancy with `organization_unit` scoping)
- Cache: Redis
- Object storage: S3-compatible
- Frontend: React SPA (out of scope for backend docs, referenced in HLD/UI-UX only)
- Auth: JWT (access + refresh), Spring Security

## Frozen Domain Decisions (from architecture review)

1. **OrganizationUnit (hierarchy, not flat tenant).** Self-referencing tree: `id, parentOrganizationUnitId, name, slug, type, status`. Types: `FEDERATION, STATE_ASSOCIATION, DISTRICT_ASSOCIATION, ACADEMY, COLLEGE, CLUB, PRIVATE_ORGANIZER`. Root node (parent = null) is the tenant. Example: SAI → Haryana → Sonipat District → (tournaments).
2. **Tournament → Competition.** A Tournament (e.g., "Khelo India 2027") contains many Competitions (e.g., "Football U16", "100m Race"). The entity is named `Competition` (never "TournamentEvent" or "Event").
3. **Participant is polymorphic.** `Participant { id, participantType, ... }` with `participantType ∈ {INDIVIDUAL, TEAM, ORGANIZATION}`. Team members via `TeamMember` join entity.
4. **Dynamic Forms are MVP (not phase 4).** Entities: `RegistrationFormDefinition` (JSON schema per Competition, versioned) and `RegistrationResponse` (JSONB answers linked to a Registration).
5. **Registration status stays simple: `PENDING, APPROVED, REJECTED` (+ `WITHDRAWN`).** Multi-level approval complexity lives in a separate configurable Approval Workflow engine, not in the registration status enum. Internal tracking like "pending at level 2" comes from workflow state, not registration status.
6. **Approval Workflow Engine (tenant-configurable).** Entities: `ApprovalWorkflow { id, organizationUnitId, workflowName, entityType }`, `ApprovalStep { workflowId, level, roleCode, approvalRequired }`, `ApprovalInstance { workflowId, entityType, entityId, currentLevel, status }`, `ApprovalAction { instanceId, stepLevel, actorId, decision, comment, timestamp }`. A tenant may configure 1 level or 3 levels — zero code changes.
7. **Sport Configuration Engine (Strategy pattern, V1).** `SportConfiguration` JSONB config per sport/competition: `{ sport, participantType, fixtureGenerator, resultEvaluator, leaderboardStrategy, rules{} }`. Strategy keys: fixtureGenerator ∈ `{ROUND_ROBIN, SINGLE_ELIMINATION, DOUBLE_ELIMINATION, SWISS, NONE}`; resultEvaluator ∈ `{POINTS, WIN_LOSS, TIME, DISTANCE, SCORE}`; leaderboardStrategy ∈ `{POINTS_TABLE, LOWEST_TIME, HIGHEST_DISTANCE, HIGHEST_SCORE, BRACKET}`. No `if (sport == FOOTBALL)` anywhere. Factories: `FixtureGeneratorFactory`, `ResultEvaluatorFactory`, `LeaderboardStrategyFactory`.
8. **Scoped RBAC (IAM-style).** `UserRoleAssignment { userId, roleId, scopeType, scopeId }` with `scopeType ∈ {GLOBAL, ORGANIZATION, TOURNAMENT, COMPETITION}`. ORGANIZATION scope grants access to the whole subtree. Seed roles: `SUPER_ADMIN (GLOBAL), TENANT_ADMIN (ORGANIZATION), ORG_OFFICIAL (ORGANIZATION), TOURNAMENT_ADMIN (TOURNAMENT), COMPETITION_OFFICIAL (COMPETITION), PARTICIPANT_USER, PUBLIC_VIEWER`. Permissions are fine-grained strings like `tournament:create`, `registration:approve`.
9. **Generic Document module (MVP).** `Document { id, organizationUnitId, entityType, entityId, fileName, fileUrl, mimeType, sizeBytes, uploadedBy, createdAt }`. S3 presigned upload/download.
10. **AuditLog (MVP, day one).** `AuditLog { id, actorId, action, entityType, entityId, beforeState (JSONB), afterState (JSONB), organizationUnitId, ipAddress, timestamp }`. Written via service-layer interceptor/AOP.
11. **Slug-based public URLs.** `/t/{tournament-slug}` e.g. `/t/haryana-games-2027`. Slugs unique per platform, immutable after publish.

## Core Entity List (canonical names)

Identity domain: `User`, `Role`, `Permission`, `RolePermission`, `UserRoleAssignment`, `OrganizationUnit`.
Competition domain: `Sport`, `SportConfiguration`, `Tournament`, `Competition`, `Venue`, `Participant`, `TeamMember`, `Registration`, `RegistrationFormDefinition`, `RegistrationResponse`, `Fixture`, `Match`, `MatchParticipant`, `Result`, `LeaderboardEntry`.
Platform domain: `ApprovalWorkflow`, `ApprovalStep`, `ApprovalInstance`, `ApprovalAction`, `Document`, `AuditLog`, `Notification` (schema reserved, delivery post-MVP).

## Canonical Status Enums

- Tournament: `DRAFT, PUBLISHED, REGISTRATION_OPEN, REGISTRATION_CLOSED, IN_PROGRESS, COMPLETED, CANCELLED, ARCHIVED`
- Competition: `DRAFT, OPEN, CLOSED, IN_PROGRESS, COMPLETED, CANCELLED`
- Registration: `PENDING, APPROVED, REJECTED, WITHDRAWN`
- ApprovalInstance: `IN_PROGRESS, APPROVED, REJECTED, CANCELLED`
- Match: `SCHEDULED, LIVE, COMPLETED, WALKOVER, CANCELLED, POSTPONED`
- User: `ACTIVE, INVITED, SUSPENDED, DEACTIVATED`
- OrganizationUnit: `ACTIVE, SUSPENDED, ARCHIVED`

## Conventions

- DB: snake_case tables/columns, singular table names prefixed by domain where noted in 04; UUID v7 primary keys; audit columns `created_at, created_by, updated_at, updated_by`; soft delete via `deleted_at` (nullable) on business entities only.
- API: REST, versioned base path `/api/v1`, plural resource nouns, camelCase JSON, RFC-7807 problem+json errors, cursor pagination (`?cursor=&limit=`).
- Java packages: `com.acme.tms.<module>` with modules `identity`, `organization`, `tournament`, `registration`, `fixture`, `result`, `workflow`, `document`, `audit`, `common`.
- Multi-tenancy: every tenant-owned row carries `organization_unit_id`; enforcement via service-layer scope checks + Hibernate filter.
- MVP sports for launch: Football (TEAM/ROUND_ROBIN/POINTS), Athletics-100m (INDIVIDUAL/NONE/TIME). Architecture must support Chess (SWISS) without code change beyond a new strategy impl.

## Document Set & Order

00_VISION → 01_PRODUCT_REQUIREMENTS → 02_DOMAIN_MODEL → 03_HLD → 04_DATABASE_DESIGN → 05_RBAC_AND_ORGANIZATION → 06_SPORT_CONFIGURATION_ENGINE → 07_APPROVAL_WORKFLOW_ENGINE → 08_API_CONTRACTS → 09_LLD → 10_DEVELOPMENT_ROADMAP → 11_FUTURE_ENHANCEMENTS → 12_UI_UX_GUIDELINES → 13_CODING_STANDARDS → 14_ARCHITECTURAL_DECISIONS.

Diagrams: use Mermaid code blocks (`erDiagram`, `flowchart`, `sequenceDiagram`, `classDiagram`) plus ASCII where clearer.
