-- 规则配置模块数据库表
-- 数据库名: monitor_platform

-- 敏感关键词表
CREATE TABLE IF NOT EXISTS sensitive_keyword (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    keyword VARCHAR(100) NOT NULL COMMENT '关键词',
    category VARCHAR(50) COMMENT '分类: 违禁词/敏感词/自定义',
    severity VARCHAR(20) DEFAULT 'medium' COMMENT '严重程度: high-高危, medium-中危, low-低危',
    status VARCHAR(20) DEFAULT 'enabled' COMMENT '状态: enabled-启用, disabled-禁用',
    remark VARCHAR(255) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_keyword (keyword),
    INDEX idx_category (category),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感关键词表';

-- 检测规则表
CREATE TABLE IF NOT EXISTS detection_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    rule_name VARCHAR(100) NOT NULL COMMENT '规则名称',
    rule_type VARCHAR(50) NOT NULL COMMENT '规则类型: keyword-关键词, format-格式, content-内容',
    rule_config TEXT COMMENT '规则配置(JSON格式)',
    priority INT DEFAULT 0 COMMENT '优先级(数字越大优先级越高)',
    status VARCHAR(20) DEFAULT 'enabled' COMMENT '状态: enabled-启用, disabled-禁用',
    description VARCHAR(500) COMMENT '描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_rule_type (rule_type),
    INDEX idx_status (status),
    INDEX idx_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检测规则表';

-- 告警阈值配置表
CREATE TABLE IF NOT EXISTS alarm_threshold (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    threshold_name VARCHAR(100) NOT NULL COMMENT '阈值名称',
    threshold_type VARCHAR(50) NOT NULL COMMENT '阈值类型: 一级(严重)/二级(严重)/三级(警告)/四级(一般)',
    threshold_config TEXT COMMENT '阈值配置(JSON格式)',
    alert_level VARCHAR(20) DEFAULT 'general' COMMENT '告警级别: critical/serious/general/minor',
    status VARCHAR(20) DEFAULT 'enabled' COMMENT '状态: enabled-启用, disabled-禁用',
    description VARCHAR(500) COMMENT '描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_threshold_type (threshold_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警阈值配置表';

-- 插入测试数据 - 敏感关键词
INSERT INTO sensitive_keyword (keyword, category, severity, status, remark) VALUES
('涉黄词汇XXX', '违禁词组', 'high', 'enabled', '测试关键词1'),
('违禁词A', '违禁词组', 'high', 'enabled', '测试关键词2'),
('测试关键词', '自定义词组', 'medium', 'enabled', '测试关键词3');

-- 插入测试数据 - 检测规则
INSERT INTO detection_rule (rule_name, rule_type, rule_config, priority, status, description) VALUES
('涉黄词汇检测', 'keyword', '{"keywords":["涉黄","色情"],"matchMode":"full"}', 1, 'enabled', '检测涉黄相关内容'),
('违禁词组检测', 'keyword', '{"keywords":["违法","反动"],"matchMode":"partial"}', 2, 'enabled', '检测违法违规内容'),
('格式检测规则', 'format', '{"maxLength":1000,"allowedFormats":["jpg","png"]}', 1, 'enabled', '内容格式检测');

-- 插入测试数据 - 告警阈值
INSERT INTO alarm_threshold (threshold_name, threshold_type, threshold_config, alert_level, status, description) VALUES
('一级(高危)', '严重', '{"matchCount":1,"timeWindow":60}', 'critical', 'enabled', '检测到1次敏感词即触发'),
('二级(严重)', '严重', '{"matchCount":3,"timeWindow":300}', 'serious', 'enabled', '5分钟内检测到3次'),
('三级(警告)', '警告', '{"matchCount":5,"timeWindow":600}', 'general', 'enabled', '10分钟内检测到5次'),
('四级(一般)', '一般', '{"matchCount":10,"timeWindow":1800}', 'minor', 'enabled', '30分钟内检测到10次');
