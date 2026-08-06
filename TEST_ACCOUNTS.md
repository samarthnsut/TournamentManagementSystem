# Local Test Accounts

Quick reference for signing in while developing. These accounts are created by
[`DevDataSeeder`](backend/src/main/java/com/acme/tms/common/config/DevDataSeeder.java) and exist
**only when the backend runs with the `dev` profile** — they are never created in staging or
production, and the seeder does nothing if the data is already there.

## Password

Every seeded account uses the same one:

```text
StrongPass123
```

## Accounts

| Email | Role | What it can reach | Good for testing |
|---|---|---|---|
| `super.admin@example.com` | `SUPER_ADMIN` (GLOBAL) | Everything, platform-wide | Anything; creating new root organizations |
| `haryana.admin@example.com` | `TENANT_ADMIN` (ORGANIZATION) | Haryana + Sonipat | **The default choice.** Full tournament lifecycle inside one tenant |
| `punjab.admin@example.com` | `TENANT_ADMIN` (ORGANIZATION) | Punjab + Ludhiana | Cross-tenant isolation — it must *not* see Haryana's data |
| `sonipat.official@example.com` | `ORG_OFFICIAL` (ORGANIZATION) | Sonipat district only | A narrower scope; read and update, but no tournament creation |
| `participant@example.com` | `PARTICIPANT_USER` (GLOBAL) | Read-only public data | What an ordinary entrant sees; most admin calls should 403 |

## The organization tree they sit in

```text
Sports Authority of India            (FEDERATION, slug: sai)
├── Haryana State Association        (STATE_ASSOCIATION, slug: haryana)
│   └── Sonipat District Association (DISTRICT_ASSOCIATION, slug: sonipat-district)
└── Punjab State Association         (STATE_ASSOCIATION, slug: punjab)
    └── Ludhiana District Association (DISTRICT_ASSOCIATION, slug: ludhiana-district)
```

An `ORGANIZATION`-scoped grant covers the whole subtree beneath it, which is why the Haryana admin
also reaches Sonipat but never Punjab.

## Starting the stack

Backend, with the seed loaded:

```bash
cd backend && docker compose up -d && ./gradlew bootRun --args='--spring.profiles.active=dev'
```

Frontend:

```bash
npm run dev
```

Then sign in at <http://localhost:3000/TournamentManagementSystem/signin>.

## Re-seeding

The seeder skips everything if `super.admin@example.com` already exists, so it is safe to restart.
To get a genuinely clean slate, drop the database volume and let Flyway rebuild it:

```bash
cd backend && docker compose down -v && docker compose up -d
```

## A note on signing up

`POST /api/v1/auth/register` is a bootstrap endpoint: it creates a **new organization** and makes
the registrant its `TENANT_ADMIN`. So an account created through the sign-up form is an organizer
with its own empty tenant, not a member of the tree above. The role dropdown on that form only
chooses between `FEDERATION` and `PRIVATE_ORGANIZER` for the new organization; a self-registration
path for athletes does not exist yet.
