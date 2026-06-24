-- ============================================================
--  UniPortal — Grade approval queue (idempotent migration)
--  A teacher SUBMITS a result → it waits here as 'pending' →
--  the ADMIN approves it → only then is it written to enrollments
--  (the official grade students can see).
--  Run with:  psql -U postgres -d university -f approvals.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS grade_submissions (
    id           SERIAL PRIMARY KEY,
    student_id   INT NOT NULL REFERENCES students(id),
    course_id    INT NOT NULL REFERENCES courses(id),
    teacher_id   INT NOT NULL REFERENCES teachers(id),
    score        NUMERIC(5,2) NOT NULL CHECK (score >= 0 AND score <= 100),
    status       TEXT NOT NULL DEFAULT 'pending',   -- pending | approved | rejected
    submitted_at TIMESTAMP NOT NULL DEFAULT now(),
    decided_at   TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_submissions_status  ON grade_submissions(status);
CREATE INDEX IF NOT EXISTS idx_submissions_teacher ON grade_submissions(teacher_id);
