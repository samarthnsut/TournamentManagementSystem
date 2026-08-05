insert into permission (id, code, description) values
    (gen_random_uuid(), 'organization:create', 'Create a child organization unit'),
    (gen_random_uuid(), 'organization:read', 'Read an organization unit'),
    (gen_random_uuid(), 'organization:update', 'Update an organization unit'),
    (gen_random_uuid(), 'organization:delete', 'Archive an organization unit'),
    (gen_random_uuid(), 'user:invite', 'Invite a user into an organization unit'),
    (gen_random_uuid(), 'user:read', 'Read users within scope'),
    (gen_random_uuid(), 'role:assign', 'Grant or revoke a role assignment'),
    (gen_random_uuid(), 'role:read', 'Read role assignments'),
    (gen_random_uuid(), 'sport:read', 'Read the sport catalog'),
    (gen_random_uuid(), 'sport-config:create', 'Create a sport configuration'),
    (gen_random_uuid(), 'sport-config:read', 'Read a sport configuration'),
    (gen_random_uuid(), 'sport-config:update', 'Update a sport configuration'),
    (gen_random_uuid(), 'tournament:create', 'Create a tournament'),
    (gen_random_uuid(), 'tournament:read', 'Read a tournament'),
    (gen_random_uuid(), 'tournament:update', 'Update a tournament'),
    (gen_random_uuid(), 'tournament:delete', 'Delete a tournament'),
    (gen_random_uuid(), 'tournament:transition', 'Transition a tournament lifecycle state'),
    (gen_random_uuid(), 'competition:create', 'Create a competition'),
    (gen_random_uuid(), 'competition:read', 'Read a competition'),
    (gen_random_uuid(), 'competition:update', 'Update a competition'),
    (gen_random_uuid(), 'competition:transition', 'Transition a competition lifecycle state'),
    (gen_random_uuid(), 'form:create', 'Create a registration form definition'),
    (gen_random_uuid(), 'form:read', 'Read a registration form definition'),
    (gen_random_uuid(), 'form:update', 'Update a registration form definition'),
    (gen_random_uuid(), 'registration:create', 'Submit a registration'),
    (gen_random_uuid(), 'registration:read', 'Read a registration'),
    (gen_random_uuid(), 'registration:update', 'Update or withdraw a registration'),
    (gen_random_uuid(), 'registration:approve', 'Approve or reject a registration'),
    (gen_random_uuid(), 'fixture:generate', 'Generate fixtures for a competition'),
    (gen_random_uuid(), 'fixture:read', 'Read fixtures'),
    (gen_random_uuid(), 'match:schedule', 'Schedule or update a match'),
    (gen_random_uuid(), 'match:read', 'Read matches'),
    (gen_random_uuid(), 'result:record', 'Record a match result'),
    (gen_random_uuid(), 'leaderboard:read', 'Read a leaderboard'),
    (gen_random_uuid(), 'approval:read', 'Read the approvals inbox'),
    (gen_random_uuid(), 'document:upload', 'Upload a document'),
    (gen_random_uuid(), 'document:read', 'Read a document'),
    (gen_random_uuid(), 'audit:read', 'Read audit log entries');

insert into role (id, code, name, description, default_scope_type, is_system_role) values
    (gen_random_uuid(), 'SUPER_ADMIN', 'Super Admin', 'Platform-wide administrator with every permission.', 'GLOBAL', true),
    (gen_random_uuid(), 'TENANT_ADMIN', 'Tenant Admin', 'Full administrative control over an organization unit subtree.', 'ORGANIZATION', true),
    (gen_random_uuid(), 'ORG_OFFICIAL', 'Organization Official', 'Operational staff member within an organization unit subtree.', 'ORGANIZATION', true),
    (gen_random_uuid(), 'TOURNAMENT_ADMIN', 'Tournament Admin', 'Administers a single tournament.', 'TOURNAMENT', true),
    (gen_random_uuid(), 'COMPETITION_OFFICIAL', 'Competition Official', 'Runs a single competition (fixtures, results).', 'COMPETITION', true),
    (gen_random_uuid(), 'PARTICIPANT_USER', 'Participant', 'A registered participant managing their own registrations.', 'GLOBAL', true),
    (gen_random_uuid(), 'PUBLIC_VIEWER', 'Public Viewer', 'Read-only public visitor.', 'GLOBAL', true);

-- SUPER_ADMIN: every permission in the catalog.
insert into role_permission (id, role_id, permission_id)
select gen_random_uuid(), r.id, p.id
from role r cross join permission p
where r.code = 'SUPER_ADMIN';

insert into role_permission (id, role_id, permission_id)
select gen_random_uuid(), r.id, p.id
from role r join permission p on p.code in (
    'organization:create', 'organization:read', 'organization:update', 'organization:delete',
    'user:invite', 'user:read', 'role:assign', 'role:read',
    'sport:read', 'sport-config:create', 'sport-config:read', 'sport-config:update',
    'tournament:create', 'tournament:read', 'tournament:update', 'tournament:delete', 'tournament:transition',
    'competition:create', 'competition:read', 'competition:update', 'competition:transition',
    'form:create', 'form:read', 'form:update',
    'registration:read', 'registration:update', 'registration:approve',
    'fixture:generate', 'fixture:read', 'match:schedule', 'match:read', 'result:record',
    'leaderboard:read', 'approval:read', 'document:upload', 'document:read', 'audit:read'
)
where r.code = 'TENANT_ADMIN';

insert into role_permission (id, role_id, permission_id)
select gen_random_uuid(), r.id, p.id
from role r join permission p on p.code in (
    'organization:read', 'user:read',
    'sport:read', 'sport-config:read',
    'tournament:read', 'tournament:update',
    'competition:read', 'competition:update',
    'form:create', 'form:read', 'form:update',
    'registration:read', 'registration:update', 'registration:approve',
    'fixture:generate', 'fixture:read', 'match:schedule', 'match:read', 'result:record',
    'leaderboard:read', 'approval:read', 'document:upload', 'document:read'
)
where r.code = 'ORG_OFFICIAL';

insert into role_permission (id, role_id, permission_id)
select gen_random_uuid(), r.id, p.id
from role r join permission p on p.code in (
    'tournament:read', 'tournament:update', 'tournament:transition',
    'competition:create', 'competition:read', 'competition:update', 'competition:transition',
    'form:create', 'form:read', 'form:update',
    'registration:read', 'registration:update', 'registration:approve',
    'fixture:generate', 'fixture:read', 'match:schedule', 'match:read', 'result:record',
    'leaderboard:read', 'approval:read', 'document:upload', 'document:read'
)
where r.code = 'TOURNAMENT_ADMIN';

insert into role_permission (id, role_id, permission_id)
select gen_random_uuid(), r.id, p.id
from role r join permission p on p.code in (
    'competition:read', 'competition:update',
    'registration:read', 'registration:approve',
    'fixture:read', 'match:schedule', 'match:read', 'result:record',
    'leaderboard:read', 'approval:read', 'document:upload', 'document:read'
)
where r.code = 'COMPETITION_OFFICIAL';

insert into role_permission (id, role_id, permission_id)
select gen_random_uuid(), r.id, p.id
from role r join permission p on p.code in (
    'tournament:read', 'competition:read', 'sport:read', 'form:read',
    'registration:create', 'registration:read', 'registration:update',
    'document:upload', 'document:read', 'leaderboard:read'
)
where r.code = 'PARTICIPANT_USER';

insert into role_permission (id, role_id, permission_id)
select gen_random_uuid(), r.id, p.id
from role r join permission p on p.code in (
    'tournament:read', 'competition:read', 'leaderboard:read', 'sport:read'
)
where r.code = 'PUBLIC_VIEWER';
