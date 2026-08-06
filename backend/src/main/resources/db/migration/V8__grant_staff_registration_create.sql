-- 08_API_CONTRACTS section 9.1 describes POST /registrations as a participant action, and the
-- Sprint 2 seed followed that literally: only PARTICIPANT_USER could create one.
--
-- That leaves no way to enter a club that submits a paper form, which is how district and state
-- associations actually collect entries. Staff who already administer the competition can now
-- register on a participant's behalf, within their own subtree as always — the scope check is
-- unchanged, so this grants no reach beyond what they can already administer.
--
-- Participants keep the permission too; this only widens who else holds it.
insert into role_permission (id, role_id, permission_id)
select gen_random_uuid(), r.id, p.id
from role r
join permission p on p.code = 'registration:create'
where r.code in ('TENANT_ADMIN', 'ORG_OFFICIAL', 'TOURNAMENT_ADMIN')
  and not exists (
      select 1 from role_permission rp where rp.role_id = r.id and rp.permission_id = p.id
  );
