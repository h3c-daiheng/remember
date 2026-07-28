/** 与 gonline 共用的 OAuth access_token Cookie 键名 */
export { TOKEN_COOKIE_KEY } from '~/utils/tokenCookie'

/**
 * 将 gonline /user/self 响应映射为智忆前端使用的用户摘要
 */
export function mapGonlineUserProfile(userData) {
    if (!userData || !userData.id) {
        return null
    }
    return {
        userId: userData.id,
        username: userData.username || '',
        nickname: userData.nickname || userData.username || '',
        avatar: userData.avatarUrl || '',
        mobile: userData.mobile || '',
        raw: userData,
    }
}

/**
 * 将智忆 /auth/me 响应映射为前端登录态（userId 为本地 sys_user.id，可与 knowledge.creatorId 比对）
 */
export function mapZhiyiLoginUser(loginUser) {
    if (!loginUser || loginUser.userId == null) {
        return null
    }
    return {
        userId: loginUser.userId,
        username: loginUser.username || '',
        nickname: loginUser.nickname || loginUser.username || '',
        avatar: loginUser.avatar || '',
        workspaceId: loginUser.workspaceId || null,
        workspaceCode: loginUser.workspaceCode || 'default',
        workspaceName: loginUser.workspaceName || '个人空间',
        memberRole: loginUser.memberRole || null,
    }
}
