CREATE TABLE USER_SOCIAL (
    social_id    INT          AUTO_INCREMENT PRIMARY KEY,
    user_id      INT          NOT NULL,
    provider     VARCHAR(20)  NOT NULL,
    provider_email VARCHAR(100),
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES USERS(USER_ID) ON DELETE CASCADE,
    UNIQUE KEY uq_user_provider (user_id, provider)
);
