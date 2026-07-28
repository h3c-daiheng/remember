package com.zhiyi.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 工作空间实体，对应 workspace 表，作为经验隔离与协作边界
 */
@Data
@TableName("workspace")
public class WorkspaceEntity {

    /** 主键 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 关联 organization.id */
    private Long organizationId;

    /** 工作空间编码，与 RecallContext.workspace 对齐 */
    private String workspaceCode;

    /** 工作空间显示名称 */
    private String workspaceName;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 治理配置覆盖 JSON */
    private String governanceConfigJson;

    /** 创建时间 */
    private Date createTime;
}
