-- Academic Attendance: sessions (classes and meetings), who is invited to a meeting, and attendance records.
-- space_id and card_holder_id point at other contexts: no foreign keys. tap_event_id is unique so that a
-- tap that is delivered twice records its attendance once (AD13).
CREATE TABLE sessions (
    id                 VARCHAR(64)  NOT NULL,
    type               VARCHAR(10)  NOT NULL,
    schedule_id        VARCHAR(64),
    title              VARCHAR(200) NOT NULL,
    space_id           VARCHAR(64)  NOT NULL,
    session_date       DATE         NOT NULL,
    scheduled_start    TIMESTAMP    NOT NULL,
    scheduled_end      TIMESTAMP    NOT NULL,
    tolerance_minutes  INTEGER      NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_sessions PRIMARY KEY (id),
    CONSTRAINT fk_sessions_schedule FOREIGN KEY (schedule_id) REFERENCES class_schedules (id)
);
CREATE INDEX idx_sessions_space_date ON sessions (space_id, session_date);
CREATE INDEX idx_sessions_schedule_id ON sessions (schedule_id);
CREATE INDEX idx_sessions_status ON sessions (status);

CREATE TABLE session_participants (
    id             VARCHAR(64) NOT NULL,
    session_id     VARCHAR(64) NOT NULL,
    card_holder_id VARCHAR(64) NOT NULL,
    CONSTRAINT pk_session_participants PRIMARY KEY (id),
    CONSTRAINT fk_session_participants_session FOREIGN KEY (session_id) REFERENCES sessions (id),
    CONSTRAINT uk_session_participants UNIQUE (session_id, card_holder_id)
);

-- seq grows with every insert: the "after" cursor of the polling endpoints.
CREATE TABLE attendance_records (
    id               VARCHAR(64)  NOT NULL,
    seq              BIGINT GENERATED ALWAYS AS IDENTITY,
    tap_event_id     VARCHAR(64),
    session_id       VARCHAR(64)  NOT NULL,
    card_holder_id   VARCHAR(64)  NOT NULL,
    role             VARCHAR(10)  NOT NULL,
    check_in_at      TIMESTAMP,
    check_out_at     TIMESTAMP,
    duration_minutes INTEGER,
    status           VARCHAR(10)  NOT NULL,
    confirmed_at     TIMESTAMP,
    source           VARCHAR(15)  NOT NULL,
    modified_by      VARCHAR(64),
    modified_at      TIMESTAMP,
    edit_reason      VARCHAR(255),
    CONSTRAINT pk_attendance_records PRIMARY KEY (id),
    CONSTRAINT fk_attendance_records_session FOREIGN KEY (session_id) REFERENCES sessions (id),
    CONSTRAINT uk_attendance_records_seq UNIQUE (seq),
    CONSTRAINT uk_attendance_records_holder UNIQUE (session_id, card_holder_id)
);
CREATE UNIQUE INDEX uk_attendance_records_tap_event ON attendance_records (tap_event_id) WHERE tap_event_id IS NOT NULL;
CREATE INDEX idx_attendance_records_card_holder_id ON attendance_records (card_holder_id);
