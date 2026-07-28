<template>
    <div class="min-h-screen flex flex-col bg-gray-50 text-gray-800">
        <LayoutAppHeader
            :current-user="currentUser"
            :show-workspace="false"
            :anchor-nav-items="homeAnchorNavItems"
            logo-link-to="/"
            show-dashboard-entry
            show-source-license-entry
            @login="goLogin"
            @enter-dashboard="goDashboard"
            @logout="handleLogout"
        />

        <main class="flex-1">
            <slot />
        </main>

        <!-- 生态统一页脚：与官网结构对齐，底色与页面内容区区分 -->
        <LayoutSiteFooter />
    </div>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { HOME_ANCHOR_NAV } from '~/constants/home'
import { SITE_TRACK_EVENTS } from '~/config/tracker'

const route = useRoute()
const { currentUser, logout, redirectToLogin } = useAuth()
const { reportEvent } = useTracker()

/** 仅首页展示顶栏锚点导航，登录页等不展示 */
const homeAnchorNavItems = computed(() => (
    route.path === '/' ? HOME_ANCHOR_NAV : []
))

/**
 * 跳转巨人肩膀统一登录页
 */
function goLogin() {
    reportEvent(SITE_TRACK_EVENTS.CLICK_LOGIN, {
        from: 'header',
        path: route.path || '',
    })
    redirectToLogin('/dashboard')
}

/**
 * 已登录用户从营销页进入工作台
 */
function goDashboard() {
    reportEvent(SITE_TRACK_EVENTS.CLICK_ENTER_DASHBOARD, {
        from: 'header',
        path: route.path || '',
    })
    navigateTo('/dashboard')
}

/**
 * 退出登录并回到首页
 */
async function handleLogout() {
    reportEvent(SITE_TRACK_EVENTS.CLICK_LOGOUT, {
        from: 'header',
        path: route.path || '',
    })
    await logout()
    ElMessage.success('已退出登录')
}
</script>
