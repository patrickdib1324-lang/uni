-- ============================================================
--  UniPortal — Teachers & role wiring (idempotent migration)
--  Links each teacher account to the courses they actually teach,
--  so a teacher can only grade their own courses.
--  Run with:  psql -U postgres -d university -f teachers.sql
-- ============================================================

-- 1) TEACHERS — the people who teach courses
CREATE TABLE IF NOT EXISTS teachers (
    id    SERIAL PRIMARY KEY,
    name  TEXT UNIQUE NOT NULL,
    email TEXT UNIQUE
);

INSERT INTO teachers (name, email) VALUES
('Dr. Karim Jad',   'karim@uni.edu'),
('Dr. Hadi Nasser', 'hadi@uni.edu'),
('Dr. Rana Sayed',  'rana@uni.edu')
ON CONFLICT (name) DO NOTHING;

-- 2) Link each COURSE to its teacher (match on the existing text name)
ALTER TABLE courses ADD COLUMN IF NOT EXISTS teacher_id INT REFERENCES teachers(id);
UPDATE courses c
   SET teacher_id = t.id
  FROM teachers t
 WHERE c.teacher = t.name
   AND c.teacher_id IS DISTINCT FROM t.id;

-- 3) Link USER accounts to a teacher (only for teacher logins)
ALTER TABLE users ADD COLUMN IF NOT EXISTS teacher_id INT REFERENCES teachers(id);

-- 4) One login per teacher → each grades only their own courses
INSERT INTO users (username, password, role, teacher_id)
SELECT v.username, v.password, 'teacher', t.id
  FROM (VALUES
        ('karim', 'karim123', 'Dr. Karim Jad'),
        ('hadi',  'hadi123',  'Dr. Hadi Nasser'),
        ('rana',  'rana123',  'Dr. Rana Sayed')
       ) AS v(username, password, tname)
  JOIN teachers t ON t.name = v.tname
ON CONFLICT (username) DO NOTHING;

-- 5) Keep the generic demo 'teacher' login working: map it to Dr. Karim Jad
UPDATE users
   SET teacher_id = (SELECT id FROM teachers WHERE name = 'Dr. Karim Jad')
 WHERE username = 'teacher' AND teacher_id IS NULL;
