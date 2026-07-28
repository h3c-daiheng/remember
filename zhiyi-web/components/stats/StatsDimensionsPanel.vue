<template>
    <StatsPanel
        title="多维分析"
        :subtitle="`近 ${dimensions?.statsPeriodDays || 30} 天 · Recall P95 ${dimensions?.recallP95LatencyMs ?? 0} ms`"
    >
        <template #actions>
            <div class="stats-dimensions__filters">
                <RecallContextFields
                    v-model:module="filterModule"
                    v-model:project="filterProject"
                    v-model:tag="filterTag"
                    :show-repository="false"
                    :show-labels="false"
                    size="small"
                    layout="inline"
                />
                <el-button size="small" type="primary" @click="handleApplyFilter">
                    筛选
                </el-button>
                <el-button size="small" plain @click="handleResetFilter">重置</el-button>
            </div>
        </template>

        <el-skeleton v-if="loading" :rows="8" animated />

        <template v-else-if="dimensions">
            <!-- 筛选结果摘要 -->
            <div
                v-if="hasActiveFilter"
                class="stats-dimensions__summary"
            >
                <el-icon class="stats-dimensions__summary-icon"><Filter /></el-icon>
                <span>
                    筛选结果：{{ dimensions.filteredKnowledgeCount ?? 0 }} 条经验，
                    Recall 累计（全量）{{ dimensions.filteredRecallCount ?? 0 }} 次，
                    helpful（近 {{ dimensions.statsPeriodDays || 30 }} 天）{{ dimensions.filteredHelpfulCount ?? 0 }} 次
                </span>
            </div>

            <div class="stats-dimensions__grid">
                <!-- 模块分布：点击行可快速筛选 -->
                <div class="stats-dimensions__block">
                    <h3 class="stats-dimensions__block-title">模块分布 Top 10</h3>
                    <el-table
                        :data="dimensions.moduleBreakdown || []"
                        size="small"
                        stripe
                        class="stats-dimensions__table stats-dimensions__table--clickable"
                        @row-click="handleModuleRowClick"
                    >
                        <el-table-column label="模块" prop="moduleName" min-width="100" />
                        <el-table-column label="经验数" prop="knowledgeCount" width="80" align="right" />
                        <el-table-column label="Recall" prop="recallCount" width="80" align="right" />
                        <el-table-column label="helpful" prop="helpfulCount" width="80" align="right" />
                    </el-table>
                    <p class="stats-dimensions__table-hint">点击模块行可快速筛选</p>
                </div>

                <!-- 标签分布 -->
                <div class="stats-dimensions__block">
                    <h3 class="stats-dimensions__block-title">标签分布 Top 10</h3>
                    <el-table
                        :data="dimensions.tagBreakdown || []"
                        size="small"
                        stripe
                        class="stats-dimensions__table stats-dimensions__table--clickable"
                        @row-click="handleTagRowClick"
                    >
                        <el-table-column label="标签" prop="tagName" min-width="100" />
                        <el-table-column label="经验数" prop="knowledgeCount" width="80" align="right" />
                        <el-table-column label="helpful" prop="helpfulCount" width="80" align="right" />
                    </el-table>
                    <p class="stats-dimensions__table-hint">点击标签行可快速筛选</p>
                </div>
            </div>

            <!-- API Key 用量 -->
            <div class="stats-dimensions__api-key">
                <h3 class="stats-dimensions__block-title">Agent API Key 用量</h3>
                <PageEmptyState
                    v-if="!(dimensions.apiKeyUsageList || []).length"
                    title="暂无 API Key 调用记录"
                    subtitle="签发 Agent API Key 并完成首次调用后，将在此展示各密钥用量"
                    compact
                    :bordered="false"
                />
                <el-table v-else :data="dimensions.apiKeyUsageList" size="small" stripe class="stats-dimensions__table">
                    <el-table-column label="密钥" min-width="140">
                        <template #default="{ row }">
                            <span>{{ row.keyLabel }}</span>
                            <span v-if="row.keyPrefix" class="text-gray-400 text-xs ml-1">{{ row.keyPrefix }}</span>
                        </template>
                    </el-table-column>
                    <el-table-column label="Recall" prop="recallCount" width="80" align="right" />
                    <el-table-column label="Remember" prop="rememberCount" width="90" align="right" />
                    <el-table-column label="Feedback" prop="feedbackCount" width="90" align="right" />
                </el-table>
            </div>
        </template>
    </StatsPanel>
</template>

<script setup>
import { Filter } from '@element-plus/icons-vue'

const props = defineProps({
    loading: {
        type: Boolean,
        default: false,
    },
    dimensions: {
        type: Object,
        default: null,
    },
})

const emit = defineEmits(['filter-change'])

const filterModule = ref('')
const filterProject = ref('')
const filterTag = ref('')

/** 是否有激活的筛选条件 */
const hasActiveFilter = computed(() => {
    const data = props.dimensions || {}
    return Boolean(data.filterModule || data.filterProject || data.filterTag)
})

/** 同步后端返回的筛选状态到输入框 */
watch(
    () => props.dimensions,
    (value) => {
        if (!value) {
            return
        }
        filterModule.value = value.filterModule || ''
        filterProject.value = value.filterProject || ''
        filterTag.value = value.filterTag || ''
    },
    { immediate: true },
)

/** 应用筛选 */
function handleApplyFilter() {
    emit('filter-change', {
        module: filterModule.value.trim(),
        project: filterProject.value.trim(),
        tag: filterTag.value.trim(),
    })
}

/** 重置筛选 */
function handleResetFilter() {
    filterModule.value = ''
    filterProject.value = ''
    filterTag.value = ''
    emit('filter-change', { module: '', project: '', tag: '' })
}

/** 点击模块分布行，填入模块并立即筛选 */
function handleModuleRowClick(row) {
    const moduleName = row?.moduleName
    if (!moduleName || moduleName === '未分类') {
        return
    }
    filterModule.value = moduleName
    handleApplyFilter()
}

/** 点击标签分布行，填入标签并立即筛选 */
function handleTagRowClick(row) {
    const tagName = row?.tagName
    if (!tagName) {
        return
    }
    filterTag.value = tagName
    handleApplyFilter()
}
</script>

<style scoped>
.stats-dimensions__filters {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    border-radius: 10px;
    background: #f9fafb;
    border: 1px solid #f3f4f6;
}

.stats-dimensions__summary {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    margin-bottom: 16px;
    padding: 12px 14px;
    border-radius: 10px;
    border: 1px solid #ced1fd;
    background: linear-gradient(145deg, #eff0fe 0%, #fafbff 100%);
    font-size: 13px;
    color: #374151;
    line-height: 1.5;
}

.stats-dimensions__summary-icon {
    flex-shrink: 0;
    margin-top: 2px;
    color: #5d65f9;
}

.stats-dimensions__grid {
    display: grid;
    grid-template-columns: 1fr;
    gap: 20px;
}

@media (min-width: 1024px) {
    .stats-dimensions__grid {
        grid-template-columns: repeat(2, 1fr);
    }
}

.stats-dimensions__block-title {
    margin: 0 0 10px;
    font-size: 12px;
    font-weight: 600;
    color: #6b7280;
    text-transform: uppercase;
    letter-spacing: 0.04em;
}

.stats-dimensions__table :deep(.el-table__header th) {
    background: #f9fafb !important;
    color: #6b7280;
    font-weight: 500;
}

.stats-dimensions__table--clickable :deep(.el-table__body tr) {
    cursor: pointer;
}

.stats-dimensions__table--clickable :deep(.el-table__body tr:hover > td) {
    background: #f5f6ff !important;
}

.stats-dimensions__table-hint {
    margin: 8px 0 0;
    font-size: 11px;
    color: #9ca3af;
}

.stats-dimensions__api-key {
    margin-top: 22px;
}

</style>
