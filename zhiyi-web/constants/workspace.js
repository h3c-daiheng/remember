/**
 * 工作空间常量：成员角色与套餐类型展示文案。
 * 主站 memberRole 为整型 1~4，智忆内部统一为 owner/admin/editor/viewer。
 *
 * 权限细表（zhiyi-web）：
 * - 内容协作（新建 / Review / 导入 / 编辑发布）：owner / admin / editor
 * - 删除他人知识 / 空间治理 / API Key：owner / admin
 * - 设置入口：全体成员可见；邀请与可赋角色对齐主站
 */

/** 主站角色枚举值（与 WorkspaceMemberRoleEnum 对齐） */
export const WORKSPACE_ROLE = {
    OWNER: 1,
    ADMIN: 2,
    EDITOR: 3,
    VIEWER: 4,
}

/** 角色优先级：数值越小权限越高（用于邀请目标角色比较） */
const WORKSPACE_ROLE_RANK = {
    owner: 1,
    admin: 2,
    editor: 3,
    viewer: 4,
}

/** 整型角色 → 内部字符串 */
const WORKSPACE_ROLE_CODE_TO_KEY = {
    [WORKSPACE_ROLE.OWNER]: 'owner',
    [WORKSPACE_ROLE.ADMIN]: 'admin',
    [WORKSPACE_ROLE.EDITOR]: 'editor',
    [WORKSPACE_ROLE.VIEWER]: 'viewer',
}

/** 成员角色中文映射（内部字符串 key） */
export const MEMBER_ROLE_LABELS = {
    owner: '拥有者',
    admin: '管理员',
    editor: '编辑者',
    viewer: '查看者',
}

/** 套餐类型中文映射（商业化能力恢复时使用） */
export const PLAN_TYPE_LABELS = {
    free: '免费版',
    pro: '专业版',
    enterprise: '企业版',
}

/**
 * 将主站整型或历史字符串角色归一为智忆内部字符串
 * @param {number|string|null|undefined} memberRole
 * @returns {'owner'|'admin'|'editor'|'viewer'|null}
 */
export function normalizeMemberRole(memberRole) {
    if (memberRole === null || memberRole === undefined || memberRole === '') {
        return null
    }
    if (typeof memberRole === 'number') {
        return WORKSPACE_ROLE_CODE_TO_KEY[memberRole] || null
    }
    const trimmedRole = String(memberRole).trim()
    if (MEMBER_ROLE_LABELS[trimmedRole]) {
        return trimmedRole
    }
    const roleCode = Number(trimmedRole)
    if (!Number.isNaN(roleCode) && WORKSPACE_ROLE_CODE_TO_KEY[roleCode]) {
        return WORKSPACE_ROLE_CODE_TO_KEY[roleCode]
    }
    return null
}

/**
 * 是否为工作空间成员（已解析出角色即可访问设置入口等）
 */
export function canAccessWorkspaceSettings(memberRole) {
    return !!normalizeMemberRole(memberRole)
}

/**
 * 判断当前角色是否可管理工作空间治理项（改名、改角色、移除成员等）
 */
export function canManageWorkspace(memberRole) {
    const normalizedRole = normalizeMemberRole(memberRole)
    return normalizedRole === 'owner' || normalizedRole === 'admin'
}

/**
 * 判断是否可签发 / 吊销 Agent API Key（对齐主站：仅 owner / admin）
 */
export function canManageApiKey(memberRole) {
    return canManageWorkspace(memberRole)
}

/**
 * 判断是否可邀请成员（对齐主站：owner / admin / editor）
 */
export function canInviteMember(memberRole) {
    const normalizedRole = normalizeMemberRole(memberRole)
    return normalizedRole === 'owner'
        || normalizedRole === 'admin'
        || normalizedRole === 'editor'
}

/**
 * 操作者是否可将目标角色作为邀请角色（对齐主站 canInviteTargetRole）
 * owner/admin → admin/editor/viewer；editor → editor/viewer
 */
export function canInviteTargetRole(operatorRole, targetRole) {
    const operatorNormalized = normalizeMemberRole(operatorRole)
    const targetNormalized = normalizeMemberRole(targetRole)
    if (!operatorNormalized || !targetNormalized || targetNormalized === 'owner') {
        return false
    }
    if (operatorNormalized === 'owner' || operatorNormalized === 'admin') {
        return targetNormalized === 'admin'
            || targetNormalized === 'editor'
            || targetNormalized === 'viewer'
    }
    if (operatorNormalized === 'editor') {
        return targetNormalized === 'editor' || targetNormalized === 'viewer'
    }
    return false
}

/**
 * 判断是否可将知识设为公开（Internal → Public，仅管理者）
 */
export function canChangeVisibility(memberRole) {
    return canManageWorkspace(memberRole)
}

/**
 * 是否为空间拥有者（删除协作空间等）
 */
export function isWorkspaceOwner(memberRole) {
    return normalizeMemberRole(memberRole) === 'owner'
}

/**
 * 判断当前角色是否可编辑、发布或下架任意知识（内容协作）
 */
export function canEditKnowledge(memberRole) {
    const normalizedRole = normalizeMemberRole(memberRole)
    return normalizedRole === 'owner'
        || normalizedRole === 'admin'
        || normalizedRole === 'editor'
}

/**
 * 判断当前用户是否可修改经验：编辑角色可改任意经验，创建者仅可修改本人创建的经验
 */
export function canModifyKnowledge(memberRole, creatorId, currentUserId) {
    if (canEditKnowledge(memberRole)) {
        return true
    }
    if (creatorId == null || currentUserId == null) {
        return false
    }
    return String(creatorId) === String(currentUserId)
}

/**
 * 判断当前用户是否可删除经验：
 * 管理者（owner/admin）可删任意；编辑者与其他角色仅可删除本人创建的经验
 */
export function canDeleteKnowledge(memberRole, creatorId, currentUserId) {
    if (canManageWorkspace(memberRole)) {
        return true
    }
    if (creatorId == null || currentUserId == null) {
        return false
    }
    return String(creatorId) === String(currentUserId)
}

/**
 * 获取成员角色展示文案
 */
export function getMemberRoleLabel(memberRole) {
    const normalizedRole = normalizeMemberRole(memberRole)
    return MEMBER_ROLE_LABELS[normalizedRole] || normalizedRole || '未知'
}

/**
 * 获取角色排序值（越小权限越高）；无法识别时返回较大值
 */
export function getMemberRoleRank(memberRole) {
    const normalizedRole = normalizeMemberRole(memberRole)
    return WORKSPACE_ROLE_RANK[normalizedRole] || 99
}

/**
 * 获取套餐类型展示文案
 */
export function getPlanTypeLabel(planType) {
    return PLAN_TYPE_LABELS[planType] || planType || '未知'
}
