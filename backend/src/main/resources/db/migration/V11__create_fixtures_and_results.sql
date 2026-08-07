-- Sprint 6: fixtures, matches, results and materialized leaderboards.
-- DDL follows 04_DATABASE_DESIGN section 8.2.

create table fixture (
    id uuid primary key,
    competition_id uuid not null references competition (id),
    round_number int not null,
    round_name varchar(100) null,
    generator_key varchar(30) not null,
    generated_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    constraint ux_fixture_round unique (competition_id, round_number),
    constraint ck_fixture_generator_key check (
        generator_key in ('ROUND_ROBIN', 'SINGLE_ELIMINATION', 'DOUBLE_ELIMINATION', 'SWISS', 'NONE'))
);

create index ix_fixture_competition on fixture (competition_id);

-- MATCH is non-reserved in PostgreSQL, so the doc-04 table name needs no quoting.
create table match (
    id uuid primary key,
    competition_id uuid not null references competition (id),
    -- Null only for matches an official creates outside a generated round.
    fixture_id uuid null references fixture (id),
    venue_id uuid null references venue (id),
    scheduled_at timestamptz null,
    status varchar(20) not null default 'SCHEDULED',
    version int not null default 0,
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    constraint ck_match_status check (
        status in ('SCHEDULED', 'LIVE', 'COMPLETED', 'WALKOVER', 'CANCELLED', 'POSTPONED'))
);

-- Live scoreboards read by competition + status; venue calendars and the BR-M-4 overlap warning
-- read by venue + time.
create index ix_match_competition_status on match (competition_id, status);
create index ix_match_venue_time on match (venue_id, scheduled_at);
create index ix_match_fixture on match (fixture_id);

create table match_participant (
    id uuid primary key,
    match_id uuid not null references match (id) on delete cascade,
    participant_id uuid not null references participant (id),
    slot varchar(20) null,
    seed int null,
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    constraint ux_match_participant unique (match_id, participant_id)
);

create index ix_match_participant_participant on match_participant (participant_id);

create table result (
    id uuid primary key,
    match_id uuid not null references match (id),
    evaluator_key varchar(20) not null,
    payload jsonb not null,
    winner_participant_id uuid null references participant (id),
    recorded_by uuid not null references app_user (id),
    recorded_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    constraint ux_result_match unique (match_id),
    constraint ck_result_evaluator_key check (
        evaluator_key in ('POINTS', 'WIN_LOSS', 'TIME', 'DISTANCE', 'SCORE'))
);

-- Derived data (BR-LE-2): every row here is recomputable from `result`, so the strategy rewrites
-- the whole competition's rows on each confirmation rather than patching them.
create table leaderboard_entry (
    id uuid primary key,
    competition_id uuid not null references competition (id),
    participant_id uuid not null references participant (id),
    rank int not null,
    metrics jsonb not null,
    computed_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    constraint ux_leaderboard_entry unique (competition_id, participant_id)
);

create index ix_leaderboard_comp_rank on leaderboard_entry (competition_id, rank);
