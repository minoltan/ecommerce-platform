CREATE TABLE users (
    id                CHAR(36)      NOT NULL PRIMARY KEY,
    email             VARCHAR(255)  NOT NULL,
    password_hash     VARCHAR(255)  NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'UNVERIFIED',
    role              VARCHAR(20)   NOT NULL DEFAULT 'CUSTOMER',
    full_name         VARCHAR(255)  NOT NULL,
    email_verified_at DATETIME(3)   NULL,
    deactivated_at    DATETIME(3)   NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at        DATETIME(3)   NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_status CHECK (status IN ('UNVERIFIED','ACTIVE','DEACTIVATED')),
    CONSTRAINT chk_users_role   CHECK (role IN ('CUSTOMER','ADMIN','INVENTORY_MANAGER')),
    INDEX idx_users_status (status)
);

CREATE TABLE user_addresses (
    id          CHAR(36)      NOT NULL PRIMARY KEY,
    user_id     CHAR(36)      NOT NULL,
    line1       VARCHAR(255)  NOT NULL,
    line2       VARCHAR(255)  NULL,
    city        VARCHAR(100)  NOT NULL,
    state       VARCHAR(100)  NOT NULL,
    pincode     VARCHAR(20)   NOT NULL,
    country     VARCHAR(100)  NOT NULL DEFAULT 'IN',
    is_default  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at  DATETIME(3)   NULL,
    CONSTRAINT fk_addr_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_addr_user (user_id)
);

CREATE TABLE email_verifications (
    id          CHAR(36)      NOT NULL PRIMARY KEY,
    user_id     CHAR(36)      NOT NULL,
    token       VARCHAR(255)  NOT NULL,
    expires_at  DATETIME(3)   NOT NULL,
    used_at     DATETIME(3)   NULL,
    created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_everif_user FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE KEY uq_everif_token (token)
);

CREATE TABLE password_reset_tokens (
    id          CHAR(36)      NOT NULL PRIMARY KEY,
    user_id     CHAR(36)      NOT NULL,
    token_hash  VARCHAR(255)  NOT NULL,
    expires_at  DATETIME(3)   NOT NULL,
    used_at     DATETIME(3)   NULL,
    created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_pwreset_user FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE KEY uq_pwreset_token_hash (token_hash)
);

CREATE TABLE user_auth_outbox (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    aggregate_id    CHAR(36)      NOT NULL,
    event_type      VARCHAR(100)  NOT NULL,
    payload         JSON          NOT NULL,
    correlation_id  VARCHAR(36)   NOT NULL,
    published       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at    DATETIME(3)   NULL,
    INDEX idx_outbox_unpublished (published, created_at)
);
