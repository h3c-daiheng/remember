package com.zhiyi.memory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * Capture 草稿 AI Review 记录实体，对应 capture_ai_review 表
 */
@Data
@TableName("capture_ai_review")
public class CaptureAiReviewEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工作空间主键 */
    private String workspaceId;

    /** 关联 capture_draft.id */
    private Long draftId;

    /** 0-排队 1-进行中 2-完成 3-失败 */
    private Integer status;

    /** approve / reject / route / escalate_human */
    private String decision;

    /** 1-命中相似记忆门禁 */
    private Integer similarHit;

    /** 裁决置信度 0~1 */
    private Double confidence;

    /** 建议动作 */
    private String recommendedAction;

    /** 建议拒绝码 */
    private String recommendedRejectReason;

    /** 相似知识列表 JSON */
    private String similarJson;

    /** 检查清单预审 JSON */
    private String checklistJson;

    /** 路由/LLM 建议原始快照 JSON */
    private String llmResultJson;

    /** 1-已自动执行落库动作 */
    private Integer executed;

    /** 失败原因 */
    private String errorMessage;

    /** 耗时毫秒 */
    private Integer latencyMs;

    /** 调用链 trace ID */
    private String traceId;

    private Date createTime;

    private Date updateTime;
}
