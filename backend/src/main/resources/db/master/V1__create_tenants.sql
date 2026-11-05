CREATE TABLE tenants (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    slug             VARCHAR(24)  NOT NULL,
    name             VARCHAR(200) NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    plan             VARCHAR(30)  NOT NULL,
    jdbc_url         VARCHAR(500) NOT NULL,
    db_username      VARCHAR(100) NOT NULL,
    db_password_enc  VARCHAR(500) NOT NULL,
    features_json    JSON         NULL,
    created_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenants_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
