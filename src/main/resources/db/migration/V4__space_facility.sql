-- Space & Facility bounded context: the campus spaces and the roles each one admits.
CREATE TABLE spaces (
    id            VARCHAR(64)  NOT NULL,
    code          VARCHAR(32)  NOT NULL,
    name          VARCHAR(160) NOT NULL,
    type          VARCHAR(32)  NOT NULL,
    building      VARCHAR(80)  NOT NULL,
    floor         INTEGER      NOT NULL,
    capacity      INTEGER      NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    access_policy VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_spaces PRIMARY KEY (id),
    CONSTRAINT uk_spaces_code UNIQUE (code)
);

CREATE TABLE space_allowed_roles (
    space_id VARCHAR(64) NOT NULL,
    role     VARCHAR(20) NOT NULL,
    CONSTRAINT pk_space_allowed_roles PRIMARY KEY (space_id, role),
    CONSTRAINT fk_space_allowed_roles_space FOREIGN KEY (space_id) REFERENCES spaces (id)
);
