<template>
    <div class="memory-page max-w-layout mx-auto px-6 py-8">
        <div class="memory-page__header mb-8">
            <div class="flex flex-col lg:flex-row lg:items-start lg:justify-between gap-6">
                <div>
                    <h1 class="text-2xl font-semibold text-gray-900 tracking-tight">记忆中心</h1>
                    <p class="text-gray-500 mt-1.5 text-sm leading-relaxed">
                        统一管理经验、规则、流程与决策，供人和 Agent 持续召回复用
                    </p>
                    <p v-if="!loading && total > 0" class="text-xs text-gray-400 mt-2">
                        共 {{ total }} 条{{ listCountLabel }}
                        <span v-if="keyword"> · 搜索「{{ keyword }}」</span>
                    </p>
                </div>

                <KnowledgeListToolbar
                    v-model:keyword="keyword"
                    class="shrink-0"
                    :can-edit="canEdit"
                    :creating="creating"
                    :create-label="createButtonLabel"
                    :show-create="showSingleCreateButton"
                    @create="handleCreate"
                    @search="handleSearch"
                >
                    <template #actions-start>
                        <el-select
                            v-model="typeFilter"
                            class="memory-page__type-select"
                            placeholder="类型"
                            @change="handleTypeChange"
                        >
                            <el-option
                                v-for="option in MEMORY_TYPE_FILTER_OPTIONS"
                                :key="option.value"
                                :label="option.label"
                                :value="option.value"
                            />
                        </el-select>
                        <el-dropdown
                            v-if="canEdit && !showSingleCreateButton"
                            trigger="click"
                            @command="handleCreateByType"
                        >
                            <el-button type="primary" :loading="creating">
                                <el-icon class="mr-1"><Plus /></el-icon>
                                新建记忆
                                <el-icon class="ml-1"><ArrowDown /></el-icon>
                            </el-button>
                            <template #dropdown>
                                <el-dropdown-menu>
                                    <el-dropdown-item
                                        v-for="option in createTypeOptions"
                                        :key="option.value"
                                        :command="option.value"
                                    >
                                        {{ option.label }}
                                    </el-dropdown-item>
                                </el-dropdown-menu>
                            </template>
                        </el-dropdown>
                    </template>
                    <template #actions-end>
                        <el-button @click="router.push('/capture')">
                            <el-icon class="mr-1"><DocumentChecked /></el-icon>
                            草稿确认
                            <el-badge
                                v-if="pendingDraftCount > 0"
                                :value="pendingDraftCount"
                                :max="99"
                                class="ml-2"
                            />
                        </el-button>
                        <el-button :loading="exporting" @click="handleExport">
                            <el-icon class="mr-1"><Download /></el-icon>导出 Markdown
                        </el-button>
                    </template>
                </KnowledgeListToolbar>
            </div>
        </div>

        <el-tabs
            v-if="showDeprecatedTab"
            v-model="activeTab"
            class="mb-6"
            @tab-change="handleTabChange"
        >
            <el-tab-pane label="已发布" name="published" />
            <el-tab-pane label="已失效" name="deprecated" />
        </el-tabs>

        <div v-if="loading" class="space-y-3">
            <div
                v-for="index in 4"
                :key="index"
                class="bg-white rounded-xl border border-gray-200 p-5"
            >
                <el-skeleton :rows="3" />
            </div>
        </div>

        <PageEmptyState
            v-else-if="memoryList.length === 0"
            :flow-steps="memoryFlywheelSteps"
            :title="emptyTitle"
            :subtitle="emptySubtitle"
            :tags="emptyTags"
            :guide="emptyGuide"
        >
            <template v-if="activeTab === 'published'">
                <el-button @click="router.push('/capture')">
                    <el-icon class="mr-1"><DocumentChecked /></el-icon>
                    前往草稿确认
                </el-button>
                <el-button
                    v-if="canEdit && showSingleCreateButton"
                    type="primary"
                    @click="handleCreate"
                >
                    {{ createButtonLabel }}
                </el-button>
                <el-dropdown
                    v-else-if="canEdit"
                    trigger="click"
                    @command="handleCreateByType"
                >
                    <el-button type="primary">
                        新建记忆
                        <el-icon class="ml-1"><ArrowDown /></el-icon>
                    </el-button>
                    <template #dropdown>
                        <el-dropdown-menu>
                            <el-dropdown-item
                                v-for="option in createTypeOptions"
                                :key="option.value"
                                :command="option.value"
                            >
                                {{ option.label }}
                            </el-dropdown-item>
                        </el-dropdown-menu>
                    </template>
                </el-dropdown>
            </template>
        </PageEmptyState>

        <div v-else class="space-y-3">
            <KnowledgeCard
                v-for="item in memoryList"
                :key="item.id"
                :knowledge="item"
                :show-type-badge="true"
                :show-lifecycle-badge="activeTab === 'deprecated'"
            />
        </div>

        <div v-if="total > pageSize" class="flex justify-center mt-10">
            <el-pagination
                background
                layout="total, prev, pager, next"
                :total="total"
                :page-size="pageSize"
                :current-page="pageNum"
                @current-change="handlePageChange"
            />
        </div>
    </div>
</template>

<script setup>
import { ArrowDown, DocumentChecked, Download, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { MEMORY_TRACK_EVENTS } from '~/config/tracker'
import { MEMORY_FLYWHEEL_STEPS } from '~/constants/pageEmptyState'
import {
    KNOWLEDGE_CREATE_LABELS,
    KNOWLEDGE_TYPE_LABELS,
    KNOWLEDGE_TYPES,
    MEMORY_TYPE_FILTER_ALL,
    MEMORY_TYPE_FILTER_OPTIONS,
} from '~/constants/knowledge'
import { exportKnowledgeMarkdown } from '~/services/knowledge.service'
import { downloadTextFile } from '~/utils/fileDownload'

definePageMeta({
    layout: 'app',
})

useHead({ title: '记忆中心' })

usePageTracker()

const router = useRouter()
const route = useRoute()
const { reportEvent } = useTracker()
const { pendingDraftCount } = useAppNavigation()
const { canEdit } = useCanEditKnowledge()
const { creating, createDraftAndNavigate } = useKnowledgeCreate()
const { currentWorkspace } = useWorkspace()
const {
    loading,
    keyword,
    pageNum,
    pageSize,
    total,
    memoryList,
    typeFilter,
    activeTab,
    showDeprecatedTab,
    loadList,
    changePage,
    search,
    switchTypeFilter,
    switchTab,
} = useMemoryList()

const memoryFlywheelSteps = MEMORY_FLYWHEEL_STEPS

const exporting = ref(false)

/** 新建类型下拉选项（不含「全部」） */
const createTypeOptions = [
    { value: KNOWLEDGE_TYPES.EXPERIENCE, label: KNOWLEDGE_CREATE_LABELS.experience },
    { value: KNOWLEDGE_TYPES.RULE, label: KNOWLEDGE_CREATE_LABELS.rule },
    { value: KNOWLEDGE_TYPES.WORKFLOW, label: KNOWLEDGE_CREATE_LABELS.workflow },
    { value: KNOWLEDGE_TYPES.DECISION, label: KNOWLEDGE_CREATE_LABELS.decision },
]

/** 选中具体类型时展示单一新建按钮，否则用下拉 */
const showSingleCreateButton = computed(() =>
    typeFilter.value !== MEMORY_TYPE_FILTER_ALL,
)

const createButtonLabel = computed(() => {
    if (typeFilter.value === MEMORY_TYPE_FILTER_ALL) {
        return '新建记忆'
    }
    return KNOWLEDGE_CREATE_LABELS[typeFilter.value] || '新建记忆'
})

const listCountLabel = computed(() => {
    if (typeFilter.value === MEMORY_TYPE_FILTER_ALL) {
        return activeTab.value === 'deprecated' ? '已失效记忆' : '已发布记忆'
    }
    const typeLabel = KNOWLEDGE_TYPE_LABELS[typeFilter.value] || '记忆'
    if (activeTab.value === 'deprecated') {
        return `已失效${typeLabel}`
    }
    return typeLabel
})

const emptyTitle = computed(() => {
    if (activeTab.value === 'deprecated') {
        return '暂无已失效记忆'
    }
    if (typeFilter.value === MEMORY_TYPE_FILTER_ALL) {
        return '把团队知识沉淀成可复用记忆'
    }
    const typeLabel = KNOWLEDGE_TYPE_LABELS[typeFilter.value] || '记忆'
    return `暂无${typeLabel}`
})

const emptySubtitle = computed(() => {
    if (activeTab.value === 'deprecated') {
        return '下架后的经验会集中在此，可随时查看详情并重新启用恢复 Recall'
    }
    return '从 Agent 自动采集到人工确认发布，形成可被 Recall 复用的结构化记忆'
})

const emptyTags = computed(() => {
    if (activeTab.value === 'deprecated') {
        return ['已失效', '重新启用', 'Recall 恢复', '历史追溯']
    }
    if (typeFilter.value === KNOWLEDGE_TYPES.RULE || typeFilter.value === KNOWLEDGE_TYPES.WORKFLOW) {
        return ['规则 (Rule)', '流程 (Workflow)', 'Agent Recall', '规范约束']
    }
    if (typeFilter.value === KNOWLEDGE_TYPES.DECISION) {
        return ['背景', '备选方案', '决策理由', '影响范围']
    }
    return ['Fact Blocks', '模块标签', 'Recall 召回', 'Feedback']
})

const emptyGuide = computed(() => {
    if (activeTab.value === 'deprecated') {
        return '在记忆详情页点击「重新启用」，即可恢复为已发布并重新进入 Agent 召回'
    }
    return 'Agent 草稿请在草稿确认页审核发布；人工撰写可点击「新建记忆」'
})

function syncRouteQuery() {
    const query = { ...route.query }
    if (typeFilter.value === MEMORY_TYPE_FILTER_ALL) {
        delete query.type
    } else {
        query.type = typeFilter.value
    }
    if (showDeprecatedTab.value && activeTab.value === 'deprecated') {
        query.tab = 'deprecated'
    } else {
        delete query.tab
    }
    router.replace({ query })
}

function handleSearch() {
    search().catch((error) => ElMessage.error(error.message || '搜索失败'))
}

function handlePageChange(page) {
    changePage(page).catch((error) => ElMessage.error(error.message || '加载失败'))
}

function handleTypeChange() {
    syncRouteQuery()
    switchTypeFilter(typeFilter.value).catch((error) => ElMessage.error(error.message || '加载失败'))
}

function handleTabChange() {
    syncRouteQuery()
    switchTab(activeTab.value).catch((error) => ElMessage.error(error.message || '加载失败'))
}

function handleCreateByType(knowledgeType) {
    reportEvent(MEMORY_TRACK_EVENTS.CREATE, {
        knowledgeType,
        from: 'memory-list',
    })
    createDraftAndNavigate(router, knowledgeType)
}

function handleCreate() {
    const knowledgeType = typeFilter.value === MEMORY_TYPE_FILTER_ALL
        ? KNOWLEDGE_TYPES.EXPERIENCE
        : typeFilter.value
    handleCreateByType(knowledgeType)
}

async function handleExport() {
  try {
    exporting.value = true
    const text = await exportKnowledgeMarkdown(typeFilter.value || 'all')
    const wsName = currentWorkspace.value?.workspaceName || '工作空间'
    const date = new Date().toISOString().slice(0, 10).replace(/-/g, '')
    downloadTextFile(`${wsName}-知识导出-${date}.md`, text)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error(e?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
    const queryType = route.query.type
    const queryTab = route.query.tab

    if (queryTab === 'draft') {
        await router.replace({ query: { ...route.query, tab: undefined } })
        await router.push('/capture')
        return
    }

    if (
        typeof queryType === 'string'
        && MEMORY_TYPE_FILTER_OPTIONS.some((option) => option.value === queryType)
    ) {
        typeFilter.value = queryType
    }

    if (queryTab === 'deprecated' || queryTab === 'published') {
        activeTab.value = queryTab
    }

    try {
        await loadList()
    } catch (error) {
        ElMessage.error(error.message || '加载失败')
    }
})

useWorkspaceChange(async () => {
    pageNum.value = 1
    try {
        await loadList({ silent: true })
    } catch (error) {
        ElMessage.error(error.message || '加载失败')
    }
})
</script>

<style scoped>
.memory-page__type-select {
    width: 120px;
}
</style>
