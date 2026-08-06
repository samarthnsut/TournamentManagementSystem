-- Sprint 3 competition core: sport catalog, strategy configuration, tournaments,
-- competitions and venues. DDL follows 04_DATABASE_DESIGN sections 10.3, 10.4 and 10.6.

create table sport (
    id uuid primary key,
    code varchar(50) not null,
    name varchar(100) not null,
    description text null,
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    deleted_at timestamptz null
);

create unique index ux_sport_code on sport (code) where deleted_at is null;

create table sport_configuration (
    id uuid primary key,
    organization_unit_id uuid not null references organization_unit (id),
    sport_id uuid not null references sport (id),
    config jsonb not null,
    version int not null default 1,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    deleted_at timestamptz null,
    -- Defence in depth: the factories validate these keys too, but a bad direct write
    -- must never reach a strategy lookup.
    constraint ck_sc_fixture_generator check (
        config ->> 'fixtureGenerator' in ('ROUND_ROBIN', 'SINGLE_ELIMINATION', 'DOUBLE_ELIMINATION', 'SWISS', 'NONE')),
    constraint ck_sc_result_evaluator check (
        config ->> 'resultEvaluator' in ('POINTS', 'WIN_LOSS', 'TIME', 'DISTANCE', 'SCORE')),
    constraint ck_sc_leaderboard_strategy check (
        config ->> 'leaderboardStrategy' in ('POINTS_TABLE', 'LOWEST_TIME', 'HIGHEST_DISTANCE', 'HIGHEST_SCORE', 'BRACKET')),
    constraint ck_sc_participant_type check (
        config ->> 'participantType' in ('INDIVIDUAL', 'TEAM', 'ORGANIZATION'))
);

create index ix_sport_config_org on sport_configuration (organization_unit_id, sport_id) where deleted_at is null;
create index ix_sport_config_config on sport_configuration using gin (config jsonb_path_ops);

create table tournament (
    id uuid primary key,
    organization_unit_id uuid not null references organization_unit (id),
    name varchar(200) not null,
    slug varchar(120) not null,
    description text null,
    start_date date null,
    end_date date null,
    status varchar(30) not null default 'DRAFT',
    published_at timestamptz null,
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    deleted_at timestamptz null,
    constraint ck_tournament_status check (status in (
        'DRAFT', 'PUBLISHED', 'REGISTRATION_OPEN', 'REGISTRATION_CLOSED',
        'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'ARCHIVED')),
    constraint ck_tournament_dates check (start_date is null or end_date is null or start_date <= end_date)
);

-- The public /t/{slug} lookup is the hottest unauthenticated read in the system.
create unique index ux_tournament_slug on tournament (slug) where deleted_at is null;
create index ix_tournament_org_status on tournament (organization_unit_id, status) where deleted_at is null;

create table competition (
    id uuid primary key,
    tournament_id uuid not null references tournament (id),
    organization_unit_id uuid not null references organization_unit (id),
    sport_id uuid not null references sport (id),
    sport_configuration_id uuid not null references sport_configuration (id),
    name varchar(200) not null,
    max_registrations int null,
    registration_open_at timestamptz null,
    registration_close_at timestamptz null,
    status varchar(20) not null default 'DRAFT',
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    deleted_at timestamptz null,
    constraint ck_competition_status check (status in ('DRAFT', 'OPEN', 'CLOSED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    constraint ck_competition_max_registrations check (max_registrations is null or max_registrations > 0)
);

create index ix_competition_tournament on competition (tournament_id) where deleted_at is null;
create index ix_competition_org_status on competition (organization_unit_id, status) where deleted_at is null;

create table venue (
    id uuid primary key,
    organization_unit_id uuid not null references organization_unit (id),
    name varchar(200) not null,
    address_line varchar(300) null,
    city varchar(100) null,
    state varchar(100) null,
    capacity int null,
    facilities jsonb null,
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    deleted_at timestamptz null
);

create index ix_venue_org on venue (organization_unit_id) where deleted_at is null;
