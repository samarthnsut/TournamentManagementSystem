# 08 — API Contracts (REST API Reference, MVP)

| Field | Value |
|---|---|
| Version | 1.0 |
| Status | Approved |
| Date | 2026-07-26 |
| Owner | Samarth |
| Depends on | 02_DOMAIN_MODEL, 03_HLD, 05_RBAC_AND_ORGANIZATION, 06_SPORT_CONFIGURATION_ENGINE, 07_APPROVAL_WORKFLOW_ENGINE |

---

## 1. Global Conventions

### 1.1 Base Path & Versioning

All endpoints are rooted at `/api/v1`. Versioning is URI-based; breaking changes bump the path to `/api/v2`. Additive changes (new optional fields, new endpoints) do not bump the version. The only exception to the base path is the unauthenticated public surface at `/api/v1/public/**`.

### 1.2 Authentication

All non-public endpoints require a JWT access token:

```
Authorization: Bearer <accessToken>
```

Access tokens are short-lived (15 min); refresh tokens (30 days, rotated on use) are exchanged via `POST /auth/refresh`. Missing/expired token → `401`. Valid token but insufficient permission/scope → `403`.

### 1.3 Error Envelope — RFC 7807 (`application/problem+json`)

Every error response uses RFC-7807. `type` is a stable documentation URI, `code` is a machine-readable extension.

```json
{
  "type": "https://docs.acme-tms.com/problems/registration-window-closed",
  "title": "Conflict",
  "status": 409,
  "detail": "Registration for competition 'Football U16' closed on 2027-01-10T18:30:00Z.",
  "instance": "/api/v1/registrations",
  "code": "REGISTRATION_WINDOW_CLOSED",
  "traceId": "8f4c2a10-91e7-7f3a-b2d1-0242ac120002"
}
```

Validation failures (`400`) add an `errors` array:

```json
{
  "type": "https://docs.acme-tms.com/problems/validation-failed",
  "title": "Bad Request",
  "status": 400,
  "detail": "Request body failed validation.",
  "code": "VALIDATION_FAILED",
  "errors": [
    { "field": "answers.dateOfBirth", "message": "must be a valid ISO-8601 date" },
    { "field": "competitionId", "message": "must not be null" }
  ]
}
```

### 1.4 Pagination (cursor-based)

List endpoints accept `?cursor=<opaque>&limit=<1..100>` (default limit 20) and return:

```json
{
  "items": [ "..." ],
  "nextCursor": "eyJpZCI6IjAxOTAifQ==",
  "hasMore": true
}
```

`nextCursor` is opaque and null on the last page. Cursors are stable across soft deletes.

### 1.5 Idempotency

`POST /registrations` requires an `Idempotency-Key` header (client-generated UUID, retained 24h). Replay with the same key returns the original response with header `Idempotency-Replayed: true`. Same key with a different body → `409 IDEMPOTENCY_KEY_REUSE`.

### 1.6 Common Conventions

- JSON field names: **camelCase**. IDs: UUID v7 strings. Timestamps: ISO-8601 UTC (`2027-02-01T09:30:00Z`).
- Resource nouns are plural. Lifecycle transitions are POST sub-resources (`POST /tournaments/{id}/publish`), never `PATCH status`.
- Every tenant-owned response carries `organizationUnitId`.
- Permissions in the tables below are fine-grained strings (e.g. `tournament:create`); **scope** states at which `UserRoleAssignment.scopeType` the permission is evaluated. ORGANIZATION scope covers the whole subtree.

---

## 2. Auth

### 2.1 `POST /api/v1/auth/login`

Authenticate with email + password. No permission required (anonymous).

Request:
```json
{ "email": "samarth.gulia@travenues.com", "password": "S3cure!pass" }
```

Success `200`:
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "refreshToken": "d2f1c9c0-6a3e-7c11-9b0e-0242ac120002",
  "expiresIn": 900,
  "user": {
    "id": "01907e2a-1111-7abc-9def-000000000001",
    "email": "samarth.gulia@travenues.com",
    "displayName": "Samarth Gulia",
    "status": "ACTIVE"
  }
}
```

Errors: `400 VALIDATION_FAILED` (malformed email), `401 INVALID_CREDENTIALS`, `403 USER_SUSPENDED` (status `SUSPENDED`/`DEACTIVATED`).

### 2.2 `POST /api/v1/auth/refresh`

Rotate refresh token, issue a new access token.

Request: `{ "refreshToken": "d2f1c9c0-6a3e-7c11-9b0e-0242ac120002" }`

Success `200`: same shape as login. Errors: `401 REFRESH_TOKEN_INVALID` (expired, revoked, or reused-after-rotation — reuse revokes the whole family).

### 2.3 `POST /api/v1/auth/logout`

Revokes the presented refresh token family. Requires a valid access token; no specific permission.

Request: `{ "refreshToken": "d2f1c9c0-..." }` — Success `204` (empty). Errors: `401 UNAUTHENTICATED`.

### 2.4 `POST /api/v1/auth/invite-accept`

Accept an invitation (user in status `INVITED`), set password, activate account. Anonymous + invite token.

Request:
```json
{ "inviteToken": "9b1a77e4-inv-7001", "password": "S3cure!pass", "displayName": "Ravi Kumar" }
```

Success `200`: login response shape (user status becomes `ACTIVE`). Errors: `400 VALIDATION_FAILED` (weak password), `404 INVITE_NOT_FOUND`, `409 INVITE_ALREADY_ACCEPTED` / `409 INVITE_EXPIRED`.

---

## 3. OrganizationUnits

| Endpoint | Permission | Scope |
|---|---|---|
| `POST /organization-units` | `organization:create` | GLOBAL or ORGANIZATION (parent subtree) |
| `GET /organization-units/{id}` | `organization:read` | ORGANIZATION |
| `PATCH /organization-units/{id}` | `organization:update` | ORGANIZATION |
| `DELETE /organization-units/{id}` | `organization:delete` | ORGANIZATION (soft delete, sets status `ARCHIVED`) |
| `GET /organization-units/{id}/tree` | `organization:read` | ORGANIZATION |
| `GET /organization-units` | `organization:read` | ORGANIZATION (returns units visible in caller scope) |

### 3.1 `POST /api/v1/organization-units`

Create a child unit under a parent (root creation is SUPER_ADMIN-only, `parentOrganizationUnitId: null`).

Request:
```json
{
  "parentOrganizationUnitId": "01907e2a-2222-7abc-9def-000000000010",
  "name": "Sonipat District Association",
  "slug": "sonipat-district",
  "type": "DISTRICT_ASSOCIATION"
}
```

Success `201`:
```json
{
  "id": "01907e2a-2222-7abc-9def-000000000011",
  "parentOrganizationUnitId": "01907e2a-2222-7abc-9def-000000000010",
  "name": "Sonipat District Association",
  "slug": "sonipat-district",
  "type": "DISTRICT_ASSOCIATION",
  "status": "ACTIVE",
  "createdAt": "2026-07-26T08:15:00Z"
}
```

Errors: `400` (invalid `type` enum), `403 SCOPE_FORBIDDEN` (parent outside caller's subtree), `404 PARENT_NOT_FOUND`, `409 SLUG_TAKEN`.

### 3.2 `GET /api/v1/organization-units/{id}/tree`

Returns the subtree rooted at `{id}` (max depth 10), nested.

Success `200`:
```json
{
  "id": "01907e2a-2222-7abc-9def-000000000001",
  "name": "Sports Authority of India",
  "slug": "sai",
  "type": "FEDERATION",
  "status": "ACTIVE",
  "children": [
    {
      "id": "01907e2a-2222-7abc-9def-000000000010",
      "name": "Haryana State Association",
      "slug": "haryana",
      "type": "STATE_ASSOCIATION",
      "status": "ACTIVE",
      "children": [
        { "id": "01907e2a-2222-7abc-9def-000000000011", "name": "Sonipat District Association", "slug": "sonipat-district", "type": "DISTRICT_ASSOCIATION", "status": "ACTIVE", "children": [] }
      ]
    }
  ]
}
```

Errors: `403 SCOPE_FORBIDDEN`, `404 ORGANIZATION_UNIT_NOT_FOUND`.

### 3.3 `PATCH /api/v1/organization-units/{id}`

Partial update of `name`, `status` (`ACTIVE|SUSPENDED|ARCHIVED`). `slug` and `type` are immutable, `parentOrganizationUnitId` immutable in MVP (no re-parenting).

Request: `{ "name": "Sonipat District Sports Association" }` — Success `200`: full resource. Errors: `400` (attempt to change immutable field), `403`, `404`, `409 UNIT_ARCHIVED`.

---

## 4. Users & Role Assignments

### 4.1 `POST /api/v1/users/invite` — permission `user:invite`, scope ORGANIZATION

Creates a `User` in status `INVITED` and emails an invite token. Optionally seeds a role assignment.

Request:
```json
{
  "email": "ravi.kumar@haryanasports.gov.in",
  "displayName": "Ravi Kumar",
  "organizationUnitId": "01907e2a-2222-7abc-9def-000000000010",
  "initialRole": { "roleCode": "ORG_OFFICIAL", "scopeType": "ORGANIZATION", "scopeId": "01907e2a-2222-7abc-9def-000000000010" }
}
```

Success `201`:
```json
{
  "id": "01907e2a-1111-7abc-9def-000000000042",
  "email": "ravi.kumar@haryanasports.gov.in",
  "displayName": "Ravi Kumar",
  "status": "INVITED",
  "organizationUnitId": "01907e2a-2222-7abc-9def-000000000010"
}
```

Errors: `400`, `403 SCOPE_FORBIDDEN` (granting a role above caller's own scope), `409 EMAIL_ALREADY_REGISTERED`.

### 4.2 `POST /api/v1/users/{userId}/role-assignments` — permission `role:assign`, scope of `scopeId`

Request:
```json
{ "roleCode": "TOURNAMENT_ADMIN", "scopeType": "TOURNAMENT", "scopeId": "01907e2a-3333-7abc-9def-000000000100" }
```

Success `201`:
```json
{
  "id": "01907e2a-4444-7abc-9def-000000000200",
  "userId": "01907e2a-1111-7abc-9def-000000000042",
  "roleCode": "TOURNAMENT_ADMIN",
  "scopeType": "TOURNAMENT",
  "scopeId": "01907e2a-3333-7abc-9def-000000000100"
}
```

Errors: `400` (scopeType/roleCode mismatch, e.g. `TOURNAMENT_ADMIN` with `GLOBAL`), `403` (caller cannot grant beyond own scope), `404 USER_NOT_FOUND` / `404 SCOPE_ENTITY_NOT_FOUND`, `409 ASSIGNMENT_EXISTS`.

### 4.3 Other user endpoints

- `GET /api/v1/users?organizationUnitId=&status=&cursor=&limit=` — `user:read`, ORGANIZATION scope. Paginated list.
- `GET /api/v1/users/{userId}/role-assignments` — `role:read`. Returns `items` of assignments.
- `DELETE /api/v1/users/{userId}/role-assignments/{assignmentId}` — `role:assign`. `204`. Errors: `403`, `404`, `409 LAST_SUPER_ADMIN` (cannot remove the final GLOBAL SUPER_ADMIN).

---

## 5. Sports & SportConfigurations

### 5.1 `GET /api/v1/sports` — permission `sport:read`, any authenticated scope

Success `200`:
```json
{
  "items": [
    { "id": "01907e2a-5555-7abc-9def-000000000001", "name": "Football", "code": "FOOTBALL" },
    { "id": "01907e2a-5555-7abc-9def-000000000002", "name": "Athletics - 100m", "code": "ATHLETICS_100M" }
  ],
  "nextCursor": null,
  "hasMore": false
}
```

### 5.2 `POST /api/v1/sport-configurations` — permission `sport-config:create`, scope ORGANIZATION

Request:
```json
{
  "organizationUnitId": "01907e2a-2222-7abc-9def-000000000010",
  "sportId": "01907e2a-5555-7abc-9def-000000000001",
  "name": "Football U16 default",
  "config": {
    "sport": "FOOTBALL",
    "participantType": "TEAM",
    "fixtureGenerator": "ROUND_ROBIN",
    "resultEvaluator": "POINTS",
    "leaderboardStrategy": "POINTS_TABLE",
    "rules": { "pointsWin": 3, "pointsDraw": 1, "pointsLoss": 0, "matchDurationMinutes": 70, "squadSizeMax": 18 }
  }
}
```

Success `201`: resource with `id`, echoed `config`, `createdAt`. 

Errors: `400 UNKNOWN_STRATEGY_KEY` (e.g. `fixtureGenerator: "LADDER"` — not in `ROUND_ROBIN|SINGLE_ELIMINATION|DOUBLE_ELIMINATION|SWISS|NONE`), `400` (`resultEvaluator` not in `POINTS|WIN_LOSS|TIME|DISTANCE|SCORE`; `leaderboardStrategy` not in `POINTS_TABLE|LOWEST_TIME|HIGHEST_DISTANCE|HIGHEST_SCORE|BRACKET`), `403`, `404 SPORT_NOT_FOUND`.

### 5.3 Other

- `GET /api/v1/sport-configurations/{id}` — `sport-config:read`. `200` full resource; `404`.
- `GET /api/v1/sport-configurations?organizationUnitId=&sportId=` — `sport-config:read`, paginated.
- `PUT /api/v1/sport-configurations/{id}` — `sport-config:update`. Full replace; `409 CONFIG_IN_USE` if referenced by a Competition past `DRAFT`.

---

## 6. Tournaments

| Endpoint | Permission | Scope |
|---|---|---|
| `POST /tournaments` | `tournament:create` | ORGANIZATION |
| `GET /tournaments/{id}` | `tournament:read` | TOURNAMENT (or ORGANIZATION ancestor) |
| `GET /tournaments?organizationUnitId=&status=` | `tournament:read` | ORGANIZATION |
| `PATCH /tournaments/{id}` | `tournament:update` | TOURNAMENT |
| `DELETE /tournaments/{id}` | `tournament:delete` | TOURNAMENT (soft delete; only from `DRAFT`) |
| `POST /tournaments/{id}/publish` etc. | `tournament:transition` | TOURNAMENT |
| `GET /public/t/{slug}` | — (anonymous) | — |

### 6.1 `POST /api/v1/tournaments`

Request:
```json
{
  "organizationUnitId": "01907e2a-2222-7abc-9def-000000000010",
  "name": "Khelo India 2027 — Haryana Games",
  "slug": "haryana-games-2027",
  "description": "State-level multi-sport games under Khelo India 2027.",
  "startDate": "2027-02-01",
  "endDate": "2027-02-14",
  "registrationOpensAt": "2026-12-01T00:00:00Z",
  "registrationClosesAt": "2027-01-10T18:30:00Z"
}
```

Success `201`:
```json
{
  "id": "01907e2a-3333-7abc-9def-000000000100",
  "organizationUnitId": "01907e2a-2222-7abc-9def-000000000010",
  "name": "Khelo India 2027 — Haryana Games",
  "slug": "haryana-games-2027",
  "status": "DRAFT",
  "startDate": "2027-02-01",
  "endDate": "2027-02-14",
  "registrationOpensAt": "2026-12-01T00:00:00Z",
  "registrationClosesAt": "2027-01-10T18:30:00Z",
  "createdAt": "2026-07-26T09:00:00Z"
}
```

Errors: `400` (endDate before startDate, invalid slug format `^[a-z0-9-]{3,60}$`), `401`, `403`, `409 SLUG_TAKEN` (slugs are platform-unique).

### 6.2 Lifecycle transitions

Statuses: `DRAFT → PUBLISHED → REGISTRATION_OPEN → REGISTRATION_CLOSED → IN_PROGRESS → COMPLETED → ARCHIVED`; `CANCELLED` reachable from any pre-`COMPLETED` state.

| Endpoint | From → To |
|---|---|
| `POST /tournaments/{id}/publish` | `DRAFT → PUBLISHED` (slug becomes immutable) |
| `POST /tournaments/{id}/open-registration` | `PUBLISHED → REGISTRATION_OPEN` |
| `POST /tournaments/{id}/close-registration` | `REGISTRATION_OPEN → REGISTRATION_CLOSED` |
| `POST /tournaments/{id}/start` | `REGISTRATION_CLOSED → IN_PROGRESS` |
| `POST /tournaments/{id}/complete` | `IN_PROGRESS → COMPLETED` |
| `POST /tournaments/{id}/cancel` | any pre-`COMPLETED` → `CANCELLED` (body: `{ "reason": "..." }`) |
| `POST /tournaments/{id}/archive` | `COMPLETED → ARCHIVED` |

Request body: empty (except cancel). Success `200`:
```json
{ "id": "01907e2a-3333-7abc-9def-000000000100", "status": "PUBLISHED", "transitionedAt": "2026-11-15T10:00:00Z" }
```

Errors: `403`, `404`, `409 INVALID_STATE_TRANSITION`:
```json
{
  "type": "https://docs.acme-tms.com/problems/invalid-state-transition",
  "title": "Conflict",
  "status": 409,
  "detail": "Cannot transition tournament from DRAFT to IN_PROGRESS.",
  "code": "INVALID_STATE_TRANSITION"
}
```

### 6.3 `GET /api/v1/public/t/{slug}` — anonymous

Public read-only view of a `PUBLISHED`+ tournament (never `DRAFT`), by slug e.g. `/api/v1/public/t/haryana-games-2027`.

Success `200`:
```json
{
  "name": "Khelo India 2027 — Haryana Games",
  "slug": "haryana-games-2027",
  "status": "REGISTRATION_OPEN",
  "startDate": "2027-02-01",
  "endDate": "2027-02-14",
  "organizer": { "name": "Haryana State Association", "type": "STATE_ASSOCIATION" },
  "competitions": [
    { "id": "01907e2a-6666-7abc-9def-000000000201", "name": "Football U16", "sportCode": "FOOTBALL", "status": "OPEN" },
    { "id": "01907e2a-6666-7abc-9def-000000000202", "name": "100m Race — Men", "sportCode": "ATHLETICS_100M", "status": "OPEN" }
  ]
}
```

Errors: `404 TOURNAMENT_NOT_FOUND` (also returned for `DRAFT`/soft-deleted — no existence leak).

---

## 7. Competitions

Nested under tournament for create/list; flat for item operations.

| Endpoint | Permission | Scope |
|---|---|---|
| `POST /tournaments/{tournamentId}/competitions` | `competition:create` | TOURNAMENT |
| `GET /tournaments/{tournamentId}/competitions` | `competition:read` | TOURNAMENT |
| `GET /competitions/{id}` | `competition:read` | COMPETITION |
| `PATCH /competitions/{id}` | `competition:update` | COMPETITION |
| `POST /competitions/{id}/open` / `/close` / `/start` / `/complete` / `/cancel` | `competition:transition` | COMPETITION |

### 7.1 `POST /api/v1/tournaments/{tournamentId}/competitions`

Request:
```json
{
  "name": "Football U16",
  "sportConfigurationId": "01907e2a-7777-7abc-9def-000000000301",
  "participantType": "TEAM",
  "maxRegistrations": 16,
  "eligibility": { "gender": "MALE", "maxAgeYears": 16, "ageAsOf": "2027-01-01" }
}
```

Success `201`:
```json
{
  "id": "01907e2a-6666-7abc-9def-000000000201",
  "tournamentId": "01907e2a-3333-7abc-9def-000000000100",
  "organizationUnitId": "01907e2a-2222-7abc-9def-000000000010",
  "name": "Football U16",
  "sportId": "01907e2a-5555-7abc-9def-000000000050",
  "sportCode": "FOOTBALL",
  "sportConfigurationId": "01907e2a-7777-7abc-9def-000000000301",
  "participantType": "TEAM",
  "fixtureGenerator": "ROUND_ROBIN",
  "resultEvaluator": "POINTS",
  "leaderboardStrategy": "POINTS_TABLE",
  "status": "DRAFT",
  "maxRegistrations": 16
}
```

The three strategy keys are resolved from the competition's SportConfiguration and returned on every competition read. Clients need them to know what to render — a score box per side, or a time per lane — and the only alternative is counting participants and guessing, which is exactly the sport-shaped inference the engine exists to prevent. Clients dispatch on these keys, never on `sportCode`.

Errors: `400` (`participantType` mismatch with SportConfiguration's `participantType`), `403`, `404 TOURNAMENT_NOT_FOUND` / `404 SPORT_CONFIGURATION_NOT_FOUND`, `409 TOURNAMENT_NOT_EDITABLE` (tournament `COMPLETED`/`CANCELLED`/`ARCHIVED`).

### 7.2 Competition lifecycle

Statuses: `DRAFT → OPEN → CLOSED → IN_PROGRESS → COMPLETED`; `CANCELLED` from any pre-`COMPLETED`. Response/error shape mirrors tournament transitions (`409 INVALID_STATE_TRANSITION`). `POST /competitions/{id}/open` additionally requires an **active** `RegistrationFormDefinition` → else `409 NO_ACTIVE_FORM_DEFINITION`.

---

## 8. RegistrationFormDefinitions

Versioned JSON-Schema forms per Competition. Publishing a new version supersedes the previous; submitted `RegistrationResponse`s keep the version they answered.

### 8.1 `POST /api/v1/competitions/{competitionId}/form-definitions` — `form:create`, COMPETITION scope

Creates the next version (server assigns `version = max + 1`) in `draft: true` state; activate with `POST .../form-definitions/{id}/activate`.

Request:
```json
{
  "schema": {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "type": "object",
    "required": ["teamName", "coachName", "coachPhone"],
    "properties": {
      "teamName": { "type": "string", "maxLength": 80 },
      "coachName": { "type": "string" },
      "coachPhone": { "type": "string", "pattern": "^[0-9]{10}$" },
      "previousParticipation": { "type": "boolean", "default": false }
    }
  },
  "uiHints": { "order": ["teamName", "coachName", "coachPhone", "previousParticipation"] }
}
```

Success `201`:
```json
{
  "id": "01907e2a-8888-7abc-9def-000000000401",
  "competitionId": "01907e2a-6666-7abc-9def-000000000201",
  "version": 2,
  "active": false,
  "schema": { "...": "..." },
  "createdAt": "2026-11-20T12:00:00Z"
}
```

Errors: `400 INVALID_JSON_SCHEMA` (schema itself fails meta-schema validation), `403`, `404 COMPETITION_NOT_FOUND`, `409 COMPETITION_NOT_EDITABLE`.

### 8.2 Read/activate

- `GET /api/v1/competitions/{competitionId}/form-definitions` — `form:read`. Paginated versions, newest first.
- `GET /api/v1/competitions/{competitionId}/form-definitions/active` — `form:read` (also served anonymously via public tournament page). `200` active version; `404 NO_ACTIVE_FORM_DEFINITION`.
- `POST /api/v1/form-definitions/{id}/activate` — `form:update`. `200`; `409 ALREADY_ACTIVE`.

---

## 9. Registrations

### 9.1 `POST /api/v1/registrations` — `registration:create`, PARTICIPANT_USER (any authenticated) — **requires `Idempotency-Key` header**

Submits a registration with dynamic form answers. Server validates answers against the active `RegistrationFormDefinition`, creates `Registration` (status `PENDING`) + `RegistrationResponse`, starts an `ApprovalInstance` if the competition's org has a matching `ApprovalWorkflow`.

Request:
```json
{
  "competitionId": "01907e2a-6666-7abc-9def-000000000201",
  "participant": {
    "participantType": "TEAM",
    "name": "Sonipat Strikers U16",
    "teamMembers": [
      { "fullName": "Aman Malik", "dateOfBirth": "2011-03-14", "role": "CAPTAIN" },
      { "fullName": "Vikram Singh", "dateOfBirth": "2011-07-02", "role": "PLAYER" }
    ]
  },
  "answers": {
    "teamName": "Sonipat Strikers U16",
    "coachName": "Deepak Hooda",
    "coachPhone": "9812345678",
    "previousParticipation": true
  }
}
```

Success `201`:
```json
{
  "id": "01907e2a-9999-7abc-9def-000000000501",
  "competitionId": "01907e2a-6666-7abc-9def-000000000201",
  "participantId": "01907e2a-aaaa-7abc-9def-000000000601",
  "status": "PENDING",
  "formDefinitionVersion": 2,
  "approvalInstanceId": "01907e2a-bbbb-7abc-9def-000000000701",
  "submittedAt": "2026-12-05T14:22:00Z"
}
```

Errors: `400 FORM_ANSWERS_INVALID` (answers fail JSON-Schema; `errors[]` lists field paths), `400` (missing `Idempotency-Key`), `401`, `404 COMPETITION_NOT_FOUND`, `409 REGISTRATION_WINDOW_CLOSED` (competition not `OPEN`), `409 MAX_REGISTRATIONS_REACHED`, `409 DUPLICATE_REGISTRATION` (same participant, same competition), `409 IDEMPOTENCY_KEY_REUSE`.

### 9.2 `GET /api/v1/registrations` — `registration:read`, COMPETITION/TOURNAMENT/ORGANIZATION scope

Filters: `?competitionId=&tournamentId=&status=PENDING&participantType=TEAM&submittedAfter=&cursor=&limit=`. Participants see only their own regardless of filters.

Success `200`:
```json
{
  "items": [
    {
      "id": "01907e2a-9999-7abc-9def-000000000501",
      "competitionId": "01907e2a-6666-7abc-9def-000000000201",
      "participant": { "id": "01907e2a-aaaa-7abc-9def-000000000601", "participantType": "TEAM", "name": "Sonipat Strikers U16" },
      "status": "PENDING",
      "submittedAt": "2026-12-05T14:22:00Z"
    }
  ],
  "nextCursor": "eyJpZCI6IjAxOTA3ZTJhIn0=",
  "hasMore": true
}
```

Errors: `400` (invalid status filter), `403`.

### 9.3 `GET /api/v1/registrations/{id}` — `registration:read` (or owner)

`200` full resource including `answers` and `approvalInstance` summary (`currentLevel`, `status`). Errors: `403`, `404`.

### 9.4 `POST /api/v1/registrations/{id}/withdraw` — owner or `registration:update`

Request: `{ "reason": "Team unavailable for tournament dates." }`

Success `200`: `{ "id": "01907e2a-9999-...", "status": "WITHDRAWN" }`. Also cancels any `IN_PROGRESS` ApprovalInstance (`CANCELLED`).

Errors: `403` (not owner/no permission), `404`, `409 ALREADY_FINALIZED` (status `REJECTED` or `WITHDRAWN`; withdrawing `APPROVED` after fixtures generated → `409 FIXTURES_EXIST`).

---

## 10. Approvals

### 10.1 `GET /api/v1/approvals/pending` — `registration:approve` (or any approve permission), scope per assignment

Returns ApprovalInstances whose **current step** `roleCode` matches one of the caller's role assignments in scope. Filters: `?entityType=REGISTRATION&cursor=&limit=`.

Success `200`:
```json
{
  "items": [
    {
      "instanceId": "01907e2a-bbbb-7abc-9def-000000000701",
      "workflowId": "01907e2a-cccc-7abc-9def-000000000801",
      "entityType": "REGISTRATION",
      "entityId": "01907e2a-9999-7abc-9def-000000000501",
      "currentLevel": 2,
      "status": "IN_PROGRESS",
      "summary": { "competition": "Football U16", "participant": "Sonipat Strikers U16", "tournament": "Khelo India 2027 — Haryana Games" }
    }
  ],
  "nextCursor": null,
  "hasMore": false
}
```

### 10.2 `POST /api/v1/approvals/{instanceId}/approve` — permission per step `roleCode`, scope of entity

Request: `{ "comment": "Documents verified, age proofs valid." }`

Success `200`:
```json
{
  "instanceId": "01907e2a-bbbb-7abc-9def-000000000701",
  "decision": "APPROVED",
  "actedLevel": 2,
  "currentLevel": 3,
  "instanceStatus": "IN_PROGRESS"
}
```

When the final level approves, `instanceStatus` becomes `APPROVED` and the Registration flips to `APPROVED`.

Errors: `403 NOT_CURRENT_STEP_APPROVER` (role/scope doesn't match current step), `404 INSTANCE_NOT_FOUND`, `409 INSTANCE_NOT_IN_PROGRESS`, `409 STALE_LEVEL` (concurrent action already advanced the level).

### 10.3 `POST /api/v1/approvals/{instanceId}/reject`

Request: `{ "comment": "Age proof for player 7 is invalid." }` — **comment required on reject** (`400` if blank).

Success `200`: `{ "instanceId": "...", "decision": "REJECTED", "instanceStatus": "REJECTED" }` — Registration flips to `REJECTED`. Errors: same as approve.

### 10.4 `GET /api/v1/approvals/{instanceId}/actions` — `approval:read`

`200`: chronological `ApprovalAction` list (`stepLevel`, `actorId`, `decision`, `comment`, `timestamp`).

---

## 11. Fixtures

### 11.1 `POST /api/v1/competitions/{competitionId}/fixtures/generate` — `fixture:generate`, COMPETITION scope

Runs the `FixtureGenerator` strategy from the competition's SportConfiguration over `APPROVED` registrations. Competition must be `CLOSED`.

Request: `{ "seedStrategy": "RANDOM" }` (or `"SEEDED"` with `"seeds": [{ "participantId": "...", "seed": 1 }]`)

A round-robin season produces one `Fixture` row **per round**, so the response is a list of rounds rather than the single `fixtureId` earlier drafts of this section assumed. The flat counts callers display (`rounds`, `matchCount`) sit alongside it.

Success `201`:
```json
{
  "competitionId": "01907e2a-6666-7abc-9def-000000000201",
  "generatorKey": "ROUND_ROBIN",
  "rounds": 15,
  "matchCount": 120,
  "fixtures": [
    {
      "fixtureId": "01907e2a-dddd-7abc-9def-000000000901",
      "round": 1,
      "roundName": "Round 1",
      "generatedAt": "2027-01-20T10:00:00Z",
      "matches": [
        {
          "id": "01907e2a-eeee-7abc-9def-000000001001",
          "round": 1,
          "status": "SCHEDULED",
          "version": 0,
          "participants": [
            { "participantId": "01907e2a-aaaa-7abc-9def-000000000601", "name": "Sonipat Strikers U16", "slot": "HOME" },
            { "participantId": "01907e2a-aaaa-7abc-9def-000000000602", "name": "Panipat Panthers U16", "slot": "AWAY" }
          ]
        }
      ]
    }
  ]
}
```

**Generator `NONE`** is generated through this same endpoint, not rejected: it emits a direct-final shell — one round named `Final` holding one match with every entrant in a lane (`LANE_1`…`LANE_n`, seeded entrants inside). That is what doc 06 §3.2 and the Sprint 6 deliverable ("direct-final for measured events like 100m") call for, and it means result and leaderboard code never special-cases "this competition has no fixtures". Its minimum entry count is 1 — a solo time trial is a real event.

Errors: `403`, `404`, `409 COMPETITION_NOT_CLOSED`, `409 FIXTURE_ALREADY_EXISTS` (use regenerate), `409 INSUFFICIENT_PARTICIPANTS` (below the generator's `minimumParticipants()` — 2 for `ROUND_ROBIN`, 1 for `NONE`).

### 11.2 `POST /api/v1/competitions/{competitionId}/fixtures/regenerate` — `fixture:generate`

Discards and rebuilds. Request: `{ "confirm": true }` (optionally `seedStrategy`/`seeds`). Success `201` (same shape). Errors: `409 MATCHES_HAVE_RESULTS` (any match past `SCHEDULED`/`POSTPONED` — BR-F-3, so `LIVE` blocks it too, while a postponed match does not), `404 FIXTURE_NOT_FOUND` (nothing to rebuild), `400` (`confirm` missing/false).

### 11.3 `GET /api/v1/competitions/{competitionId}/fixtures` — `fixture:read`, COMPETITION scope

`200`: the same rounds-and-matches shape as 11.1. `404 FIXTURE_NOT_FOUND`.

### 11.4 `GET /api/v1/competitions/{competitionId}/matches` — `match:read`

`200`: every match in the competition as a flat list, each with participants and its result if one has been recorded.

---

## 12. Matches

Item operations are addressed flat, by match id. Authority is inherited from the match's competition through the resolve-only `MATCH` scope — see ADR-015; no MATCH-scoped role assignment exists or is accepted.

### 12.1 `POST /api/v1/matches/{matchId}/schedule` — `match:schedule`, MATCH scope

Request:
```json
{ "scheduledAt": "2027-02-03T09:30:00Z", "venueId": "01907e2a-ffff-7abc-9def-000000001101" }
```

Success `200`: `{ "match": { … }, "warnings": [] }`. A `POSTPONED` match returns to `SCHEDULED` when rescheduled.

Venue double-booking is reported in `warnings` rather than rejected (BR-M-4): organizers legitimately run two events on one ground, and refusing the booking would have them scheduling around the software.

Errors: `400` (missing `scheduledAt`), `403`, `404 MATCH_NOT_FOUND` / `404 VENUE_NOT_FOUND`, `409 MATCH_FINALIZED` (`COMPLETED`/`CANCELLED`/`WALKOVER`).

### 12.1a Match lifecycle transitions — `match:schedule`, MATCH scope

`POST /api/v1/matches/{matchId}/start` → `LIVE`, `/postpone` → `POSTPONED`, `/cancel` → `CANCELLED`. Each returns the updated match, or `409 INVALID_STATE_TRANSITION` against the lifecycle in 02 §5.12.

Recording a result does **not** require passing through `LIVE` first: officials routinely score a match without ever flagging it live, and refusing the result would be the system arguing with reality. `SCHEDULED → COMPLETED` is therefore legal.

### 12.2 `POST /api/v1/matches/{matchId}/result` — `result:record`, MATCH scope

Payload shape is interpreted by the competition's `ResultEvaluator`. Transitions match to `COMPLETED` (or `WALKOVER`).

Request (Football, `POINTS`):
```json
{
  "outcome": "COMPLETED",
  "scores": [
    { "participantId": "01907e2a-aaaa-7abc-9def-000000000601", "value": 2 },
    { "participantId": "01907e2a-aaaa-7abc-9def-000000000602", "value": 1 }
  ],
  "version": 0
}
```

Request (Athletics-100m, `TIME`):
```json
{
  "outcome": "COMPLETED",
  "scores": [
    { "participantId": "01907e2a-aaaa-7abc-9def-000000000699", "value": 11.42, "unit": "SECONDS" }
  ],
  "version": 0
}
```

A walkover carries no scores: `{ "outcome": "WALKOVER", "winnerParticipantId": "…" }`.

`version` is optional; when present it must equal the match's current version. The database's own `@Version` check is the backstop and surfaces as the same `409 STALE_VERSION`, so a client cannot tell "beaten by a millisecond" from "beaten by a minute" — and does not need to.

Success `200`:
```json
{
  "matchId": "01907e2a-eeee-7abc-9def-000000001001",
  "status": "COMPLETED",
  "version": 1,
  "result": {
    "resultId": "01907e2a-1212-7abc-9def-000000001201",
    "evaluatorKey": "POINTS",
    "outcome": "COMPLETED",
    "winnerParticipantId": "01907e2a-aaaa-7abc-9def-000000000601",
    "recordedAt": "2027-02-03T11:15:00Z",
    "participants": [
      { "participantId": "01907e2a-aaaa-…601", "name": "Sonipat Strikers U16", "value": 2, "points": 3, "standing": "WIN" },
      { "participantId": "01907e2a-aaaa-…602", "name": "Panipat Panthers U16", "value": 1, "points": 0, "standing": "LOSS" }
    ]
  }
}
```

`standing` is `WIN | DRAW | LOSS | RANKED | NO_CONTEST`. `RANKED` is placement by measurement (a race), `NO_CONTEST` an entrant who produced no result (did not start, did not finish, disqualified) — recorded rather than dropped, so the field that lined up stays visible.

Recording a result transitions the match and recomputes the leaderboard in the same transaction (BR-RES-2).

Errors: `400 INVALID_RESULT` (scores don't match match participants; negative values; a time below the configured record bound; wrong unit — every problem is reported at once), `403`, `404`, `409 MATCH_ALREADY_COMPLETED`, `409 STALE_VERSION` (refetch and retry).

### 12.3 `GET /api/v1/matches/{matchId}` — `match:read`, MATCH scope

`200` with participants, status, scheduledAt, venue, result (if any). `404`.

---

## 13. Leaderboards

### 13.1 `GET /api/v1/competitions/{competitionId}/leaderboard` — `leaderboard:read`, COMPETITION scope

Computed by the competition's `LeaderboardStrategy` and recomputed in full on every result confirmation. The materialized `leaderboard_entry` table is the V1 cache (04 §12 decision 3): written in the same transaction as the result, so it can never serve a standing that disagrees with the match it came from. The Redis read-through layer in 03 §7 sits in front of this later and changes no contract.

`metrics` is **strategy-shaped and sport-neutral** (ADR-016) — clients render what they are given rather than assuming a sport, which is what lets a points table and a timed board share one response type.

Success `200` (Football, `POINTS_TABLE`):
```json
{
  "competitionId": "01907e2a-6666-7abc-9def-000000000201",
  "strategyKey": "POINTS_TABLE",
  "computedAt": "2027-02-05T18:00:00Z",
  "frozen": false,
  "entries": [
    {
      "rank": 1,
      "participantId": "01907e2a-aaaa-7abc-9def-000000000601",
      "name": "Sonipat Strikers U16",
      "metrics": { "played": 5, "won": 4, "drawn": 1, "lost": 0, "points": 13, "scoreFor": 12, "scoreAgainst": 3, "scoreDifference": 9 }
    }
  ]
}
```

For `LOWEST_TIME` (100m), `metrics` is `{ "bestValue": 11.42, "unit": "SECONDS", "attempts": 2 }` — an entrant's best time across every heat they ran.

`frozen` is true once the competition is `COMPLETED`; the board stops being recomputed (BR-LE-3). Genuinely tied entrants share a rank and the next rank skips (1, 1, 3), so a reader can tell a tie from an arbitrary ordering. Entrants who have not played yet still appear, on nil.

Tie-breaking follows `rules.tiebreakers` in order, defaulting to `["SCORE_DIFFERENCE", "SCORE_FOR", "HEAD_TO_HEAD"]`. `GOAL_DIFFERENCE`/`GOALS_FOR` are accepted aliases; `DIRECT_ENCOUNTER` aliases `HEAD_TO_HEAD`. An unrecognized name ranks nobody rather than throwing — a typo in a tenant's config must not take a live standings page down mid-tournament.

Errors: `404 COMPETITION_NOT_FOUND`, `409 LEADERBOARD_NOT_AVAILABLE` (no completed matches yet — MVP decision: 409 with empty-state hint rather than empty 200, so clients distinguish "not started").

---

## 13A. Public results — anonymous

Earlier drafts marked §11.3, §12.3 and §13.1 "public for published tournaments". They are not: they sit behind `@RequiresPermission` and always have. The anonymous surface is separate, lives under `/api/v1/public/**`, and is addressed **by slug**.

### 13A.1 `GET /api/v1/public/t/{slug}/competitions/{competitionId}/fixtures` — anonymous

### 13A.2 `GET /api/v1/public/t/{slug}/competitions/{competitionId}/leaderboard` — anonymous

Both take the slug *and* the competition id, and both are checked (`PublicAccessService`): the tournament must be publicly visible (any status but `DRAFT`), and the competition must belong to **that** tournament. Addressing by competition id alone would let anyone who scraped or guessed an id read an unpublished tournament's draw, which is exactly what the slug closes off.

Every failure is `404`, never `403` — a forbidden would confirm the thing exists, and an unpublished tournament's existence is itself private.

The leaderboard response is byte-identical to §13.1; both paths share one read, so the public board cannot drift from the organizer's. The fixtures response is **deliberately narrower** than §11.1 — no `fixtureId`, `venueId`, `version` or `seed`, and no per-entrant `points`:

```json
{
  "competitionId": "01907e2a-6666-7abc-9def-000000000201",
  "competitionName": "Football U16",
  "generatorKey": "ROUND_ROBIN",
  "rounds": 5,
  "matchCount": 10,
  "fixtures": [
    {
      "round": 1,
      "roundName": "Round 1",
      "matches": [
        {
          "id": "01907e2a-eeee-7abc-9def-000000001001",
          "status": "COMPLETED",
          "scheduledAt": null,
          "participants": [
            { "participantId": "01907e2a-aaaa-…601", "name": "Sonipat Strikers U16", "slot": "HOME" },
            { "participantId": "01907e2a-aaaa-…602", "name": "Panipat Panthers U16", "slot": "AWAY" }
          ],
          "result": {
            "outcome": "COMPLETED",
            "winnerParticipantId": "01907e2a-aaaa-…601",
            "participants": [
              { "participantId": "01907e2a-aaaa-…601", "name": "Sonipat Strikers U16", "value": 3, "unit": null, "standing": "WIN" },
              { "participantId": "01907e2a-aaaa-…602", "name": "Panipat Panthers U16", "value": 1, "unit": null, "standing": "LOSS" }
            ]
          }
        }
      ]
    }
  ]
}
```

Errors: `404 TOURNAMENT_NOT_FOUND` (unknown or `DRAFT` slug), `404 COMPETITION_NOT_FOUND` (competition belongs to a different tournament), `404 FIXTURE_NOT_FOUND` (no draw yet), `409 LEADERBOARD_NOT_AVAILABLE` (nothing played yet).

---

## 14. Documents

### 14.1 `POST /api/v1/documents/upload-init` — `document:upload`, scope of `entityType/entityId`

Returns an S3 presigned PUT URL. Client uploads directly, then calls attach.

The permission is evaluated at ORGANIZATION scope against the unit that owns the *target entity*, resolved from `entityType`/`entityId` — never against anything the caller sent. The check therefore happens in `DocumentService` rather than in a `@RequiresPermission` annotation, which cannot express a scope that is only known after a polymorphic lookup.

`entityType` is limited to the types some module has deployed an `AttachableEntityResolver` for (`TOURNAMENT`, `REGISTRATION` today); anything else is `400 ENTITY_TYPE_NOT_ATTACHABLE`. The object key is composed by the server and namespaced by organization unit — a client-supplied key would be a path traversal, and would let one tenant overwrite another's object.

Request:
```json
{ "fileName": "age-proof-aman-malik.pdf", "mimeType": "application/pdf", "sizeBytes": 482133, "entityType": "REGISTRATION", "entityId": "01907e2a-9999-7abc-9def-000000000501" }
```

Success `200`:
```json
{
  "uploadId": "01907e2a-1313-7abc-9def-000000001301",
  "presignedUrl": "https://s3.ap-south-1.amazonaws.com/tms-docs/...&X-Amz-Signature=...",
  "expiresAt": "2026-12-05T15:00:00Z"
}
```

Errors: `400` (mimeType not in allowlist: pdf/jpeg/png; sizeBytes > 10485760), `403`, `404 ENTITY_NOT_FOUND`.

### 14.2 `POST /api/v1/documents/{uploadId}/attach` — `document:upload`

Confirms upload; server verifies object exists (HEAD) and persists `Document`.

The HEAD is not just an existence check. A presigned PUT signs the content type but **not the length**, so a caller who declared 400 KB at init can still push far more; the size and type recorded are the ones read back off the stored object, and an object that breaks either rule is deleted rather than attached (`400 FILE_TOO_LARGE` / `400 MIME_TYPE_NOT_ALLOWED`). The permission is re-checked here too — authority can be revoked between the two calls.

Nothing about the attachment is taken from this request: the owning entity, file name, and key all come from the `document_upload` row the server wrote at init. `uploadId` says only "the upload you authorized is done".

Success `201`:
```json
{
  "id": "01907e2a-1414-7abc-9def-000000001401",
  "organizationUnitId": "01907e2a-2222-7abc-9def-000000000010",
  "entityType": "REGISTRATION",
  "entityId": "01907e2a-9999-7abc-9def-000000000501",
  "fileName": "age-proof-aman-malik.pdf",
  "fileUrl": "s3://tms-docs/01907e2a-1313-.../age-proof-aman-malik.pdf",
  "mimeType": "application/pdf",
  "sizeBytes": 482133,
  "uploadedBy": "01907e2a-1111-7abc-9def-000000000042",
  "createdAt": "2026-12-05T14:40:00Z"
}
```

Errors: `404 UPLOAD_NOT_FOUND`, `409 UPLOAD_NOT_COMPLETED` (object missing in S3), `409 ALREADY_ATTACHED`.

### 14.3 `GET /api/v1/documents?entityType=REGISTRATION&entityId=...` — `document:read`

`200`: list; each item includes a short-lived presigned GET `downloadUrl` (5 min), signed with a `Content-Disposition: attachment` so a PDF is downloaded rather than rendered inline under our own origin. `fileUrl` is the durable object key; `downloadUrl` expires and is never persisted. Errors: `403` (entity outside scope), `400 ENTITY_TYPE_NOT_ATTACHABLE`, `404 ENTITY_NOT_FOUND`.

---

## 15. AuditLogs

### 15.1 `GET /api/v1/audit-logs` — `audit:read`, scope ORGANIZATION (TENANT_ADMIN and above)

Filters: `?entityType=REGISTRATION&entityId=&actorId=&action=&from=&to=&cursor=&limit=`. Read-only; no create/update/delete endpoints — logs are written exclusively by the service-layer AOP interceptor.

Success `200`:
```json
{
  "items": [
    {
      "id": "01907e2a-1515-7abc-9def-000000001501",
      "actorId": "01907e2a-1111-7abc-9def-000000000042",
      "action": "REGISTRATION_APPROVED",
      "entityType": "REGISTRATION",
      "entityId": "01907e2a-9999-7abc-9def-000000000501",
      "beforeState": { "status": "PENDING" },
      "afterState": { "status": "APPROVED" },
      "organizationUnitId": "01907e2a-2222-7abc-9def-000000000010",
      "ipAddress": "203.0.113.42",
      "timestamp": "2026-12-08T10:15:30Z"
    }
  ],
  "nextCursor": "eyJ0cyI6IjIwMjYtMTItMDgifQ==",
  "hasMore": true
}
```

Errors: `400` (`from` after `to`), `403 SCOPE_FORBIDDEN` (querying an org unit outside subtree).

---

## 16. Error Code Registry (summary)

| HTTP | `code` | Meaning |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bean/request validation failure (`errors[]`) |
| 400 | `FORM_ANSWERS_INVALID` | Dynamic answers fail form JSON Schema |
| 400 | `INVALID_JSON_SCHEMA` | Form definition schema is itself invalid |
| 400 | `UNKNOWN_STRATEGY_KEY` | SportConfiguration references unknown strategy |
| 401 | `UNAUTHENTICATED` / `INVALID_CREDENTIALS` / `REFRESH_TOKEN_INVALID` | Auth failures |
| 403 | `SCOPE_FORBIDDEN` / `NOT_CURRENT_STEP_APPROVER` / `USER_SUSPENDED` | Permission/scope failures |
| 404 | `*_NOT_FOUND` | Resource absent or hidden by scope/soft delete |
| 409 | `SLUG_TAKEN`, `INVALID_STATE_TRANSITION`, `DUPLICATE_REGISTRATION`, `REGISTRATION_WINDOW_CLOSED`, `MAX_REGISTRATIONS_REACHED`, `IDEMPOTENCY_KEY_REUSE`, `STALE_VERSION`, `STALE_LEVEL`, `FIXTURE_ALREADY_EXISTS`, `MATCHES_HAVE_RESULTS` | State conflicts |

---

*End of 08_API_CONTRACTS.md — cross-reference 09_LLD.md for the exception hierarchy that produces these problem responses.*
