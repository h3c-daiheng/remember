import { createIpResolver } from '@gonline/tracker-sdk'
import ZhiyiTracker from '~/utils/tracker'
import { fetchGonlineClientIp } from '~/utils/gonlineRequest'

/**
 * 客户端埋点插件：初始化 SDK、拉取 IP、同步登录用户
 */
export default defineNuxtPlugin(async (nuxtApp) => {
    const tracker = new ZhiyiTracker()
    // provide 后 Nuxt 会挂只读 $tracker，勿再写 globalProperties.$tracker
    nuxtApp.provide('tracker', tracker)

    // 拉取客户端 IP（失败静默，不影响页面）
    const trackerIpResolver = createIpResolver({
        fetchIp: () => fetchGonlineClientIp(),
    })
    const clientIp = await trackerIpResolver.fetchIp()
    tracker.init({ ip: clientIp || '' })

    // 登录态已由 auth.client 恢复时，把用户写入埋点上下文
    try {
        const { currentUser, authReady, fetchCurrentUser } = useAuth()
        if (!authReady.value) {
            await fetchCurrentUser()
        }
        if (currentUser.value?.userId) {
            tracker.addUserInfo({
                userId: currentUser.value.userId,
                userName: currentUser.value.username || currentUser.value.nickname || '',
            })
        }

        // 登录态变化时同步埋点用户信息
        watch(
            currentUser,
            (nextUser) => {
                if (nextUser?.userId) {
                    tracker.addUserInfo({
                        userId: nextUser.userId,
                        userName: nextUser.username || nextUser.nickname || '',
                    })
                } else {
                    tracker.clearUserInfo()
                }
            },
            { deep: false }
        )
    } catch (_) {
        // 未登录或 auth 未就绪时忽略
    }
})
