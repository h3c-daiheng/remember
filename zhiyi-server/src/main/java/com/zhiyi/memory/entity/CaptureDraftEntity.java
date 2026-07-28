package com.zhiyi.memory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * Capture 草稿实体，对应 capture_draft 表
 */
@Data
@TableName("capture_draft")
public class CaptureDraftEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属工作空间主键 */
    private String workspaceId;

    private Long eventId;

    private String draftJson;

    private Integer reviewStatus;

    private Long knowledgeId;

    private Long reviewerId;

    /** 拒绝码，如 REJECT_DOC_GAP */
    private String rejectReason;

    /** Review 动作：approve_experience / reject / route_to_rule 等 */
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

    private Date updateTime;
}
