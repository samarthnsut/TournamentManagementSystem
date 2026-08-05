# Tournament Management System — Documentation Index & Build Plan

| Field | Value |
|---|---|
| Version | 1.0 |
| Date | 2026-07-26 |
| Owner | Samarth |

---

## 1. Project Summary

A multi-tenant SaaS platform for organizing sports tournaments — built for the Sports Authority of India, federations, district associations, academies, colleges, clubs, and private organizers as a commercial white-label product. A Java 21 / Spring Boot 3.x modular monolith over PostgreSQL 16 (shared schema, `OrganizationUnit`-scoped), it models a hierarchical org tree (Tournament → Competition), polymorphic participants, dynamic registration forms, a tenant-configurable Approval Workflow engine, a Strategy-pattern Sport Configuration engine (fixtures, results, leaderboards with zero sport-specific `if`s), scoped IAM-style RBAC, documents on S3, day-one audit logging, and slug-based public pages at `/t/{slug}`. `ARCHITECTURE_BRIEF.md` is the frozen source of truth; every other document conforms to its entity names, enums, and conventions.

## 2. Document Index

| # | File | Purpose | ~Lines |
|---|---|---|---|
| — | `ARCHITECTURE_BRIEF.md` | Frozen canonical spec: entities, enums, statuses, conventions all docs must follow | 60 |
| 00 | `00_VISION.md` | Why the product exists, target customers, principles, MVP pillars | 174 |
| 01 | `01_PRODUCT_REQUIREMENTS.md` | Actors, user stories, functional + non-functional requirements, assumptions | 319 |
| 02 | `02_DOMAIN_MODEL.md` | Every entity: purpose, attributes, relationships, business rules, invariants | 583 |
| 03 | `03_HLD.md` | High-level design: modules, deployment, multi-tenancy, security architecture | 350 |
| 04 | `04_DATABASE_DESIGN.md` | Full schema: tables, columns, constraints, indexes, DDL | 731 |
| 05 | `05_RBAC_AND_ORGANIZATION.md` | Org tree, scoped role assignments, permission evaluation algorithm, caching | 308 |
| 06 | `06_SPORT_CONFIGURATION_ENGINE.md` | JSONB sport config, strategy factories, validation, adding a new sport | 345 |
| 07 | `07_APPROVAL_WORKFLOW_ENGINE.md` | Configurable N-level approvals; workflow state vs. simple registration status | 260 |
| 08 | `08_API_CONTRACTS.md` | REST reference under `/api/v1`: endpoints, payloads, errors, pagination | 877 |
| 09 | `09_LLD.md` | Low-level design: `com.acme.tms` packages, classes, events, transactions | 470 |
| 10 | `10_DEVELOPMENT_ROADMAP.md` | Sprints 0–8 with milestones, deliverables, DoD, risks | 301 |
| 11 | `11_FUTURE_ENHANCEMENTS.md` | Post-MVP features and the hooks already in place for them | 124 |
| 12 | `12_UI_UX_GUIDELINES.md` | SPA + public-page design system, theming via CSS variables | 202 |
| 13 | `13_CODING_STANDARDS.md` | Naming, layering, testing, and review rules for the codebase | 254 |
| 14 | `14_ARCHITECTURAL_DECISIONS.md` | ADR log: context, decision, alternatives, consequences | 259 |

## 3. Recommended Reading Order

Read the brief first, then in layers:

1. **Product** — `ARCHITECTURE_BRIEF` → `00_VISION` → `01_PRODUCT_REQUIREMENTS`
2. **Domain** — `02_DOMAIN_MODEL`
3. **Architecture** — `03_HLD` → `04_DATABASE_DESIGN` → `05_RBAC_AND_ORGANIZATION`
4. **Engines** — `06_SPORT_CONFIGURATION_ENGINE` → `07_APPROVAL_WORKFLOW_ENGINE`
5. **Implementation** — `08_API_CONTRACTS` → `09_LLD`
6. **Process** — `10_DEVELOPMENT_ROADMAP` → `11_FUTURE_ENHANCEMENTS` → `12_UI_UX_GUIDELINES` → `13_CODING_STANDARDS` → `14_ARCHITECTURAL_DECISIONS`

Skim 14 early if you want the "why" behind any surprising choice — each ADR is self-contained.

## 4. How These Docs Map to Build Order

`10_DEVELOPMENT_ROADMAP.md` is the execution plan; each sprint pulls from specific docs:

| Sprint | Milestone | Primary docs |
|---|---|---|
| 0 | Walking skeleton (repo, CI, Docker, Flyway) | 03, 09, 13 |
| 1 | Identity + `OrganizationUnit` tree + JWT auth | 02, 04, 05 |
| 2 | Scoped RBAC + permission catalog | 05, 08 §role-assignments |
| 3 | Sport config engine core + Tournament/Competition lifecycle + public slugs | 06, 02, 08 |
| 4 | Dynamic forms + Registration submit | 02, 04, 08 |
| 5 | Approval Workflow engine + inbox | 07, 09 |
| 6 | Fixtures, Matches, Results, Leaderboards | 06, 02, 08 |
| 7 | Documents (S3) + AuditLog + hardening | 03 §security, 04 |
| 8 | White-label theming, prod deploy, pilot tenant | 12, 03 |

Rule of thumb: before starting a sprint, re-read its primary docs end-to-end; the sprint's Definition of Done in doc 10 is the acceptance gate.

## 5. Keeping Docs Alive

- **Docs change with code.** Any PR that alters a design decision, entity, enum, endpoint, or schema updates the affected doc(s) **in the same PR** — a code change that contradicts these docs is a blocking review comment.
- **ADRs are append-only.** Never rewrite or delete an accepted ADR in `14_ARCHITECTURAL_DECISIONS.md`; supersede it with a new ADR that references the old one and mark the old one "Superseded".
- **The brief stays frozen.** Changing anything in `ARCHITECTURE_BRIEF.md` requires an ADR first, then a coordinated sweep of every dependent doc.
