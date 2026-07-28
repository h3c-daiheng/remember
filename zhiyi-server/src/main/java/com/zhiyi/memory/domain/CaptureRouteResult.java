package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Capture 路由成功响应
 */
@Data
public class CaptureRouteResult {

    private Long knowledgeId;

    private String knowledgeType;

    private Integer lifecycleStatus;

    private Long captureDraftId;

    /** 前端跳转路径 */
    private String redirectPath;

    /** 人工发布时的相似记忆告警（不阻断） */
    private List<CaptureSimilarKnowledgeHint> similarWarnings = new ArrayList<CaptureSimilarKnowledgeHint>();
}
