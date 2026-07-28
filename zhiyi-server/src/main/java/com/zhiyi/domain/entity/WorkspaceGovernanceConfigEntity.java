package com.zhiyi.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 工作空间治理配置，对应 workspace_governance_config 表。
 * 与主站 workspaceId 对齐，不依赖本地 workspace 种子表。
 */
@Data
@TableName("workspace_governance_config")
public class WorkspaceGovernanceConfigEntity {

    /** 工作空间主键，与 knowledge / governance_issue 一致 */
    @TableId(type = IdType.INPUT)
    private String workspaceId;

    /** 治理配置覆盖 JSON */
    private String governanceConfigJson;

    /** 最近更新时间 */
    private Date updateTime;
}
