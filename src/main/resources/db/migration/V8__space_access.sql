-- Space & Facility: access attempts and work shifts. card_holder_id, credential_id, reader_id and
-- tap_event_id point at other contexts: no foreign keys. tap_event_id is unique so a tap that is delivered
-- twice records one attempt (AD13). seq is the cursor of the polling endpoints (D13).
ALTER TABLE spaces ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE access_records (
    id             VARCHAR(64) NOT NULL,
    seq            BIGINT GENERATED ALWAYS AS IDENTITY,
    tap_event_id   VARCHAR(64) NOT NULL,
    card_uid       VARCHAR(32) NOT NULL,
    credential_id  VARCHAR(64),
    card_holder_id VARCHAR(64),
    space_id       VARCHAR(64) NOT NULL,
    reader_id      VARCHAR(64) NOT NULL,
    access_type    VARCHAR(20) NOT NULL,
    decision       VARCHAR(10) NOT NULL,
    denial_reason  VARCHAR(30) NOT NULL,
    attempted_at   TIMESTAMP   NOT NULL,
    CONSTRAINT pk_access_records PRIMARY KEY (id),
    CONSTRAINT uk_access_records_seq UNIQUE (seq),
    CONSTRAINT uk_access_records_tap_event UNIQUE (tap_event_id)
);
CREATE INDEX idx_access_records_space_id ON access_records (space_id, seq);
CREATE INDEX idx_access_records_card_holder_id ON access_records (card_holder_id, attempted_at);

CREATE TABLE work_shifts (
    id             VARCHAR(64) NOT NULL,
    seq            BIGINT GENERATED ALWAYS AS IDENTITY,
    card_holder_id VARCHAR(64) NOT NULL,
    shift_date     DATE        NOT NULL,
    entry_at       TIMESTAMP   NOT NULL,
    exit_at        TIMESTAMP,
    worked_minutes INTEGER,
    status         VARCHAR(10) NOT NULL,
    CONSTRAINT pk_work_shifts PRIMARY KEY (id),
    CONSTRAINT uk_work_shifts_seq UNIQUE (seq)
);
CREATE INDEX idx_work_shifts_card_holder_id ON work_shifts (card_holder_id, status);
