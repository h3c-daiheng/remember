CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gonline_user_id` varchar(64) DEFAULT NULL COMMENT 'gonline 平台用户 ID，SSO 绑定',
  `username` varchar(64) NOT NULL COMMENT '登录账号',
  `password` varchar(128) NOT NULL COMMENT '登录密码哈希',
  `nickname` varchar(64) NOT NULL COMMENT '用户昵称',
  `avatar_url` varchar(512) DEFAULT NULL COMMENT '用户头像地址，SSO 登录时从主站同步',
  `last_workspace_id` bigint(20) DEFAULT NULL COMMENT '最近使用的工作空间',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-正常 0-禁用',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0-未删除 1-已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_gonline_user_id` (`gonline_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

INSERT INTO `sys_user` (`username`, `password`, `nickname`, `status`, `deleted`)
VALUES
  ('admin', 'd5369f39cf685c9f949ad2d114d17f21ad01ac115d5d8e39a9acf32ad468bd30', '管理员', 1, 0),
  ('alice', 'd5369f39cf685c9f949ad2d114d17f21ad01ac115d5d8e39a9acf32ad468bd30', 'Alice', 1, 0),
  ('bob', 'd5369f39cf685c9f949ad2d114d17f21ad01ac115d5d8e39a9acf32ad468bd30', 'Bob', 1, 0)
ON DUPLICATE KEY UPDATE `nickname` = VALUES(`nickname`);

CREATE TABLE IF NOT EXISTS `knowledge` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` bigint(20) NOT NULL COMMENT '关联 workspace.id',
  `knowledge_type` varchar(32) NOT NULL COMMENT '类型，MVP 固定 experience',
  `title` varchar(256) NOT NULL COMMENT '标题',
  `project` varchar(128) DEFAULT NULL COMMENT '项目',
  `module` varchar(128) DEFAULT NULL COMMENT '模块',
  `repository` varchar(256) DEFAULT NULL COMMENT '代码仓库',
  `language` varchar(64) DEFAULT NULL COMMENT '编程语言',
  `framework` varchar(128) DEFAULT NULL COMMENT '框架',
  `lifecycle_status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0-草稿 1-已发布 2-已失效',
  `recall_count` int(11) NOT NULL DEFAULT '0' COMMENT 'Recall 调用次数',
  `creator_id` bigint(20) NOT NULL COMMENT '创建者',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_workspace` (`workspace_id`),
  KEY `idx_project_module` (`project`, `module`),
  KEY `idx_type_status` (`knowledge_type`, `lifecycle_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一知识对象';

CREATE TABLE IF NOT EXISTS `knowledge_fact` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `knowledge_id` bigint(20) NOT NULL COMMENT '关联 knowledge.id',
  `block_type` varchar(32) NOT NULL COMMENT 'observation/decision/constraint/rule/evidence/action/outcome',
  `block_text` text NOT NULL COMMENT '事实文本',
  `block_metadata` json DEFAULT NULL COMMENT '扩展元数据',
  `sort_order` int(11) NOT NULL DEFAULT '0' COMMENT '排序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_knowledge` (`knowledge_id`),
  KEY `idx_block_type` (`block_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Fact Block';

CREATE TABLE IF NOT EXISTS `knowledge_tag` (
  `knowledge_id` bigint(20) NOT NULL COMMENT '关联 knowledge.id',
  `tag_name` varchar(64) NOT NULL COMMENT '标签',
  PRIMARY KEY (`knowledge_id`, `tag_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识标签';

CREATE TABLE IF NOT EXISTS `knowledge_artifact` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `knowledge_id` bigint(20) NOT NULL COMMENT '关联 knowledge.id',
  `artifact_type` varchar(32) NOT NULL COMMENT 'commit/pr/issue/conversation/code/test/deploy/manual',
  `artifact_role` varchar(32) NOT NULL COMMENT 'origin/evidence/attachment/reference',
  `artifact_url` varchar(512) DEFAULT NULL COMMENT '外链',
  `content_ref` text COMMENT '内容引用',
  `author_id` bigint(20) DEFAULT NULL COMMENT '作者',
  `artifact_time` datetime DEFAULT NULL COMMENT 'artifact 时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_knowledge` (`knowledge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一 Artifact';

CREATE TABLE IF NOT EXISTS `knowledge_vector_ref` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `knowledge_id` bigint(20) NOT NULL COMMENT '关联 knowledge.id',
  `chunk_index` int(11) NOT NULL DEFAULT '0' COMMENT '分块序号',
  `chunk_text` text NOT NULL COMMENT '分块原文',
  `vector_id` varchar(128) NOT NULL COMMENT '向量库中的 ID',
  `model_name` varchar(64) NOT NULL COMMENT 'Embedding 模型',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_knowledge` (`knowledge_id`),
  KEY `idx_vector_id` (`vector_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='向量索引引用';

CREATE TABLE IF NOT EXISTS `knowledge_relation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` bigint(20) NOT NULL COMMENT '工作空间，隔离边界',
  `source_id` bigint(20) NOT NULL COMMENT '源 knowledge.id',
  `target_id` bigint(20) NOT NULL COMMENT '目标 knowledge.id',
  `relation_type` varchar(32) NOT NULL COMMENT '关系类型',
  `relation_source` varchar(32) NOT NULL COMMENT 'auto-自动建边 manual-人工 merge-合并导入',
  `confidence` decimal(5,4) DEFAULT NULL COMMENT '自动建边置信度 0~1',
  `relation_metadata` json DEFAULT NULL COMMENT '建边依据快照',
  `creator_id` bigint(20) DEFAULT NULL COMMENT '人工建边时填写',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_edge` (`workspace_id`,`source_id`,`target_id`,`relation_type`),
  KEY `idx_source` (`source_id`),
  KEY `idx_target` (`target_id`),
  KEY `idx_type` (`relation_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经验关系边';

CREATE TABLE IF NOT EXISTS `knowledge_timeline` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` bigint(20) NOT NULL COMMENT '工作空间',
  `knowledge_id` bigint(20) NOT NULL COMMENT '关联 knowledge.id',
  `event_type` varchar(32) NOT NULL COMMENT 'publish/deprecate/reactivate/supersede/cascade_review_hint',
  `related_knowledge_id` bigint(20) DEFAULT NULL COMMENT '关联经验，如被替代者或级联源头',
  `operator_id` bigint(20) DEFAULT NULL COMMENT '操作者',
  `event_summary` varchar(512) DEFAULT NULL COMMENT '事件摘要',
  `event_metadata` json DEFAULT NULL COMMENT '扩展信息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_knowledge` (`knowledge_id`),
  KEY `idx_workspace` (`workspace_id`),
  KEY `idx_event_type` (`event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经验版本时间线';

CREATE TABLE IF NOT EXISTS `system_event` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` bigint(20) DEFAULT NULL COMMENT '关联 workspace.id',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型',
  `actor` varchar(128) DEFAULT NULL COMMENT '触发者',
  `workspace` varchar(128) DEFAULT NULL COMMENT '工作空间编码',
  `repository` varchar(256) DEFAULT NULL COMMENT '仓库',
  `module` varchar(128) DEFAULT NULL COMMENT '模块',
  `event_time` datetime DEFAULT NULL COMMENT '事件发生时间',
  `artifacts_json` json DEFAULT NULL COMMENT '统一 artifacts 数组',
  `metadata_json` json DEFAULT NULL COMMENT '扩展元数据',
  `payload_json` json NOT NULL COMMENT '业务载荷',
  `process_status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0-待处理 1-处理中 2-已完成 3-失败',
  `knowledge_id` bigint(20) DEFAULT NULL COMMENT 'Capture 产出',
  `creator_id` bigint(20) DEFAULT NULL COMMENT '关联用户',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_workspace_id` (`workspace_id`),
  KEY `idx_event_type_status` (`event_type`, `process_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一 Event Schema';

CREATE TABLE IF NOT EXISTS `capture_draft` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` bigint(20) DEFAULT NULL COMMENT '关联 workspace.id',
  `event_id` bigint(20) NOT NULL COMMENT '关联 system_event.id',
  `draft_json` json NOT NULL COMMENT 'Fact Blocks + Artifacts 草稿',
  `review_status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0-待确认 1-已采纳 2-已拒绝',
  `knowledge_id` bigint(20) DEFAULT NULL COMMENT '采纳后 knowledge.id',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '审核者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_workspace_review` (`workspace_id`, `review_status`),
  KEY `idx_review_status` (`review_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Capture 草稿';

CREATE TABLE IF NOT EXISTS `memory_feedback` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `knowledge_id` bigint(20) NOT NULL COMMENT '关联 knowledge.id',
  `recall_session` varchar(128) DEFAULT NULL COMMENT 'Recall 会话 ID',
  `feedback_type` varchar(32) NOT NULL COMMENT 'used/helpful/not_helpful/outdated/wrong',
  `context_json` json DEFAULT NULL COMMENT 'RecallContext 快照',
  `actor_id` bigint(20) DEFAULT NULL COMMENT '反馈者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_knowledge` (`knowledge_id`),
  KEY `idx_feedback_type` (`feedback_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Recall 效果反馈';

CREATE TABLE IF NOT EXISTS `memory_operation_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` bigint(20) NOT NULL COMMENT '工作空间',
  `operation` varchar(32) NOT NULL COMMENT 'remember/recall/feedback/search',
  `trace_id` varchar(64) NOT NULL COMMENT '调用链 trace ID',
  `recall_session` varchar(128) DEFAULT NULL COMMENT 'Recall 会话 ID，仅 recall/search 有值',
  `api_key_id` bigint(20) DEFAULT NULL COMMENT 'API Key 主键',
  `auth_type` varchar(16) DEFAULT NULL COMMENT 'api_key/jwt',
  `request_json` json DEFAULT NULL COMMENT '请求快照',
  `response_summary_json` json DEFAULT NULL COMMENT '响应摘要',
  `success` tinyint(4) NOT NULL DEFAULT '1' COMMENT '1-成功 0-失败',
  `error_message` varchar(512) DEFAULT NULL COMMENT '失败原因',
  `latency_ms` int(11) DEFAULT NULL COMMENT '耗时毫秒',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_workspace_operation_time` (`workspace_id`, `operation`, `create_time`),
  KEY `idx_trace_id` (`trace_id`),
  KEY `idx_recall_session` (`recall_session`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Memory API 操作追踪日志';

CREATE TABLE IF NOT EXISTS `ai_model_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(128) NOT NULL COMMENT '配置名称',
  `provider` varchar(32) NOT NULL COMMENT 'openai/compatible/ernie',
  `model_code` varchar(128) NOT NULL COMMENT '模型编码',
  `api_base` varchar(512) DEFAULT NULL COMMENT 'API 地址',
  `api_key_enc` varchar(512) DEFAULT NULL COMMENT '加密密钥',
  `usage_type` varchar(32) NOT NULL COMMENT 'embedding/summary/chat',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '1-启用 0-禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 模型配置';

CREATE TABLE IF NOT EXISTS `organization` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `org_name` varchar(128) NOT NULL COMMENT '企业/组织名称',
  `plan_type` varchar(32) NOT NULL DEFAULT 'free' COMMENT 'free/pro/enterprise',
  `plan_status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '1-有效 0-停用',
  `seat_limit` int(11) NOT NULL DEFAULT '3' COMMENT '席位上限',
  `recall_quota` int(11) NOT NULL DEFAULT '1000' COMMENT '月 Recall 配额',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='付费主体';

CREATE TABLE IF NOT EXISTS `workspace` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `organization_id` bigint(20) NOT NULL COMMENT '关联 organization.id',
  `workspace_code` varchar(64) NOT NULL COMMENT 'RecallContext.workspace 对齐',
  `workspace_name` varchar(128) NOT NULL COMMENT '显示名称',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '1-启用 0-停用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_code` (`organization_id`, `workspace_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作空间';

CREATE TABLE IF NOT EXISTS `workspace_governance_config` (
  `workspace_id` bigint(20) NOT NULL COMMENT '工作空间主键，与主站/业务表对齐',
  `governance_config_json` json DEFAULT NULL COMMENT '治理配置覆盖：autoResolveEnabled/autoResolveSimilarityThreshold等',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`workspace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作空间治理配置';

CREATE TABLE IF NOT EXISTS `workspace_member` (
  `workspace_id` bigint(20) NOT NULL COMMENT '关联 workspace.id',
  `user_id` bigint(20) NOT NULL COMMENT '关联用户',
  `member_role` varchar(32) NOT NULL COMMENT 'owner/admin/editor/viewer',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`workspace_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作空间成员';

INSERT INTO `organization` (`id`, `org_name`, `plan_type`, `plan_status`, `seat_limit`, `recall_quota`)
VALUES (1, '智忆默认组织', 'free', 1, 3, 1000)
ON DUPLICATE KEY UPDATE `org_name` = VALUES(`org_name`);

INSERT INTO `workspace` (`id`, `organization_id`, `workspace_code`, `workspace_name`, `status`)
VALUES
  (1, 1, 'default', '默认工作空间', 1),
  (2, 1, 'backend', '后端组', 1)
ON DUPLICATE KEY UPDATE `workspace_name` = VALUES(`workspace_name`);

INSERT INTO `workspace_member` (`workspace_id`, `user_id`, `member_role`)
VALUES
  (1, 1, 'owner'),
  (1, 2, 'editor'),
  (1, 3, 'viewer'),
  (2, 1, 'owner'),
  (2, 2, 'editor')
ON DUPLICATE KEY UPDATE `member_role` = VALUES(`member_role`);

CREATE TABLE IF NOT EXISTS `api_key` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` bigint(20) NOT NULL COMMENT '关联 workspace.id',
  `key_name` varchar(128) NOT NULL COMMENT '密钥名称',
  `key_hash` varchar(128) NOT NULL COMMENT '密钥哈希',
  `key_prefix` varchar(16) NOT NULL COMMENT '密钥前缀便于识别',
  `permission_recall` tinyint(4) NOT NULL DEFAULT '1' COMMENT '允许 Recall',
  `permission_remember` tinyint(4) NOT NULL DEFAULT '1' COMMENT '允许 Remember',
  `last_used_time` datetime DEFAULT NULL COMMENT '最后使用时间',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '1-启用 0-吊销',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_workspace` (`workspace_id`),
  KEY `idx_key_prefix` (`key_prefix`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent API 密钥';

ALTER TABLE `sys_user` ADD COLUMN `gonline_user_id` varchar(64) DEFAULT NULL COMMENT 'gonline 平台用户 ID，SSO 绑定' AFTER `id`;
ALTER TABLE `sys_user` ADD COLUMN `last_workspace_id` bigint(20) DEFAULT NULL COMMENT '最近使用的工作空间' AFTER `nickname`;
ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_gonline_user_id` (`gonline_user_id`);

ALTER TABLE `knowledge` ADD COLUMN `workspace_id` bigint(20) DEFAULT NULL COMMENT '关联 workspace.id' AFTER `id`;
ALTER TABLE `knowledge` ADD KEY `idx_workspace` (`workspace_id`);
UPDATE `knowledge` knowledge_table
LEFT JOIN `sys_user` user_table ON knowledge_table.creator_id = user_table.id
SET knowledge_table.workspace_id = COALESCE(user_table.last_workspace_id, 1)
WHERE knowledge_table.workspace_id IS NULL;
ALTER TABLE `knowledge` MODIFY COLUMN `workspace_id` bigint(20) NOT NULL COMMENT '关联 workspace.id';

ALTER TABLE `system_event` ADD COLUMN `workspace_id` bigint(20) DEFAULT NULL COMMENT '关联 workspace.id' AFTER `id`;
ALTER TABLE `system_event` ADD KEY `idx_workspace_id` (`workspace_id`);
UPDATE `system_event` event_table
INNER JOIN `workspace` workspace_table ON event_table.workspace = workspace_table.workspace_code AND workspace_table.organization_id = 1
SET event_table.workspace_id = workspace_table.id
WHERE event_table.workspace_id IS NULL AND event_table.workspace IS NOT NULL;

ALTER TABLE `capture_draft` ADD COLUMN `workspace_id` bigint(20) DEFAULT NULL COMMENT '关联 workspace.id' AFTER `id`;
ALTER TABLE `capture_draft` ADD KEY `idx_workspace_review` (`workspace_id`, `review_status`);
UPDATE `capture_draft` draft_table
INNER JOIN `system_event` event_table ON draft_table.event_id = event_table.id
SET draft_table.workspace_id = event_table.workspace_id
WHERE draft_table.workspace_id IS NULL AND event_table.workspace_id IS NOT NULL;

ALTER TABLE `capture_draft` ADD COLUMN `reject_reason` varchar(64) DEFAULT NULL COMMENT '拒绝码 REJECT_*' AFTER `reviewer_id`;
ALTER TABLE `capture_draft` ADD COLUMN `review_action` varchar(32) DEFAULT NULL COMMENT 'approve_experience/reject/route_to_rule/route_to_workflow/merge_to_rule' AFTER `reject_reason`;
ALTER TABLE `capture_draft` ADD COLUMN `routed_knowledge_id` bigint(20) DEFAULT NULL COMMENT '路由产出的 knowledge.id' AFTER `review_action`;
ALTER TABLE `capture_draft` ADD COLUMN `review_comment` varchar(512) DEFAULT NULL COMMENT 'Review 备注' AFTER `routed_knowledge_id`;

ALTER TABLE `knowledge` ADD COLUMN `source_capture_draft_id` bigint(20) DEFAULT NULL COMMENT '来源 Capture 草稿 ID' AFTER `creator_id`;
ALTER TABLE `knowledge` ADD KEY `idx_source_capture_draft` (`source_capture_draft_id`);

CREATE TABLE IF NOT EXISTS `usage_daily` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` bigint(20) NOT NULL COMMENT '关联 workspace.id',
  `usage_date` date NOT NULL COMMENT '统计日期',
  `recall_count` int(11) NOT NULL DEFAULT '0' COMMENT 'Recall + Search 次数',
  `remember_count` int(11) NOT NULL DEFAULT '0' COMMENT 'Remember 次数',
  `feedback_count` int(11) NOT NULL DEFAULT '0' COMMENT 'Feedback 次数',
  `embedding_tokens` bigint(20) NOT NULL DEFAULT '0' COMMENT 'Embedding token 估算',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workspace_date` (`workspace_id`, `usage_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日用量统计，供计费与限流';

UPDATE `knowledge` knowledge_table
INNER JOIN `workspace` workspace_table ON knowledge_table.workspace_id = workspace_table.id
SET knowledge_table.project = CASE
  WHEN knowledge_table.repository IS NOT NULL AND INSTR(knowledge_table.repository, '/') > 0
    THEN SUBSTRING_INDEX(knowledge_table.repository, '/', -1)
  WHEN knowledge_table.repository IS NOT NULL AND INSTR(knowledge_table.repository, '-') > 0
    THEN SUBSTRING_INDEX(knowledge_table.repository, '-', 1)
  WHEN knowledge_table.repository IS NOT NULL AND TRIM(knowledge_table.repository) <> ''
    THEN TRIM(knowledge_table.repository)
  ELSE NULL
END
WHERE knowledge_table.deleted = 0
  AND knowledge_table.project = workspace_table.workspace_code;

CREATE TABLE IF NOT EXISTS `knowledge_timeline` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` bigint(20) NOT NULL COMMENT '工作空间',
  `knowledge_id` bigint(20) NOT NULL COMMENT '关联 knowledge.id',
  `event_type` varchar(32) NOT NULL COMMENT 'publish/deprecate/reactivate/supersede/cascade_review_hint',
  `related_knowledge_id` bigint(20) DEFAULT NULL COMMENT '关联经验，如被替代者或级联源头',
  `operator_id` bigint(20) DEFAULT NULL COMMENT '操作者',
  `event_summary` varchar(512) DEFAULT NULL COMMENT '事件摘要',
  `event_metadata` json DEFAULT NULL COMMENT '扩展信息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_knowledge` (`knowledge_id`),
  KEY `idx_workspace` (`workspace_id`),
  KEY `idx_event_type` (`event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经验版本时间线';

ALTER TABLE `sys_user` ADD COLUMN `avatar_url` varchar(512) DEFAULT NULL COMMENT '用户头像地址，SSO 登录时从主站同步' AFTER `nickname`;

CREATE TABLE IF NOT EXISTS `capture_ai_review` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` bigint(20) NOT NULL COMMENT '工作空间',
  `draft_id` bigint(20) NOT NULL COMMENT '关联 capture_draft.id',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0-排队 1-进行中 2-完成 3-失败',
  `decision` varchar(32) DEFAULT NULL COMMENT 'approve/reject/route/escalate_human',
  `similar_hit` tinyint(4) NOT NULL DEFAULT '0' COMMENT '1-命中相似记忆门禁',
  `confidence` double DEFAULT NULL COMMENT '裁决置信度 0~1',
  `recommended_action` varchar(64) DEFAULT NULL COMMENT '建议动作 approve_*/reject/route_to_*',
  `recommended_reject_reason` varchar(64) DEFAULT NULL COMMENT '建议拒绝码 REJECT_*',
  `similar_json` json DEFAULT NULL COMMENT '相似知识列表',
  `checklist_json` json DEFAULT NULL COMMENT '检查清单预审结果',
  `llm_result_json` json DEFAULT NULL COMMENT '路由/LLM 建议原始快照',
  `executed` tinyint(4) NOT NULL DEFAULT '0' COMMENT '1-已自动执行落库动作',
  `error_message` varchar(512) DEFAULT NULL COMMENT '失败原因',
  `latency_ms` int(11) DEFAULT NULL COMMENT '耗时毫秒',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '调用链 trace ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_draft_id` (`draft_id`),
  KEY `idx_workspace_status_time` (`workspace_id`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Capture 草稿 AI Review 记录';

ALTER TABLE `capture_draft` ADD COLUMN `ai_review_status` tinyint(4) DEFAULT NULL COMMENT 'AI Review 状态 0排队/1进行中/2完成/3失败' AFTER `review_comment`;
ALTER TABLE `capture_draft` ADD COLUMN `ai_review_decision` varchar(32) DEFAULT NULL COMMENT 'AI Review 决策 approve/reject/route/escalate_human' AFTER `ai_review_status`;

CREATE TABLE IF NOT EXISTS `governance_scan_batch` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` bigint(20) NOT NULL COMMENT '工作空间',
  `scan_type` varchar(32) NOT NULL COMMENT 'full/incremental/module',
  `knowledge_type` varchar(32) DEFAULT NULL COMMENT '扫描限定知识类型',
  `module_filter` varchar(128) DEFAULT NULL COMMENT '扫描限定模块',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0-进行中 1-完成 2-失败',
  `scanned_count` int(11) NOT NULL DEFAULT '0' COMMENT '扫描条目数',
  `issue_count` int(11) NOT NULL DEFAULT '0' COMMENT '发现问题数',
  `similarity_threshold` double NOT NULL DEFAULT '0.65' COMMENT '重复判定阈值',
  `error_message` varchar(512) DEFAULT NULL COMMENT '失败原因',
  `operator_id` bigint(20) DEFAULT NULL COMMENT '操作者',
  `auto_resolved_count` int(11) NOT NULL DEFAULT '0' COMMENT '本次扫描自动处置工单数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `finish_time` datetime DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`),
  KEY `idx_workspace_time` (`workspace_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记忆治理扫描批次';

CREATE TABLE IF NOT EXISTS `governance_issue` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` bigint(20) NOT NULL COMMENT '工作空间',
  `issue_type` varchar(32) NOT NULL COMMENT 'duplicate',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0-待处理 1-已解决 2-已忽略',
  `primary_knowledge_id` bigint(20) NOT NULL COMMENT '推荐保留的主版本',
  `related_knowledge_ids` json NOT NULL COMMENT '重复记忆 ID 列表',
  `knowledge_type` varchar(32) NOT NULL COMMENT '知识类型',
  `similarity_score` double NOT NULL COMMENT '组内最高相似度',
  `suggested_action` varchar(64) NOT NULL COMMENT '建议处置动作',
  `scan_batch_id` bigint(20) DEFAULT NULL COMMENT '来源扫描批次',
  `resolved_action` varchar(64) DEFAULT NULL COMMENT '实际处置动作',
  `resolve_comment` varchar(512) DEFAULT NULL COMMENT '处置备注',
  `operator_id` bigint(20) DEFAULT NULL COMMENT '处置操作者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `resolve_time` datetime DEFAULT NULL COMMENT '处置时间',
  PRIMARY KEY (`id`),
  KEY `idx_workspace_status` (`workspace_id`, `status`),
  KEY `idx_batch` (`scan_batch_id`),
  KEY `idx_primary` (`primary_knowledge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记忆治理工单';

ALTER TABLE `governance_scan_batch` MODIFY COLUMN `scan_type` varchar(32) NOT NULL COMMENT 'full/incremental/module/fragment';

ALTER TABLE `governance_issue` MODIFY COLUMN `issue_type` varchar(32) NOT NULL COMMENT 'duplicate/fragment_cluster/incomplete/outdated/conflict';

ALTER TABLE `governance_issue` ADD COLUMN `issue_metadata_json` json DEFAULT NULL COMMENT '校验详情、反馈统计等扩展';

ALTER TABLE `governance_scan_batch` MODIFY COLUMN `scan_type` varchar(32) NOT NULL COMMENT 'full/incremental/module/fragment/validate';

ALTER TABLE `governance_scan_batch` ADD COLUMN `auto_resolved_count` int(11) NOT NULL DEFAULT '0' COMMENT '本次扫描自动处置工单数';

ALTER TABLE `workspace` ADD COLUMN `governance_config_json` json DEFAULT NULL COMMENT '治理配置覆盖：autoResolveEnabled/autoResolveSimilarityThreshold等';

CREATE TABLE IF NOT EXISTS `workspace_governance_config` (
  `workspace_id` bigint(20) NOT NULL COMMENT '工作空间主键，与主站/业务表对齐',
  `governance_config_json` json DEFAULT NULL COMMENT '治理配置覆盖：autoResolveEnabled/autoResolveSimilarityThreshold等',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`workspace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作空间治理配置';
