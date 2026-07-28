package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Capture 草稿归类建议，由规则引擎与 LLM 混合分析产出
 */
@Data
public class CaptureRouteSuggestion {

    /** 推荐动作：approve_experience / route_to_rule / route_to_workflow / route_to_decision / reject */
    private String recommendedAction;

    /** 置信度 0~1 */
    private double confidence;

    /** 若建议拒绝，推荐的拒绝码 */
    private String recommendedRejectReason;

    /** 推荐理由列表 */
    private List<String> reasons = new ArrayList<String>();

    /** 备选动作 */
    private List<String> alternatives = new ArrayList<String>();

    /** 目标知识类型 */
    private String targetKnowledgeType;

    /** 建议来源：rule=纯规则 / llm=纯 AI / hybrid=规则+AI 混合 */
    private String suggestionSource;

    /** AI / 规则对 Review 检查项的预审提示（R1–R4 / Q1–Q6 / C1–C3） */
    private List<CaptureRouteChecklistHint> checklistHints = new ArrayList<CaptureRouteChecklistHint>();

    /** 工作空间内相似已有知识，辅助 R4 重复判断 */
    private List<CaptureSimilarKnowledgeHint> similarKnowledge = new ArrayList<CaptureSimilarKnowledgeHint>();
}
