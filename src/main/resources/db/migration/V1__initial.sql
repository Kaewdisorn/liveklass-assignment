CREATE TABLE course (
    id BIGSERIAL PRIMARY KEY,
    creator_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    description TEXT,
    price NUMERIC(10, 2) NOT NULL,
    capacity INTEGER NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_course_price_non_negative CHECK (price >= 0),
    CONSTRAINT ck_course_capacity_positive CHECK (capacity > 0),
    CONSTRAINT ck_course_date_range CHECK (start_date <= end_date),
    CONSTRAINT ck_course_status CHECK (
        status IN ('DRAFT', 'OPEN', 'CLOSED')
    )
);

CREATE TABLE enrollment (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_enrollment_course FOREIGN KEY (course_id) REFERENCES course (id) ON DELETE RESTRICT,
    CONSTRAINT ck_enrollment_status CHECK (
        status IN (
            'PENDING',
            'CONFIRMED',
            'CANCELLED'
        )
    )
);