CREATE TABLE IF NOT EXISTS ai_agent_training_consent
(
    id              BIGINT AUTO_INCREMENT COMMENT '主键ID' PRIMARY KEY,
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    agent_id        VARCHAR(32)  NOT NULL COMMENT '智能体ID',
    enabled         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否允许将匿名化对话用于模型优化',
    consent_version VARCHAR(32)  NOT NULL DEFAULT 'v1' COMMENT '授权版本',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_ai_agent_training_consent_user_agent (user_id, agent_id),
    KEY idx_ai_agent_training_consent_agent (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='训练素材授权表';

CREATE TABLE IF NOT EXISTS ai_agent_training_sample
(
    id              BIGINT AUTO_INCREMENT COMMENT '主键ID' PRIMARY KEY,
    chat_history_id BIGINT      NOT NULL COMMENT '来源聊天记录ID',
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    agent_id        VARCHAR(32)  NOT NULL COMMENT '智能体ID',
    mac_address     VARCHAR(50)  NOT NULL COMMENT '设备MAC地址',
    session_id      VARCHAR(50)  NOT NULL COMMENT '会话ID',
    chat_type       TINYINT(3)   NOT NULL COMMENT '消息类型: 1-用户, 2-智能体',
    raw_text        TEXT         NOT NULL COMMENT '原始文本',
    redacted_text   TEXT         NOT NULL COMMENT '脱敏文本',
    has_pii         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否包含敏感信息',
    quality_score   DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '样本质量分',
    trainable       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可用于训练导出',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    KEY idx_ai_agent_training_sample_agent_created (agent_id, created_at),
    KEY idx_ai_agent_training_sample_session_created (session_id, created_at),
    KEY idx_ai_agent_training_sample_trainable (agent_id, trainable, created_at),
    KEY idx_ai_agent_training_sample_history (chat_history_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='训练素材样本表';
