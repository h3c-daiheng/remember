package com.zhiyi.domain.vo;

import lombok.Data;

/**
 * 经验替代演进请求：当前经验替代 predecessorId 所指旧版
 */
@Data
public class KnowledgeSupersedeRequest {

    /** 被替代的旧版 knowledge.id */
    private Long predecessorId;

    /** 可选备注 */
    private String comment;
}
