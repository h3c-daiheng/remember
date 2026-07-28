<template>
    <div class="graph-page max-w-layout mx-auto">
        <header class="graph-page__header">
            <div class="graph-page__header-main">
                <div>
                    <h1 class="graph-page__title">经验图谱</h1>
                    <p class="graph-page__subtitle">
                        沿显式关系边浏览经验网络，支持以任意节点为中心扩展
                        <strong class="graph-page__subtitle-em">{{ filters.depth }} 跳</strong>
                        子图
                    </p>
                    <div class="graph-page__header-tags">
                        <span
                            v-for="option in GRAPH_HEADER_RELATION_TAGS"
                            :key="option.value"
                            class="graph-page__header-tag"
                            :style="{ '--tag-color': getRelationEdgeColor(option.value) }"
                        >
                            <span class="graph-page__header-tag-dot" />
                            {{ option.label }}
                        </span>
                    </div>
                </div>
            </div>
        </header>

        <section class="graph-page__toolbar">
            <div class="graph-page__toolbar-head">
                <div>
                    <h2 class="graph-page__toolbar-title">子图配置</h2>
                    <p class="graph-page__toolbar-desc">
                        选择中心经验后，按深度与关系类型扩展邻接网络
                    </p>
                </div>
                <div v-if="filters.centerId" class="graph-page__stats">
                    <div class="graph-page__stat">
                        <span class="graph-page__stat-value">{{ filteredGraph.nodes.length }}</span>
                        <span class="graph-page__stat-label">节点</span>
                    </div>
                    <div class="graph-page__stat-divider" aria-hidden="true" />
                    <div class="graph-page__stat">
                        <span class="graph-page__stat-value">{{ filteredGraph.edges.length }}</span>
                        <span class="graph-page__stat-label">边</span>
                    </div>
                </div>
            </div>

            <el-form label-position="top" class="graph-page__form" @submit.prevent="loadGraph">
                <div class="graph-page__form-primary">
                    <el-form-item label="中心经验" class="graph-page__field graph-page__field--center">
                        <el-select
                            v-model="filters.centerId"
                            filterable
                            remote
                            clearable
                            reserve-keyword
                            placeholder="搜索并选择中心节点"
                            :remote-method="searchCenterCandidates"
                            :loading="centerSearchLoading"
                            @change="handleCenterChange"
                        >
                            <el-option
                                v-for="item in centerOptions"
                                :key="item.id"
                                :label="item.title"
                                :value="item.id"
                            />
                        </el-select>
                    </el-form-item>

                    <el-form-item label="关系类型" class="graph-page__field graph-page__field--types">
                        <el-select
                            v-model="filters.relationTypes"
                            multiple
                            collapse-tags
                            collapse-tags-tooltip
                            clearable
                            placeholder="全部类型"
                            @change="loadGraph"
                        >
                            <el-option
                                v-for="option in RELATION_TYPE_OPTIONS"
                                :key="option.value"
                                :label="option.label"
                                :value="option.value"
                            />
                        </el-select>
                    </el-form-item>

                    <el-form-item label="扩展深度" class="graph-page__field graph-page__field--depth">
                        <el-select v-model="filters.depth" @change="loadGraph">
                            <el-option :value="1" label="1 跳" />
                            <el-option :value="2" label="2 跳（默认）" />
                            <el-option :value="3" label="3 跳" />
                        </el-select>
                    </el-form-item>

                    <div class="graph-page__field graph-page__field--action">
                        <el-button
                            type="primary"
                            :loading="loading"
                            :disabled="!filters.centerId"
                            @click="loadGraph"
                        >
                            刷新子图
                        </el-button>
                    </div>
                </div>

                <button
                    type="button"
                    class="graph-page__filter-toggle"
                    @click="clientFilterExpanded = !clientFilterExpanded"
                >
                    <span class="graph-page__filter-toggle-title">客户端筛选</span>
                    <span class="graph-page__filter-toggle-hint">模块 / 标签，仅过滤已加载子图</span>
                    <el-icon
                        class="graph-page__filter-toggle-icon"
                        :class="{ 'graph-page__filter-toggle-icon--expanded': clientFilterExpanded }"
                        :size="14"
                    >
                        <ArrowRight />
                    </el-icon>
                </button>

                <el-collapse-transition>
                    <div v-show="clientFilterExpanded" class="graph-page__client-filters">
                        <el-form-item label="模块" class="graph-page__field">
                            <el-input
                                v-model="filters.moduleKeyword"
                                clearable
                                placeholder="按模块名过滤节点"
                            />
                        </el-form-item>
                        <el-form-item label="标签" class="graph-page__field">
                            <el-input
                                v-model="filters.tagKeyword"
                                clearable
                                placeholder="暂按标题关键词匹配"
                            />
                        </el-form-item>
                    </div>
                </el-collapse-transition>
            </el-form>
        </section>

        <div v-if="loading && !graphData" class="graph-page__loading">
            <div class="graph-page__skeleton">
                <el-skeleton :rows="10" animated />
            </div>
        </div>

        <PageEmptyState
            v-else-if="!filters.centerId"
            :flow-steps="GRAPH_EMPTY_FLOW_STEPS"
            title="选择中心经验，开始浏览关系网络"
            subtitle="可从经验详情页跳转，或在此搜索已发布经验作为子图中心"
            :tags="GRAPH_EMPTY_FEATURE_TAGS"
            guide="发布经验后 RelationEngine 会自动建边；也可在详情页人工建边"
        >
            <div class="graph-page__empty-actions">
                <el-select
                    v-model="filters.centerId"
                    class="graph-page__empty-search"
                    filterable
                    remote
                    clearable
                    reserve-keyword
                    placeholder="搜索已发布经验作为中心节点"
                    :remote-method="searchCenterCandidates"
                    :loading="centerSearchLoading"
                    @change="handleCenterChange"
                >
                    <el-option
                        v-for="item in centerOptions"
                        :key="item.id"
                        :label="item.title"
                        :value="item.id"
                    />
                </el-select>
                <el-button @click="router.push('/memory')">
                    前往记忆中心
                </el-button>
            </div>
        </PageEmptyState>

        <div v-else class="graph-page__layout">
            <div class="graph-page__main">
                <ClientOnly>
                    <ExperienceGraphChart
                        :nodes="filteredGraph.nodes"
                        :edges="filteredGraph.edges"
                        :center-id="filters.centerId"
                        @node-click="handleNodeClick"
                        @node-dblclick="handleNodeRecenter"
                    />
                </ClientOnly>
                <!-- 子图已加载但无边：页面级空态，避免被 ECharts 画布遮挡 -->
                <div v-if="showSubgraphEmptyHint" class="graph-page__subgraph-empty">
                    <div class="graph-page__subgraph-empty-inner">
                        <PageEmptyState
                            compact
                            bordered
                            :flow-steps="subgraphEmptyFlowSteps"
                            :title="subgraphEmptyTitle"
                            :subtitle="subgraphEmptySubtitle"
                            :tags="subgraphEmptyTags"
                            :guide="subgraphEmptyGuide"
                        >
                            <div class="graph-page__subgraph-empty-actions">
                                <el-button
                                    v-if="hasRelationTypeFilter"
                                    size="small"
                                    @click="clearRelationTypesFilter"
                                >
                                    清空关系类型
                                </el-button>
                                <el-button
                                    v-if="hasClientFiltersActive"
                                    size="small"
                                    @click="clearClientFilters"
                                >
                                    清空客户端筛选
                                </el-button>
                                <el-button
                                    v-if="centerNodeForAction"
                                    size="small"
                                    type="primary"
                                    @click="openNodeDetail(centerNodeForAction)"
                                >
                                    查看中心经验
                                </el-button>
                            </div>
                        </PageEmptyState>
                    </div>
                </div>
            </div>

            <div class="graph-page__side">
                <GraphNodePreviewPanel
                    v-if="selectedNode"
                    :node="selectedNode"
                    @open-detail="openNodeDetail"
                    @recenter="handleNodeRecenter"
                />
                <div v-else class="graph-page__side-empty">
                    <div class="graph-page__side-empty-icon" aria-hidden="true">
                        <svg viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <circle cx="24" cy="14" r="6" stroke="currentColor" stroke-width="2" />
                            <circle cx="12" cy="34" r="5" stroke="currentColor" stroke-width="2" />
                            <circle cx="36" cy="34" r="5" stroke="currentColor" stroke-width="2" />
                            <path d="M21 18L14 29M27 18L34 29M17 34H31" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
                        </svg>
                    </div>
                    <p class="graph-page__side-empty-title">单击节点查看预览</p>
                    <p class="graph-page__side-empty-desc">双击节点可将其设为中心并重绘子图</p>
                </div>

                <div class="graph-page__legend">
                    <h4 class="graph-page__legend-title">关系类型</h4>
                    <ul class="graph-page__legend-list">
                        <li
                            v-for="option in RELATION_TYPE_OPTIONS"
                            :key="option.value"
                            class="graph-page__legend-item"
                        >
                            <span
                                class="graph-page__legend-dot"
                                :style="{ backgroundColor: getRelationEdgeColor(option.value) }"
                            />
                            {{ option.label }}
                        </li>
                    </ul>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ArrowRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
    GRAPH_DEFAULT_DEPTH,
    GRAPH_DEFAULT_LIMIT,
    getRelationEdgeColor,
    getRelationTypeLabel,
    RELATION_TYPE_OPTIONS,
    resolveKnowledgeDetailPath,
} from '~/constants/graph'
import { KNOWLEDGE_LIFECYCLE } from '~/constants/knowledge'
import { fetchKnowledgeGraph, fetchKnowledgeList } from '~/services/knowledge.service'

/** 页头展示的核心关系类型标签 */
const GRAPH_HEADER_RELATION_TAGS = RELATION_TYPE_OPTIONS.filter((option) => (
    ['same_module', 'same_tag', 'related_semantic', 'depends_on'].includes(option.value)
))

/** 空态流程步骤 */
const GRAPH_EMPTY_FLOW_STEPS = ['选中心', '扩展子图', '浏览关系']

/** 空态能力标签（中文） */
const GRAPH_EMPTY_FEATURE_TAGS = GRAPH_HEADER_RELATION_TAGS.map((option) => option.label)

definePageMeta({
    layout: 'app',
})

const route = useRoute()
const router = useRouter()

useHead({ title: '经验图谱' })
usePageTracker()

const loading = ref(false)
const centerSearchLoading = ref(false)
/** 客户端筛选区默认折叠，减少首屏控件密度 */
const clientFilterExpanded = ref(false)
const graphData = ref(null)
const centerOptions = ref([])
const selectedNode = ref(null)

const filters = reactive({
    centerId: route.query.centerId ? Number(route.query.centerId) : null,
    depth: GRAPH_DEFAULT_DEPTH,
    relationTypes: [],
    moduleKeyword: '',
    tagKeyword: '',
})

/** 客户端按模块/关键词过滤后的子图 */
const filteredGraph = computed(() => {
    const sourceGraph = graphData.value || { nodes: [], edges: [] }
    const moduleKeyword = filters.moduleKeyword.trim().toLowerCase()
    const tagKeyword = filters.tagKeyword.trim().toLowerCase()

    let nodeList = sourceGraph.nodes || []
    if (moduleKeyword) {
        nodeList = nodeList.filter((node) => (node.module || '').toLowerCase().includes(moduleKeyword))
    }
    if (tagKeyword) {
        nodeList = nodeList.filter((node) => (node.title || '').toLowerCase().includes(tagKeyword))
    }

    const nodeIdSet = new Set(nodeList.map((node) => String(node.id)))
    const edgeList = (sourceGraph.edges || []).filter((edge) => {
        return nodeIdSet.has(String(edge.sourceId)) && nodeIdSet.has(String(edge.targetId))
    })

    return {
        nodes: nodeList,
        edges: edgeList,
    }
})

/** 子图已加载但当前筛选下边数为 0，需展示空态提示 */
const showSubgraphEmptyHint = computed(() => {
    if (!filters.centerId || loading.value || !graphData.value) {
        return false
    }
    return filteredGraph.value.edges.length === 0
})

/** 中心经验标题，用于空态文案 */
const centerNodeTitle = computed(() => {
    if (selectedNode.value?.title) {
        return selectedNode.value.title
    }
    const graphCenterNode = (graphData.value?.nodes || []).find(
        (node) => Number(node.id) === Number(filters.centerId),
    )
    if (graphCenterNode?.title) {
        return graphCenterNode.title
    }
    const optionItem = centerOptions.value.find(
        (item) => Number(item.id) === Number(filters.centerId),
    )
    return optionItem?.title || `经验 #${filters.centerId}`
})

/** 空态快捷操作使用的中心节点 */
const centerNodeForAction = computed(() => {
    if (selectedNode.value) {
        return selectedNode.value
    }
    return (graphData.value?.nodes || []).find(
        (node) => Number(node.id) === Number(filters.centerId),
    ) || null
})

/** 是否已筛选关系类型 */
const hasRelationTypeFilter = computed(() => filters.relationTypes.length > 0)

/** 是否启用客户端模块/标签筛选 */
const hasClientFiltersActive = computed(() => (
    Boolean(filters.moduleKeyword.trim() || filters.tagKeyword.trim())
))

/** 关系类型筛选摘要 */
function buildRelationTypesSummary() {
    if (!hasRelationTypeFilter.value) {
        return '全部类型'
    }
    return filters.relationTypes
        .map((relationType) => getRelationTypeLabel(relationType))
        .join('、')
}

/** 0 边空态流程步骤 */
const subgraphEmptyFlowSteps = computed(() => {
    if (filteredGraph.value.nodes.length === 0) {
        return ['已选中心', '加载子图', '暂无数据']
    }
    return [
        { label: '已选中心', tone: 'green' },
        { label: `${filters.depth} 跳扩展`, tone: 'blue' },
        { label: '暂无关联', tone: 'purple' },
    ]
})

/** 0 边空态标题 */
const subgraphEmptyTitle = computed(() => {
    if (filteredGraph.value.nodes.length === 0) {
        return '暂无可渲染的图谱数据'
    }
    return '暂无邻接关系'
})

/** 0 边空态副标题：结合当前筛选条件给出原因说明 */
const subgraphEmptySubtitle = computed(() => {
    if (filteredGraph.value.nodes.length === 0) {
        return '接口未返回可渲染节点，请确认该经验已发布且属于当前工作空间'
    }

    const summaryParts = [`「${centerNodeTitle.value}」`]
    if (hasRelationTypeFilter.value) {
        summaryParts.push(`在「${buildRelationTypesSummary()}」关系类型下`)
    }
    summaryParts.push(`${filters.depth} 跳范围内`)
    if (hasClientFiltersActive.value) {
        summaryParts.push('（含模块/标签客户端筛选）')
    }
    summaryParts.push('未发现关联边')
    return summaryParts.join('')
})

/** 0 边空态建议标签 */
const subgraphEmptyTags = computed(() => {
    if (filteredGraph.value.nodes.length === 0) {
        return ['确认已发布', '切换中心经验', '检查工作空间']
    }

    const suggestionTags = []
    if (hasRelationTypeFilter.value) {
        suggestionTags.push('清空关系类型')
    }
    if (hasClientFiltersActive.value) {
        suggestionTags.push('清空客户端筛选')
    }
    if (filters.depth < 3) {
        suggestionTags.push('增加扩展深度')
    }
    suggestionTags.push('详情页人工建边')
    return suggestionTags.slice(0, 4)
})

/** 0 边空态底部引导 */
const subgraphEmptyGuide = computed(() => {
    if (filteredGraph.value.nodes.length === 0) {
        return '发布经验后 RelationEngine 会自动建边；也可在详情页补充人工关系'
    }
    if (hasRelationTypeFilter.value || hasClientFiltersActive.value) {
        return '可先放宽上方筛选条件后点击「刷新子图」，或前往中心经验详情页确认是否已建边'
    }
    return '当前中心经验尚未与其他经验建立关系，可等待自动建边或在详情页人工建边'
})

/** 清空关系类型筛选并重新加载子图 */
function clearRelationTypesFilter() {
    filters.relationTypes = []
    loadGraph()
}

/** 清空客户端筛选，仅过滤已加载子图 */
function clearClientFilters() {
    filters.moduleKeyword = ''
    filters.tagKeyword = ''
}

/** 远程搜索可作为中心节点的已发布经验 */
async function searchCenterCandidates(keyword) {
    centerSearchLoading.value = true
    try {
        const response = await fetchKnowledgeList(1, 20, keyword, {
            lifecycleStatus: KNOWLEDGE_LIFECYCLE.PUBLISHED,
        })
        centerOptions.value = response?.list || []
    } catch (error) {
        ElMessage.error(error.message || '搜索中心节点失败')
    } finally {
        centerSearchLoading.value = false
    }
}

/** 加载以 centerId 为中心的子图 */
async function loadGraph() {
    if (!filters.centerId) {
        graphData.value = null
        selectedNode.value = null
        return
    }
    loading.value = true
    try {
        const types = filters.relationTypes.length > 0
            ? filters.relationTypes.join(',')
            : undefined
        graphData.value = await fetchKnowledgeGraph(filters.centerId, {
            depth: filters.depth,
            limit: GRAPH_DEFAULT_LIMIT,
            types,
        })
        selectedNode.value = (graphData.value?.nodes || []).find(
            (node) => Number(node.id) === Number(filters.centerId),
        ) || null
        await router.replace({
            query: {
                ...route.query,
                centerId: String(filters.centerId),
            },
        })
    } catch (error) {
        ElMessage.error(error.message || '加载图谱失败')
    } finally {
        loading.value = false
    }
}

function handleCenterChange() {
    selectedNode.value = null
    loadGraph()
}

function handleNodeClick(node) {
    selectedNode.value = node
}

function handleNodeRecenter(node) {
    filters.centerId = Number(node.id)
    selectedNode.value = node
    loadGraph()
}

function openNodeDetail(node) {
    const detailPath = resolveKnowledgeDetailPath(node.knowledgeType)
    router.push(`${detailPath}/${node.id}`)
}

/** 初始化：若 URL 带 centerId，预加载中心节点标题 */
onMounted(async () => {
    if (filters.centerId) {
        await searchCenterCandidates('')
        if (!centerOptions.value.some((item) => Number(item.id) === Number(filters.centerId))) {
            centerOptions.value.unshift({
                id: filters.centerId,
                title: `经验 #${filters.centerId}`,
            })
        }
        await loadGraph()
    }
})
</script>

<style scoped>
.graph-page {
    padding: 28px 24px 40px;
}

/* 页头 */
.graph-page__header {
    margin-bottom: 24px;
}

.graph-page__header-main {
    display: flex;
    align-items: flex-start;
    gap: 16px;
}

.graph-page__title {
    margin: 0;
    font-size: 26px;
    font-weight: 700;
    color: #111827;
    letter-spacing: -0.02em;
}

.graph-page__subtitle {
    margin: 6px 0 0;
    font-size: 14px;
    color: #6b7280;
    line-height: 1.6;
    max-width: 640px;
}

.graph-page__subtitle-em {
    font-weight: 600;
    color: #5d65f9;
}

.graph-page__header-tags {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
    margin-top: 12px;
}

.graph-page__header-tag {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 3px 10px;
    border-radius: 999px;
    font-size: 11px;
    font-weight: 500;
    color: #4b5563;
    background: #f9fafb;
    border: 1px solid #e5e7eb;
}

.graph-page__header-tag-dot {
    width: 7px;
    height: 7px;
    border-radius: 999px;
    background: var(--tag-color, #94a3b8);
    flex-shrink: 0;
}

/* 工具栏卡片 */
.graph-page__toolbar {
    margin-bottom: 20px;
    padding: 22px 24px;
    background: #fff;
    border-radius: 14px;
    border: 1px solid #e8eaef;
    box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.graph-page__toolbar-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 16px;
}

.graph-page__toolbar-title {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: #111827;
}

.graph-page__toolbar-desc {
    margin: 4px 0 0;
    font-size: 12px;
    color: #9ca3af;
}

.graph-page__stats {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-shrink: 0;
    padding: 8px 14px;
    border-radius: 10px;
    background: #f9fafb;
    border: 1px solid #f3f4f6;
}

.graph-page__stat {
    display: flex;
    flex-direction: column;
    align-items: center;
    min-width: 40px;
}

.graph-page__stat-value {
    font-size: 20px;
    font-weight: 700;
    color: #5d65f9;
    font-variant-numeric: tabular-nums;
    line-height: 1.2;
}

.graph-page__stat-label {
    margin-top: 2px;
    font-size: 11px;
    color: #9ca3af;
}

.graph-page__stat-divider {
    width: 1px;
    height: 28px;
    background: #e5e7eb;
}

.graph-page__form-primary {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(180px, 240px) 140px auto;
    gap: 12px 16px;
    align-items: end;
    margin-bottom: 4px;
}

.graph-page__field {
    margin-bottom: 0 !important;
}

.graph-page__field--action {
    display: flex;
    align-items: flex-end;
    padding-bottom: 2px;
}

.graph-page__form :deep(.el-form-item__label) {
    font-size: 13px;
    font-weight: 500;
    color: #374151;
    padding-bottom: 4px;
}

.graph-page__filter-toggle {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    margin-top: 14px;
    padding: 10px 0 0;
    border: none;
    border-top: 1px solid #f3f4f6;
    background: transparent;
    cursor: pointer;
    text-align: left;
}

.graph-page__filter-toggle-title {
    font-size: 13px;
    font-weight: 500;
    color: #374151;
}

.graph-page__filter-toggle-hint {
    flex: 1;
    min-width: 0;
    font-size: 12px;
    color: #9ca3af;
}

.graph-page__filter-toggle-icon {
    flex-shrink: 0;
    color: #9ca3af;
    transition: transform 0.2s ease;
}

.graph-page__filter-toggle-icon--expanded {
    transform: rotate(90deg);
}

.graph-page__client-filters {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px 16px;
    padding-top: 14px;
}

/* 加载骨架 */
.graph-page__loading {
    margin-bottom: 20px;
}

.graph-page__skeleton {
    padding: 24px;
    background: #fff;
    border-radius: 14px;
    border: 1px solid #e8eaef;
}

/* 空态内嵌搜索 */
.graph-page__empty-actions {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: center;
    gap: 10px;
    width: 100%;
    max-width: 520px;
}

.graph-page__empty-search {
    flex: 1;
    min-width: 240px;
}

/* 主布局 */
.graph-page__layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 300px;
    gap: 16px;
    align-items: start;
}

.graph-page__main {
    position: relative;
    min-height: 560px;
}

.graph-page__subgraph-empty {
    position: absolute;
    inset: 0;
    z-index: 10;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 24px;
    background: rgba(248, 250, 252, 0.72);
    backdrop-filter: blur(2px);
    pointer-events: none;
}

.graph-page__subgraph-empty-inner {
    width: 100%;
    max-width: 520px;
    pointer-events: auto;
}

.graph-page__subgraph-empty-actions {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: center;
    gap: 8px;
}

.graph-page__side {
    display: flex;
    flex-direction: column;
    gap: 12px;
    position: sticky;
    top: 16px;
}

.graph-page__side-empty {
    border: 1px dashed #d1d5db;
    border-radius: 14px;
    padding: 24px 20px;
    text-align: center;
    background: linear-gradient(180deg, #f9fafb 0%, #fff 100%);
}

.graph-page__side-empty-icon {
    display: flex;
    justify-content: center;
    margin-bottom: 12px;
    color: #cbd5e1;
}

.graph-page__side-empty-icon svg {
    width: 48px;
    height: 48px;
}

.graph-page__side-empty-title {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
    color: #374151;
}

.graph-page__side-empty-desc {
    margin: 6px 0 0;
    font-size: 12px;
    color: #9ca3af;
    line-height: 1.5;
}

.graph-page__legend {
    border: 1px solid #e8eaef;
    border-radius: 14px;
    padding: 14px 16px;
    background: #fff;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
}

.graph-page__legend-title {
    margin: 0;
    font-size: 12px;
    font-weight: 600;
    color: #6b7280;
    text-transform: uppercase;
    letter-spacing: 0.04em;
}

.graph-page__legend-list {
    margin: 10px 0 0;
    padding: 0;
    list-style: none;
    display: grid;
    gap: 5px;
}

.graph-page__legend-item {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    color: #4b5563;
}

.graph-page__legend-dot {
    width: 8px;
    height: 8px;
    border-radius: 999px;
    flex-shrink: 0;
}

@media (max-width: 1024px) {
    .graph-page__form-primary {
        grid-template-columns: 1fr;
    }

    .graph-page__field--action {
        justify-content: flex-start;
    }

    .graph-page__client-filters {
        grid-template-columns: 1fr;
    }

    .graph-page__layout {
        grid-template-columns: 1fr;
    }

    .graph-page__side {
        position: static;
    }

    .graph-page__toolbar-head {
        flex-direction: column;
    }

    .graph-page__stats {
        align-self: stretch;
        justify-content: center;
    }
}
</style>
