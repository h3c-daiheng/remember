package com.zhiyi.memory.domain;

import lombok.Data;

/**
 * 文档导入 AI 抽取响应：预览结构化草稿，不直接入库
 */
@Data
public class DocumentImportExtractResponse {

    /** 是否建议提交为 Capture 草稿 */
    private boolean submit;

    /** 不建议提交时的原因码 */
    private String skipReason;

    /** LLM 推荐的知识类型 */
    private String suggestedType;

    /** 置信度 0~1 */
    private Double confidence;

    /** 路由建议：approve_experience / route_to_rule 等 */
    private String routeHint;

    /** 结构化草稿预览 */
    private KnowledgeDraftContent draft;

    /** 质量检查结果 */
    private DocumentImportQualityChecks qualityChecks;

    /** 模型请求追踪 ID */
    private String requestId;
}
