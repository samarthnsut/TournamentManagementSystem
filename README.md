# Tournament Management Frontend (Static MVP)

Scaffolded Next.js + TypeScript + Tailwind starter for the tournament management frontend.

The project now also includes a Spring Boot backend scaffold in `backend/`. The dashboard and create tournament page are wired to the backend API through `NEXT_PUBLIC_API_BASE_URL`.

To run the frontend locally:

```bash
npm install
npm run dev
```

To run the backend locally:

```bash
cd backend
docker compose up -d
./gradlew bootRun --args='--spring.profiles.active=dev'
```

The `dev` profile supplies the local database credentials and a development signing secret. Outside
it the app requires `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and `JWT_SECRET`, and will not start
until they are set.

Gradle itself does not need to be installed, but it must run on a JDK between 17 and 24 — Java 25
is not yet supported. Docker is required for the database and the integration tests. See
[`backend/README.md`](backend/README.md) for the full setup and the design docs in
[`Arch & context documents`](Arch%20&%20context%20documents).

Create `.env.local` from `.env.local.example` before starting the frontend:

```text
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
```

## Signing in locally

Start the backend with the `dev` profile and it seeds a demo organization tree with one user per
role. See [`TEST_ACCOUNTS.md`](TEST_ACCOUNTS.md) for the list — `haryana.admin@example.com` with
password `StrongPass123` is the usual starting point.
