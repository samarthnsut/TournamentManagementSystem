-- Sprint 7: the immutable audit trail. DDL follows 04_DATABASE_DESIGN section 8.3 and ADR-014.
--
-- Two deliberate departures from doc 04's table spec, both recorded in ADR-017:
--
-- 1. Not partitioned. ADR-014's own consequences defer time-based partitioning/archival to doc 11,
--    and a partitioned table without a partition-maintenance job is worse than none: inserts start
--    failing the moment the clock passes the last declared partition. The primary key is therefore
--    plain `id`; when partitioning arrives it becomes (id, timestamp) in the same migration that
--    introduces the maintenance job.
-- 2. ip_address is varchar(45) rather than inet. Nothing in V1 queries by subnet, and inet needs a
--    custom Hibernate type to bind from a String — cost with no query benefit. 45 chars holds a
--    full IPv6 address with an IPv4 tail.

create table audit_log (
    id uuid primary key,
    -- Null for system actions: the approval engine auto-approving under a no-workflow policy has
    -- no human actor, and inventing one would be a lie in the record.
    actor_id uuid null references app_user (id),
    action varchar(80) not null,
    entity_type varchar(50) not null,
    entity_id uuid not null,
    before_state jsonb null,
    after_state jsonb null,
    organization_unit_id uuid null references organization_unit (id),
    ip_address varchar(45) null,
    timestamp timestamptz not null default now()
);

-- The two read paths that exist: a tenant admin scanning their own subtree's activity, and the
-- history of one entity.
create index ix_audit_log_org_time on audit_log (organization_unit_id, timestamp desc);
create index ix_audit_log_entity on audit_log (entity_type, entity_id, timestamp desc);
create index ix_audit_log_actor_time on audit_log (actor_id, timestamp desc);

comment on table audit_log is
    'Append-only. No update or delete path exists in application code (ADR-014); corrections are '
    'recorded as further rows, never by editing history.';
