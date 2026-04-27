ALTER TABLE enrollment DROP CONSTRAINT ck_enrollment_status;

ALTER TABLE enrollment
ADD CONSTRAINT ck_enrollment_status CHECK (
    status IN (
        'PENDING',
        'CONFIRMED',
        'CANCELLED',
        'WAITLISTED'
    )
);

DROP INDEX uq_enrollment_active_per_student_course;

CREATE UNIQUE INDEX uq_enrollment_active_per_student_course ON enrollment (course_id, student_id)
WHERE
    status IN (
        'PENDING',
        'CONFIRMED',
        'WAITLISTED'
    );