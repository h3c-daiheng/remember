package com.zhiyi.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 登录成功后返回的用户信息（不含密码）
 */
@Data
public class LoginUserVO {

    /** 用户主键 */
    private Long userId;

    /** 登录账号 */
    private String username;

    /** 用户昵称 */
    private String nickname;

    /** 头像地址，管理端暂未接入时可留空 */
    private String avatar;

    /** 管理端菜单权限标识 */
    private List<String> permissions;

    /** 组织主键 */
    private Long organizationId;

    /** 当前工作空间主键 */
    private String workspaceId;

    /** 当前工作空间编码，与 RecallContext.workspace 对齐 */
    private String workspaceCode;

    /** 当前工作空间显示名称 */
    private String workspaceName;

    /** 当前用户在工作空间内的角色 */
    private String memberRole;
}
