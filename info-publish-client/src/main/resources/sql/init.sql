-- 信息发布监管客户端 本地数据库表
-- 数据库名: info_publish_client

-- 平台配置表
CREATE TABLE IF NOT EXISTS platform_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_key VARCHAR(64) NOT NULL UNIQUE COMMENT '配置键',
    config_value VARCHAR(512) COMMENT '配置值',
    description VARCHAR(255) COMMENT '配置说明',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台配置表';

-- 预置数据
INSERT INTO platform_config (config_key, config_value, description) VALUES
('platform_url', 'http://127.0.0.1:8063', '管控平台地址'),
('client_id', 'info-publish-client-001', '客户端唯一ID'),
('server_id', '44010000002000000001', '服务端认证ID'),
('server_cert_path', 'certs/server.cer', '服务端证书路径'),
('ukey_password', '88888888', 'UKey密码');

-- 操作日志表
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_type VARCHAR(32) NOT NULL COMMENT '事件类型：UKEY_INSERT/UKEY_REMOVE/AUTH_SUCCESS/AUTH_FAIL/CHANNEL_CREATE/CHANNEL_STOP/LOGOUT',
    detail TEXT COMMENT '事件详情',
    status VARCHAR(20) COMMENT '状态：SUCCESS/FAIL',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- UDP代理规则表（与 publish-gateway 结构一致）
CREATE TABLE IF NOT EXISTS `udp_proxy_rule` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `rule_id` VARCHAR(128) NOT NULL COMMENT '规则ID（唯一标识）',
    `rule_name` VARCHAR(256) DEFAULT NULL COMMENT '规则名称',
    `listen_ip` VARCHAR(64) DEFAULT NULL COMMENT '监听IP（为空时监听所有网卡）',
    `listen_port` INT(11) NOT NULL COMMENT '监听端口',
    `source_ip` VARCHAR(64) DEFAULT NULL COMMENT '允许接入的源IP（为空则允许所有）',
    `target_ip` VARCHAR(64) NOT NULL COMMENT '转发目标IP（情报板IP）',
    `target_port` INT(11) NOT NULL COMMENT '转发目标端口（情报板端口）',
    `encrypt_enabled` TINYINT(1) DEFAULT 0 COMMENT '是否启用加密转发（0-否，1-是）',
    `terminal_gateway_ip` VARCHAR(64) DEFAULT NULL COMMENT '终端网关IP（加密模式时使用）',
    `terminal_gateway_port` INT(11) DEFAULT NULL COMMENT '终端网关端口（加密模式时使用）',
    `status` VARCHAR(32) DEFAULT 'ENABLED' COMMENT '规则状态：ENABLED/DISABLED',
    `display_board_id` VARCHAR(128) DEFAULT NULL COMMENT '关联的情报板ID',
    `gateway_sn` VARCHAR(128) DEFAULT NULL COMMENT '网关序列号',
    `config_source` VARCHAR(32) DEFAULT 'local' COMMENT '配置来源：platform-管控平台下发/local-本地配置',
    `config_id` BIGINT(20) DEFAULT NULL COMMENT '管控平台的配置ID',
    `chain_id` BIGINT(20) DEFAULT NULL COMMENT '链路ID',
    `chain_code` VARCHAR(128) DEFAULT NULL COMMENT '链路编码',
    `branch_code` VARCHAR(64) DEFAULT NULL COMMENT '分支编码',
    `branch_name` VARCHAR(256) DEFAULT NULL COMMENT '分支名称',
    `config_version` INT(11) DEFAULT 1 COMMENT '配置版本号',
    `manufacturer` VARCHAR(64) DEFAULT NULL COMMENT '情报板厂家标识（决定协议解析策略，如: sigma, nova）',
    `protocol` VARCHAR(10) DEFAULT 'UDP' COMMENT '传输协议：UDP（默认）或 TCP',
    `gateway_mac` VARCHAR(64) DEFAULT NULL COMMENT '发布网关MAC地址（客户端ARP绑定使用）',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT(1) DEFAULT 0 COMMENT '逻辑删除（0-未删除，1-已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_id` (`rule_id`),
    KEY `idx_listen_port` (`listen_port`),
    KEY `idx_status` (`status`),
    KEY `idx_gateway_sn` (`gateway_sn`),
    KEY `idx_chain_id` (`chain_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UDP代理规则表（与发布网关结构一致，客户端额外增加gateway_mac列）';
