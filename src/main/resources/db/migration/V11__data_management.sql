-- Data Management bounded context: immutable data records and reports.
-- event_id is the id of the integration event a fact came from; with data_type and subject_id it makes ingestion idempotent.
CREATE TABLE data_records (
    id             VARCHAR(64)  NOT NULL,
    seq            BIGINT GENERATED ALWAYS AS IDENTITY,
    event_id       VARCHAR(64)  NOT NULL,
    source_context VARCHAR(32)  NOT NULL,
    data_type      VARCHAR(32)  NOT NULL,
    subject_id     VARCHAR(64)  NOT NULL,
    occurred_at    TIMESTAMP    NOT NULL,
    recorded_at    TIMESTAMP    NOT NULL,
    payload        TEXT         NOT NULL DEFAULT '{}',
    CONSTRAINT pk_data_records PRIMARY KEY (id),
    CONSTRAINT uk_data_records_seq UNIQUE (seq),
    CONSTRAINT uk_data_records_event UNIQUE (event_id, data_type, subject_id)
);
CREATE INDEX idx_data_records_occurred_at ON data_records (occurred_at);
CREATE INDEX idx_data_records_type ON data_records (data_type, occurred_at);

CREATE TABLE reports (
    id                      VARCHAR(64) NOT NULL,
    seq                     BIGINT GENERATED ALWAYS AS IDENTITY,
    report_type             VARCHAR(16) NOT NULL,
    period_start            TIMESTAMP   NOT NULL,
    period_end              TIMESTAMP   NOT NULL,
    status                  VARCHAR(16) NOT NULL,
    generated_at            TIMESTAMP,
    total_records           INTEGER     NOT NULL DEFAULT 0,
    attendance_rate         DOUBLE PRECISION,
    access_granted          INTEGER,
    access_denied           INTEGER,
    peak_hour               VARCHAR(5),
    readers_with_incidents  INTEGER,
    requested_by            VARCHAR(64) NOT NULL,
    requested_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_reports PRIMARY KEY (id),
    CONSTRAINT uk_reports_seq UNIQUE (seq)
);
CREATE INDEX idx_reports_status ON reports (status);
