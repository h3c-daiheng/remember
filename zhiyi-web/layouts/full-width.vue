<template>
    <div class="full-width-layout min-h-screen flex flex-col text-gray-800">
        <header class="border-b border-gray-200/80 bg-white/90 backdrop-blur sticky top-0 z-20 shrink-0 shadow-sm">
            <div class="max-w-none mx-auto px-4 h-14 flex items-center justify-between">
                <BrandLogo link-to="/" show-text />
                <nav class="flex items-center gap-3 text-sm text-gray-600">
                    <template v-if="clientMounted && currentUser">
                        <!-- 与 AppHeader / imagehub 顶栏一致：28px 头像 + 昵称 -->
                        <LayoutUserAvatar :user="currentUser" />
                        <span class="hidden sm:inline text-gray-700">
                            你好，{{ userDisplayName }}
                        </span>
                        <el-button round @click="handleLogout">退出登录</el-button>
                    </template>
                    <template v-else>
                        <el-button type="primary" round @click="goLogin">登录</el-button>
                    </template>
                </nav>
            </div>
        </header>

        <main class="flex-1 min-h-0 overflow-hidden">
            <slot />
        </main>
    </div>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { SITE_TRACK_EVENTS } from '~/config/tracker'

const { currentUser, logout, redirectToLogin } = useAuth()
const clientMounted = useClientMounted()
const { reportEvent } = useTracker()

/** 顶栏展示的用户名，优先昵称，无昵称时回退登录账号 */
const userDisplayName = computed(() => {
    return currentUser.value?.nickname || currentUser.value?.username || ''
})

/**
 * 跳转巨人肩膀统一登录页
 */
function goLogin() {
    reportEvent(SITE_TRACK_EVENTS.CLICK_LOGIN, {
        from: 'full-width-header',
    })
    redirectToLogin()
}

/**
 * 退出登录并回到首页
 */
async function handleLogout() {
    reportEvent(SITE_TRACK_EVENTS.CLICK_LOGOUT, {
        from: 'full-width-header',
    })
    await logout()
    ElMessage.success('已退出登录')
}
</script>
