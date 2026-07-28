package com.zhiyi.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 组织实体，对应 organization 表，作为付费与配额主体
 */
@Data
@TableName("organization")
public class OrganizationEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 企业/组织名称 */
    private String orgName;

    /** 套餐类型：free/pro/enterprise */
    private String planType;

    /** 套餐状态：1-有效 0-停用 */
    private Integer planStatus;

    /** 席位上限 */
    private Integer seatLimit;

    /** 月 Recall 配额 */
    private Integer recallQuota;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;
}
