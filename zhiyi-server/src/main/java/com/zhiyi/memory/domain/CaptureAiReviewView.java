package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Capture AI Review 记录视图，供前端展示审查结论
 */
@Data
public class CaptureAiReviewView {

    private Long id;

    private Long draftId;

    /** 0-排队 1-进行中 2-完成 3-失败 */
    private Integer status;

    /** approve / reject / route / escalate_human */
    private String decision;

    /** 是否命中相似记忆门禁 */
    private Boolean similarHit;

    private Double confidence;

    private String recommendedAction;

    private String recommendedRejectReason;

    private List<CaptureSimilarKnowledgeHint> similarKnowledge = new ArrayList<CaptureSimilarKnowledgeHint>();

    private List<CaptureRouteChecklistHint> checklistHints = new ArrayList<CaptureRouteChecklistHint>();

    /** 路由建议原始快照（JSON 对象反序列化后的建议） */
    private CaptureRouteSuggestion routeSuggestion;

    /** 是否已自动执行落库动作 */
    private Boolean executed;

    private String errorMessage;

    private Integer latencyMs;

    private String traceId;

    private Date createTime;

    private Date updateTime;
}
