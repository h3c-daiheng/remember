package com.zhiyi.domain.vo;

import lombok.Data;

import java.util.Date;

/**
 * 工作空间列表项 / 切换后上下文（本地通道，对齐前端 normalizeWorkspaceListItem）
 */
@Data
public class WorkspaceListItemVO {

    /** 工作空间主键（前端映射为 workspaceId） */
    private String id;

    /** 工作空间编码 */
    private String workspaceCode;

    /** 显示名称 */
    private String workspaceName;

    /** 空间类型：本地无此字段，保留为 null */
    private Integer workspaceType;

    /** 当前用户在该空间的成员角色 owner/admin/editor/viewer */
    private String memberRole;

    /** 成员数量 */
    private Integer memberCount;

    /** 是否为当前会话所在空间 */
    private Boolean current;

    /** 创建时间 */
    private Date createTime;
}
