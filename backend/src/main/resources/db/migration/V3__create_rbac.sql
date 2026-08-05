create table role (
    id uuid primary key,
    code varchar(50) not null,
    name varchar(100) not null,
    description text null,
    default_scope_type varchar(20) not null,
    is_system_role boolean not null default true,
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    deleted_at timestamptz null,
    constraint ck_role_default_scope_type check (default_scope_type in ('GLOBAL', 'ORGANIZATION', 'TOURNAMENT', 'COMPETITION'))
);

create unique index ux_role_code
    on role (code)
    where deleted_at is null;

create table permission (
    id uuid primary key,
    code varchar(80) not null,
    description text null,
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    deleted_at timestamptz null
);

create unique index ux_permission_code
    on permission (code)
    where deleted_at is null;

create table role_permission (
    id uuid primary key,
    role_id uuid not null references role(id) on delete cascade,
    permission_id uuid not null references permission(id) on delete cascade,
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null
);

create unique index ux_role_permission
    on role_permission (role_id, permission_id);

create table user_role_assignment (
    id uuid primary key,
    user_id uuid not null references app_user(id) on delete restrict,
    role_id uuid not null references role(id) on delete restrict,
    scope_type varchar(20) not null,
    scope_id uuid null,
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    deleted_at timestamptz null,
    constraint ck_ura_scope_type check (scope_type in ('GLOBAL', 'ORGANIZATION', 'TOURNAMENT', 'COMPETITION')),
    constraint ck_ura_global_scope check ((scope_type = 'GLOBAL') = (scope_id is null))
);

create unique index ux_user_role_assignment
    on user_role_assignment (user_id, role_id, scope_type, coalesce(scope_id, '00000000-0000-0000-0000-000000000000'))
    where deleted_at is null;

create index ix_ura_user
    on user_role_assignment (user_id)
    where deleted_at is null;

create index ix_ura_scope
    on user_role_assignment (scope_type, scope_id)
    where deleted_at is null;
