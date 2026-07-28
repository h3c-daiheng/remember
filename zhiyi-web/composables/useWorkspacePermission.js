/**
 * 工作空间角色权限：同步 memberRole，并暴露细表对应的能力开关
 */
import {
    canAccessWorkspaceSettings,
    canEditKnowledge,
    canInviteMember,
    canManageApiKey,
    canManageWorkspace,
    getMemberRoleLabel,
    isWorkspaceOwner,
} from '~/constants/workspace'
import { getStoredToken } from '~/utils/token'

/**
 * 挂载时若角色未就绪则补拉 /auth/me，供各业务页统一复用
 */
export function useWorkspacePermission() {
    const { currentWorkspace, syncWorkspaceFromAuth } = useWorkspace()
    const syncing = ref(false)

    const memberRole = computed(() => currentWorkspace.value?.memberRole || null)
    const memberRoleLabel = computed(() => getMemberRoleLabel(memberRole.value))

    /** 内容协作：新建、Review、导入、编辑发布任意知识 */
    const canEdit = computed(() => canEditKnowledge(memberRole.value))
    /** 空间治理：改名、改角色、移除成员 */
    const canManage = computed(() => canManageWorkspace(memberRole.value))
    /** Agent API Key 签发 / 吊销（对齐主站） */
    const canManageKey = computed(() => canManageApiKey(memberRole.value))
    /** 工作空间设置入口（全体成员） */
    const canAccessSettings = computed(() => canAccessWorkspaceSettings(memberRole.value))
    /** 邀请成员（对齐主站） */
    const canInvite = computed(() => canInviteMember(memberRole.value))
    /** 删除工作空间（仅 owner） */
    const canDeleteWorkspace = computed(() => isWorkspaceOwner(memberRole.value))

    /** 页面挂载时若角色未就绪，补拉智忆登录上下文 */
    onMounted(async () => {
        if (!import.meta.client || !getStoredToken()) {
            return
        }
        if (memberRole.value) {
            return
        }
        syncing.value = true
        try {
            await syncWorkspaceFromAuth()
        } catch (error) {
            // 同步失败时保持权限为 false，由后端鉴权兜底
        } finally {
            syncing.value = false
        }
    })

    return {
        memberRole,
        memberRoleLabel,
        canEdit,
        canManage,
        canManageKey,
        canAccessSettings,
        canInvite,
        canDeleteWorkspace,
        syncing,
    }
}
