-- Academic Attendance bounded context, catalogue part: courses, class schedules and enrollments.
-- teacher_id, student_id and space_id point at other contexts (card holders, spaces): no foreign keys.
CREATE TABLE courses (
    id      VARCHAR(64)  NOT NULL,
    code    VARCHAR(32)  NOT NULL,
    name    VARCHAR(160) NOT NULL,
    subject VARCHAR(160) NOT NULL,
    CONSTRAINT pk_courses PRIMARY KEY (id),
    CONSTRAINT uk_courses_code UNIQUE (code)
);

CREATE TABLE class_schedules (
    id          VARCHAR(64) NOT NULL,
    course_id   VARCHAR(64) NOT NULL,
    section     VARCHAR(16) NOT NULL,
    teacher_id  VARCHAR(64) NOT NULL,
    space_id    VARCHAR(64) NOT NULL,
    day_of_week INTEGER     NOT NULL,
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    term        VARCHAR(16) NOT NULL,
    CONSTRAINT pk_class_schedules PRIMARY KEY (id),
    CONSTRAINT fk_class_schedules_course FOREIGN KEY (course_id) REFERENCES courses (id)
);
CREATE INDEX idx_class_schedules_teacher_id ON class_schedules (teacher_id);

CREATE TABLE enrollments (
    id          VARCHAR(64) NOT NULL,
    student_id  VARCHAR(64) NOT NULL,
    schedule_id VARCHAR(64) NOT NULL,
    term        VARCHAR(16) NOT NULL,
    status      VARCHAR(20) NOT NULL,
    CONSTRAINT pk_enrollments PRIMARY KEY (id),
    CONSTRAINT fk_enrollments_schedule FOREIGN KEY (schedule_id) REFERENCES class_schedules (id)
);
CREATE INDEX idx_enrollments_student_id ON enrollments (student_id);
CREATE INDEX idx_enrollments_schedule_id ON enrollments (schedule_id);
