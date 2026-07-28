<template>
    <!-- 首页固定为营销落地页，与工作台 /dashboard 分离 -->
    <HomeMarketingPage
        :is-logged-in="isLoggedIn"
        @login="handlePrimaryAction"
        @enter="goDashboard"
    />
</template>

<script setup>
import { HOME_TRACK_EVENTS } from '~/config/tracker'

definePageMeta({
    layout: 'default',
})

useHead({
    title: '首页',
})

/** 页面浏览埋点 */
usePageTracker()

const { currentUser, redirectToLogin } = useAuth()
const clientMounted = useClientMounted()
const { reportEvent } = useTracker()

/** 是否已登录（须等水合完成再读 Cookie 鉴权结果，避免与 SSR 快照不一致） */
const isLoggedIn = computed(() => clientMounted.value && !!currentUser.value)

/** 已登录用户进入工作台 */
function goDashboard() {
    reportEvent(HOME_TRACK_EVENTS.CLICK_ENTER_DASHBOARD, {
        from: 'home',
    })
    navigateTo('/dashboard')
}

/** 主按钮：未登录跳转登录，已登录进入工作台 */
function handlePrimaryAction() {
    reportEvent(HOME_TRACK_EVENTS.CLICK_PRIMARY_CTA, {
        loggedIn: !!currentUser.value,
        from: 'home',
    })
    if (currentUser.value) {
        navigateTo('/dashboard')
        return
    }
    reportEvent(HOME_TRACK_EVENTS.CLICK_LOGIN, {
        from: 'home-cta',
    })
    redirectToLogin('/dashboard')
}
</script>
