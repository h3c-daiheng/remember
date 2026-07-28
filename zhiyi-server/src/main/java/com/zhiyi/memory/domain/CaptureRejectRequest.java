package com.zhiyi.memory.domain;

import lombok.Data;

/**
 * Capture 拒绝请求体
 */
@Data
public class CaptureRejectRequest {

    /** 拒绝码，如 REJECT_DOC_GAP */
    private String rejectReason;

    /** Review 备注 */
    private String reviewComment;
}
