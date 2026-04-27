-- =============================================================
-- Seed: dummy courses and enrollments
-- =============================================================

-- ---------------------------------------------------------------
-- Courses  (3 creators, varied statuses and capacities)
-- ---------------------------------------------------------------
INSERT INTO
    course (
        creator_id,
        title,
        description,
        price,
        capacity,
        start_date,
        end_date,
        status
    )
VALUES (
        1,
        'Introduction to Java',
        'Learn Java from scratch.',
        49.99,
        30,
        '2026-05-01',
        '2026-06-30',
        'OPEN'
    ),
    (
        1,
        'Spring Boot Masterclass',
        'Build REST APIs with Spring Boot.',
        89.99,
        5,
        '2026-05-15',
        '2026-07-15',
        'OPEN'
    ),
    (
        2,
        'Docker & Kubernetes 101',
        'Containerise and orchestrate apps.',
        69.99,
        20,
        '2026-06-01',
        '2026-07-31',
        'OPEN'
    ),
    (
        2,
        'React Fundamentals',
        'Modern front-end development with React.',
        59.99,
        25,
        '2026-04-01',
        '2026-04-30',
        'CLOSED'
    ),
    (
        3,
        'Data Structures & Algos',
        'Core CS concepts for interviews.',
        79.99,
        10,
        '2026-07-01',
        '2026-08-31',
        'OPEN'
    ),
    (
        3,
        'System Design Basics',
        'Design scalable distributed systems.',
        99.99,
        15,
        '2026-07-15',
        '2026-09-15',
        'DRAFT'
    );

-- ---------------------------------------------------------------
-- Enrollments
--   course 1 (cap 30)  – several confirmed
--   course 2 (cap  5)  – full → extras waitlisted
--   course 3 (cap 20)  – mix of confirmed + one cancelled
--   course 4 (CLOSED)  – historical confirmed + cancelled
--   course 5 (cap 10)  – pending + waitlisted
-- ---------------------------------------------------------------

-- Course 1: Introduction to Java
INSERT INTO
    enrollment (course_id, student_id, status)
VALUES (1, 101, 'CONFIRMED'),
    (1, 102, 'CONFIRMED'),
    (1, 103, 'CONFIRMED'),
    (1, 104, 'CONFIRMED'),
    (1, 105, 'CONFIRMED'),
    (1, 106, 'PENDING'),
    (1, 107, 'CANCELLED');

-- Course 2: Spring Boot Masterclass  (capacity = 5, so seats fill fast)
INSERT INTO
    enrollment (course_id, student_id, status)
VALUES (2, 201, 'CONFIRMED'),
    (2, 202, 'CONFIRMED'),
    (2, 203, 'CONFIRMED'),
    (2, 204, 'CONFIRMED'),
    (2, 205, 'CONFIRMED'),
    (2, 206, 'WAITLISTED'),
    (2, 207, 'WAITLISTED'),
    (2, 208, 'CANCELLED');

-- Course 3: Docker & Kubernetes 101
INSERT INTO
    enrollment (course_id, student_id, status)
VALUES (3, 301, 'CONFIRMED'),
    (3, 302, 'CONFIRMED'),
    (3, 303, 'CONFIRMED'),
    (3, 304, 'PENDING'),
    (3, 305, 'CANCELLED');

-- Course 4: React Fundamentals (CLOSED – historical data)
INSERT INTO
    enrollment (course_id, student_id, status)
VALUES (4, 401, 'CONFIRMED'),
    (4, 402, 'CONFIRMED'),
    (4, 403, 'CONFIRMED'),
    (4, 404, 'CANCELLED'),
    (4, 405, 'CANCELLED');

-- Course 5: Data Structures & Algos
INSERT INTO
    enrollment (course_id, student_id, status)
VALUES (5, 501, 'PENDING'),
    (5, 502, 'PENDING'),
    (5, 503, 'WAITLISTED'),
    (5, 504, 'WAITLISTED');