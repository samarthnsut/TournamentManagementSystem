# 11 — Future Enhancements

| Field | Value |
|---|---|
| Version | 1.0 |
| Status | Living (the only doc that grows during the build) |
| Date | 2026-08-05 |
| Owner | Samarth |
| Depends on | 10_DEVELOPMENT_ROADMAP §4.1 (the out-of-scope list), 00_VISION §6 (non-goals) |
| Consumed by | post-V1 planning; every sprint's scope-creep escape valve |

---

## 1. What This Document Is For

10 §4 freezes V1 scope: *"No entity renames, no enum changes, no scope additions mid-sprint. New ideas go to `11_FUTURE_ENHANCEMENTS.md`."* This is that file — the pressure-release valve that lets scope stay frozen without losing good ideas.

**Intake rules**

- Anything not in the roadmap's eight sprints lands here, same day, rather than being argued into the current sprint (10 §5, risk R8 — pilot scope creep).
- An entry states: **what**, **why it was deferred**, **the hook already in place**, and a **rough size**. An entry without a hook is a warning that V1 may need a seam it doesn't have — that is the interesting case, and it is called out explicitly below.
- Filing here is not a commitment. Post-pilot, the top-5 by pilot demand get owners and sizing (10 §7).
- If an entry would require changing `ARCHITECTURE_BRIEF.md`, it needs an ADR in 14 first. Entries that need only new rows, new beans, or new modules are cheap; entries that need enum changes are not.

**Sizing key:** S ≈ ≤1 sprint · M ≈ 1–2 sprints · L ≈ 2+ sprints or a new subsystem.

## 2. Backlog at a Glance

| # | Enhancement | Source | Hook in V1? | Size |
|---|---|---|---|:-:|
| 1 | Notification delivery (email/SMS/push) | 10 §4.1, 00 §6 | Yes — `Notification` schema + engine events | M |
| 2 | Custom tenant domains | 10 §4.1, 12 §7 | Partial — slug URLs + per-tenant theme | M |
| 3 | Payment collection / registration fees | 10 §4.1, 00 §6 | **No** — needs a new module | L |
| 4 | SSO / OIDC for tenants | 10 §4.1 | Partial — Spring Security chain, nullable `passwordHash` | M |
| 5 | `SINGLE_ELIMINATION` / `DOUBLE_ELIMINATION` / `SWISS` generators | 10 §4.1 | Yes — strategy keys already frozen | S each |
| 6 | Reporting & analytics over `RegistrationResponse` | 10 §4.1, 00 §6 | Partial — JSONB answers + pinned form versions | M |
| 7 | PostgreSQL row-level security | 10 §4.1, 04 §13.1 | Yes — `organization_unit_id` on every tenant row | S |
| 8 | Native mobile apps | 10 §4.1, 00 §6 | Yes — REST + JWT is client-agnostic | L |
| 9 | Custom tenant roles | 02 BR-R-1, 05 §12.6 | Yes — `is_system_role` discriminator | M |
| 10 | Waitlists for full competitions | 07 §7.3 | Partial — deliberately *not* a status | M |
| 11 | New approval subjects (result verification, org onboarding) | 07 §9 | Yes — engine is generic over `entityType` | S each |
| 12 | Certificates, live scoring, eKYC, multi-language | 00 §6 | **No** | L |

## 3. Entries

### 3.1 Notification delivery

**What.** Email/SMS/push fan-out: "a registration awaits your approval", "your registration was approved", "fixtures published".

**Why deferred.** V1 assumption A-01 (01 §6): organizers communicate off-platform. Delivery means providers, templates, retries, bounce handling, opt-outs — a subsystem, not a feature.

**Hook.** The strongest in the codebase. The `Notification` entity schema is reserved (brief §Core Entity List) and the approval engine already emits `ApprovalInstanceCreated`, `ApprovalAdvanced` (payload carries the next `roleCode` + scope hint for audience resolution), `ApprovalCompleted`, `ApprovalCancelled` (07 §8). A delivery worker subscribes to those same events — **no engine change required.** Audience resolution reuses 05 §6.5 (`visibleOrganizationUnitIds` inverted: who can act at this scope).

**Size.** M. Start with email-only on the four approval events.

### 3.2 Custom tenant domains

**What.** `tournaments.haryanasports.gov.in` instead of `…/t/haryana-games-2027`.

**Why deferred.** Certificate provisioning, DNS validation and per-tenant routing are ops work with no product logic behind them (12 §7 explicitly defers it).

**Hook.** Partial. Slugs are already platform-unique and immutable after publish (brief §11), and tenant theming is per root `OrganizationUnit` (12 §7), so branding is solved. What is missing is a domain → tenant resolution step ahead of `TenantContext`. 12 §7 already forbids hard-coding the platform domain in copy or emails, which keeps this cheap later.

**Size.** M, mostly infrastructure.

### 3.3 Payment collection

**What.** Registration fees, gateway integration, invoices, refunds, reconciliation.

**Why deferred.** 00 §6 non-goal. Money brings compliance (PCI, GST invoicing, refund policy) that would dominate a V1 built for federations who currently collect fees offline.

**Hook — none, deliberately.** There is no `Payment` entity, no fee field, no order state. This is the honest gap: it arrives as a **new module** (`com.acme.tms.payment`) with its own tables, hanging off `Registration` by id. Two V1 decisions keep that door open: `Registration.status` stays `PENDING/APPROVED/REJECTED/WITHDRAWN` (brief §5) so payment state never contaminates it — a paid-but-unapproved registration is two facts, not a fifth status — and the approval engine is generic over `entityType`, so "payment received" can become a workflow step rather than an `if` in the registration service.

**Size.** L.

### 3.4 SSO / OIDC for tenants

**What.** A federation signs in with its own IdP (Azure AD, Google Workspace, NIC SSO).

**Why deferred.** V1 is password + JWT (brief §Tech Stack). Per-tenant IdP config, JIT provisioning and role mapping are real work, and no pilot tenant has asked yet.

**Hook.** Partial and decent. Authentication is a filter-chain concern (03 §6.2) fully separated from authorization (05) — an OIDC login would mint the same `AuthenticatedUser`, and every scope check downstream is unchanged. `User.passwordHash` is already nullable (invited users have none), so a federated user is a legal row today. Missing: an IdP-config table on the root `OrganizationUnit` and a claim → role mapping (which should reuse 05 §8's escalation rule rather than bypassing it — **JIT role assignment must go through `RoleAssignmentService`, never straight into `user_role_assignment`**).

**Size.** M.

### 3.5 Remaining fixture strategies

**What.** `SINGLE_ELIMINATION`, `DOUBLE_ELIMINATION`, `SWISS` generators, plus `BRACKET` leaderboard rendering.

**Why deferred.** V1 ships `ROUND_ROBIN` and `NONE` — enough for Football and Athletics-100m (brief §MVP sports). Brackets need seeding rules, byes, and a bracket UI.

**Hook.** The best-prepared item on this list, and by design: the strategy keys are **already frozen in the brief** (§7) and validated at config-save time, the factories exist as registries (10 §Sprint 3), and ADR-008 pins a fake-SWISS dispatch test (roadmap Sprint 6) as the proof. Adding Chess = one `SwissFixtureGenerator` bean + a `SportConfiguration` row, zero factory changes. If it ever costs more than that, rule 13 §13.7 (no sport conditionals) has been violated somewhere and *that* is the bug.

**Size.** S per generator (plus UI for brackets).

### 3.6 Reporting & analytics over registration answers

**What.** "How many U16 girls registered per district", cross-tournament participation trends, exports.

**Why deferred.** 10 §Sprint 4 keeps JSONB reads simple — fetch by registration, no ad-hoc JSONB search — because query shapes are unknown until real tenants have real forms.

**Hook.** Partial. Answers are stored as JSONB against a **pinned form-definition version** (09 §11), so historical answers stay interpretable — the single hardest property to retrofit, and V1 has it. `audit_log` is already monthly-partitioned with S3 Parquet export (04 §11), which is the natural path for an analytics store. Missing: GIN indexes on `registration_response`, a semantic layer over per-tenant form fields, and a read model — analytics on the OLTP tables is not the plan.

**Size.** M.

### 3.7 PostgreSQL row-level security

**What.** RLS policies on `organization_unit_id` as defense-in-depth layer 3, beneath service checks and the Hibernate filter.

**Why deferred.** 04 §13.1 (which cites ADR-007; the substantive trade-off is ADR-002's shared-schema decision): two layers are enough while all access is via the app; RLS costs session-variable plumbing on every connection and complicates Flyway.

**Hook.** Yes — the precondition is already met: every tenant-owned row carries `organization_unit_id` (brief §Conventions), and `tms_app` is already a least-privilege role distinct from `tms_owner` (04 §12). Revisit when direct SQL or BI access is granted to anyone outside the app, which is the trigger 04 §13.1 names.

**Size.** S, but only once someone owns the connection-level context.

### 3.8 Native mobile apps

**What.** iOS/Android for participants (register, see fixtures) and officials (record results pitch-side).

**Why deferred.** 00 §6 — responsive web only in V1.

**Hook.** Yes, by construction: `/api/v1` is a versioned REST contract with JWT auth (brief §Conventions), no server-rendered coupling, cursor pagination for infinite lists, and presigned S3 upload/download that works identically from a phone. The likely first need is push tokens — which lands with §3.1, not here.

**Size.** L (a client, not a backend change).

### 3.9 Custom tenant roles

**What.** A federation defines "Referee Coordinator" with a chosen subset of the permission catalog.

**Why deferred.** BR-R-1 — system roles are immutable in V1 and the seven seeds cover every pilot need. Tenant-authored roles introduce a governance question (which permissions may a tenant grant themselves?) that deserves its own design.

**Hook.** Yes. `role.is_system_role` already distinguishes seeds from tenant roles, `role_permission` is a plain join, and the evaluator (05 §6) reads roles generically — it has no knowledge of the seven codes. The one rule to carry over: a tenant may only put permissions into a custom role that the *creator already holds at that scope*, extending 05 §8's no-escalation rule from grants to role definition. Roles would also need an owning `organization_unit_id`, which system roles do not have.

**Size.** M.

### 3.10 Waitlists

**What.** Capacity-full competitions hold registrations in a queue and auto-promote when a slot opens.

**Why deferred.** The frozen enums have no `WAITLISTED` (brief §5, 07 §7.3) and adding one would reopen the exact complexity the registration/workflow split was built to prevent.

**Hook.** Partial and intentional. V1's answer is a rejection with a tenant-defined reason code (`WAITLIST_FULL`) in `ApprovalAction.comment` (07 §7.3). A real waitlist layers **on top** — its own entity with position and expiry, promotion creating a *new* Registration — without touching `Registration.status`. Any proposal that adds a status value goes to an ADR first.

**Size.** M.

### 3.11 New approval subjects

**What.** Result verification before leaderboard publication (record-sensitive Athletics events); org onboarding (a district applying to join a federation).

**Why deferred.** Not needed for the pilot; the engine ships MVP-ready for both (07 §9).

**Hook.** Yes, the cleanest one. Nothing in the engine mentions Registration except through `entityType`/`entityId` and the `ApprovalOutcomeHandler` callback. Each new subject is: a new `entityType` value, one handler implementation in the owning module, and tenant-configured `ApprovalWorkflow` rows. It also needs `approval:act` seeded (05 §4.2) — which Sprint 5 owes regardless.

**Size.** S per subject.

### 3.12 Filed, not planned

Collected from 00 §6 for completeness. No hooks, no sizing, no design work until demand is demonstrated: **certificate generation**, **live ball-by-ball scoring** (`Match.LIVE` exists as a status; there is no real-time feed and none is designed), **AI-assisted scheduling**, **streaming and video analysis**, **dedicated federation/coach/referee portals** beyond scoped RBAC, **Aadhaar/DigiLocker eKYC** (documents are uploaded and manually verified), **multi-language UI** (English only; strings are externalized, which is the whole hook), and **microservices** (deliberately a modular monolith — see the ADR before reopening).

## 4. Reviewing This Backlog

- **After the pilot** (10 §7): the top five by pilot demand get an owner and rough sizing here; the rest stay filed.
- **Every entry that graduates** leaves this file and enters a roadmap sprint with a design section in the relevant doc — an item is never built straight out of this backlog.
- **Entries decay.** An idea that survives two review cycles with no advocate gets deleted; this file is a backlog, not an archive.

---

*End of 11_FUTURE_ENHANCEMENTS. Next: 12_UI_UX_GUIDELINES.*
