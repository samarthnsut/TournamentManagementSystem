-- Whether a submitted registration needs a human decision.
--
-- 07_APPROVAL_WORKFLOW_ENGINE section 7.2 defines this policy and its two values, but places it on
-- the tenant. One switch per organization is too coarse in practice: a federation runs a strict
-- national championship and a casual district friendly under the same organization. The tournament
-- column overrides, and null means "inherit", so the documented tenant policy becomes the default
-- rather than the only answer.
--
-- A configured ApprovalWorkflow still outranks both once Sprint 5 lands; this policy is precisely
-- the "no workflow configured" fallback the doc describes.

alter table organization_unit
    add column registration_approval_policy varchar(30) not null default 'DIRECT_SINGLE_APPROVAL';

alter table organization_unit
    add constraint ck_org_unit_approval_policy
        check (registration_approval_policy in ('AUTO_APPROVE', 'DIRECT_SINGLE_APPROVAL'));

-- Nullable on purpose: null inherits from the owning organization unit.
alter table tournament
    add column registration_approval_policy varchar(30) null;

alter table tournament
    add constraint ck_tournament_approval_policy
        check (registration_approval_policy is null
               or registration_approval_policy in ('AUTO_APPROVE', 'DIRECT_SINGLE_APPROVAL'));
