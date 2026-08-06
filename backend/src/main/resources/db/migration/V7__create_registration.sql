-- Sprint 4: participants, dynamic registration forms and registrations.
-- DDL follows 04_DATABASE_DESIGN sections 8.2 and 10.5.

create table participant (
    id uuid primary key,
    organization_unit_id uuid not null references organization_unit (id),
    participant_type varchar(20) not null,
    display_name varchar(200) not null,
    contact_email varchar(320) null,
    profile jsonb null,
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    deleted_at timestamptz null,
    constraint ck_participant_type check (participant_type in ('INDIVIDUAL', 'TEAM', 'ORGANIZATION'))
);

create index ix_participant_org_type
    on participant (organization_unit_id, participant_type)
    where deleted_at is null;

create table team_member (
    id uuid primary key,
    participant_id uuid not null references participant (id),
    user_id uuid null references app_user (id),
    full_name varchar(200) not null,
    date_of_birth date null,
    member_role varchar(20) not null default 'PLAYER',
    jersey_number int null,
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    deleted_at timestamptz null,
    constraint ck_team_member_role check (member_role in ('CAPTAIN', 'PLAYER', 'COACH'))
);

-- A team has at most one captain (BR-TM-2).
create unique index ux_team_member_captain
    on team_member (participant_id)
    where member_role = 'CAPTAIN' and deleted_at is null;

create index ix_team_member_participant
    on team_member (participant_id)
    where deleted_at is null;

create table registration_form_definition (
    id uuid primary key,
    organization_unit_id uuid not null references organization_unit (id),
    competition_id uuid not null references competition (id),
    version int not null,
    schema jsonb not null,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    deleted_at timestamptz null,
    constraint ux_form_definition_version unique (competition_id, version)
);

-- Exactly one active version per competition (BR-RFD-1).
create unique index ux_form_definition_active
    on registration_form_definition (competition_id)
    where is_active and deleted_at is null;

create table registration (
    id uuid primary key,
    organization_unit_id uuid not null references organization_unit (id),
    competition_id uuid not null references competition (id),
    participant_id uuid not null references participant (id),
    status varchar(20) not null default 'PENDING',
    submitted_at timestamptz not null default now(),
    decided_at timestamptz null,
    withdrawn_at timestamptz null,
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    deleted_at timestamptz null,
    constraint ck_registration_status check (status in ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN'))
);

-- One live registration per participant per competition; withdrawing frees the slot (BR-REG-2).
create unique index ux_registration_live
    on registration (competition_id, participant_id)
    where status <> 'WITHDRAWN' and deleted_at is null;

create index ix_registration_org_comp_status
    on registration (organization_unit_id, competition_id, status)
    where deleted_at is null;

create index ix_registration_participant
    on registration (participant_id)
    where deleted_at is null;

create table registration_response (
    id uuid primary key,
    registration_id uuid not null references registration (id),
    form_definition_id uuid not null references registration_form_definition (id),
    answers jsonb not null,
    submitted_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    constraint ux_registration_response_registration unique (registration_id)
);

-- Organizers filter on answer contents ("all entrants with bloodGroup O+"); without this those
-- queries are sequential scans over JSONB.
create index ix_reg_response_answers
    on registration_response using gin (answers jsonb_path_ops);
