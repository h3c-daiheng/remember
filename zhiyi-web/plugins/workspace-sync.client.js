/**
 * 工作空间切换后同步全站导航状态；从主站设置页回到本页时静默刷新上下文
 */
import { getStoredToken } from '~/utils/token'

/** 离开页面超过此时长再同步，避免系统通知等短闪触发频繁请求 */
const RETURN_SYNC_MIN_HIDDEN_MS = 1500

export default defineNuxtPlugin(() => {
    const {
        workspaceRevision,
        refreshWorkspaceAfterReturnFromMainSite,
    } = useWorkspace()
    const { refreshPendingDraftCount } = useAppNavigation()

    watch(workspaceRevision, (revision, previousRevision) => {
        if (previousRevision === undefined) {
            return
        }
        refreshPendingDraftCount()
    })

    let lastHiddenAt = 0
    let syncingAfterReturn = false

    /**
     * 从其他标签页（如主站工作空间设置）回到智忆时，同步空间名/角色/当前空间
     */
    async function handleVisibilityChange() {
        if (typeof document === 'undefined') {
            return
        }
        if (document.visibilityState === 'hidden') {
            lastHiddenAt = Date.now()
            return
        }
        if (!getStoredToken() || syncingAfterReturn) {
            return
        }
        if (!lastHiddenAt || Date.now() - lastHiddenAt < RETURN_SYNC_MIN_HIDDEN_MS) {
            return
        }
        syncingAfterReturn = true
        try {
            await refreshWorkspaceAfterReturnFromMainSite()
        } catch (error) {
            // 回跳同步失败不打断用户操作
        } finally {
            syncingAfterReturn = false
        }
    }

    document.addEventListener('visibilitychange', handleVisibilityChange)
})
