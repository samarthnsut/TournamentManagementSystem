-- Venues shipped a table in V5 and an entity, but never a permission, an endpoint or a screen.
-- The organizer UI needs all three; this adds the catalog entries the endpoints check against.
--
-- Scoped like every other tenant-owned resource: a venue belongs to an organization unit, so an
-- ORGANIZATION grant covers the subtree.

insert into permission (id, code, description) values
    (gen_random_uuid(), 'venue:create', 'Create a venue'),
    (gen_random_uuid(), 'venue:read', 'Read venues'),
    (gen_random_uuid(), 'venue:update', 'Update a venue'),
    (gen_random_uuid(), 'venue:delete', 'Archive a venue');

-- SUPER_ADMIN holds every permission by definition (05 section 4.1); the V4 seed granted the
-- catalog as it stood then, so new codes have to be added to it explicitly.
insert into role_permission (id, role_id, permission_id)
select gen_random_uuid(), r.id, p.id
from role r
join permission p on p.code in ('venue:create', 'venue:read', 'venue:update', 'venue:delete')
where r.code = 'SUPER_ADMIN'
  and not exists (select 1 from role_permission rp where rp.role_id = r.id and rp.permission_id = p.id);

-- Administering venues is an organizational act, not a day-of one: the roles that already manage
-- an organization's tournaments manage its grounds.
insert into role_permission (id, role_id, permission_id)
select gen_random_uuid(), r.id, p.id
from role r
join permission p on p.code in ('venue:create', 'venue:read', 'venue:update', 'venue:delete')
where r.code in ('TENANT_ADMIN', 'ORG_OFFICIAL')
  and not exists (select 1 from role_permission rp where rp.role_id = r.id and rp.permission_id = p.id);

-- Everyone who schedules matches needs to see the list to pick from it, without being able to
-- change it.
insert into role_permission (id, role_id, permission_id)
select gen_random_uuid(), r.id, p.id
from role r
join permission p on p.code = 'venue:read'
where r.code in ('TOURNAMENT_ADMIN', 'COMPETITION_OFFICIAL')
  and not exists (select 1 from role_permission rp where rp.role_id = r.id and rp.permission_id = p.id);
