-- Sprint 5: tenant-configurable approval chains.
-- DDL follows 04_DATABASE_DESIGN section 10.7 and the approval_instance / approval_action tables.

create table approval_workflow (
    id uuid primary key,
    organization_unit_id uuid not null references organization_unit (id),
    workflow_name varchar(150) not null,
    entity_type varchar(50) not null,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    deleted_at timestamptz null
);

-- One active chain per organization unit per entity type; resolution walks ancestors from there.
create unique index ux_workflow_active
    on approval_workflow (organization_unit_id, entity_type)
    where is_active and deleted_at is null;

create table approval_step (
    id uuid primary key,
    workflow_id uuid not null references approval_workflow (id) on delete cascade,
    level int not null check (level >= 1),
    role_code varchar(50) not null,
    step_name varchar(150) null,
    approval_required boolean not null default true,
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    deleted_at timestamptz null,
    constraint ux_step_level unique (workflow_id, level)
);

create table approval_instance (
    id uuid primary key,
    organization_unit_id uuid not null references organization_unit (id),
    -- Nullable, which departs from doc 04's NOT NULL, because doc 07 section 7.2 requires an
    -- implicit one-level chain when a tenant has configured no workflow. That chain has no
    -- workflow row to point at, and inventing one would collide with ux_workflow_active the
    -- moment the tenant configures a real chain. Null therefore means "implicit single step".
    workflow_id uuid null references approval_workflow (id),
    entity_type varchar(50) not null,
    entity_id uuid not null,
    current_level int not null default 1,
    status varchar(20) not null default 'IN_PROGRESS',
    -- Optimistic lock: two approvers acting on the same level must not both advance it.
    lock_version bigint not null default 0,
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    deleted_at timestamptz null,
    constraint ck_approval_instance_status check (status in ('IN_PROGRESS', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

-- At most one open instance per target (BR-AI-1).
create unique index ux_approval_instance_open
    on approval_instance (entity_type, entity_id)
    where status = 'IN_PROGRESS';

-- The approver work queue: everything waiting at my level, in my organization.
create index ix_approval_instance_org_status
    on approval_instance (organization_unit_id, status, current_level);

create index ix_approval_instance_entity
    on approval_instance (entity_type, entity_id);

-- Append-only: no updates, no deletes, no soft delete. With AuditLog this is the approval trail.
create table approval_action (
    id uuid primary key,
    instance_id uuid not null references approval_instance (id),
    step_level int not null,
    actor_id uuid not null references app_user (id),
    decision varchar(20) not null,
    comment text null,
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    constraint ck_approval_action_decision check (decision in ('APPROVE', 'REJECT'))
);

-- A double-clicked approve cannot record twice or double-advance the level.
create unique index ux_approval_action_once
    on approval_action (instance_id, step_level, actor_id, decision);

create index ix_approval_action_instance
    on approval_action (instance_id, created_at);
