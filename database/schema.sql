-- ============================================================
--  UniPortal — University Database (PostgreSQL)
--  Students select courses → get a score → pass/fail → GPA
-- ============================================================
--  Run with:  psql -U myuser -d university -f schema.sql
-- ============================================================


-- ============================================================
-- 1) STUDENTS  — the people
-- ============================================================
CREATE TABLE students (
    id          SERIAL PRIMARY KEY,
    name        TEXT NOT NULL,
    email       TEXT UNIQUE NOT NULL,
    major       TEXT DEFAULT 'Computer Science',
    enrolled_at DATE NOT NULL DEFAULT CURRENT_DATE
);


-- ============================================================
-- 2) COURSES  — the CS major courses (each has CREDITS)
-- ============================================================
CREATE TABLE courses (
    id       SERIAL PRIMARY KEY,
    code     TEXT UNIQUE NOT NULL,     -- e.g. 'CS201'
    title    TEXT NOT NULL,
    teacher  TEXT,
    credits  INT NOT NULL              -- used to weight the GPA
);


-- ============================================================
-- 3) ENROLLMENTS  — each STUDENT SELECTS their COURSES
--    This is the link table (many students ↔ many courses).
--    score is filled at the end of the term (NULL = in progress).
-- ============================================================
CREATE TABLE enrollments (
    id          SERIAL PRIMARY KEY,
    student_id  INT NOT NULL REFERENCES students(id),
    course_id   INT NOT NULL REFERENCES courses(id),
    score       NUMERIC(5,2),          -- 0–100, NULL until graded
    term        TEXT DEFAULT 'Fall 2026',
    UNIQUE (student_id, course_id)      -- can't take same course twice
);


-- ============================================================
-- SAMPLE DATA
-- ============================================================

-- students
INSERT INTO students (name, email, major) VALUES
('Sara Ahmed',  'sara@uni.edu',  'Computer Science'),
('Ali Hassan',  'ali@uni.edu',   'Computer Science'),
('Omar Khaled', 'omar@uni.edu',  'Computer Science'),
('Lina Yousef', 'lina@uni.edu',  'Computer Science'),
('Noor Adel',   'noor@uni.edu',  'Computer Science');

-- courses (CS major)
INSERT INTO courses (code, title, teacher, credits) VALUES
('CS101', 'Intro to Programming',   'Dr. Karim Jad',   3),
('CS201', 'Data Structures',        'Dr. Hadi Nasser', 4),
('CS202', 'Algorithms',             'Dr. Rana Sayed',  4),
('CS203', 'Object-Oriented (Java)', 'Dr. Karim Jad',   3),
('CS301', 'Databases (SQL)',        'Dr. Rana Sayed',  3),
('CS302', 'Operating Systems',      'Dr. Hadi Nasser', 4);

-- enrollments — each student SELECTS courses, with their final score
INSERT INTO enrollments (student_id, course_id, score) VALUES
-- Sara (strong student)
(1, 1, 92), (1, 2, 88), (1, 3, 81), (1, 5, 95),
-- Ali (mixed)
(2, 1, 78), (2, 2, 66), (2, 4, 73),
-- Omar (some fails)
(3, 1, 64), (3, 2, 55), (3, 3, 71), (3, 6, 49),
-- Lina (excellent)
(4, 1, 90), (4, 3, 86), (4, 5, 93), (4, 6, 84),
-- Noor (struggling)
(5, 1, 51), (5, 2, 58), (5, 4, 62);


-- ============================================================
-- 4) GRADE + GPA LOGIC
--    score → letter + grade points (4.0 scale) + pass/fail
-- ============================================================

-- A reusable VIEW that turns each enrollment's score into a grade
CREATE VIEW graded_enrollments AS
SELECT
    e.id,
    e.student_id,
    e.course_id,
    c.code,
    c.title,
    c.credits,
    e.score,
    -- letter grade
    CASE
        WHEN e.score >= 90 THEN 'A'
        WHEN e.score >= 80 THEN 'B'
        WHEN e.score >= 70 THEN 'C'
        WHEN e.score >= 60 THEN 'D'
        ELSE 'F'
    END AS letter,
    -- grade points (used for GPA)
    CASE
        WHEN e.score >= 90 THEN 4.0
        WHEN e.score >= 80 THEN 3.0
        WHEN e.score >= 70 THEN 2.0
        WHEN e.score >= 60 THEN 1.0
        ELSE 0.0
    END AS grade_points,
    -- pass / fail  (pass = 60 and above)
    CASE WHEN e.score >= 60 THEN 'PASS' ELSE 'FAIL' END AS status
FROM enrollments e
JOIN courses c ON c.id = e.course_id
WHERE e.score IS NOT NULL;       -- only graded (finished) courses


-- ============================================================
-- 5) GPA per student  (credit-weighted, 4.0 scale)
--    GPA = sum(grade_points * credits) / sum(credits)
-- ============================================================
CREATE VIEW student_gpa AS
SELECT
    s.id,
    s.name,
    s.major,
    COUNT(g.id)                         AS courses_taken,
    SUM(CASE WHEN g.status='PASS' THEN 1 ELSE 0 END) AS courses_passed,
    SUM(g.credits)                      AS total_credits,
    ROUND(
        SUM(g.grade_points * g.credits) / NULLIF(SUM(g.credits),0)
    , 2)                                AS gpa
FROM students s
LEFT JOIN graded_enrollments g ON g.student_id = s.id
GROUP BY s.id, s.name, s.major;


-- ============================================================
--  EXAMPLE QUERIES  (run these to see results)
-- ============================================================

-- (A) Which courses did Sara select, and how did she do?
--     SELECT code, title, credits, score, letter, status
--     FROM graded_enrollments
--     WHERE student_id = 1;

-- (B) Every student's GPA (the final result you asked for):
--     SELECT name, courses_passed, total_credits, gpa
--     FROM student_gpa
--     ORDER BY gpa DESC;

-- (C) Only students who PASSED everything, with their GPA:
--     SELECT name, gpa
--     FROM student_gpa
--     WHERE courses_taken = courses_passed
--     ORDER BY gpa DESC;

-- (D) GPA using only PASSED courses (ignore failed ones):
--     SELECT s.name,
--            ROUND(SUM(g.grade_points*g.credits)/NULLIF(SUM(g.credits),0),2) AS gpa_passed_only
--     FROM students s
--     JOIN graded_enrollments g ON g.student_id = s.id
--     WHERE g.status = 'PASS'
--     GROUP BY s.name
--     ORDER BY gpa_passed_only DESC;
