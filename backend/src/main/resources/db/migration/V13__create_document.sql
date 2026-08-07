-- Sprint 7: documents. DDL follows 04_DATABASE_DESIGN section 8.3 for `document`.
--
-- `document_upload` is not in doc 04, and is not optional. Uploads are two-phase (08 §14): the
-- server presigns a PUT, the client uploads straight to object storage, then calls attach. Without
-- a record of what was authorized, `attach` would have to trust the client for the object key, the
-- owning entity, the declared size and the mime type — which is to say it would let a caller
-- attach any object they liked to any entity they liked. This table is the server's memory of what
-- it actually signed.

create table document_upload (
    id uuid primary key,
    organization_unit_id uuid not null references organization_unit (id),
    entity_type varchar(50) not null,
    entity_id uuid not null,
    file_name varchar(255) not null,
    -- The key is chosen by the server, never by the client: a client-supplied key is a path
    -- traversal waiting to happen, and lets one tenant overwrite another's object.
    object_key varchar(1024) not null,
    mime_type varchar(120) not null,
    declared_size_bytes bigint not null,
    requested_by uuid not null references app_user (id),
    expires_at timestamptz not null,
    attached_at timestamptz null,
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    constraint ck_document_upload_size check (declared_size_bytes > 0)
);

create index ix_document_upload_entity on document_upload (entity_type, entity_id);

create table document (
    id uuid primary key,
    organization_unit_id uuid not null references organization_unit (id),
    entity_type varchar(50) not null,
    entity_id uuid not null,
    file_name varchar(255) not null,
    file_url varchar(1024) not null,
    mime_type varchar(120) not null,
    size_bytes bigint not null,
    uploaded_by uuid not null references app_user (id),
    created_at timestamptz not null default now(),
    created_by uuid null,
    updated_at timestamptz not null default now(),
    updated_by uuid null,
    deleted_at timestamptz null,
    constraint ck_document_size check (size_bytes > 0)
);

-- "The attachments of this registration" is the only read that matters; the org index backs the
-- tenant-scoped listing.
create index ix_document_entity on document (entity_type, entity_id) where deleted_at is null;
create index ix_document_org on document (organization_unit_id) where deleted_at is null;

-- One upload becomes at most one document (08 §14.2 ALREADY_ATTACHED).
create unique index ux_document_object_key on document (file_url) where deleted_at is null;
