-- Demo-only key storage for secure publish signatures.
-- Do not use plaintext private_key_pem as a production key-management design.
CREATE TABLE IF NOT EXISTS `secure_publish_key` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `key_id` VARCHAR(64) NOT NULL,
    `key_role` VARCHAR(32) NOT NULL DEFAULT 'SIGNER',
    `algorithm` VARCHAR(64) NOT NULL DEFAULT 'SHA256withRSA',
    `private_key_pem` MEDIUMTEXT NULL,
    `public_key_pem` MEDIUMTEXT NOT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_secure_publish_key_lookup` (`key_id`, `key_role`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Secure publish demo signature key table';
