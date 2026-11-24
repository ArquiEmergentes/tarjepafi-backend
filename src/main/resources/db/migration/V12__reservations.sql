-- Space & Facility: reservations of spaces with the RESERVATION_REQUIRED policy (TS20 to TS23).
-- space_id and card_holder_id are plain columns: Space & Facility owns spaces, Credentials owns holders.
CREATE TABLE reservations (
    id                   VARCHAR(64) NOT NULL,
    seq                  BIGINT GENERATED ALWAYS AS IDENTITY,
    reservation_code     VARCHAR(24) NOT NULL,
    space_id             VARCHAR(64) NOT NULL,
    card_holder_id       VARCHAR(64) NOT NULL,
    start_time           TIMESTAMP   NOT NULL,
    end_time             TIMESTAMP   NOT NULL,
    status               VARCHAR(16) NOT NULL,
    check_in_window_end  TIMESTAMP   NOT NULL,
    checked_in_at        TIMESTAMP,
    created_at           TIMESTAMP   NOT NULL,
    version              BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_reservations PRIMARY KEY (id),
    CONSTRAINT uk_reservations_seq UNIQUE (seq),
    CONSTRAINT uk_reservations_code UNIQUE (reservation_code),
    CONSTRAINT ck_reservations_slot CHECK (end_time > start_time)
);
CREATE INDEX idx_reservations_space ON reservations (space_id, start_time);
CREATE INDEX idx_reservations_holder ON reservations (card_holder_id, status);
CREATE INDEX idx_reservations_status ON reservations (status, check_in_window_end);
