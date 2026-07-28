package com.zhiyi.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 工作空间成员实体，对应 workspace_member 表
 */
@Data
@TableName("workspace_member")
public class WorkspaceMemberEntity {

    /** 关联 workspace.id */
    private String workspaceId;

    /** 关联 sys_user.id */
    private Long userId;

    /** 成员角色：owner/admin/editor/viewer */
    private String memberRole;

    /** 加入时间 */
    private Date createTime;
}
