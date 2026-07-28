/**
 * 工作空间相关 API：本地通道 /api/workspace/*（与本地账号登录同源，code=0）
 * 成员/邀请/改名/创建/API Key 管理暂留主站；stats 仍走智忆本地 /api
 */
import { normalizeMemberRole } from '~/constants/workspace'
import { apiRequest } from '~/services/http'

/**
 * 将本地列表项归一为智忆前端字段（id -> workspaceId；角色归一为字符串）
 * @param {object} item WorkspaceListItemVO
 */
function normalizeWorkspaceListItem(item) {
    if (!item) {
        return null
    }
    return {
        workspaceId: item.id || item.workspaceId || null,
        workspaceCode: item.workspaceCode || '',
        workspaceName: item.workspaceName || '',
        workspaceType: item.workspaceType ?? null,
        organizationId: null,
        organizationName: '',
        memberRole: normalizeMemberRole(item.memberRole),
        memberCount: item.memberCount ?? null,
        current: Boolean(item.current),
        createTime: item.createTime || null,
    }
}

/**
 * 将切换后上下文转为智忆 LoginUser 形态的片段（供切换后合并 authUser）
 * @param {object} context WorkspaceListItemVO
 */
export function normalizeWorkspaceContextAsUser(context) {
    if (!context) {
        return null
    }
    return {
        workspaceId: context.id || context.workspaceId || null,
        workspaceCode: context.workspaceCode || '',
        workspaceName: context.workspaceName || '',
        workspaceType: context.workspaceType ?? null,
        organizationId: null,
        organizationName: '',
        memberRole: normalizeMemberRole(context.memberRole),
    }
}

/**
 * 获取当前用户可访问的工作空间列表
 */
export async function fetchWorkspaceListRequest() {
    const list = await apiRequest('/workspace/list')
    return (list || []).map(normalizeWorkspaceListItem).filter(Boolean)
}

/**
 * 切换工作空间；本地返回新 token（重签 JWT）与空间上下文，包装为 { token, user } 兼容 useWorkspace
 */
export async function switchWorkspaceRequest(workspaceId) {
    const data = await apiRequest('/workspace/switch', {
        method: 'POST',
        body: JSON.stringify({ workspaceId }),
    })
    return {
        token: data?.token || null,
        user: normalizeWorkspaceContextAsUser(data),
    }
}

/**
 * 新建工作空间；本地创建并切到新空间，返回新 token 与空间上下文
 * @param {string} workspaceName 显示名称
 * @param {string} [workspaceCode] 空间标识，留空则后端自动生成
 */
export async function createWorkspaceRequest(workspaceName, workspaceCode) {
    const payload = { workspaceName }
    if (workspaceCode) {
        payload.workspaceCode = workspaceCode
    }
    const data = await apiRequest('/workspace', {
        method: 'POST',
        body: JSON.stringify(payload),
    })
    return {
        token: data?.token || null,
        user: normalizeWorkspaceContextAsUser(data),
    }
}
