<template>
    <div class="search-page max-w-layout mx-auto">
        <!-- 页头 -->
        <header class="search-page__header">
            <div class="search-page__header-main">
                <div>
                    <h1 class="search-page__title">经验搜索</h1>
                    <p class="search-page__subtitle">
                        基于任务上下文的语义检索，Task Match 优先于泛化相似度
                    </p>
                    <div class="search-page__tags">
                        <span class="search-page__tag">Rule</span>
                        <span class="search-page__tag">Workflow</span>
                        <span class="search-page__tag">Experience</span>
                        <span class="search-page__tag search-page__tag--muted">默认召回</span>
                    </div>
                </div>
            </div>
        </header>

        <!-- 搜索表单 -->
        <section class="search-page__form">
            <div class="search-page__form-head">
                <h2 class="search-page__form-title">搜索条件</h2>
                <p class="search-page__form-desc">与 Agent memory_recall 使用同一套 Recall 引擎</p>
            </div>

            <el-form label-position="top" @submit.prevent="handleSearch">
                <!-- 任务描述：主输入，不再额外套卡片 -->
                <el-form-item label="任务描述" required class="search-page__task-item">
                    <el-input
                        v-model="searchForm.task"
                        type="textarea"
                        :rows="4"
                        placeholder="例如：payment 模块新增 Redis 缓存字段，需要遵循库表规范"
                        maxlength="2000"
                        show-word-limit
                        @keyup.enter.ctrl="handleSearch"
                    />
                </el-form-item>
                <p class="search-page__task-hint">
                    <el-icon :size="14"><InfoFilled /></el-icon>
                    描述你要做的任务，系统会结合仓库、模块等上下文进行语义召回
                    <span class="search-page__shortcut">Ctrl + Enter 搜索</span>
                </p>

                <!-- 更多条件：默认折叠，仅用分隔线与间距区分 -->
                <div class="search-page__advanced">
                    <button
                        type="button"
                        class="search-page__advanced-toggle"
                        @click="advancedExpanded = !advancedExpanded"
                    >
                        <span class="search-page__advanced-toggle-title">更多搜索条件</span>
                        <span class="search-page__advanced-toggle-hint">{{ advancedOptionsSummary }}</span>
                        <el-icon
                            class="search-page__advanced-toggle-icon"
                            :class="{ 'search-page__advanced-toggle-icon--expanded': advancedExpanded }"
                            :size="14"
                        >
                            <ArrowRight />
                        </el-icon>
                    </button>

                    <el-collapse-transition>
                        <div v-show="advancedExpanded" class="search-page__advanced-body">
                            <h3 class="search-page__section-title">上下文</h3>
                            <div class="search-page__context-grid">
                                <RecallContextFields
                                    v-model:project="searchForm.project"
                                    v-model:module="searchForm.module"
                                    v-model:repository="searchForm.repository"
                                    layout="grid"
                                    size="small"
                                />
                                <el-form-item label="当前文件" class="search-page__field search-page__field--inline">
                                    <el-input
                                        v-model="searchForm.currentFile"
                                        size="small"
                                        placeholder="可选，例如 src/.../CaptureService.java"
                                        clearable
                                    />
                                </el-form-item>
                            </div>

                            <h3 class="search-page__section-title search-page__section-title--filter">筛选</h3>
                            <div class="search-page__filter-compact">
                                <div class="search-page__filter-row">
                                    <el-form-item
                                        label="知识类型"
                                        class="search-page__field search-page__field--inline search-page__field--grow"
                                    >
                                        <el-checkbox-group v-model="searchForm.knowledgeTypes">
                                            <el-checkbox
                                                v-for="optionItem in SEARCH_KNOWLEDGE_TYPE_OPTIONS"
                                                :key="optionItem.value"
                                                :value="optionItem.value"
                                            >
                                                {{ optionItem.label }}
                                            </el-checkbox>
                                        </el-checkbox-group>
                                    </el-form-item>
                                    <el-form-item
                                        label="返回条数"
                                        class="search-page__field search-page__field--inline search-page__field--limit"
                                    >
                                        <el-select
                                            v-model="searchForm.limit"
                                            size="small"
                                            class="search-page__limit-select"
                                        >
                                            <el-option
                                                v-for="limitValue in SEARCH_LIMIT_OPTIONS"
                                                :key="limitValue"
                                                :label="`${limitValue} 条`"
                                                :value="limitValue"
                                            />
                                        </el-select>
                                    </el-form-item>
                                </div>
                                <el-form-item
                                    label="Fact 类型"
                                    class="search-page__field search-page__field--inline"
                                >
                                    <div class="search-page__fact-row">
                                        <el-checkbox-group v-model="searchForm.factTypes">
                                            <el-checkbox
                                                v-for="optionItem in SEARCH_FACT_TYPE_OPTIONS"
                                                :key="optionItem.value"
                                                :value="optionItem.value"
                                            >
                                                {{ FACT_TYPE_LABELS[optionItem.value] || optionItem.label }}
                                            </el-checkbox>
                                        </el-checkbox-group>
                                        <span class="search-page__fact-hint">可选，不选则返回全部</span>
                                    </div>
                                </el-form-item>
                            </div>
                        </div>
                    </el-collapse-transition>
                </div>

                <!-- 操作按钮 -->
                <div class="search-page__actions">
                    <el-button plain @click="handleReset">清空</el-button>
                    <el-button type="primary" :loading="loading" @click="handleSearch">
                        <el-icon class="mr-1"><Search /></el-icon>
                        搜索
                    </el-button>
                </div>
            </el-form>
        </section>

        <!-- 错误提示 -->
        <div v-if="errorMessage" class="search-page__alert search-page__alert--error">
            <el-icon class="search-page__alert-icon" :size="18"><CircleCloseFilled /></el-icon>
            <p class="search-page__alert-text">{{ errorMessage }}</p>
        </div>

        <!-- 加载中 -->
        <div v-if="loading" class="search-page__loading">
            <div
                v-for="index in 3"
                :key="index"
                class="search-page__skeleton"
            >
                <el-skeleton :rows="4" />
            </div>
        </div>

        <!-- 搜索结果 -->
        <template v-else-if="searched">
            <div class="search-page__summary">
                <div class="search-page__summary-main">
                    <span class="search-page__summary-count">{{ resultItems.length }}</span>
                    <span class="search-page__summary-label">条匹配结果</span>
                </div>
                <span v-if="sessionId" class="search-page__session">Session {{ sessionId }}</span>
            </div>

            <PageEmptyState
                v-if="resultItems.length === 0"
                title="未找到匹配的知识"
                subtitle="尝试补充仓库、模块等上下文，或放宽知识类型筛选；也可先在 Capture 确认发布相关经验或 Rule"
                :tags="['Task Match', 'Rule 加权', '模块筛选', 'promptBlock']"
                compact
            >
                <el-button type="primary" plain @click="router.push('/capture')">
                    前往草稿确认
                </el-button>
            </PageEmptyState>

            <div v-else class="search-page__results">
                <SearchResultCard
                    v-for="recallItem in resultItems"
                    :key="`${recallItem.rank}-${recallItem.knowledgeId}`"
                    :recall-item="recallItem"
                />
            </div>

            <!-- promptBlock 预览 -->
            <section v-if="promptBlock" class="search-page__prompt">
                <div class="search-page__prompt-head">
                    <div>
                        <h2 class="search-page__prompt-title">promptBlock 预览</h2>
                        <p class="search-page__prompt-desc">
                            Agent memory_recall 将注入的文本块，共 {{ promptBlock.length }} 字符
                        </p>
                    </div>
                    <el-button size="small" type="primary" plain @click="copyPromptBlock">
                        <el-icon class="mr-1"><CopyDocument /></el-icon>
                        复制
                    </el-button>
                </div>
                <pre class="search-page__prompt-code">{{ promptBlock }}</pre>
            </section>
        </template>

        <!-- 初始引导 -->
        <PageEmptyState
            v-else
            :flow-steps="['描述任务', '补充上下文', '查看召回']"
            title="填写任务描述后开始搜索"
            subtitle="Task Match 优先，Rule 类型有额外加权；搜索结果与 Agent 侧 memory_recall 一致"
            :tags="['任务描述', '仓库模块', '知识类型', 'promptBlock']"
            guide="在上方表单填写当前开发任务，补充仓库与模块后点击「开始搜索」"
        />
    </div>
</template>

<script setup>
import {
    ArrowRight,
    CircleCloseFilled,
    CopyDocument,
    InfoFilled,
    Search,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
    SEARCH_DEFAULT_KNOWLEDGE_TYPES,
    SEARCH_DEFAULT_LIMIT,
    SEARCH_FACT_TYPE_OPTIONS,
    SEARCH_KNOWLEDGE_TYPE_OPTIONS,
    SEARCH_LIMIT_OPTIONS,
} from '~/constants/memorySearch'
import { FACT_TYPE_LABELS } from '~/constants/knowledge'
import { SEARCH_TRACK_EVENTS } from '~/config/tracker'

definePageMeta({
    layout: 'app',
})

useHead({ title: '经验搜索' })

/** 页面浏览埋点 */
usePageTracker()

const router = useRouter()
const route = useRoute()
const { reportEvent } = useTracker()

const {
    loading,
    searched,
    errorMessage,
    sessionId,
    promptBlock,
    resultItems,
    searchForm,
    applyQueryToForm,
    buildRouteQuery,
    executeSearch,
    resetSearch,
} = useMemorySearch({
    initialQuery: () => route.query,
})

/** 更多条件区域是否展开，默认折叠；含非默认筛选项时自动展开 */
const advancedExpanded = ref(false)

/**
 * 判断知识类型是否与 Beta 默认召回一致
 */
function isDefaultKnowledgeTypes(knowledgeTypes) {
    if (knowledgeTypes.length !== SEARCH_DEFAULT_KNOWLEDGE_TYPES.length) {
        return false
    }
    const defaultTypeSet = new Set(SEARCH_DEFAULT_KNOWLEDGE_TYPES)
    return knowledgeTypes.every((typeItem) => defaultTypeSet.has(typeItem))
}

/**
 * 表单是否使用了非默认的可选条件（上下文、Fact 类型、条数、知识类型）
 */
function hasNonDefaultAdvancedOptions() {
    return Boolean(
        searchForm.repository.trim()
        || searchForm.module.trim()
        || searchForm.project.trim()
        || searchForm.currentFile.trim()
        || searchForm.factTypes.length > 0
        || searchForm.limit !== SEARCH_DEFAULT_LIMIT
        || !isDefaultKnowledgeTypes(searchForm.knowledgeTypes)
    )
}

/** 折叠态摘要：无自定义时提示默认行为，有自定义时展示已设条件 */
const advancedOptionsSummary = computed(() => {
    const summaryParts = []

    if (searchForm.repository.trim()) {
        summaryParts.push(`仓库 ${searchForm.repository.trim()}`)
    }
    if (searchForm.module.trim()) {
        summaryParts.push(`模块 ${searchForm.module.trim()}`)
    }
    if (searchForm.project.trim()) {
        summaryParts.push(`项目 ${searchForm.project.trim()}`)
    }
    if (searchForm.currentFile.trim()) {
        summaryParts.push('已指定当前文件')
    }
    if (!isDefaultKnowledgeTypes(searchForm.knowledgeTypes)) {
        summaryParts.push(`知识类型 ${searchForm.knowledgeTypes.length} 项`)
    }
    if (searchForm.factTypes.length > 0) {
        summaryParts.push(`Fact 类型 ${searchForm.factTypes.length} 项`)
    }
    if (searchForm.limit !== SEARCH_DEFAULT_LIMIT) {
        summaryParts.push(`${searchForm.limit} 条`)
    }

    if (summaryParts.length === 0) {
        return '可选 · 默认召回 Rule / Workflow / Decision / Experience，返回 10 条'
    }
    return `已设置：${summaryParts.join(' · ')}`
})

/** 执行搜索并同步 URL */
async function handleSearch() {
    if (!searchForm.task.trim()) {
        ElMessage.warning('请填写任务描述')
        return
    }
    if (searchForm.knowledgeTypes.length === 0) {
        ElMessage.warning('请至少选择一种知识类型')
        return
    }

    reportEvent(SEARCH_TRACK_EVENTS.SEARCH, {
        taskLength: searchForm.task.trim().length,
        knowledgeTypeCount: searchForm.knowledgeTypes.length,
        limit: searchForm.limit,
    })

    try {
        await executeSearch()
        await router.replace({ query: buildRouteQuery() })
    } catch (error) {
        if (!errorMessage.value) {
            ElMessage.error(error.message || '搜索失败')
        }
    }
}

/** 清空表单与结果 */
function handleReset() {
    resetSearch()
    advancedExpanded.value = false
    router.replace({ query: {} })
}

/** 复制 promptBlock 到剪贴板 */
async function copyPromptBlock() {
    if (!promptBlock.value) {
        return
    }
    try {
        await navigator.clipboard.writeText(promptBlock.value)
        ElMessage.success('已复制 promptBlock')
    } catch {
        ElMessage.error('复制失败，请手动选择文本')
    }
}

/** 页面加载时若 URL 带 task 则自动搜索 */
onMounted(async () => {
    applyQueryToForm(route.query)
    if (hasNonDefaultAdvancedOptions()) {
        advancedExpanded.value = true
    }
    if (searchForm.task.trim()) {
        try {
            await executeSearch()
        } catch {
            // 错误已在 composable 中写入 errorMessage
        }
    }
})

/** 切换工作空间后清空结果，避免串数据 */
useWorkspaceChange(() => {
    resetSearch()
    advancedExpanded.value = false
    router.replace({ query: {} })
})
</script>

<style scoped>
.search-page {
    padding: 28px 24px 40px;
}

/* 页头 */
.search-page__header {
    margin-bottom: 24px;
}

.search-page__header-main {
    display: flex;
    align-items: flex-start;
    gap: 16px;
}

.search-page__title {
    margin: 0;
    font-size: 26px;
    font-weight: 700;
    color: #111827;
    letter-spacing: -0.02em;
}

.search-page__subtitle {
    margin: 6px 0 0;
    font-size: 14px;
    color: #6b7280;
    line-height: 1.6;
    max-width: 640px;
}

.search-page__tags {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
    margin-top: 12px;
}

.search-page__tag {
    padding: 3px 10px;
    border-radius: 999px;
    font-size: 11px;
    font-weight: 500;
    color: #5d65f9;
    background: #eff0fe;
    border: 1px solid #ced1fd;
}

.search-page__tag--muted {
    color: #9ca3af;
    background: #f9fafb;
    border-color: #e5e7eb;
}

/* 搜索表单 */
.search-page__form {
    background: #fff;
    border-radius: 14px;
    border: 1px solid #e8eaef;
    box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
    padding: 22px 24px;
    margin-bottom: 20px;
}

.search-page__form-head {
    margin-bottom: 16px;
}

.search-page__form-title {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: #111827;
}

.search-page__form-desc {
    margin: 4px 0 0;
    font-size: 12px;
    color: #9ca3af;
}

.search-page__task-item {
    margin-bottom: 6px !important;
}

.search-page__task-item :deep(.el-form-item__label) {
    font-weight: 600;
    color: #374151;
}

.search-page__task-hint {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
    margin: 0 0 16px;
    font-size: 12px;
    color: #9ca3af;
    line-height: 1.5;
}

.search-page__shortcut {
    margin-left: auto;
    font-size: 11px;
    color: #9ca3af;
}

/* 更多条件：扁平折叠，无额外卡片 */
.search-page__advanced {
    padding-top: 16px;
    border-top: 1px solid #f3f4f6;
}

.search-page__advanced-toggle {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    padding: 0;
    border: none;
    background: transparent;
    cursor: pointer;
    text-align: left;
}

.search-page__advanced-toggle:hover .search-page__advanced-toggle-title {
    color: #5d65f9;
}

.search-page__advanced-toggle-title {
    flex-shrink: 0;
    font-size: 13px;
    font-weight: 600;
    color: #374151;
    transition: color 0.15s ease;
}

.search-page__advanced-toggle-hint {
    flex: 1;
    min-width: 0;
    font-size: 12px;
    color: #9ca3af;
    line-height: 1.5;
}

.search-page__advanced-toggle-icon {
    flex-shrink: 0;
    color: #9ca3af;
    transition: transform 0.2s ease;
}

.search-page__advanced-toggle-icon--expanded {
    transform: rotate(90deg);
}

.search-page__advanced-body {
    padding-top: 16px;
}

.search-page__section-title {
    margin: 0 0 10px;
    font-size: 12px;
    font-weight: 600;
    color: #6b7280;
}

.search-page__section-title--filter {
    margin-top: 20px;
}

.search-page__advanced-body :deep(.search-page__field--inline) {
    margin-bottom: 0 !important;
    display: flex;
    align-items: flex-start;
}

.search-page__advanced-body :deep(.search-page__field--inline .el-form-item__label) {
    width: 72px;
    flex-shrink: 0;
    margin-bottom: 0 !important;
    padding-right: 8px;
    line-height: 28px;
    font-size: 13px;
    color: #6b7280;
}

.search-page__advanced-body :deep(.search-page__field--inline .el-form-item__content) {
    flex: 1;
    min-width: 0;
}

.search-page__context-grid {
    display: grid;
    grid-template-columns: 1fr;
    gap: 8px 16px;
}

.search-page__context-grid :deep(.recall-context-fields--grid) {
    display: contents;
}

.search-page__context-grid :deep(.recall-context-fields__item) {
    margin-bottom: 0 !important;
    display: flex;
    align-items: center;
}

.search-page__context-grid :deep(.recall-context-fields__item .el-form-item__label) {
    width: 72px;
    flex-shrink: 0;
    margin-bottom: 0 !important;
    padding-right: 8px;
    line-height: 28px;
    font-size: 13px;
    color: #6b7280;
}

@media (min-width: 768px) {
    .search-page__context-grid {
        grid-template-columns: repeat(2, 1fr);
    }
}

.search-page__field {
    margin-bottom: 0 !important;
}

.search-page__filter-compact {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.search-page__filter-row {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;
    gap: 8px 16px;
}

.search-page__field--grow {
    flex: 1;
    min-width: 240px;
}

.search-page__field--limit {
    flex-shrink: 0;
    width: auto;
}

.search-page__field--limit :deep(.el-form-item__content) {
    flex: none;
    width: 100px;
}

.search-page__limit-select {
    width: 100%;
}

.search-page__fact-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 12px;
    width: 100%;
}

.search-page__fact-row :deep(.el-checkbox-group) {
    display: flex;
    flex-wrap: wrap;
    gap: 2px 12px;
}

.search-page__advanced-body :deep(.el-checkbox-group) {
    display: flex;
    flex-wrap: wrap;
    gap: 2px 12px;
}

.search-page__advanced-body :deep(.el-checkbox) {
    height: 26px;
    margin-right: 0;
}

.search-page__advanced-body :deep(.el-checkbox__label) {
    font-size: 13px;
    padding-left: 6px;
}

.search-page__fact-hint {
    flex-shrink: 0;
    font-size: 11px;
    color: #9ca3af;
    white-space: nowrap;
}

.search-page__actions {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    margin-top: 20px;
    padding-top: 16px;
    border-top: 1px solid #f3f4f6;
}

/* 错误提示 */
.search-page__alert {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    margin-bottom: 20px;
    padding: 14px 16px;
    border-radius: 12px;
}

.search-page__alert--error {
    background: linear-gradient(145deg, #fef2f2 0%, #fff 100%);
    border: 1px solid #fecaca;
}

.search-page__alert-icon {
    flex-shrink: 0;
    margin-top: 1px;
    color: #ef4444;
}

.search-page__alert-text {
    margin: 0;
    font-size: 13px;
    color: #991b1b;
    line-height: 1.5;
}

/* 加载骨架 */
.search-page__loading {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.search-page__skeleton {
    padding: 20px 24px;
    background: #fff;
    border-radius: 14px;
    border: 1px solid #e8eaef;
}

/* 结果摘要 */
.search-page__summary {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 16px;
    padding: 12px 16px;
    border-radius: 10px;
    background: #f9fafb;
    border: 1px solid #f3f4f6;
}

.search-page__summary-main {
    display: flex;
    align-items: baseline;
    gap: 6px;
}

.search-page__summary-count {
    font-size: 22px;
    font-weight: 700;
    color: #5d65f9;
    font-variant-numeric: tabular-nums;
}

.search-page__summary-label {
    font-size: 13px;
    color: #6b7280;
}

.search-page__session {
    font-size: 11px;
    color: #9ca3af;
    font-family: ui-monospace, monospace;
}

/* 结果列表 */
.search-page__results {
    display: flex;
    flex-direction: column;
    gap: 12px;
    margin-bottom: 20px;
}

/* promptBlock 预览 */
.search-page__prompt {
    background: #fff;
    border-radius: 14px;
    border: 1px solid #e8eaef;
    box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
    padding: 20px 22px;
}

.search-page__prompt-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 14px;
}

.search-page__prompt-title {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: #111827;
}

.search-page__prompt-desc {
    margin: 4px 0 0;
    font-size: 12px;
    color: #9ca3af;
}

.search-page__prompt-code {
    margin: 0;
    padding: 16px 18px;
    border-radius: 10px;
    background: #1e1e2e;
    border: 1px solid #2d2d3d;
    font-size: 12px;
    line-height: 1.65;
    color: #cdd6f4;
    font-family: ui-monospace, 'SF Mono', Menlo, monospace;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 360px;
    overflow: auto;
}
</style>
