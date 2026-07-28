package com.zhiyi.memory;

/**
 * Memory 模块常量定义
 */
public final class MemoryConstants {

    /** 知识类型：经验 */
    public static final String KNOWLEDGE_TYPE_EXPERIENCE = "experience";

    /** 知识类型：规则 */
    public static final String KNOWLEDGE_TYPE_RULE = "rule";

    /** 知识类型：流程 */
    public static final String KNOWLEDGE_TYPE_WORKFLOW = "workflow";

    /** 知识类型：架构决策 */
    public static final String KNOWLEDGE_TYPE_DECISION = "decision";

    /** Review 动作：发布为经验 */
    public static final String REVIEW_ACTION_APPROVE_EXPERIENCE = "approve_experience";

    /** Review 动作：仅拒绝 */
    public static final String REVIEW_ACTION_REJECT = "reject";

    /** Review 动作：转为 Rule 草稿 */
    public static final String REVIEW_ACTION_ROUTE_TO_RULE = "route_to_rule";

    /** Review 动作：转为 Workflow 草稿 */
    public static final String REVIEW_ACTION_ROUTE_TO_WORKFLOW = "route_to_workflow";

    /** Review 动作：转为 Decision 草稿 */
    public static final String REVIEW_ACTION_ROUTE_TO_DECISION = "route_to_decision";

    /** Review 动作：确认发布为 Rule 草稿（Agent 提交时声明为 rule） */
    public static final String REVIEW_ACTION_APPROVE_RULE = "approve_rule";

    /** Review 动作：确认发布为 Workflow 草稿（Agent 提交时声明为 workflow） */
    public static final String REVIEW_ACTION_APPROVE_WORKFLOW = "approve_workflow";

    /** Review 动作：确认发布为 Decision 草稿（Agent 提交时声明为 decision） */
    public static final String REVIEW_ACTION_APPROVE_DECISION = "approve_decision";

    /** Review 动作：合并到已有 Rule */
    public static final String REVIEW_ACTION_MERGE_TO_RULE = "merge_to_rule";

    /** 生命周期：草稿 */
    public static final int LIFECYCLE_DRAFT = 0;

    /** 生命周期：已发布 */
    public static final int LIFECYCLE_PUBLISHED = 1;

    /** 生命周期：已失效 */
    public static final int LIFECYCLE_DEPRECATED = 2;

    /** 审核状态：待确认 */
    public static final int REVIEW_PENDING = 0;

    /** 审核状态：已采纳 */
    public static final int REVIEW_APPROVED = 1;

    /** 审核状态：已拒绝 */
    public static final int REVIEW_REJECTED = 2;

    /** 事件处理：待处理 */
    public static final int EVENT_PENDING = 0;

    /** 事件处理：已完成 */
    public static final int EVENT_DONE = 2;

    /** 事件处理：失败 */
    public static final int EVENT_FAILED = 3;

    /** MVP 默认 Embedding 模型标识（文本相似度） */
    public static final String DEFAULT_TEXT_MODEL = "text-similarity-mvp";

    /** Memory 操作：统一 Submit（Agent 提交记忆草稿） */
    public static final String OPERATION_SUBMIT = "submit";

    /** Memory 操作：Remember（历史日志兼容，新调用请用 submit） */
    public static final String OPERATION_REMEMBER = "remember";

    /** Memory 操作：Recall */
    public static final String OPERATION_RECALL = "recall";

    /** Memory 操作：Search（MVP 与 Recall 共用引擎） */
    public static final String OPERATION_SEARCH = "search";

    /** Memory 操作：Feedback */
    public static final String OPERATION_FEEDBACK = "feedback";

    /** Memory 操作：Store（历史日志兼容，已废弃） */
    public static final String OPERATION_STORE = "store";

    /** 事件类型：Web 文档导入 */
    public static final String EVENT_TYPE_DOCUMENT_IMPORT = "document_import";

    /** 文档导入来源：粘贴 */
    public static final String IMPORT_SOURCE_PASTE = "paste";

    /** 文档导入来源：文件上传 */
    public static final String IMPORT_SOURCE_UPLOAD = "upload";

    /** Web 文档导入操作者标识 */
    public static final String IMPORT_ACTOR_WEB = "web-import";

    /** 文档导入单次最大字符数 */
    public static final int IMPORT_MAX_CONTENT_LENGTH = 100000;

    /** HTTP 请求头：调用链 trace ID */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /** AI Review 状态：排队 */
    public static final int AI_REVIEW_STATUS_QUEUED = 0;

    /** AI Review 状态：进行中 */
    public static final int AI_REVIEW_STATUS_RUNNING = 1;

    /** AI Review 状态：完成 */
    public static final int AI_REVIEW_STATUS_DONE = 2;

    /** AI Review 状态：失败 */
    public static final int AI_REVIEW_STATUS_FAILED = 3;

    /** AI Review 决策：自动采纳并发布 */
    public static final String AI_REVIEW_DECISION_APPROVE = "approve";

    /** AI Review 决策：自动拒绝 */
    public static final String AI_REVIEW_DECISION_REJECT = "reject";

    /** AI Review 决策：自动路由并发布 */
    public static final String AI_REVIEW_DECISION_ROUTE = "route";

    /** AI Review 决策：转人工（相似记忆/低置信/异常） */
    public static final String AI_REVIEW_DECISION_ESCALATE_HUMAN = "escalate_human";

    /** AI 自动审核时写入的 Review 备注前缀 */
    public static final String AI_REVIEW_COMMENT_PREFIX = "AI 审查自动处理：";

    private MemoryConstants() {
    }
}
