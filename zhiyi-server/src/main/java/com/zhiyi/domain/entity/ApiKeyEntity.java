package com.zhiyi.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * Agent API Key 实体，对应 api_key 表
 */
@Data
@TableName("api_key")
public class ApiKeyEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 workspace.id */
    private String workspaceId;

    /** 密钥名称，便于识别用途 */
    private String keyName;

    /** 密钥哈希，明文不落库 */
    private String keyHash;

    /** 密钥前缀，便于展示与检索 */
    private String keyPrefix;

    /** 是否允许 Recall：1-是 0-否 */
    private Integer permissionRecall;

    /** 是否允许 Remember：1-是 0-否 */
    private Integer permissionRemember;

    /** 最后使用时间 */
    private Date lastUsedTime;

    /** 状态：1-启用 0-吊销 */
    private Integer status;

    /** 创建时间 */
    private Date createTime;
}
