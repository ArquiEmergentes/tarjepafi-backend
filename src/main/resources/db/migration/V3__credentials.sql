-- Credentials bounded context: card holders, cards, their status history, permission exceptions, campus settings.
-- space_id in credential_permissions is a plain column: spaces belong to another context (no foreign key).
CREATE TABLE card_holders (
    id                VARCHAR(64)  NOT NULL,
    code              VARCHAR(32)  NOT NULL,
    full_name         VARCHAR(160) NOT NULL,
    email             VARCHAR(254) NOT NULL,
    role              VARCHAR(20)  NOT NULL,
    program           VARCHAR(160) NOT NULL,
    enrollment_status VARCHAR(20),
    CONSTRAINT pk_card_holders PRIMARY KEY (id),
    CONSTRAINT uk_card_holders_code UNIQUE (code)
);

CREATE TABLE credentials (
    id                VARCHAR(64) NOT NULL,
    card_uid          VARCHAR(32) NOT NULL,
    card_holder_id    VARCHAR(64),
    status            VARCHAR(20) NOT NULL,
    issued_at         TIMESTAMP   NOT NULL,
    status_changed_at TIMESTAMP   NOT NULL,
    last_used_at      TIMESTAMP,
    CONSTRAINT pk_credentials PRIMARY KEY (id),
    CONSTRAINT uk_credentials_card_uid UNIQUE (card_uid),
    CONSTRAINT fk_credentials_card_holder FOREIGN KEY (card_holder_id) REFERENCES card_holders (id)
);
CREATE INDEX idx_credentials_card_holder_id ON credentials (card_holder_id);
-- A holder may have at most one ACTIVE card (US11, US13).
CREATE UNIQUE INDEX uk_credentials_one_active_per_holder ON credentials (card_holder_id) WHERE status = 'ACTIVE';

CREATE TABLE credential_status_history (
    id            VARCHAR(64)  NOT NULL,
    credential_id VARCHAR(64)  NOT NULL,
    from_status   VARCHAR(20)  NOT NULL,
    to_status     VARCHAR(20)  NOT NULL,
    reason        VARCHAR(40)  NOT NULL,
    changed_by    VARCHAR(64)  NOT NULL,
    changed_at    TIMESTAMP    NOT NULL,
    note          VARCHAR(255),
    CONSTRAINT pk_credential_status_history PRIMARY KEY (id),
    CONSTRAINT fk_credential_status_history_credential FOREIGN KEY (credential_id) REFERENCES credentials (id)
);
CREATE INDEX idx_credential_status_history_credential_id ON credential_status_history (credential_id, changed_at);

CREATE TABLE credential_permissions (
    id            VARCHAR(64) NOT NULL,
    credential_id VARCHAR(64) NOT NULL,
    space_id      VARCHAR(64) NOT NULL,
    effect        VARCHAR(10) NOT NULL,
    CONSTRAINT pk_credential_permissions PRIMARY KEY (id),
    CONSTRAINT uk_credential_permissions_credential_space UNIQUE (credential_id, space_id),
    CONSTRAINT fk_credential_permissions_credential FOREIGN KEY (credential_id) REFERENCES credentials (id)
);

-- One row: the campus settings the enrollment verification and (later) attendance read.
CREATE TABLE settings (
    id                          VARCHAR(16) NOT NULL,
    term                        VARCHAR(16) NOT NULL,
    enrollment_deadline         TIMESTAMP   NOT NULL,
    default_tolerance_minutes   INTEGER     NOT NULL,
    CONSTRAINT pk_settings PRIMARY KEY (id)
);
