/**
 * 登录鉴权：watch authReady 后跳转，不阻塞路由与页面 mount
 * @param {string} [redirectPath] 未登录时回跳路径，默认当前路由 fullPath
 */
export function useRequireAuth(redirectPath) {
    const route = useRoute()
    const { currentUser, authReady, redirectToLogin } = useAuth()

    watch([authReady, currentUser], ([ready, user]) => {
        if (ready && !user && import.meta.client) {
            redirectToLogin(redirectPath || route.fullPath)
        }
    }, { immediate: true })
}
