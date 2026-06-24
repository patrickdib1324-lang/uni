-- ============================================================
--  UniPortal — Account sign-up requests (idempotent migration)
--  New students AND teachers do NOT get an account immediately.
--  Their sign-up is stored here as 'pending' and SENT to the admin,
--  who accepts or rejects it (stops fake students/teachers).
--  Only on accept is a real users/students/teachers row created.
--  Run with:  psql -U postgres -d university -f accounts.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS account_requests (
    id           SERIAL PRIMARY KEY,
    name         TEXT NOT NULL,
    email        TEXT,
    username     TEXT NOT NULL,
    password     TEXT NOT NULL,
    role         TEXT NOT NULL CHECK (role IN ('student','teacher')),
    status       TEXT NOT NULL DEFAULT 'pending',   -- pending | approved | rejected
    requested_at TIMESTAMP NOT NULL DEFAULT now(),
    decided_at   TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_acct_req_status ON account_requests(status);
