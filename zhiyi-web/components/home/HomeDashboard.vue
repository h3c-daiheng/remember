<template>
    <div class="dashboard max-w-layout mx-auto">
        <!-- 页头 + 概览指标 -->
        <section class="dashboard__hero">
            <div class="dashboard__hero-top">
                <div class="dashboard__hero-text">
                    <h1 class="dashboard__title">工作台</h1>
                    <div class="dashboard__meta">
                        <span class="dashboard__greeting">
                            你好<span v-if="currentUser?.nickname">，{{ currentUser.nickname }}</span>
                        </span>
                        <template v-if="currentWorkspaceName">
                            <span class="dashboard__meta-dot" />
                            <span class="dashboard__workspace">
                                <el-icon :size="12"><OfficeBuilding /></el-icon>
                                {{ currentWorkspaceName }}
                            </span>
                        </template>
                    </div>
                </div>
            </div>

            <div class="dashboard__metrics">
                <button
                    type="button"
                    class="dashboard__metric dashboard__metric--published"
                    @click="router.push('/memory')"
                >
                    <div class="dashboard__metric-icon dashboard__metric-icon--published">
                        <el-icon :size="18"><Collection /></el-icon>
                    </div>
                    <div class="dashboard__metric-body">
                        <span class="dashboard__metric-label">已发布经验</span>
                        <span class="dashboard__metric-value">
                            {{ statsLoading ? '—' : publishedCount }}
                        </span>
                    </div>
                    <span class="dashboard__metric-action">
                        查看
                        <el-icon :size="14"><ArrowRight /></el-icon>
                    </span>
                </button>

                <button
                    type="button"
                    class="dashboard__metric dashboard__metric--draft"
                    @click="router.push('/capture')"
                >
                    <div class="dashboard__metric-icon dashboard__metric-icon--draft">
                        <el-icon :size="18"><DocumentChecked /></el-icon>
                    </div>
                    <div class="dashboard__metric-body">
                        <span class="dashboard__metric-label">待确认草稿</span>
                        <span class="dashboard__metric-value">{{ pendingDraftCount }}</span>
                    </div>
                    <span v-if="pendingDraftCount > 0" class="dashboard__metric-badge">
                        {{ pendingDraftCount > 99 ? '99+' : pendingDraftCount }}
                    </span>
                    <span v-else class="dashboard__metric-action">
                        查看
                        <el-icon :size="14"><ArrowRight /></el-icon>
                    </span>
                </button>
            </div>
        </section>

        <!-- 新手快速开始 -->
        <HomeQuickStart
            class="dashboard__section"
            :published-count="publishedCount"
            :pending-draft-count="pendingDraftCount"
        />

        <!-- 快捷入口：分组收纳为单块面板 -->
        <section class="dashboard__nav-panel">
            <div
                v-for="(section, sectionIndex) in quickLinkSections"
                :key="section.title"
                class="dashboard__nav-group"
                :class="{ 'dashboard__nav-group--bordered': sectionIndex > 0 }"
            >
                <h2 class="dashboard__nav-title">{{ section.title }}</h2>
                <div class="dashboard__links">
                    <article
                        v-for="card in section.items"
                        :key="card.path"
                        class="dashboard__link-card"
                        :class="{ 'dashboard__link-card--placeholder': card.placeholder }"
                        @click="handleQuickLinkClick(card)"
                    >
                        <div
                            class="dashboard__link-icon"
                            :class="`dashboard__link-icon--${card.tone}`"
                        >
                            <el-icon :size="16">
                                <component :is="iconMap[card.icon]" />
                            </el-icon>
                        </div>
                        <div class="dashboard__link-body">
                            <div class="dashboard__link-head">
                                <h3 class="dashboard__link-label">{{ card.label }}</h3>
                                <el-tag
                                    v-if="card.phase"
                                    size="small"
                                    type="info"
                                    effect="plain"
                                    class="dashboard__link-tag"
                                >
                                    {{ card.phase }}
                                </el-tag>
                                <el-badge
                                    v-else-if="card.badgeKey && badgeValues[card.badgeKey] > 0"
                                    :value="badgeValues[card.badgeKey]"
                                    :max="99"
                                />
                            </div>
                            <p class="dashboard__link-desc">{{ card.description }}</p>
                        </div>
                        <el-icon :size="12" class="dashboard__link-arrow"><ArrowRight /></el-icon>
                    </article>
                </div>
            </div>
        </section>

        <!-- 最近发布 -->
        <section v-if="recentList.length > 0" class="dashboard__section">
            <div class="dashboard__recent-head">
                <div>
                    <h2 class="dashboard__section-title dashboard__section-title--inline">最近发布</h2>
                    <p class="dashboard__recent-desc">最新入库的经验条目</p>
                </div>
                <el-button type="primary" plain size="small" @click="router.push('/memory')">
                    查看全部
                </el-button>
            </div>
            <div class="dashboard__recent-list">
                <KnowledgeCard
                    v-for="item in recentList"
                    :key="item.id"
                    :knowledge="item"
                />
            </div>
        </section>

        <!-- 飞轮标语 -->
        <footer class="dashboard__footer">
            <span class="dashboard__footer-dot" />
            {{ flywheelText }}
            <span class="dashboard__footer-dot" />
        </footer>
    </div>
</template>

<script setup>
import {
    ArrowRight,
    Collection,
    Connection,
    Cpu,
    DataAnalysis,
    DocumentChecked,
    HomeFilled,
    House,
    Memo,
    Notebook,
    Operation,
    OfficeBuilding,
    Search,
    Setting,
    Share,
    Upload,
} from '@element-plus/icons-vue'
import { APP_SIDEBAR_SECTIONS, NAV_ITEM_TONE_MAP } from '~/constants/navigation'
import { PRODUCT_FLYWHEEL_TEXT } from '~/constants/terminology'
import { fetchKnowledgeList } from '~/services/knowledge.service'
import { openGonlineWorkspaceSettings } from '~/utils/gonlineAuthLogin'

defineProps({
    /** 当前登录用户；ClientOnly Shell 内由父级传入 */
    currentUser: {
        type: Object,
        default: null,
    },
})

const router = useRouter()
const { badgeValues } = useAppNavigation()
const { currentWorkspace: currentWorkspaceState } = useWorkspace()

const flywheelText = PRODUCT_FLYWHEEL_TEXT
const statsLoading = ref(false)
const publishedCount = ref(0)
const recentList = ref([])

/** 侧栏图标映射 */
const iconMap = {
    House,
    HomeFilled,
    Collection,
    DocumentChecked,
    Cpu,
    Notebook,
    Connection,
    Search,
    Memo,
    Operation,
    Share,
    Upload,
    DataAnalysis,
    Setting,
}

/** 当前工作空间名称 */
const currentWorkspaceName = computed(() => {
    const workspace = currentWorkspaceState.value || {}
    return workspace.workspaceName || ''
})

/** 待确认草稿数量 */
const pendingDraftCount = computed(() => badgeValues.value.pendingDraftCount || 0)

/** 快捷入口分组，排除当前工作台页 */
const quickLinkSections = computed(() =>
    APP_SIDEBAR_SECTIONS.map((section) => ({
        title: section.title,
        items: section.items
            .filter((item) => item.path !== '/dashboard')
            .map((item) => ({
                path: item.path,
                label: item.label,
                description: item.description,
                icon: item.icon,
                phase: item.placeholder ? item.phase : null,
                placeholder: item.placeholder,
                badgeKey: item.badgeKey,
                external: Boolean(item.external),
                tone: NAV_ITEM_TONE_MAP[item.path] || 'brand',
            })),
    })).filter((section) => section.items.length > 0),
)

/**
 * 工作台快捷入口点击：外链（主站工作空间）新窗口打开，其余站内跳转
 * @param {{ path?: string, external?: boolean }} card
 */
function handleQuickLinkClick(card) {
    if (card?.external || card?.path === 'gonline-workspace-settings') {
        openGonlineWorkspaceSettings()
        return
    }
    router.push(card.path)
}

/** 加载概览数据：已发布经验总数与最近 3 条 */
async function loadDashboardStats() {
    statsLoading.value = true
    try {
        const result = await fetchKnowledgeList(1, 3, '')
        publishedCount.value = result.total || 0
        recentList.value = result.list || []
    } finally {
        statsLoading.value = false
    }
}

onMounted(() => {
    loadDashboardStats()
})

/** 切换工作空间后刷新概览 */
useWorkspaceChange(async () => {
    await loadDashboardStats()
})
</script>

<style scoped>
.dashboard {
    padding: 24px 24px 32px;
}

/* 页头 + 概览 */
.dashboard__hero {
    margin-bottom: 20px;
    padding: 20px 22px;
    border-radius: 16px;
    border: 1px solid #e8eaef;
    background: linear-gradient(160deg, #fafbff 0%, #fff 45%, #fffbeb 100%);
    box-shadow: 0 1px 4px rgba(15, 23, 42, 0.05);
}

.dashboard__hero-top {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 18px;
}

.dashboard__hero-text {
    min-width: 0;
}

.dashboard__title {
    margin: 0;
    font-size: 22px;
    font-weight: 700;
    color: #111827;
    letter-spacing: -0.02em;
    line-height: 1.2;
}

.dashboard__meta {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    margin-top: 4px;
}

.dashboard__greeting {
    font-size: 13px;
    color: #6b7280;
}

.dashboard__meta-dot {
    width: 3px;
    height: 3px;
    border-radius: 50%;
    background: #d1d5db;
    flex-shrink: 0;
}

.dashboard__workspace {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: #5d65f9;
}

/* 概览指标 */
.dashboard__metrics {
    display: grid;
    grid-template-columns: 1fr;
    gap: 10px;
}

@media (min-width: 640px) {
    .dashboard__metrics {
        grid-template-columns: repeat(2, 1fr);
    }
}

.dashboard__metric {
    display: flex;
    align-items: center;
    gap: 12px;
    width: 100%;
    padding: 12px 14px;
    border-radius: 12px;
    border: 1px solid transparent;
    background: rgba(255, 255, 255, 0.85);
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
    cursor: pointer;
    text-align: left;
    font: inherit;
    appearance: none;
    transition: box-shadow 0.2s ease, transform 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.dashboard__metric:hover {
    background: #fff;
    box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
    transform: translateY(-1px);
}

.dashboard__metric--published {
    border-color: #d8dcfe;
}

.dashboard__metric--published:hover {
    border-color: #aeb2fc;
}

.dashboard__metric--draft {
    border-color: #fde68a;
}

.dashboard__metric--draft:hover {
    border-color: #fcd34d;
}

.dashboard__metric-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    width: 36px;
    height: 36px;
    border-radius: 10px;
}

.dashboard__metric-icon--published {
    background: linear-gradient(135deg, #eff0fe 0%, #dfe0fe 100%);
    color: #5d65f9;
}

.dashboard__metric-icon--draft {
    background: linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);
    color: #d97706;
}

.dashboard__metric-body {
    display: flex;
    flex-direction: column;
    gap: 2px;
    flex: 1;
    min-width: 0;
}

.dashboard__metric-label {
    font-size: 12px;
    color: #6b7280;
    line-height: 1.2;
}

.dashboard__metric-value {
    font-size: 24px;
    font-weight: 700;
    color: #111827;
    font-variant-numeric: tabular-nums;
    line-height: 1.1;
}

.dashboard__metric-action {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    flex-shrink: 0;
    padding: 4px 10px;
    border-radius: 999px;
    font-size: 12px;
    font-weight: 500;
    color: #6b7280;
    background: #f3f4f6;
    transition: color 0.15s ease, background 0.15s ease;
}

.dashboard__metric:hover .dashboard__metric-action {
    color: #5d65f9;
    background: #eff0fe;
}

.dashboard__metric-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    min-width: 24px;
    height: 24px;
    padding: 0 8px;
    border-radius: 999px;
    font-size: 12px;
    font-weight: 600;
    color: #fff;
    background: #f59e0b;
}

/* 区块通用 */
.dashboard__section {
    margin-bottom: 16px;
}

.dashboard__section-title {
    margin: 0 0 8px;
    font-size: 12px;
    font-weight: 600;
    color: #6b7280;
    text-transform: uppercase;
    letter-spacing: 0.04em;
}

.dashboard__section-title--inline {
    margin-bottom: 2px;
    text-transform: none;
    letter-spacing: -0.01em;
    font-size: 15px;
    color: #111827;
}

/* 快捷入口面板 */
.dashboard__nav-panel {
    margin-bottom: 16px;
    padding: 12px 14px;
    border-radius: 12px;
    border: 1px solid #e8eaef;
    background: #fff;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
}

.dashboard__nav-group--bordered {
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px solid #f3f4f6;
}

.dashboard__nav-title {
    margin: 0 0 8px;
    font-size: 11px;
    font-weight: 600;
    color: #9ca3af;
    text-transform: uppercase;
    letter-spacing: 0.05em;
}

/* 快捷入口 */
.dashboard__links {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
}

@media (min-width: 768px) {
    .dashboard__links {
        grid-template-columns: repeat(3, 1fr);
    }
}

@media (min-width: 1024px) {
    .dashboard__links {
        grid-template-columns: repeat(4, 1fr);
    }
}

.dashboard__link-card {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 12px;
    border-radius: 10px;
    border: 1px solid #f3f4f6;
    background: #fafafa;
    cursor: pointer;
    transition: border-color 0.15s ease, background 0.15s ease;
}

.dashboard__link-card:hover {
    border-color: #e0e3fe;
    background: #fff;
}

.dashboard__link-card--placeholder {
    opacity: 0.8;
}

.dashboard__link-card--placeholder:hover {
    border-color: #f3f4f6;
    background: #fafafa;
}

.dashboard__link-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    width: var(--nav-icon-size);
    height: var(--nav-icon-size);
    border-radius: var(--nav-icon-radius);
    border: 1px solid transparent;
    transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.dashboard__link-icon--brand {
    background: var(--nav-icon-bg-brand);
    color: var(--nav-icon-color-brand);
    border-color: var(--nav-icon-border-brand);
}

.dashboard__link-icon--neutral {
    background: var(--nav-icon-bg);
    color: var(--nav-icon-color);
}

.dashboard__link-card:hover .dashboard__link-icon--neutral {
    background: var(--nav-icon-bg-hover);
    color: var(--nav-icon-color-hover);
}

.dashboard__link-body {
    flex: 1;
    min-width: 0;
}

.dashboard__link-head {
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
}

.dashboard__link-label {
    margin: 0;
    font-size: 13px;
    font-weight: 600;
    color: #111827;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.dashboard__link-tag {
    transform: scale(0.9);
    transform-origin: left center;
}

.dashboard__link-desc {
    margin: 1px 0 0;
    font-size: 11px;
    color: #9ca3af;
    line-height: 1.35;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.dashboard__link-arrow {
    flex-shrink: 0;
    color: #d1d5db;
    transition: color 0.15s ease;
}

.dashboard__link-card:hover .dashboard__link-arrow {
    color: #5d65f9;
}

/* 最近发布 */
.dashboard__recent-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 14px;
}

.dashboard__recent-desc {
    margin: 0;
    font-size: 12px;
    color: #9ca3af;
}

.dashboard__recent-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

/* 页脚飞轮 */
.dashboard__footer {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    margin-top: 12px;
    padding: 14px 20px;
    border-radius: 10px;
    font-size: 12px;
    color: #9ca3af;
    background: #f9fafb;
    border: 1px solid #f3f4f6;
}

.dashboard__footer-dot {
    width: 4px;
    height: 4px;
    border-radius: 50%;
    background: #d1d5db;
}
</style>
