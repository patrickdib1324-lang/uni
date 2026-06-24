-- Users for login, each with a ROLE
CREATE TABLE IF NOT EXISTS users (
    id         SERIAL PRIMARY KEY,
    username   TEXT UNIQUE NOT NULL,
    password   TEXT NOT NULL,            -- plain text (demo only!)
    role       TEXT NOT NULL,            -- 'student' | 'teacher' | 'admin'
    student_id INT REFERENCES students(id)   -- only set for students
);

INSERT INTO users (username, password, role, student_id) VALUES
('admin',   'admin123',   'admin',   NULL),
('teacher', 'teacher123', 'teacher', NULL),
('sara',    'sara123',    'student', 1),
('ali',     'ali123',     'student', 2),
('omar',    'omar123',    'student', 3),
('lina',    'lina123',    'student', 4),
('noor',    'noor123',    'student', 5)
ON CONFLICT (username) DO NOTHING;
