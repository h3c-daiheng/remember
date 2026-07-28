package com.zhiyi.memory.domain;

import lombok.Data;

/**
 * Capture 路由请求：将草稿转为 Rule / Workflow 草稿
 */
@Data
public class CaptureRouteRequest {

    /** 目标类型：rule / workflow */
    private String targetType;

    /** 拒绝码，路由时通常带 REJECT_DOC_GAP 等 */
    private String rejectReason;

    /** Review 备注 */
    private String reviewComment;

    /** Review 人编辑后的草稿内容，可选 */
    private KnowledgeDraftContent editedContent;
}
