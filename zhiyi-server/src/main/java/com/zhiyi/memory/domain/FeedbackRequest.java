package com.zhiyi.memory.domain;

import lombok.Data;

/**
 * Feedback API 请求体
 */
@Data
public class FeedbackRequest {

    /** Recall 会话 ID */
    private String sessionId;

    private Long knowledgeId;

    /** used/helpful/not_helpful/outdated/wrong */
    private String feedbackType;

    private String comment;
}
