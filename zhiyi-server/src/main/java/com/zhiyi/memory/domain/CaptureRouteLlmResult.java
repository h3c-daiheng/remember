package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Capture 归类 LLM 返回 JSON 映射
 */
@Data
public class CaptureRouteLlmResult {

    /** 推荐动作：approve_experience / route_to_rule / route_to_workflow / route_to_decision / reject */
    private String recommendedAction;

    /** 目标知识类型 */
    private String targetKnowledgeType;

    /** 置信度 0~1 */
    private Double confidence;

    /** 若建议拒绝，推荐的拒绝码 */
    private String recommendedRejectReason;

    /** 推荐理由 */
    private List<String> reasons = new ArrayList<String>();

    /** R1–R4 等检查项 AI 预审结果 */
    private List<CaptureRouteChecklistHint> checklistHints = new ArrayList<CaptureRouteChecklistHint>();
}
