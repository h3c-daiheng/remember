package com.zhiyi.workspace;

import org.apache.commons.lang3.StringUtils;

/**
 * 工作空间成员角色常量与归一化。
 * 主站 WorkspaceMemberRoleEnum 使用整型 1~4；智忆内部统一为字符串 owner/admin/editor/viewer。
 */
public final class WorkspaceMemberRole {

    /** 拥有者，可管理成员与空间设置 */
    public static final String OWNER = "owner";

    /** 管理员，可管理成员与 API Key */
    public static final String ADMIN = "admin";

    /** 编辑者，可 Review 与发布经验 */
    public static final String EDITOR = "editor";

    /** 查看者，只读访问 */
    public static final String VIEWER = "viewer";

    /** 主站枚举：所有者 */
    public static final int CODE_OWNER = 1;

    /** 主站枚举：管理员 */
    public static final int CODE_ADMIN = 2;

    /** 主站枚举：编辑者 */
    public static final int CODE_EDITOR = 3;

    /** 主站枚举：访客 */
    public static final int CODE_VIEWER = 4;

    private WorkspaceMemberRole() {
    }

    /**
     * 将主站整型角色归一为智忆内部字符串角色
     *
     * @param memberRoleCode 主站角色枚举值 1~4
     * @return owner/admin/editor/viewer；无法识别时返回 null
     */
    public static String normalize(Integer memberRoleCode) {
        if (memberRoleCode == null) {
            return null;
        }
        if (memberRoleCode.intValue() == CODE_OWNER) {
            return OWNER;
        }
        if (memberRoleCode.intValue() == CODE_ADMIN) {
            return ADMIN;
        }
        if (memberRoleCode.intValue() == CODE_EDITOR) {
            return EDITOR;
        }
        if (memberRoleCode.intValue() == CODE_VIEWER) {
            return VIEWER;
        }
        return null;
    }

    /**
     * 将主站整型（含数字字符串）或历史字符串角色归一为智忆内部字符串
     *
     * @param memberRole 主站返回的 memberRole（可能为 "1" 或 "owner"）
     * @return owner/admin/editor/viewer；无法识别时返回 null
     */
    public static String normalize(String memberRole) {
        if (StringUtils.isBlank(memberRole)) {
            return null;
        }
        String trimmedRole = memberRole.trim();
        if (OWNER.equals(trimmedRole)
                || ADMIN.equals(trimmedRole)
                || EDITOR.equals(trimmedRole)
                || VIEWER.equals(trimmedRole)) {
            return trimmedRole;
        }
        try {
            return normalize(Integer.valueOf(trimmedRole));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 判断角色是否具备空间管理权限（修改名称、成员等）
     */
    public static boolean canManageWorkspace(String memberRole) {
        String normalizedRole = normalize(memberRole);
        return OWNER.equals(normalizedRole) || ADMIN.equals(normalizedRole);
    }

    /**
     * 判断角色是否可编辑、发布或下架经验
     */
    public static boolean canEditKnowledge(String memberRole) {
        String normalizedRole = normalize(memberRole);
        return OWNER.equals(normalizedRole)
                || ADMIN.equals(normalizedRole)
                || EDITOR.equals(normalizedRole);
    }

    /**
     * 判断当前用户是否可删除经验：管理者可删任意经验，其余角色仅可删除本人创建的经验
     */
    public static boolean canDeleteKnowledge(String memberRole, Long operatorUserId, Long creatorId) {
        if (canManageWorkspace(memberRole)) {
            return true;
        }
        return operatorUserId != null && creatorId != null && operatorUserId.equals(creatorId);
    }

    /**
     * 判断当前用户是否可修改经验：编辑角色可改任意经验，发布者仅可修改本人创建的经验
     */
    public static boolean canModifyKnowledge(String memberRole, Long operatorUserId, Long creatorId) {
        if (canEditKnowledge(memberRole)) {
            return true;
        }
        return operatorUserId != null && creatorId != null && operatorUserId.equals(creatorId);
    }
}
