package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * Capture 草稿列表项
 */
@Data
public class CaptureDraftView {

    private Long id;

    private Long eventId;

    /** 提交人本地用户 ID（来自 system_event.creator_id） */
    private Long submitterId;

    /** 提交人昵称 */
    private String submitterNickname;

    /** 提交人头像 URL */
    private String submitterAvatar;

    private KnowledgeDraftContent draftContent;

    /** 0-待确认 1-已采纳 2-已拒绝 */
    private Integer reviewStatus;

    private Long knowledgeId;

    /** 拒绝码 */
    private String rejectReason;

    /** Review 动作 */
    private String reviewAction;

    /** 路由产出的 knowledge 主键 */
    private Long routedKnowledgeId;

    /** Review 备注 */
    private String reviewComment;

    /** AI Review 状态：0排队/1进行中/2完成/3失败 */
    private Integer aiReviewStatus;

    /** AI Review 决策：approve/reject/route/escalate_human */
    private String aiReviewDecision;

    private Date createTime;
}
