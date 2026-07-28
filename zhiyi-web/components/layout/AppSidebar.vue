<template>
    <!-- 侧栏占满顶栏下方剩余高度，菜单过长时在 nav 内独立滚动 -->
    <aside class="app-sidebar hidden md:flex md:flex-col md:h-full md:min-h-0 md:shrink-0">
        <nav class="app-sidebar__nav">
            <div
                v-for="(section, sectionIndex) in sidebarSections"
                :key="section.title"
                class="app-sidebar__section"
                :class="{ 'app-sidebar__section--bordered': sectionIndex > 0 }"
            >
                <h2 class="app-sidebar__section-title">{{ section.title }}</h2>
                <div class="app-sidebar__items">
                    <template v-for="item in section.items" :key="item.path">
                        <!-- 外链：主站工作空间等，新窗口打开，不进入智忆本地路由 -->
                        <button
                            v-if="item.external"
                            type="button"
                            class="app-sidebar__item app-sidebar__item--external"
                            @click="handleSidebarNavClick(item)"
                        >
                            <div class="app-sidebar__icon" :class="getIconClass(item)">
                                <el-icon :size="16">
                                    <component :is="iconMap[item.icon]" />
                                </el-icon>
                            </div>
                            <span class="app-sidebar__body">
                                <span class="app-sidebar__label-row">
                                    <span class="app-sidebar__label">{{ item.label }}</span>
                                </span>
                                <span class="app-sidebar__desc">{{ item.description }}</span>
                            </span>
                        </button>
                        <NuxtLink
                            v-else
                            :to="item.path"
                            class="app-sidebar__item"
                            :class="{
                                'app-sidebar__item--active': isActive(item.path),
                                'app-sidebar__item--placeholder': item.placeholder,
                            }"
                            @click="handleSidebarNavClick(item)"
                        >
                            <div
                                class="app-sidebar__icon"
                                :class="getIconClass(item)"
                            >
                                <el-icon :size="16">
                                    <component :is="iconMap[item.icon]" />
                                </el-icon>
                            </div>
                            <span class="app-sidebar__body">
                                <span class="app-sidebar__label-row">
                                    <span class="app-sidebar__label">{{ item.label }}</span>
                                    <el-tag
                                        v-if="item.placeholder"
                                        size="small"
                                        type="info"
                                        effect="plain"
                                        class="app-sidebar__phase-tag"
                                    >
                                        {{ item.phase }}
                                    </el-tag>
                                    <el-badge
                                        v-else-if="item.badgeKey && badgeValues[item.badgeKey] > 0"
                                        :value="badgeValues[item.badgeKey]"
                                        :max="99"
                                    />
                                </span>
                                <span class="app-sidebar__desc">{{ item.description }}</span>
                            </span>
                        </NuxtLink>
                    </template>
                </div>
            </div>
        </nav>

        <footer class="app-sidebar__footer">
            <span class="app-sidebar__footer-dot" />
            <span class="app-sidebar__footer-text">{{ PRODUCT_FLYWHEEL_TEXT }}</span>
            <span class="app-sidebar__footer-dot" />
        </footer>
    </aside>
</template>

<script setup>
import {
    Collection,
    Connection,
    Cpu,
    DataAnalysis,
    DocumentChecked,
    House,
    Operation,
    Memo,
    Notebook,
    Search,
    SetUp,
    Setting,
    Share,
    Upload,
} from '@element-plus/icons-vue'
import { SITE_TRACK_EVENTS } from '~/config/tracker'
import { APP_SIDEBAR_SECTIONS } from '~/constants/navigation'
import { PRODUCT_FLYWHEEL_TEXT } from '~/constants/terminology'
import { openGonlineWorkspaceSettings } from '~/utils/gonlineAuthLogin'

const route = useRoute()
const { reportEvent } = useTracker()

const props = defineProps({
    badgeValues: {
        type: Object,
        default: () => ({}),
    },
})

const sidebarSections = APP_SIDEBAR_SECTIONS

/**
 * 侧栏导航点击：埋点；外链项（主站工作空间）新窗口打开
 * @param {{ path?: string, label?: string, external?: boolean }} item
 */
function handleSidebarNavClick(item) {
    reportEvent(SITE_TRACK_EVENTS.CLICK_SIDEBAR_NAV, {
        path: item?.path || '',
        label: item?.label || '',
        fromPath: route.path || '',
    })
    if (!item?.external) {
        return
    }
    // 工作空间管理统一走主站页，新窗口打开以免打断智忆当前业务页
    openGonlineWorkspaceSettings()
}

/** 侧栏图标映射，需与 navigation.js 中 icon 字段保持一致 */
const iconMap = {
    House,
    Collection,
    DocumentChecked,
    Cpu,
    Notebook,
    Connection,
    Operation,
    Search,
    Memo,
    Share,
    Upload,
    DataAnalysis,
    SetUp,
    Setting,
}

/** 根据激活态与待审数量决定图标样式 */
function getIconClass(item) {
    if (isActive(item.path)) {
        return 'app-sidebar__icon--active'
    }
    if (item.badgeKey && props.badgeValues[item.badgeKey] > 0) {
        return 'app-sidebar__icon--attention'
    }
    return ''
}

/** 判断侧栏项是否为当前路由 */
function isActive(itemPath) {
    if (itemPath === '/dashboard') {
        return route.path === '/dashboard'
    }
    if (itemPath === '/memory') {
        return route.path === '/memory'
            || route.path.startsWith('/experience/')
            || route.path.startsWith('/rule/')
            || route.path.startsWith('/decision/')
    }
    if (itemPath === '/capture') {
        return route.path === '/capture' || route.path.startsWith('/capture/')
    }
    if (itemPath === '/import') {
        return route.path === '/import' || route.path.startsWith('/import/')
    }
    if (itemPath === '/trace') {
        return route.path === '/trace' || route.path.startsWith('/trace/')
    }
    return route.path === itemPath || route.path.startsWith(`${itemPath}/`)
}
</script>

<style scoped>
.app-sidebar {
    width: 13.5rem;
    flex-shrink: 0;
    height: 100%;
    min-height: 0;
    border-right: 1px solid #e8eaef;
    background: #fff;
}

.app-sidebar__nav {
    flex: 1;
    padding: 12px 10px;
    overflow-y: auto;
}

.app-sidebar__section--bordered {
    margin-top: 10px;
    padding-top: 10px;
    border-top: 1px solid #f3f4f6;
}

.app-sidebar__section-title {
    margin: 0 0 6px;
    padding: 0 8px;
    font-size: 12px;
    font-weight: 500;
    color: #94a3b8;
    letter-spacing: 0.02em;
}

.app-sidebar__items {
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.app-sidebar__item {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;
    padding: 7px 8px;
    border-radius: 10px;
    border: 1px solid transparent;
    background: transparent;
    text-align: left;
    cursor: pointer;
    font: inherit;
    text-decoration: none;
    transition: background 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
}

.app-sidebar__item--external {
    color: inherit;
}

.app-sidebar__item:hover {
    background: #fafafa;
    border-color: #f3f4f6;
}

.app-sidebar__item--active {
    background: var(--nav-item-bg-active);
    border-color: var(--nav-item-border-active);
}

.app-sidebar__item--active .app-sidebar__label {
    color: var(--nav-item-label-active);
    font-weight: 600;
}

.app-sidebar__item--active .app-sidebar__desc {
    color: var(--nav-item-desc-active);
}

.app-sidebar__item--placeholder {
    opacity: 0.82;
}

.app-sidebar__item--placeholder:hover {
    background: transparent;
    border-color: transparent;
}

.app-sidebar__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    width: var(--nav-icon-size);
    height: var(--nav-icon-size);
    border-radius: var(--nav-icon-radius);
    background: var(--nav-icon-bg);
    color: var(--nav-icon-color);
    border: 1px solid transparent;
    transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
}

.app-sidebar__item:hover .app-sidebar__icon:not(.app-sidebar__icon--active):not(.app-sidebar__icon--attention) {
    background: var(--nav-icon-bg-hover);
    color: var(--nav-icon-color-hover);
}

.app-sidebar__icon--active {
    background: var(--nav-icon-bg-brand);
    color: var(--nav-icon-color-brand);
    border-color: var(--nav-icon-border-brand);
    box-shadow: 0 1px 2px rgba(93, 101, 249, 0.08);
}

.app-sidebar__icon--attention {
    background: var(--nav-icon-bg-attention);
    color: var(--nav-icon-color-attention);
    border-color: var(--nav-icon-border-attention);
}

.app-sidebar__body {
    flex: 1;
    min-width: 0;
}

.app-sidebar__label-row {
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
}

.app-sidebar__label {
    font-size: 13px;
    font-weight: 500;
    color: #111827;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    line-height: 1.25;
}

.app-sidebar__phase-tag {
    transform: scale(0.88);
    transform-origin: left center;
}

.app-sidebar__desc {
    display: block;
    margin-top: 1px;
    font-size: 11px;
    color: #9ca3af;
    line-height: 1.3;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.app-sidebar__footer {
    display: flex;
    flex-shrink: 0;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 10px 12px;
    border-top: 1px solid #f3f4f6;
    background: #fafafa;
}

.app-sidebar__footer-dot {
    width: 3px;
    height: 3px;
    border-radius: 50%;
    background: #d1d5db;
    flex-shrink: 0;
}

.app-sidebar__footer-text {
    font-size: 10px;
    line-height: 1.4;
    color: #9ca3af;
    text-align: center;
}
</style>
