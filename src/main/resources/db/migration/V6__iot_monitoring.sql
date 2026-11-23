-- IoT Monitoring bounded context: readers, their telemetry, incidents and the card tap events.
-- space_id points at Space & Facility (no foreign key). incidents.reader_id and card_tap_events.reader_id are plain
-- columns because a tap or an error can arrive from a reader that was never registered.
CREATE TABLE readers (
    id                     VARCHAR(64)  NOT NULL,
    name                   VARCHAR(160) NOT NULL,
    device_type            VARCHAR(32)  NOT NULL,
    space_id               VARCHAR(64)  NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    reader_mode            VARCHAR(20)  NOT NULL,
    communication_interval INTEGER      NOT NULL,
    heartbeat_interval     INTEGER      NOT NULL,
    firmware_version       VARCHAR(32)  NOT NULL,
    registered_at          TIMESTAMP    NOT NULL,
    last_seen_at           TIMESTAMP,
    CONSTRAINT pk_readers PRIMARY KEY (id)
);
CREATE INDEX idx_readers_space_id ON readers (space_id);

CREATE TABLE telemetry (
    id               VARCHAR(64) NOT NULL,
    reader_id        VARCHAR(64) NOT NULL,
    captured_at      TIMESTAMP   NOT NULL,
    signal_strength  INTEGER     NOT NULL,
    uptime           BIGINT      NOT NULL,
    firmware_version VARCHAR(32) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    CONSTRAINT pk_telemetry PRIMARY KEY (id),
    CONSTRAINT fk_telemetry_reader FOREIGN KEY (reader_id) REFERENCES readers (id)
);
CREATE INDEX idx_telemetry_reader_id ON telemetry (reader_id, captured_at);

CREATE TABLE incidents (
    id          VARCHAR(64)  NOT NULL,
    reader_id   VARCHAR(64)  NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    detected_at TIMESTAMP    NOT NULL,
    description VARCHAR(500) NOT NULL,
    resolved_at TIMESTAMP,
    CONSTRAINT pk_incidents PRIMARY KEY (id)
);
CREATE INDEX idx_incidents_reader_id ON incidents (reader_id, detected_at);

-- id is the eventId of the device message: a second delivery of the same event is ignored (AD13).
-- seq grows with every insert and is what the "after" parameter of the polling endpoints compares with.
CREATE TABLE card_tap_events (
    id             VARCHAR(64)  NOT NULL,
    seq            BIGINT GENERATED ALWAYS AS IDENTITY,
    reader_id      VARCHAR(64)  NOT NULL,
    card_uid       VARCHAR(32)  NOT NULL,
    tapped_at      TIMESTAMP    NOT NULL,
    outcome        VARCHAR(20)  NOT NULL,
    tone           VARCHAR(10),
    message_key    VARCHAR(64),
    message_params TEXT,
    display_message VARCHAR(500) NOT NULL DEFAULT '',
    source         VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_card_tap_events PRIMARY KEY (id),
    CONSTRAINT uk_card_tap_events_seq UNIQUE (seq)
);
CREATE INDEX idx_card_tap_events_reader_id ON card_tap_events (reader_id, seq);
