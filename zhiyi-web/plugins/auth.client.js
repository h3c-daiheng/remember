/**
 * 应用启动时恢复登录态（对齐 chat2x-web：await 完成后再 mount，页面 onMounted 可直接拉数）
 */
import { bootstrapTokenFromUrl } from '~/utils/tokenBootstrap'
import { getStoredToken } from '~/utils/token'

export default defineNuxtPlugin(async () => {
    if (import.meta.client) {
        bootstrapTokenFromUrl()
    }
    const { fetchCurrentUser } = useAuth()
    await fetchCurrentUser()

    // 同步智忆工作空间上下文（memberRole），否则各中心「新建」等编辑入口会被 v-if 隐藏
    if (import.meta.client && getStoredToken()) {
        const { syncWorkspaceFromAuth } = useWorkspace()
        try {
            await syncWorkspaceFromAuth()
        } catch (error) {
            // 不阻断首屏；各页 useCanEditKnowledge 会二次尝试
        }
    }
})
