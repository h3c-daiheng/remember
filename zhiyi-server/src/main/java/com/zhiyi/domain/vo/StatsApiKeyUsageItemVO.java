package com.zhiyi.domain.vo;

import lombok.Data;

/**
 * Agent API Key 调用用量项
 */
@Data
public class StatsApiKeyUsageItemVO {

    /** API Key 主键，JWT 调用时为 null */
    private Long apiKeyId;

    /** 密钥名称或鉴权来源说明 */
    private String keyLabel;

    /** 密钥前缀 */
    private String keyPrefix;

    private Integer recallCount;

    private Integer rememberCount;

    private Integer feedbackCount;
}
