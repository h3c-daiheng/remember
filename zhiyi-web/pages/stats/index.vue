<template>
    <div class="stats-page max-w-layout mx-auto">
        <!-- 页头 -->
        <header class="stats-page__header">
            <div class="stats-page__header-main">
                <div>
                    <h1 class="stats-page__title">数据统计</h1>
                    <p class="stats-page__subtitle">
                        召回、沉淀与反馈的用量与质量指标，驱动经验飞轮
                    </p>
                    <div v-if="currentWorkspaceName" class="stats-page__workspace">
                        <el-icon :size="12"><OfficeBuilding /></el-icon>
                        {{ currentWorkspaceName }}
                    </div>
                </div>
            </div>
        </header>

        <el-skeleton v-if="loading && !overview" :rows="14" animated />

        <template v-else>
            <!-- 无 Memory 调用空态 -->
            <PageEmptyState
                v-if="isEmptyUsage"
                :flow-steps="memoryFlywheelSteps"
                title="启动经验飞轮，让数据在此生长"
                subtitle="配置 MCP 并完成首次 Remember / Recall 后，将展示召回、沉淀与反馈的用量与质量指标"
                :tags="['Recall', 'Remember', 'Feedback', 'Capture 漏斗']"
                guide="按接入指南完成 MCP 配置，Agent 首次调用后本页将自动更新"
            >
                <el-button type="primary" @click="router.push('/dashboard/guide')">
                    查看接入指南
                </el-button>
            </PageEmptyState>

            <!-- helpful 占比偏低提示 -->
            <div v-if="isLowHelpfulRate" class="stats-page__alert stats-page__alert--warning">
                <el-icon class="stats-page__alert-icon" :size="18"><WarningFilled /></el-icon>
                <div class="stats-page__alert-content">
                    <p class="stats-page__alert-title">helpful 占比偏低</p>
                    <p class="stats-page__alert-desc">
                        当前低于 40%，说明召回经验可能不够精准或 Capture 质量待提升。建议检查 Ranking 上下文、补充 Task Match 字段，并引导 Agent 提交 Feedback。
                    </p>
                </div>
            </div>

            <!-- 概览指标卡 -->
            <StatsOverviewCards
                class="stats-page__section"
                :loading="loading"
                :overview="overview"
            />

            <!-- 月用量与配额 -->
            <StatsUsagePanel
                class="stats-page__section"
                :loading="loading"
                :usage="usage"
                @month-change="changeUsageMonth"
            />

            <!-- Capture 闭环漏斗 -->
            <StatsCaptureFunnel
                class="stats-page__section"
                :loading="loading"
                :funnel="funnel"
            />

            <!-- 多维分析 -->
            <StatsDimensionsPanel
                class="stats-page__section"
                :loading="loading"
                :dimensions="dimensions"
                @filter-change="changeDimensionFilters"
            />

            <!-- 次级指标 -->
            <section v-if="overview" class="stats-page__secondary">
                <div
                    v-for="item in secondaryMetrics"
                    :key="item.key"
                    class="stats-page__secondary-card"
                >
                    <p class="stats-page__secondary-label">{{ item.label }}</p>
                    <p class="stats-page__secondary-value">{{ item.value }}</p>
                </div>
            </section>

            <!-- 趋势图 -->
            <StatsRecallTrendChart
                class="stats-page__section"
                :loading="loading"
                :trend-list="trendList"
            />

            <!-- Top 经验表 -->
            <StatsTopKnowledgeTable
                class="stats-page__section"
                :loading="loading"
                :top-knowledge-list="topKnowledgeList"
            />

            <!-- 图谱枢纽 -->
            <StatsGraphHubTable
                class="stats-page__section"
                :loading="loading"
                :graph-hub-list="graphHubList"
            />
        </template>
    </div>
</template>

<script setup>
import { OfficeBuilding, WarningFilled } from '@element-plus/icons-vue'
import { formatRatePercent, useWorkspaceStats } from '~/composables/useWorkspaceStats'
import { MEMORY_FLYWHEEL_STEPS } from '~/constants/pageEmptyState'

definePageMeta({
    layout: 'app',
})

useHead({ title: '数据统计' })

/** 页面浏览埋点 */
usePageTracker()

const router = useRouter()
const memoryFlywheelSteps = MEMORY_FLYWHEEL_STEPS
const { currentWorkspace } = useWorkspace()
const {
    loading,
    overview,
    trendList,
    topKnowledgeList,
    graphHubList,
    usage,
    funnel,
    dimensions,
    isEmptyUsage,
    isLowHelpfulRate,
    loadStats,
    changeUsageMonth,
    changeDimensionFilters,
} = useWorkspaceStats()

/** 当前工作空间名称 */
const currentWorkspaceName = computed(() => currentWorkspace.value?.workspaceName || '')

/** 次级指标行配置 */
const secondaryMetrics = computed(() => {
    const data = overview.value || {}
    const periodDays = data.statsPeriodDays || 7
    return [
        {
            key: 'publishedCount',
            label: '已发布经验',
            value: data.publishedCount ?? 0,
        },
        {
            key: 'pendingDraftCount',
            label: '待确认草稿',
            value: data.pendingDraftCount ?? 0,
        },
        {
            key: 'rememberToDraftRate',
            label: 'Remember 转草稿率',
            value: formatRatePercent(data.rememberToDraftRate),
        },
        {
            key: 'feedbackCount7d',
            label: `近 ${periodDays} 天 Feedback`,
            value: data.feedbackCount7d ?? 0,
        },
    ]
})

onMounted(() => {
    loadStats()
})

/** 切换工作空间后静默刷新 */
useWorkspaceChange(async () => {
    await loadStats({ silent: true })
})
</script>

<style scoped>
.stats-page {
    padding: 28px 24px 40px;
}

.stats-page__header {
    margin-bottom: 28px;
}

.stats-page__header-main {
    display: flex;
    align-items: flex-start;
    gap: 16px;
}

.stats-page__title {
    margin: 0;
    font-size: 26px;
    font-weight: 700;
    color: #111827;
    letter-spacing: -0.02em;
}

.stats-page__subtitle {
    margin: 6px 0 0;
    font-size: 14px;
    color: #6b7280;
    line-height: 1.6;
}

.stats-page__workspace {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    margin-top: 10px;
    padding: 4px 10px;
    border-radius: 999px;
    font-size: 12px;
    color: #5d65f9;
    background: #eff0fe;
    border: 1px solid #ced1fd;
}

.stats-page__alert {
    display: flex;
    gap: 12px;
    margin-bottom: 20px;
    padding: 16px 18px;
    border-radius: 12px;
}

.stats-page__alert--info {
    background: linear-gradient(145deg, #f9fafb 0%, #fff 100%);
    border: 1px dashed #d1d5db;
}

.stats-page__alert--warning {
    background: linear-gradient(145deg, #fffbeb 0%, #fff 100%);
    border: 1px solid #fde68a;
}

.stats-page__alert-icon {
    flex-shrink: 0;
    margin-top: 2px;
}

.stats-page__alert--info .stats-page__alert-icon {
    color: #5d65f9;
}

.stats-page__alert--warning .stats-page__alert-icon {
    color: #d97706;
}

.stats-page__alert-title {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
    color: #111827;
}

.stats-page__alert-desc {
    margin: 4px 0 0;
    font-size: 13px;
    color: #6b7280;
    line-height: 1.6;
}

.stats-page__section {
    margin-bottom: 20px;
}

.stats-page__secondary {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
    margin-bottom: 20px;
}

@media (min-width: 640px) {
    .stats-page__secondary {
        grid-template-columns: repeat(4, 1fr);
    }
}

.stats-page__secondary-card {
    padding: 14px 16px;
    border-radius: 12px;
    border: 1px solid #e8eaef;
    background: #fff;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
}

.stats-page__secondary-label {
    margin: 0;
    font-size: 12px;
    color: #6b7280;
}

.stats-page__secondary-value {
    margin: 4px 0 0;
    font-size: 20px;
    font-weight: 700;
    color: #111827;
    font-variant-numeric: tabular-nums;
}
</style>
