create table organization_unit (
    id uuid primary key,
    parent_organization_unit_id uuid null references organization_unit(id) on delete restrict,
    name varchar(200) not null,
    slug varchar(120) not null,
    type varchar(30) not null,
    status varchar(20) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    deleted_at timestamptz null,
    constraint ck_organization_unit_type check (type in ('FEDERATION', 'STATE_ASSOCIATION', 'DISTRICT_ASSOCIATION', 'ACADEMY', 'COLLEGE', 'CLUB', 'PRIVATE_ORGANIZER')),
    constraint ck_organization_unit_status check (status in ('ACTIVE', 'SUSPENDED', 'ARCHIVED'))
);

create unique index ux_organization_unit_slug
    on organization_unit (slug)
    where deleted_at is null;

create index ix_organization_unit_parent
    on organization_unit (parent_organization_unit_id)
    where deleted_at is null;

create table app_user (
    id uuid primary key,
    email varchar(320) not null,
    password_hash varchar(100) null,
    full_name varchar(200) not null,
    phone varchar(20) null,
    status varchar(20) not null default 'INVITED',
    invite_token_hash varchar(64) null,
    invite_expires_at timestamptz null,
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    deleted_at timestamptz null,
    constraint ck_app_user_status check (status in ('ACTIVE', 'INVITED', 'SUSPENDED', 'DEACTIVATED'))
);

create unique index ux_app_user_email
    on app_user (lower(email))
    where deleted_at is null;

create index ix_app_user_status
    on app_user (status)
    where deleted_at is null;

create table refresh_token (
    id uuid primary key,
    user_id uuid not null references app_user(id) on delete restrict,
    token_hash varchar(64) not null unique,
    family_id uuid not null,
    expires_at timestamptz not null,
    revoked_at timestamptz null,
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null
);

create index ix_refresh_token_user
    on refresh_token (user_id);

create index ix_refresh_token_family
    on refresh_token (family_id);

