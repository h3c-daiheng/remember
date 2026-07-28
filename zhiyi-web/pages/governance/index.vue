<template>
    <div class="governance-page max-w-layout mx-auto">
        <header class="governance-page__header">
            <div class="governance-page__header-main">
                <div class="governance-page__header-text">
                    <h1 class="governance-page__title">记忆治理</h1>
                    <p class="governance-page__subtitle">
                        扫描重复记忆、识别碎片聚类，合并优化零散不完整经验
                    </p>
                </div>

                <div class="governance-page__tabs">
                    <button
                        type="button"
                        class="governance-page__tab"
                        :class="{ 'governance-page__tab--active': pageTab === 'issues' }"
                        @click="pageTab = 'issues'"
                    >
                        <el-icon :size="16"><Tickets /></el-icon>
                        治理工单
                    </button>
                    <button
                        type="button"
                        class="governance-page__tab"
                        :class="{ 'governance-page__tab--active': pageTab === 'merge-wizard' }"
                        @click="pageTab = 'merge-wizard'"
                    >
                        <el-icon :size="16"><Connection /></el-icon>
                        合并向导
                    </button>
                </div>
            </div>

            <div
                v-if="!canEdit"
                class="governance-page__alert governance-page__alert--info"
            >
                <el-icon class="governance-page__alert-icon" :size="18"><InfoFilled /></el-icon>
                <div>
                    <p class="governance-page__alert-title">当前为查看者，仅可浏览治理工单</p>
                    <p class="governance-page__alert-desc">扫描与处置操作需编辑者及以上角色</p>
                </div>
            </div>
        </header>

        <template v-if="pageTab === 'issues'">
            <section class="governance-page__overview">
                <GovernanceStatsPanel
                    :stats="stats"
                    :config="config"
                    :can-manage="canManage"
                    :config-saving="configSaving"
                    @update-config="handleUpdateConfig"
                />
                <GovernanceScanPanel
                    v-model:scan-task="scanTask"
                    v-model:knowledge-type="scanKnowledgeType"
                    :scanning="scanning"
                    :disabled="!canEdit"
                    :stats="stats"
                    @scan="handleRunScan"
                />
            </section>

            <section class="governance-page__toolbar">
                <div class="governance-page__toolbar-row">
                    <span class="governance-page__toolbar-label">工单类型</span>
                    <div class="governance-page__category-group">
                        <button
                            v-for="category in issueCategoryOptions"
                            :key="category.value"
                            type="button"
                            class="governance-page__category-btn"
                            :class="{
                                'governance-page__category-btn--active': issueCategory === category.value,
                                [`governance-page__category-btn--${category.tone}`]: issueCategory === category.value,
                            }"
                            @click="handleCategoryClick(category.value)"
                        >
                            {{ category.label }}
                            <span
                                v-if="category.openCount"
                                class="governance-page__category-count"
                            >
                                {{ category.openCount }}
                            </span>
                        </button>
                    </div>
                </div>

                <div class="governance-page__toolbar-row governance-page__toolbar-row--status">
                    <button
                        v-for="statusTab in statusTabOptions"
                        :key="statusTab.name"
                        type="button"
                        class="governance-page__status-btn"
                        :class="{ 'governance-page__status-btn--active': activeTab === statusTab.name }"
                        @click="handleStatusClick(statusTab.name)"
                    >
                        {{ statusTab.label }}
                        <span v-if="statusTab.countLabel" class="governance-page__status-count">
                            {{ statusTab.countLabel }}
                        </span>
                    </button>
                </div>
            </section>

            <div v-if="loading" class="governance-page__list">
                <div v-for="index in 3" :key="index" class="governance-page__skeleton">
                    <el-skeleton :rows="3" />
                </div>
            </div>

            <PageEmptyState
                v-else-if="issueList.length === 0"
                :title="emptyTitle"
                :subtitle="emptySubtitle"
                :tags="emptyTags"
                :compact="activeTab !== 'open'"
            >
                <el-button
                    v-if="canEdit && activeTab === 'open'"
                    type="primary"
                    :loading="scanning"
                    @click="handleRunScan"
                >
                    开始扫描
                </el-button>
            </PageEmptyState>

            <div v-else class="governance-page__list">
                <article
                    v-for="issue in issueList"
                    :key="issue.id"
                    class="governance-page__issue"
                    :class="`governance-page__issue--${getIssueTone(issue.issueType)}`"
                    @click="openIssueDrawer(issue)"
                >
                    <div class="governance-page__issue-accent" aria-hidden="true" />

                    <div class="governance-page__issue-body">
                        <div class="governance-page__issue-top">
                            <span
                                class="governance-page__issue-badge"
                                :class="`governance-page__issue-badge--${getIssueTone(issue.issueType)}`"
                            >
                                {{ GOVERNANCE_ISSUE_TYPE_LABELS[issue.issueType] || issue.issueType }}
                            </span>
                            <span class="governance-page__issue-id">#{{ issue.id }}</span>
                        </div>

                        <h2 class="governance-page__issue-title">
                            {{ issue.primaryKnowledge?.title || `记忆 #${issue.primaryKnowledgeId}` }}
                        </h2>

                        <div class="governance-page__issue-meta">
                            <span>{{ issue.knowledgeType }}</span>
                            <span aria-hidden="true">·</span>
                            <span>{{ issue.relatedKnowledgeIds?.length || 0 }} 条关联</span>
                            <span aria-hidden="true">·</span>
                            <span>{{ GOVERNANCE_ACTION_LABELS[issue.suggestedAction] || issue.suggestedAction }}</span>
                        </div>

                        <div class="governance-page__issue-similarity">
                            <div class="governance-page__issue-similarity-bar">
                                <div
                                    class="governance-page__issue-similarity-fill"
                                    :style="{ width: `${Math.round((issue.similarityScore || 0) * 100)}%` }"
                                />
                            </div>
                            <span class="governance-page__issue-similarity-label">
                                相似度 {{ Math.round((issue.similarityScore || 0) * 100) }}%
                            </span>
                        </div>
                    </div>

                    <el-icon class="governance-page__issue-arrow"><ArrowRight /></el-icon>
                </article>
            </div>

            <div v-if="total > pageSize" class="governance-page__pagination">
                <el-pagination
                    background
                    layout="total, prev, pager, next"
                    :total="total"
                    :page-size="pageSize"
                    :current-page="pageNum"
                    @current-change="handlePageChange"
                />
            </div>
        </template>

        <GovernanceMergeWizard
            v-else
            ref="mergeWizardRef"
            :can-edit="canEdit"
            :preset-issue-id="mergeWizardIssueId"
            :merge-preview-loading="mergePreviewLoading"
            :merge-confirm-loading="mergeConfirmLoading"
            @preview="handleMergePreview"
            @confirm="handleMergeConfirm"
            @completed="handleMergeCompleted"
        />

        <GovernanceIssueDrawer
            :visible="drawerVisible"
            :issue="activeIssue"
            :issue-detail="activeIssueDetail"
            :detail-loading="detailLoading"
            :resolving="resolvingIssueId === activeIssue?.id"
            :can-edit="canEdit"
            @update:visible="drawerVisible = $event"
            @resolve="handleResolve"
            @open-merge-wizard="handleOpenMergeWizardFromIssue"
        />
    </div>
</template>

<script setup>
import {
    ArrowRight,
    Connection,
    InfoFilled,
    Tickets,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
    GOVERNANCE_ACTION_LABELS,
    GOVERNANCE_EMPTY_DISMISSED_TAGS,
    GOVERNANCE_EMPTY_RESOLVED_TAGS,
    GOVERNANCE_EMPTY_TAGS,
    GOVERNANCE_ISSUE_STATUS_DISMISSED,
    GOVERNANCE_ISSUE_STATUS_OPEN,
    GOVERNANCE_ISSUE_STATUS_RESOLVED,
    GOVERNANCE_ISSUE_TYPE_CONFLICT,
    GOVERNANCE_ISSUE_TYPE_DUPLICATE,
    GOVERNANCE_ISSUE_TYPE_FRAGMENT_CLUSTER,
    GOVERNANCE_ISSUE_TYPE_INCOMPLETE,
    GOVERNANCE_ISSUE_TYPE_OUTDATED,
    GOVERNANCE_ISSUE_TYPE_LABELS,
    GOVERNANCE_SCAN_TASK_DUPLICATE_FULL,
    GOVERNANCE_SCAN_TASK_FRAGMENT,
    GOVERNANCE_SCAN_TASK_OPTIONS,
    GOVERNANCE_SCAN_TASK_VALIDATE,
} from '~/constants/governance'

definePageMeta({ layout: 'app' })

useHead({ title: '记忆治理' })
usePageTracker()

const router = useRouter()
const { canEdit } = useCanEditKnowledge()
const { canManage } = useWorkspacePermission()
const {
    loading,
    scanning,
    issueList,
    total,
    stats,
    config,
    configSaving,
    resolvingIssueId,
    mergePreviewLoading,
    mergeConfirmLoading,
    loadIssueList,
    loadStats,
    loadConfig,
    saveConfig,
    runScan,
    loadIssueDetail,
    resolveIssue,
    loadMergePreview,
    confirmMerge,
} = useGovernance()

const pageTab = ref('issues')
const issueCategory = ref(GOVERNANCE_ISSUE_TYPE_DUPLICATE)
const scanTask = ref(GOVERNANCE_SCAN_TASK_DUPLICATE_FULL)
const activeTab = ref('open')
const pageNum = ref(1)
const pageSize = 20
const scanKnowledgeType = ref('')
const drawerVisible = ref(false)
const activeIssue = ref(null)
const activeIssueDetail = ref(null)
const detailLoading = ref(false)
const mergeWizardRef = ref(null)
const mergeWizardIssueId = ref(null)

const tabStatusMap = {
    open: GOVERNANCE_ISSUE_STATUS_OPEN,
    resolved: GOVERNANCE_ISSUE_STATUS_RESOLVED,
    dismissed: GOVERNANCE_ISSUE_STATUS_DISMISSED,
}

/** 工单类型筛选配置 */
const issueCategoryOptions = computed(() => [
    {
        value: GOVERNANCE_ISSUE_TYPE_DUPLICATE,
        label: '重复记忆',
        tone: 'amber',
        openCount: stats.value?.openIssueCount || 0,
    },
    {
        value: GOVERNANCE_ISSUE_TYPE_FRAGMENT_CLUSTER,
        label: '碎片聚类',
        tone: 'indigo',
        openCount: stats.value?.openFragmentIssueCount || 0,
    },
    {
        value: GOVERNANCE_ISSUE_TYPE_INCOMPLETE,
        label: '不完整',
        tone: 'rose',
        openCount: stats.value?.openIncompleteIssueCount || 0,
    },
    {
        value: GOVERNANCE_ISSUE_TYPE_OUTDATED,
        label: '过时',
        tone: 'slate',
        openCount: stats.value?.openOutdatedIssueCount || 0,
    },
    {
        value: GOVERNANCE_ISSUE_TYPE_CONFLICT,
        label: '冲突',
        tone: 'red',
        openCount: stats.value?.openConflictIssueCount || 0,
    },
])

/** 状态 Tab 配置 */
const statusTabOptions = computed(() => [
    {
        name: 'open',
        label: '待处理',
        countLabel: openCountLabel.value.replace(/[()]/g, '') || '',
    },
    {
        name: 'resolved',
        label: '已解决',
        countLabel: resolvedCountLabel.value.replace(/[()]/g, '') || '',
    },
    {
        name: 'dismissed',
        label: '已忽略',
        countLabel: dismissedCountLabel.value.replace(/[()]/g, '') || '',
    },
])

watch(scanTask, (value) => {
    const scanOption = GOVERNANCE_SCAN_TASK_OPTIONS.find((option) => option.value === value)
    if (!scanOption) {
        return
    }
    if (scanOption.value === GOVERNANCE_SCAN_TASK_FRAGMENT) {
        issueCategory.value = GOVERNANCE_ISSUE_TYPE_FRAGMENT_CLUSTER
    } else if (scanOption.value === GOVERNANCE_SCAN_TASK_VALIDATE) {
        issueCategory.value = GOVERNANCE_ISSUE_TYPE_INCOMPLETE
    } else {
        issueCategory.value = GOVERNANCE_ISSUE_TYPE_DUPLICATE
    }
})

const openCountLabel = computed(() => {
    const countMap = {
        [GOVERNANCE_ISSUE_TYPE_DUPLICATE]: stats.value?.openIssueCount || 0,
        [GOVERNANCE_ISSUE_TYPE_FRAGMENT_CLUSTER]: stats.value?.openFragmentIssueCount || 0,
        [GOVERNANCE_ISSUE_TYPE_INCOMPLETE]: stats.value?.openIncompleteIssueCount || 0,
        [GOVERNANCE_ISSUE_TYPE_OUTDATED]: stats.value?.openOutdatedIssueCount || 0,
        [GOVERNANCE_ISSUE_TYPE_CONFLICT]: stats.value?.openConflictIssueCount || 0,
    }
    const count = countMap[issueCategory.value] || 0
    return count ? ` (${count})` : ''
})

const resolvedCountLabel = computed(() =>
    stats.value?.resolvedIssueCount ? ` (${stats.value.resolvedIssueCount})` : '',
)

const dismissedCountLabel = computed(() =>
    stats.value?.dismissedIssueCount ? ` (${stats.value.dismissedIssueCount})` : '',
)

const emptyTitle = computed(() => {
    const titleMap = {
        [GOVERNANCE_ISSUE_TYPE_FRAGMENT_CLUSTER]: '暂无碎片聚类工单',
        [GOVERNANCE_ISSUE_TYPE_INCOMPLETE]: '暂无结构不完整工单',
        [GOVERNANCE_ISSUE_TYPE_OUTDATED]: '暂无过时记忆工单',
        [GOVERNANCE_ISSUE_TYPE_CONFLICT]: '暂无 Rule 冲突工单',
    }
    if (activeTab.value === 'resolved') {
        return '暂无已解决工单'
    }
    if (activeTab.value === 'dismissed') {
        return '暂无已忽略工单'
    }
    return titleMap[issueCategory.value] || '暂无重复记忆工单'
})

const emptySubtitle = computed(() => {
    const subtitleMap = {
        [GOVERNANCE_ISSUE_TYPE_FRAGMENT_CLUSTER]: '请选择「碎片聚类」扫描任务，识别同模块零散不完整记忆',
        [GOVERNANCE_ISSUE_TYPE_INCOMPLETE]: '请选择「质量校验」扫描，或等待 Feedback 累计触发过时工单',
        [GOVERNANCE_ISSUE_TYPE_OUTDATED]: '当 Recall Feedback 累计 outdated/wrong 达阈值时会自动生成工单',
        [GOVERNANCE_ISSUE_TYPE_CONFLICT]: '质量校验会检测同模块 Rule 约束的潜在冲突',
    }
    if (activeTab.value === 'resolved') {
        return '处置完成的工单会归档在此，可追溯 Timeline'
    }
    if (activeTab.value === 'dismissed') {
        return '标记为误报的工单会归档在此'
    }
    return subtitleMap[issueCategory.value] || '请选择重复扫描任务并扫描已发布库'
})

const emptyTags = computed(() => {
    if (activeTab.value === 'resolved') {
        return GOVERNANCE_EMPTY_RESOLVED_TAGS
    }
    if (activeTab.value === 'dismissed') {
        return GOVERNANCE_EMPTY_DISMISSED_TAGS
    }
    return GOVERNANCE_EMPTY_TAGS
})

/** 工单类型对应主题色 */
function getIssueTone(issueType) {
    const toneMap = {
        [GOVERNANCE_ISSUE_TYPE_DUPLICATE]: 'amber',
        [GOVERNANCE_ISSUE_TYPE_FRAGMENT_CLUSTER]: 'indigo',
        [GOVERNANCE_ISSUE_TYPE_INCOMPLETE]: 'rose',
        [GOVERNANCE_ISSUE_TYPE_OUTDATED]: 'slate',
        [GOVERNANCE_ISSUE_TYPE_CONFLICT]: 'red',
    }
    return toneMap[issueType] || 'amber'
}

async function refreshPage() {
    await Promise.all([
        loadIssueList({
            pageNum: pageNum.value,
            pageSize,
            status: tabStatusMap[activeTab.value],
            issueType: issueCategory.value,
        }),
        loadStats(),
        loadConfig(),
    ])
}

async function handleCategoryClick(nextCategory) {
    if (issueCategory.value === nextCategory) {
        return
    }
    issueCategory.value = nextCategory
    pageNum.value = 1
    await refreshPage()
}

async function handleStatusClick(nextTab) {
    if (activeTab.value === nextTab) {
        return
    }
    activeTab.value = nextTab
    pageNum.value = 1
    await refreshPage()
}

async function handlePageChange(nextPage) {
    pageNum.value = nextPage
    await refreshPage()
}

async function handleRunScan() {
    if (!canEdit.value) {
        ElMessage.warning('当前角色无权限执行扫描')
        return
    }
    try {
        const scanOption = GOVERNANCE_SCAN_TASK_OPTIONS.find((option) => option.value === scanTask.value)
        const batchResult = await runScan({
            scanType: scanOption?.scanType,
            scanMode: scanOption?.scanMode,
            knowledgeType: scanKnowledgeType.value || undefined,
        })
        const unitLabel = scanOption?.value === GOVERNANCE_SCAN_TASK_VALIDATE
            ? '条质量问题'
            : scanOption?.value === GOVERNANCE_SCAN_TASK_FRAGMENT
                ? '组碎片聚类'
                : '组重复记忆'
        const autoResolvedHint = batchResult.autoResolvedCount
            ? `，自动处置 ${batchResult.autoResolvedCount} 组`
            : ''
        ElMessage.success(`扫描完成，发现 ${batchResult.issueCount || 0} ${unitLabel}${autoResolvedHint}`)
        if (scanOption?.value === GOVERNANCE_SCAN_TASK_FRAGMENT) {
            issueCategory.value = GOVERNANCE_ISSUE_TYPE_FRAGMENT_CLUSTER
        } else if (scanOption?.value === GOVERNANCE_SCAN_TASK_VALIDATE) {
            issueCategory.value = GOVERNANCE_ISSUE_TYPE_INCOMPLETE
        } else {
            issueCategory.value = GOVERNANCE_ISSUE_TYPE_DUPLICATE
        }
        activeTab.value = 'open'
        pageNum.value = 1
        await refreshPage()
    } catch (error) {
        ElMessage.error(error.message || '扫描失败')
    }
}

async function handleUpdateConfig(configRequest) {
    if (!canManage.value) {
        ElMessage.warning('当前角色无权限修改治理配置')
        return
    }
    try {
        await saveConfig(configRequest)
        ElMessage.success('治理配置已更新')
    } catch (error) {
        ElMessage.error(error.message || '保存配置失败')
    }
}

async function openIssueDrawer(issue) {
    activeIssue.value = issue
    activeIssueDetail.value = null
    detailLoading.value = true
    drawerVisible.value = true
    try {
        activeIssueDetail.value = await loadIssueDetail(issue.id)
    } catch (error) {
        ElMessage.error(error.message || '加载详情失败')
        drawerVisible.value = false
        activeIssue.value = null
    } finally {
        detailLoading.value = false
    }
}

async function handleResolve(resolveRequest) {
    if (!canEdit.value || !activeIssue.value) {
        return
    }
    const issueSnapshot = activeIssue.value
    try {
        await resolveIssue(issueSnapshot.id, resolveRequest)
        ElMessage.success('处置成功')
        drawerVisible.value = false
        activeIssue.value = null
        activeIssueDetail.value = null
        await refreshPage()
    } catch (error) {
        ElMessage.error(error.message || '处置失败')
        activeIssue.value = issueSnapshot
        drawerVisible.value = true
    }
}

function handleOpenMergeWizardFromIssue(issue) {
    mergeWizardIssueId.value = issue?.id || null
    pageTab.value = 'merge-wizard'
    nextTick(async () => {
        try {
            const previewResult = await loadMergePreview({ issueId: issue.id })
            mergeWizardRef.value?.applyPreview(previewResult)
        } catch (error) {
            ElMessage.error(error.message || '生成预览失败')
        }
    })
}

async function handleMergePreview(previewOptions) {
    try {
        const previewResult = await loadMergePreview(previewOptions)
        mergeWizardRef.value?.applyPreview(previewResult)
    } catch (error) {
        ElMessage.error(error.message || '生成预览失败')
    }
}

async function handleMergeConfirm(confirmRequest) {
    try {
        const newKnowledgeId = await confirmMerge(confirmRequest)
        ElMessage.success('合并优化完成')
        mergeWizardRef.value?.markCompleted(newKnowledgeId)
        mergeWizardIssueId.value = null
        await refreshPage()
    } catch (error) {
        ElMessage.error(error.message || '合并失败')
    }
}

function handleMergeCompleted(newKnowledgeId) {
    if (newKnowledgeId) {
        router.push(`/experience/${newKnowledgeId}`)
    }
}

onMounted(async () => {
    try {
        await refreshPage()
    } catch (error) {
        ElMessage.error(error.message || '加载失败')
    }
})

useWorkspaceChange(async () => {
    drawerVisible.value = false
    activeIssue.value = null
    activeIssueDetail.value = null
    mergeWizardIssueId.value = null
    pageNum.value = 1
    try {
        await refreshPage()
    } catch (error) {
        ElMessage.error(error.message || '加载失败')
    }
})
</script>

<style scoped>
.governance-page {
    padding: 20px 24px 32px;
}

.governance-page__header {
    margin-bottom: 16px;
}

.governance-page__header-main {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
}

.governance-page__header-text {
    min-width: 0;
}

.governance-page__title {
    margin: 0;
    font-size: 26px;
    font-weight: 700;
    color: #111827;
    letter-spacing: -0.02em;
}

.governance-page__subtitle {
    margin: 6px 0 0;
    font-size: 14px;
    color: #6b7280;
    line-height: 1.6;
}

.governance-page__alert {
    display: flex;
    gap: 12px;
    margin-top: 16px;
    padding: 14px 16px;
    border-radius: 12px;
}

.governance-page__alert--info {
    background: linear-gradient(145deg, #f9fafb 0%, #fff 100%);
    border: 1px dashed #d1d5db;
}

.governance-page__alert-icon {
    flex-shrink: 0;
    margin-top: 2px;
    color: #5d65f9;
}

.governance-page__alert-title {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
    color: #111827;
}

.governance-page__alert-desc {
    margin: 4px 0 0;
    font-size: 13px;
    color: #6b7280;
}

.governance-page__tabs {
    display: inline-flex;
    flex-shrink: 0;
    gap: 4px;
    padding: 3px;
    border-radius: 10px;
    background: #f3f4f6;
    border: 1px solid #e5e7eb;
}

.governance-page__tab {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 8px 16px;
    border: none;
    border-radius: 8px;
    font-size: 14px;
    font-weight: 500;
    color: #6b7280;
    background: transparent;
    cursor: pointer;
    transition: all 0.15s ease;
}

.governance-page__tab:hover {
    color: #374151;
}

.governance-page__tab--active {
    color: #111827;
    background: #fff;
    box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
}

.governance-page__overview {
    margin-bottom: 14px;
    padding: 12px 14px;
    border-radius: 12px;
    border: 1px solid #e8eaef;
    background: #fff;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
}

.governance-page__overview :deep(.governance-scan-panel) {
    margin-top: 10px;
    padding-top: 10px;
    border-top: 1px solid #f3f4f6;
}

.governance-page__toolbar {
    margin-bottom: 12px;
    padding: 12px 14px;
    border-radius: 12px;
    border: 1px solid #e8eaef;
    background: #fff;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
}

.governance-page__toolbar-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 10px;
}

.governance-page__toolbar-row + .governance-page__toolbar-row {
    margin-top: 14px;
    padding-top: 14px;
    border-top: 1px solid #f3f4f6;
}

.governance-page__toolbar-label {
    flex-shrink: 0;
    font-size: 12px;
    font-weight: 600;
    color: #9ca3af;
    letter-spacing: 0.04em;
    text-transform: uppercase;
}

.governance-page__category-group {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
}

.governance-page__category-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 12px;
    border-radius: 999px;
    border: 1px solid #e5e7eb;
    font-size: 13px;
    color: #4b5563;
    background: #f9fafb;
    cursor: pointer;
    transition: all 0.15s ease;
}

.governance-page__category-btn:hover {
    border-color: #d1d5db;
    background: #fff;
}

.governance-page__category-btn--active {
    font-weight: 600;
    color: #111827;
    background: #fff;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}

.governance-page__category-btn--active.governance-page__category-btn--amber {
    border-color: #fde68a;
    color: #b45309;
    background: #fffbeb;
}

.governance-page__category-btn--active.governance-page__category-btn--indigo {
    border-color: #c7d2fe;
    color: #4338ca;
    background: #eef2ff;
}

.governance-page__category-btn--active.governance-page__category-btn--rose {
    border-color: #fecdd3;
    color: #be123c;
    background: #fff1f2;
}

.governance-page__category-btn--active.governance-page__category-btn--slate {
    border-color: #cbd5e1;
    color: #334155;
    background: #f8fafc;
}

.governance-page__category-btn--active.governance-page__category-btn--red {
    border-color: #fecaca;
    color: #b91c1c;
    background: #fef2f2;
}

.governance-page__category-count {
    min-width: 18px;
    padding: 0 5px;
    border-radius: 999px;
    font-size: 11px;
    font-weight: 700;
    text-align: center;
    color: inherit;
    background: rgba(0, 0, 0, 0.06);
}

.governance-page__status-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 14px;
    border: none;
    border-radius: 8px;
    font-size: 13px;
    color: #6b7280;
    background: transparent;
    cursor: pointer;
    transition: all 0.15s ease;
}

.governance-page__status-btn:hover {
    color: #374151;
    background: #f9fafb;
}

.governance-page__status-btn--active {
    font-weight: 600;
    color: #5d65f9;
    background: #eff0fe;
}

.governance-page__status-count {
    font-size: 11px;
    font-weight: 700;
    color: inherit;
    opacity: 0.85;
}

.governance-page__list {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.governance-page__skeleton {
    padding: 20px;
    border-radius: 14px;
    border: 1px solid #e8eaef;
    background: #fff;
}

.governance-page__issue {
    position: relative;
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 18px 20px 18px 24px;
    border-radius: 14px;
    border: 1px solid #e8eaef;
    background: #fff;
    cursor: pointer;
    transition: box-shadow 0.2s ease, transform 0.2s ease, border-color 0.2s ease;
    overflow: hidden;
}

.governance-page__issue:hover {
    border-color: #d8dcfe;
    box-shadow: 0 4px 14px rgba(15, 23, 42, 0.07);
    transform: translateY(-1px);
}

.governance-page__issue-accent {
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 4px;
}

.governance-page__issue--amber .governance-page__issue-accent {
    background: linear-gradient(180deg, #fbbf24, #f59e0b);
}

.governance-page__issue--indigo .governance-page__issue-accent {
    background: linear-gradient(180deg, #818cf8, #6366f1);
}

.governance-page__issue--rose .governance-page__issue-accent {
    background: linear-gradient(180deg, #fb7185, #e11d48);
}

.governance-page__issue--slate .governance-page__issue-accent {
    background: linear-gradient(180deg, #94a3b8, #64748b);
}

.governance-page__issue--red .governance-page__issue-accent {
    background: linear-gradient(180deg, #f87171, #dc2626);
}

.governance-page__issue-body {
    flex: 1;
    min-width: 0;
}

.governance-page__issue-top {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 6px;
}

.governance-page__issue-badge {
    display: inline-flex;
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 11px;
    font-weight: 600;
}

.governance-page__issue-badge--amber {
    color: #b45309;
    background: #fffbeb;
}

.governance-page__issue-badge--indigo {
    color: #4338ca;
    background: #eef2ff;
}

.governance-page__issue-badge--rose {
    color: #be123c;
    background: #fff1f2;
}

.governance-page__issue-badge--slate {
    color: #334155;
    background: #f8fafc;
}

.governance-page__issue-badge--red {
    color: #b91c1c;
    background: #fef2f2;
}

.governance-page__issue-id {
    font-size: 11px;
    color: #9ca3af;
}

.governance-page__issue-title {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: #111827;
    line-height: 1.4;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.governance-page__issue-meta {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
    margin-top: 6px;
    font-size: 12px;
    color: #6b7280;
}

.governance-page__issue-similarity {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: 10px;
    max-width: 280px;
}

.governance-page__issue-similarity-bar {
    flex: 1;
    height: 4px;
    border-radius: 999px;
    background: #eef0f3;
    overflow: hidden;
}

.governance-page__issue-similarity-fill {
    height: 100%;
    border-radius: inherit;
    background: linear-gradient(90deg, #5d65f9, #818cf8);
}

.governance-page__issue-similarity-label {
    flex-shrink: 0;
    font-size: 11px;
    font-weight: 600;
    color: #5d65f9;
    font-variant-numeric: tabular-nums;
}

.governance-page__issue-arrow {
    flex-shrink: 0;
    color: #d1d5db;
    transition: color 0.15s ease, transform 0.15s ease;
}

.governance-page__issue:hover .governance-page__issue-arrow {
    color: #5d65f9;
    transform: translateX(2px);
}

.governance-page__pagination {
    display: flex;
    justify-content: center;
    margin-top: 32px;
}
</style>
