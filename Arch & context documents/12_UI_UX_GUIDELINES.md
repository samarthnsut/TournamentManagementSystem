# 12 — UI / UX Guidelines

| | |
|---|---|
| **Version** | 1.0 |
| **Status** | Approved |
| **Date** | 2026-07-26 |
| **Owner** | Samarth |
| **Depends on** | ARCHITECTURE_BRIEF.md, 01_PRODUCT_REQUIREMENTS, 08_API_CONTRACTS |

Applies to the React SPA (admin console) and the public tournament pages (`/t/{tournament-slug}`). The platform is white-label: every visual rule below must hold for any tenant palette, not just the default.

---

## 1. Design Tokens

All colors, fonts and spacing are consumed **only** through CSS variables. Components never hard-code hex values. Tenant theming (§7) works by overriding the variables at the root.

### 1.1 Color tokens

```css
:root {
  /* Brand — overridable per tenant */
  --tms-primary:        #1D4ED8;
  --tms-primary-hover:  #1E40AF;
  --tms-primary-soft:   #DBEAFE;  /* backgrounds, selected states */
  --tms-on-primary:     #FFFFFF;
  --tms-secondary:      #0F766E;
  --tms-secondary-soft: #CCFBF1;
  --tms-on-secondary:   #FFFFFF;

  /* Semantic — fixed platform-wide, NOT tenant-overridable */
  --tms-success: #15803D;  --tms-success-soft: #DCFCE7;
  --tms-warning: #B45309;  --tms-warning-soft: #FEF3C7;
  --tms-danger:  #B91C1C;  --tms-danger-soft:  #FEE2E2;
  --tms-info:    #0369A1;  --tms-info-soft:    #E0F2FE;

  /* Neutrals */
  --tms-bg: #F8FAFC;  --tms-surface: #FFFFFF;
  --tms-border: #E2E8F0;
  --tms-text: #0F172A;  --tms-text-muted: #64748B;
  --tms-text-disabled: #94A3B8;
}
```

Rules:
- Semantic colors are reserved for state (success/warning/danger/info). Tenants cannot override them — an APPROVED registration is green on every tenant.
- Brand colors carry identity only (buttons, links, header, accents). Never encode meaning with brand colors.
- Each `*-soft` token pairs with its base token for badge/alert backgrounds; text on soft backgrounds uses the base token.

### 1.2 Typography scale

System font stack by default (`Inter, system-ui, sans-serif`); tenant font override is post-MVP.

| Token | Size / line-height | Weight | Use |
|---|---|---|---|
| `--tms-font-display` | 32 / 40 px | 700 | Public tournament hero, page titles |
| `--tms-font-h1` | 24 / 32 px | 700 | Admin page headings |
| `--tms-font-h2` | 20 / 28 px | 600 | Section headings, card titles |
| `--tms-font-h3` | 16 / 24 px | 600 | Sub-sections, table headers |
| `--tms-font-body` | 14 / 20 px | 400 | Default text, form inputs |
| `--tms-font-small` | 12 / 16 px | 400 | Help text, timestamps, badges |
| `--tms-font-mono` | 13 / 20 px | 400 | Slugs, IDs, JSON config preview |

### 1.3 Spacing scale

4px base unit: `--tms-space-1..12` = 4, 8, 12, 16, 24, 32, 40, 48, 64, 80, 96, 128 px. Component padding uses 8–16; section gaps 24–32; page gutters 16 (mobile) / 32 (desktop). Border radius: `--tms-radius-sm: 4px` (inputs, badges), `--tms-radius-md: 8px` (cards, buttons), `--tms-radius-lg: 12px` (modals, hero panels).

---

## 2. Component Inventory

### 2.1 Buttons
- Variants: **primary** (one per view, main action), **secondary** (outline), **tertiary** (text-only), **danger** (destructive — always paired with a confirm dialog for irreversible actions like `CANCELLED` transitions).
- Sizes: md (40px height, default), sm (32px, tables/inline). Min touch target 44×44px on mobile (padding may exceed visual bounds).
- Loading state: spinner replaces label, button stays same width, disabled while pending. Never allow double-submit on Registration or approval actions.

### 2.2 Forms & the dynamic-form renderer
- Standard fields: labels above inputs, required marker `*`, help text below, inline error below in `--tms-danger` with an icon (never color alone).
- Validation: client-side on blur + on submit; always re-validated server-side; server RFC-7807 field errors map back onto fields by JSON pointer.
- **Dynamic-form renderer** (for `RegistrationFormDefinition`): a single component that takes the versioned JSON schema and renders field types — text, textarea, number, date, select, multi-select, radio, checkbox, file (via Document presigned upload). It must:
  - render strictly from the schema version pinned to the `RegistrationResponse` (old submissions render against their original version);
  - enforce required/min/max/options client-side, mirroring server rules;
  - support read-only mode for approvers viewing submitted answers in the approvals inbox;
  - degrade gracefully on unknown field types (render as read-only text + warning, never crash).

### 2.3 Tables
- Used for lists: tournaments, competitions, registrations, matches, users, audit log.
- Sticky header; row height 48px; cursor pagination controls ("Load more" / infinite scroll — matches API `?cursor=&limit=`; no numbered pages).
- Column priority for responsive collapse (§4): each table defines which columns drop first; below `md` tables become stacked cards.
- Row actions in a kebab menu; bulk actions only where the API supports them (post-MVP).

### 2.4 Status badges
Pill badges: `--tms-font-small`, 600 weight, soft background + base-color text, 4px radius. **Badge colors map 1:1 to the canonical enums** — this table is normative:

| Enum | Value | Token pair |
|---|---|---|
| Tournament | `DRAFT` | neutral (`--tms-border` bg, `--tms-text-muted`) |
| Tournament | `PUBLISHED` | info |
| Tournament | `REGISTRATION_OPEN` | success |
| Tournament | `REGISTRATION_CLOSED` | warning |
| Tournament | `IN_PROGRESS` | primary-soft / primary |
| Tournament | `COMPLETED` | success |
| Tournament | `CANCELLED` | danger |
| Tournament | `ARCHIVED` | neutral |
| Competition | `DRAFT` | neutral |
| Competition | `OPEN` | success |
| Competition | `CLOSED` | warning |
| Competition | `IN_PROGRESS` | primary-soft / primary |
| Competition | `COMPLETED` | success |
| Competition | `CANCELLED` | danger |
| Registration | `PENDING` | warning |
| Registration | `APPROVED` | success |
| Registration | `REJECTED` | danger |
| Registration | `WITHDRAWN` | neutral |
| ApprovalInstance | `IN_PROGRESS` | info |
| ApprovalInstance | `APPROVED` | success |
| ApprovalInstance | `REJECTED` | danger |
| ApprovalInstance | `CANCELLED` | neutral |
| Match | `SCHEDULED` | info |
| Match | `LIVE` | danger-soft bg + pulsing dot (the only animated badge) |
| Match | `COMPLETED` | success |
| Match | `WALKOVER` | warning |
| Match | `CANCELLED` | danger |
| Match | `POSTPONED` | warning |
| User | `ACTIVE` | success |
| User | `INVITED` | info |
| User | `SUSPENDED` | warning |
| User | `DEACTIVATED` | neutral |
| OrganizationUnit | `ACTIVE` | success |
| OrganizationUnit | `SUSPENDED` | warning |
| OrganizationUnit | `ARCHIVED` | neutral |

Badges always show the label text (localized) — color is reinforcement, never the sole signal (§5).

### 2.5 Tournament creation wizard
Multi-step wizard: **1. Basics** (name, dates, OrganizationUnit, slug preview with edit-before-publish note) → **2. Competitions** (add rows: sport, SportConfiguration pick/preview) → **3. Registration** (windows, form definition per competition) → **4. Review & publish**. Rules: progress indicator with step labels; steps validate before advance but allow back-navigation without loss; save-as-`DRAFT` at any step; the final step shows lifecycle consequences ("slug becomes immutable after publish").

### 2.6 Approval inbox card
One card per pending `ApprovalInstance`: entity summary (participant name, competition, tournament), submitted answers (dynamic-form renderer, read-only), workflow progress ("Level 2 of 3", prior `ApprovalAction`s with actor + comment + timestamp), and Approve / Reject actions. Reject requires a comment (blocking validation). After action, the card animates out and the inbox count decrements optimistically with rollback on failure.

### 2.7 Other components
- **Modal:** confirmations and small forms only; anything longer becomes a page or drawer. Destructive confirmations restate the object name and consequence ("Cancel *Haryana Games 2027*? Registrations will be closed permanently.").
- **Toast:** action feedback, auto-dismiss 5s, `aria-live="polite"`; errors persist until dismissed and include the correlation ID on expand.
- **Org-tree picker:** searchable, lazy-loaded subtree browser for selecting an `OrganizationUnit`; shows type badge (`FEDERATION`, `STATE_ASSOCIATION`, `DISTRICT_ASSOCIATION`, `ACADEMY`, `COLLEGE`, `CLUB`, `PRIVATE_ORGANIZER`) next to each node; only nodes within the caller's scope are selectable.
- **Leaderboard table:** public dense variant; columns driven by `leaderboardStrategy` (`POINTS_TABLE` shows P/W/D/L/Pts; `LOWEST_TIME` shows rank/time); auto-refresh poll every 30s while any Match in the competition is `LIVE`.
- **Match card:** public schedule/results view — participants, venue, time, status badge, score/result when `COMPLETED` or `WALKOVER`.
- **Workflow progress stepper:** renders `ApprovalStep` levels for an `ApprovalInstance` — completed levels with actor + timestamp, current level highlighted, future levels muted.

---

## 3. Layout Patterns

**Admin console:** fixed left sidebar (240px; collapses to icons at `lg`, drawer below) with nav scoped to the user's permissions — hide, don't disable, whole sections the user cannot access. Top bar: OrganizationUnit scope switcher (tree picker), user menu. Content: max-width 1280px, page title + primary action top-right, breadcrumbs reflecting the org hierarchy (SAI / Haryana / Sonipat District / Tournament).

**Public tournament pages** (`/t/{tournament-slug}`): no sidebar; tenant-branded header (logo + tournament name); hero with tournament status badge and dates; tab nav — Overview · Competitions · Schedule/Results · Leaderboards · Register (visible only when Tournament is `REGISTRATION_OPEN`). Public pages are anonymous-first: no login required to view; registration prompts auth only at submit. Server-render-friendly and shareable (OpenGraph tags per tournament).

---

## 4. Responsive Breakpoints

| Token | Min width | Targets |
|---|---|---|
| `sm` | 0 | Phones — public pages must be excellent here (participants and spectators are mobile-majority) |
| `md` | 640px | Large phones / small tablets — tables become cards below this |
| `lg` | 1024px | Tablets / laptops — sidebar collapses below this |
| `xl` | 1280px | Admin default; content max-width |

Admin console is optimized `lg`+ but must remain usable at `sm` (approvals inbox and result entry are the two admin flows officials genuinely use on phones — treat them as mobile-first).

---

## 5. Accessibility — WCAG 2.1 AA

- **Contrast:** text ≥ 4.5:1, large text and UI components ≥ 3:1. The theming pipeline validates tenant-supplied brand colors at save time and auto-derives `--tms-on-primary` (white/black) to preserve ratios; failing combinations are rejected with guidance.
- **Keyboard:** every interactive element reachable and operable by keyboard; visible focus ring (2px, `--tms-primary`, never removed); modal focus trap + `Esc` to close; skip-to-content link; wizard steps and inbox actions fully keyboard-operable.
- **Semantics:** native elements first; ARIA only to fill gaps; tables use real `<table>` markup; status badges include text (never color-only meaning); form fields programmatically associated with labels and errors (`aria-describedby`); toasts and leaderboard live-updates announced via `aria-live`.
- **Testing:** axe-core in component CI; manual keyboard + screen-reader pass (NVDA/VoiceOver) per sprint on new screens.

## 6. Empty / Loading / Error States

Every list and detail view ships all three states — no blank panels.
- **Empty:** illustration-light message + explanation + primary CTA when actionable ("No competitions yet — Add competition"). Empty-because-filtered shows "Clear filters" instead.
- **Loading:** skeleton screens matching final layout (rows for tables, blocks for cards); spinners only for sub-300ms inline actions; never layout-shift on load completion.
- **Error:** inline panel with human message derived from problem+json `title`/`detail`, a Retry action, and the correlation ID in small print for support. 403 states explain scope ("You don't have access to this organization") rather than generic failure. Full-page error boundary as last resort.

## 7. White-Label Rules

- **Tenant theme** stored on the root `OrganizationUnit`: logo (SVG/PNG, light + dark variants), `--tms-primary`, `--tms-secondary` (+ derived hover/soft/on-* values computed by the pipeline). Applied by injecting a per-tenant CSS-variable block at document root; zero per-tenant component code.
- **Cannot be overridden:** semantic colors, spacing, typography scale, layout, accessibility behavior.
- **Logo placement:** admin sidebar header and public page header; fixed bounding box (contain-fit) so any aspect ratio works.
- **Custom domains:** post-MVP (see 11_FUTURE_ENHANCEMENTS); until then tenant branding appears on shared-domain slug URLs. Design nothing that assumes the platform domain in copy or emails.
- Default (unthemed) palette is the platform brand — a new tenant looks polished before uploading anything.

## 8. Iconography & Motion

- One icon set platform-wide (Lucide), 20px default, stroke inherits `currentColor` so icons follow theme tokens automatically. Icons accompany text; icon-only buttons require `aria-label` and a tooltip.
- Motion is functional only: 150–200ms ease-out for hover/expand, 250ms for modals/drawers; the `LIVE` badge pulse is the single decorative animation. Respect `prefers-reduced-motion` — all non-essential animation disabled.

## 9. i18n Readiness

MVP ships English-only, but: all strings live in locale resource files from day one (no literals in components); no string concatenation for sentences (ICU message format for plurals/interpolation); dates/numbers rendered via `Intl` with tenant locale placeholder; layouts tolerate +40% text expansion; enum labels (statuses, org-unit types) map through the locale layer, never rendered raw from the API. RTL is explicitly out of scope for V1 but avoid direction-dependent icons where a neutral option exists.
