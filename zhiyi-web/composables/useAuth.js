/**
 * 全局登录态：优先走智忆 /auth/me（userId 与 knowledge.creatorId 同源），回退 gonline /user/self
 */
import { mapGonlineUserProfile, mapZhiyiLoginUser } from '~/config/auth'
import { fetchAuthMeRequest } from '~/services/auth.service'
import { clearStoredToken, getStoredToken, redirectAfterLogout, redirectToGonlineLogin } from '~/utils/token'
import { fetchGonlineUserSelf } from '~/utils/gonlineRequest'

export function useAuth() {
    const currentUser = useState('authUser', () => null)
    const authReady = useState('authReady', () => false)

    /**
     * 恢复当前登录用户：走智忆 /auth/me（本地 JWT）
     */
    async function fetchCurrentUser() {
        const token = getStoredToken()
        if (!token) {
            currentUser.value = null
            authReady.value = true
            return null
        }

        try {
            const loginUser = await fetchAuthMeRequest()
            const mappedUser = mapZhiyiLoginUser(loginUser)
            if (mappedUser) {
                currentUser.value = mappedUser
                return mappedUser
            }
            clearStoredToken()
            currentUser.value = null
            return null
        } catch (error) {
            clearStoredToken()
            currentUser.value = null
            return null
        } finally {
            authReady.value = true
        }
    }

    /**
     * 智忆 /auth/me 未返回头像时，从主站 /user/self 补全 avatarUrl，保证顶栏能展示真实头像。
     */
    async function enrichUserProfileFromGonline(mappedUser) {
        if (!mappedUser || mappedUser.avatar) {
            return mappedUser
        }
        try {
            const userData = await fetchGonlineUserSelf()
            const avatarUrl = userData?.avatarUrl || ''
            if (!avatarUrl) {
                return mappedUser
            }
            return {
                ...mappedUser,
                avatar: avatarUrl,
            }
        } catch (error) {
            return mappedUser
        }
    }

    /**
     * 跳转巨人肩膀登录页（智忆不再提供独立账号登录/注册）
     */
    function redirectToLogin(redirectPath) {
        redirectToGonlineLogin(redirectPath)
    }

    /**
     * 退出：清除共享 Cookie 并回到智忆首页
     */
    async function logout() {
        clearStoredToken()
        currentUser.value = null
        redirectAfterLogout()
    }

    return {
        currentUser,
        authReady,
        fetchCurrentUser,
        redirectToLogin,
        logout,
    }
}
