ALTER TABLE users
    ADD COLUMN google_subject VARCHAR(255) NULL AFTER password,
    ADD CONSTRAINT uk_users_google_subject UNIQUE (google_subject);

CREATE TABLE oauth_exchange_codes (
    oauth_exchange_code_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (oauth_exchange_code_id),
    CONSTRAINT uk_oauth_exchange_codes_code_hash UNIQUE (code_hash),
    INDEX idx_oauth_exchange_codes_user_id (user_id),
    CONSTRAINT fk_oauth_exchange_codes_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
