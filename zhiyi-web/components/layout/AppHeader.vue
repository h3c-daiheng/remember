<template>
    <header class="border-b border-gray-200 bg-white/80 backdrop-blur sticky top-0 z-20 shrink-0">
        <div :class="[
            'h-14 px-4 md:px-6 items-center gap-4',
            hasAnchorNav ? 'grid grid-cols-3' : 'flex justify-between',
        ]">
            <div class="flex items-center gap-4 min-w-0 justify-self-start">
                <BrandLogo :link-to="logoLinkTo"
                    show-text />
                <WorkspaceSwitcher v-if="showWorkspace" />
            </div>

            <!-- 首页营销页：顶栏锚点导航（三列网格中间列居中；按钮拉满栏高扩大点击区，不改变 h-14） -->
            <nav v-if="hasAnchorNav"
                class="hidden lg:flex self-stretch items-stretch justify-center gap-1 xl:gap-2 text-sm text-gray-600 justify-self-center min-w-0">
                <button v-for="item in anchorNavItems"
                    :key="item.targetId"
                    type="button"
                    class="px-2 xl:px-3 h-full flex items-center hover:text-primary transition-colors whitespace-nowrap"
                    @click="handleAnchorClick(item.targetId)">
                    {{ item.label }}
                </button>
            </nav>

            <!-- 小屏：顶栏横向 Tab（仅核心菜单，避免占位项过多） -->
            <nav v-if="showNavLinks"
                class="flex md:hidden items-center gap-2 text-sm overflow-x-auto">
                <NuxtLink v-for="item in mobileHeaderItems"
                    :key="item.path"
                    :to="item.path"
                    class="px-2 py-1 rounded hover:text-primary transition-colors"
                    active-class="text-primary font-medium">
                    {{ item.label }}
                    <el-badge v-if="item.badgeKey && badgeValues[item.badgeKey] > 0"
                        :value="badgeValues[item.badgeKey]"
                        class="ml-1" />
                </NuxtLink>
            </nav>

            <!-- 右侧操作区：按钮与用户信息分组，组间距大于组内间距，避免挤在一起 -->
            <div class="flex items-center gap-4 md:gap-5 shrink-0 justify-self-end">
                <!-- 营销页顶栏：源码授权入口（对齐画境） -->
                <NuxtLink v-if="showSourceLicenseEntry"
                    to="/source"
                    class="hidden sm:inline text-sm text-gray-600 hover:text-primary transition-colors whitespace-nowrap"
                    @click="handleSourceLicenseClick">
                    源码授权
                </NuxtLink>
                <template v-if="showAuthUser">
                    <el-button v-if="showDashboardEntry"
                        type="primary"
                        round
                        @click="emit('enter-dashboard')">
                        进入工作台
                    </el-button>
                    <!-- 用户区：头像 + 昵称，样式对齐 imagehub 顶栏 -->
                    <div class="flex items-center gap-2 min-w-0">
                        <LayoutUserAvatar :user="props.currentUser" />
                        <span class="hidden sm:inline text-sm text-gray-700 truncate max-w-28">
                            {{ userDisplayName }}
                        </span>
                    </div>
                    <el-button round
                        @click="emit('logout')">退出</el-button>
                </template>
                <template v-else>
                    <el-button type="primary"
                        round
                        @click="emit('login')">登录</el-button>
                </template>
            </div>
        </div>
    </header>
</template>

<script setup>
import { HOME_TRACK_EVENTS, SITE_TRACK_EVENTS } from '~/config/tracker'
import { APP_SIDEBAR_SECTIONS } from '~/constants/navigation'

const props = defineProps({
    currentUser: {
        type: Object,
        default: null,
    },
    /** Logo 点击跳转路径，统一回到营销首页 */
    logoLinkTo: {
        type: String,
        default: '/',
    },
    /** 是否展示 Workspace 名称（Beta 多租户） */
    showWorkspace: {
        type: Boolean,
        default: true,
    },
    /** 是否展示导航链接（App Shell 内小屏展示） */
    showNavLinks: {
        type: Boolean,
        default: false,
    },
    badgeValues: {
        type: Object,
        default: () => ({}),
    },
    /** 营销页顶栏：已登录时展示「进入工作台」 */
    showDashboardEntry: {
        type: Boolean,
        default: false,
    },
    /** 营销页顶栏：展示源码授权入口 */
    showSourceLicenseEntry: {
        type: Boolean,
        default: false,
    },
    /** 首页锚点导航项，传入后在顶栏居中展示 */
    anchorNavItems: {
        type: Array,
        default: () => [],
    },
})

const emit = defineEmits(['login', 'logout', 'enter-dashboard'])

const clientMounted = useClientMounted()
const route = useRoute()
const { scrollToSection } = useHomeAnchorScroll()
const { reportEvent } = useTracker()

/** 点击锚点导航，平滑滚动到对应区块 */
function handleAnchorClick(targetId) {
    reportEvent(HOME_TRACK_EVENTS.CLICK_HOME_NAV, {
        targetId: targetId || '',
    })
    scrollToSection(targetId)
}

/** 顶栏源码授权点击埋点 */
function handleSourceLicenseClick() {
    reportEvent(SITE_TRACK_EVENTS.CLICK_SOURCE_LICENSE, {
        action: 'open-page',
        from: 'header',
        path: route.path || '',
    })
}

/** 是否展示首页锚点导航 */
const hasAnchorNav = computed(() => props.anchorNavItems.length > 0)

/** 营销页 SSR 须等 mount；App 业务页 ssr:false 且 auth 插件已 await，mount 后即有用户 */
const showAuthUser = computed(() => {
    if (props.showNavLinks) {
        return Boolean(props.currentUser)
    }
    return clientMounted.value && Boolean(props.currentUser)
})

/** 顶栏展示的用户名，优先昵称，无昵称时回退登录账号 */
const userDisplayName = computed(() => {
    return props.currentUser?.nickname || props.currentUser?.username || ''
})

/** 小屏顶栏仅展示核心分组，完整菜单见侧栏 */
const mobileHeaderItems = APP_SIDEBAR_SECTIONS[0].items
</script>
