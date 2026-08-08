-- Password reset. The /forgot-password and /reset-password screens shipped in Sprint 1 with no
-- backend at all, so a locked-out user had no route back in.
--
-- Separate columns from the invite token rather than reusing it: an invite and a reset mean
-- different things, can legitimately be outstanding at the same time, and consuming one must not
-- silently consume the other.

alter table app_user
    -- Only the SHA-256 hash is stored, exactly as for invite tokens. A reset token in the database
    -- in readable form is a password in the database in readable form.
    add column password_reset_token_hash varchar(64) null,
    add column password_reset_expires_at timestamptz null;

-- The lookup is by hash on every reset attempt. Partial, because the column is null for almost
-- every row almost all of the time.
create index ix_app_user_password_reset
    on app_user (password_reset_token_hash)
    where password_reset_token_hash is not null;
