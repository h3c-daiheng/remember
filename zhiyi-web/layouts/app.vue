<template>
    <!-- 锁定视口高度，避免整页滚动导致侧栏随内容位移 -->
    <div class="h-screen flex flex-col overflow-hidden bg-gray-50 text-gray-800">
        <LayoutAppHeader
            :current-user="currentUser"
            show-nav-links
            show-source-license-entry
            logo-link-to="/"
            :badge-values="badgeValues"
            @login="goLogin"
            @logout="handleLogout"
        />

        <div class="flex flex-1 min-h-0 overflow-hidden">
            <LayoutAppSidebar :badge-values="badgeValues" />

            <main class="flex-1 min-w-0 min-h-0 overflow-auto">
                <slot />
            </main>
        </div>
    </div>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { SITE_TRACK_EVENTS } from '~/config/tracker'

const router = useRouter()
const route = useRoute()
const { currentUser, logout, redirectToLogin } = useAuth()
const { badgeValues, refreshPendingDraftCount } = useAppNavigation()
const { reportEvent } = useTracker()

/** 未登录跳转登录页（对齐 chat2x-web，watch 非阻塞） */
useRequireAuth()

/** 首屏刷新角标；capture 页会自行 sync，避免重复请求 */
onMounted(() => {
    if (!route.path.startsWith('/capture')) {
        refreshPendingDraftCount()
    }
})

/** 路由切换后刷新角标 */
watch(
    () => router.currentRoute.value.fullPath,
    (path, previousPath) => {
        if (previousPath !== undefined && !path.startsWith('/capture')) {
            refreshPendingDraftCount()
        }
    },
)

/**
 * 跳转巨人肩膀统一登录页
 */
function goLogin() {
    reportEvent(SITE_TRACK_EVENTS.CLICK_LOGIN, {
        from: 'app-header',
        path: route.path || '',
    })
    redirectToLogin()
}

/**
 * 退出登录
 */
async function handleLogout() {
    reportEvent(SITE_TRACK_EVENTS.CLICK_LOGOUT, {
        from: 'app-header',
        path: route.path || '',
    })
    await logout()
    ElMessage.success('已退出登录')
}
</script>
