CREATE TABLE users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    status ENUM('ACTIVE', 'INACTIVE', 'WITHDRAWN') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE categories (
    category_id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    display_order INT NOT NULL,
    active BIT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (category_id),
    CONSTRAINT uk_categories_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE subscriptions (
    subscription_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    category_id BIGINT NULL,
    service_name VARCHAR(100) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    currency ENUM('EUR', 'GBP', 'JPY', 'KRW', 'USD') NOT NULL,
    billing_cycle ENUM('BIMONTHLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'WEEKLY', 'YEARLY') NOT NULL,
    billing_interval INT NOT NULL,
    start_date DATE NOT NULL,
    next_billing_date DATE NOT NULL,
    auto_renew BIT(1) NOT NULL,
    status ENUM('ACTIVE', 'CANCEL_PENDING', 'INACTIVE') NOT NULL,
    reminder_days INT NULL,
    payment_method VARCHAR(100) NULL,
    service_url VARCHAR(500) NULL,
    memo VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (subscription_id),
    INDEX idx_subscriptions_user_id (user_id),
    INDEX idx_subscriptions_category_id (category_id),
    CONSTRAINT fk_subscriptions_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_subscriptions_category
        FOREIGN KEY (category_id) REFERENCES categories (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notifications (
    notification_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    message VARCHAR(255) NOT NULL,
    is_read BIT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (notification_id),
    INDEX idx_notifications_user_id (user_id),
    INDEX idx_notifications_subscription_id (subscription_id),
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_notifications_subscription
        FOREIGN KEY (subscription_id) REFERENCES subscriptions (subscription_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_settings (
    setting_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    default_currency ENUM('EUR', 'GBP', 'JPY', 'KRW', 'USD') NOT NULL,
    default_reminder_days INT NOT NULL,
    email_notification_enabled BIT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (setting_id),
    CONSTRAINT uk_user_settings_user_id UNIQUE (user_id),
    CONSTRAINT fk_user_settings_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
