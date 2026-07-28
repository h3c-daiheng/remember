package com.zhiyi.domain.vo;

import lombok.Data;

import java.util.Date;

/**
 * Agent API Key 视图（不含 hash；对齐前端 keyList/enabled 结构）
 */
@Data
public class ApiKeyVO {

    /** 主键 */
    private Long id;

    /** 密钥名称 */
    private String keyName;

    /** 明文前缀，便于识别 */
    private String keyPrefix;

    /** 允许 Recall */
    private Boolean permissionRecall;

    /** 允许 Remember */
    private Boolean permissionRemember;

    /** 是否启用（status==1） */
    private Boolean enabled;

    /** 最后使用时间 */
    private Date lastUsedTime;

    /** 创建时间 */
    private Date createTime;
}
