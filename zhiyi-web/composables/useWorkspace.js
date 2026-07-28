/**
 * Workspace 上下文：列表加载、切换、与登录态联动（元数据源为主站）
 */
import { normalizeMemberRole } from '~/constants/workspace'
import { fetchAuthMeRequest } from '~/services/auth.service'
import { saveStoredToken } from '~/utils/token'
import {
    createWorkspaceRequest,
    fetchWorkspaceListRequest,
    switchWorkspaceRequest,
} from '~/services/workspace.service'

export function useWorkspace() {
    const currentWorkspace = useState('currentWorkspace', () => ({
        workspaceId: null,
        workspaceCode: 'default',
        workspaceName: '个人空间',
        workspaceType: null,
        organizationId: null,
        organizationName: '',
        memberRole: null,
    }))

    /** 用户可访问的工作空间列表 */
    const workspaceList = useState('workspaceList', () => [])

    /** 工作空间上下文版本号，切换后递增，供各页面监听并静默刷新 */
    const workspaceRevision = useState('workspaceRevision', () => 0)

    /**
     * 从登录用户 Claims 初始化 Workspace 上下文（角色归一为内部字符串）
     */
    function initWorkspaceFromUser(user) {
        if (!user) {
            return
        }
        currentWorkspace.value = {
            workspaceId: user.workspaceId || null,
            workspaceCode: user.workspaceCode || 'default',
            workspaceName: user.workspaceName || '个人空间',
            workspaceType: user.workspaceType ?? null,
            organizationId: null,
            organizationName: '',
            memberRole: normalizeMemberRole(user.memberRole),
        }
    }

    /**
     * 合并切换后的空间上下文到 authUser（保留本地 userId 等字段）
     */
    function mergeAuthUserWorkspace(workspaceUserFragment) {
        if (!workspaceUserFragment) {
            return
        }
        const authUser = useState('authUser')
        authUser.value = {
            ...(authUser.value || {}),
            ...workspaceUserFragment,
            organizationId: null,
            organizationName: '',
            memberRole: normalizeMemberRole(workspaceUserFragment.memberRole),
        }
        initWorkspaceFromUser(authUser.value)
    }

    /**
     * 从智忆 /auth/me 同步工作空间与成员角色（服务端已从主站 me-context 解析并归一）
     */
    async function syncWorkspaceFromAuth() {
        try {
            const loginUser = await fetchAuthMeRequest()
            if (loginUser) {
                const authUser = useState('authUser')
                const mappedUser = {
                    ...(authUser.value || {}),
                    userId: loginUser.userId,
                    username: loginUser.username || authUser.value?.username || '',
                    nickname: loginUser.nickname || authUser.value?.nickname || '',
                    avatar: loginUser.avatar || authUser.value?.avatar || '',
                    workspaceId: loginUser.workspaceId,
                    workspaceCode: loginUser.workspaceCode,
                    workspaceName: loginUser.workspaceName,
                    memberRole: loginUser.memberRole,
                }
                authUser.value = mappedUser
                initWorkspaceFromUser(loginUser)
                return loginUser
            }
        } catch (error) {
            // /auth/me 失败时回退拉工作空间列表
        }
        await loadWorkspaceList()
        return null
    }

    /**
     * 加载工作空间列表
     */
    async function loadWorkspaceList() {
        const list = await fetchWorkspaceListRequest()
        workspaceList.value = list || []
        const currentItem = workspaceList.value.find((item) => item.current)
        if (currentItem) {
            currentWorkspace.value = {
                ...currentWorkspace.value,
                workspaceId: currentItem.workspaceId,
                workspaceCode: currentItem.workspaceCode,
                workspaceName: currentItem.workspaceName,
                workspaceType: currentItem.workspaceType,
                organizationId: null,
                organizationName: '',
                memberRole: normalizeMemberRole(currentItem.memberRole),
            }
        }
        return workspaceList.value
    }

    /** 通知各业务页：工作空间上下文已变更，需刷新 workspace 域数据 */
    function notifyWorkspaceContextChanged() {
        workspaceRevision.value += 1
    }

    /**
     * 切换工作空间：调用本地接口更新偏好并重签 JWT，同步登录态
     */
    async function switchWorkspace(workspaceId) {
        const switchResult = await switchWorkspaceRequest(workspaceId)
        if (switchResult.token) {
            // 后端已按新空间重签 JWT，替换本地存储，后续 Memory API 即用新空间上下文
            saveStoredToken(switchResult.token)
        }
        mergeAuthUserWorkspace(switchResult.user)
        await loadWorkspaceList()
        notifyWorkspaceContextChanged()
        return switchResult.user
    }

    /**
     * 新建工作空间：本地创建并切到新空间，同步登录态
     */
    async function createWorkspace(workspaceName) {
        const result = await createWorkspaceRequest(workspaceName)
        if (result.token) {
            saveStoredToken(result.token)
        }
        mergeAuthUserWorkspace(result.user)
        await loadWorkspaceList()
        notifyWorkspaceContextChanged()
        return result.user
    }

    /**
     * 从主站设置页返回后静默同步：对比关键字段变化再通知业务页刷新
     */
    async function refreshWorkspaceAfterReturnFromMainSite() {
        const previousWorkspaceId = currentWorkspace.value?.workspaceId
        const previousMemberRole = currentWorkspace.value?.memberRole
        const previousWorkspaceName = currentWorkspace.value?.workspaceName

        await syncWorkspaceFromAuth()
        try {
            await loadWorkspaceList()
        } catch (error) {
            // 列表失败不阻断已同步的 me 上下文
        }

        const workspaceChanged = previousWorkspaceId !== currentWorkspace.value?.workspaceId
            || previousMemberRole !== currentWorkspace.value?.memberRole
            || previousWorkspaceName !== currentWorkspace.value?.workspaceName
        if (workspaceChanged) {
            notifyWorkspaceContextChanged()
        }
    }

    return {
        currentWorkspace,
        workspaceList,
        workspaceRevision,
        initWorkspaceFromUser,
        syncWorkspaceFromAuth,
        loadWorkspaceList,
        switchWorkspace,
        createWorkspace,
        notifyWorkspaceContextChanged,
        refreshWorkspaceAfterReturnFromMainSite,
    }
}
