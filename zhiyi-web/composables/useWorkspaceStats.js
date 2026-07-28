/**
 * 工作空间统计数据加载与格式化
 */
import { ElMessage } from 'element-plus'
import {
    fetchStatsDimensions,
    fetchStatsFunnel,
    fetchStatsGraphHubs,
    fetchStatsOverview,
    fetchStatsRecallTrend,
    fetchStatsTopKnowledge,
    fetchStatsUsage,
} from '~/services/stats.service'

/**
 * 将 0~1 比率格式化为百分比文案
 */
export function formatRatePercent(rate) {
    if (rate === null || rate === undefined) {
        return '—'
    }
    return `${Math.round(rate * 100)}%`
}

/** 当前月份 yyyy-MM */
function getCurrentMonthValue() {
    const now = new Date()
    const month = String(now.getMonth() + 1).padStart(2, '0')
    return `${now.getFullYear()}-${month}`
}

export function useWorkspaceStats() {
    const { currentWorkspace } = useWorkspace()

    const loading = ref(false)
    const overview = ref(null)
    const trendList = ref([])
    const topKnowledgeList = ref([])
    const graphHubList = ref([])
    const usage = ref(null)
    const funnel = ref(null)
    const dimensions = ref(null)
    const selectedMonth = ref(getCurrentMonthValue())
    const dimensionFilters = ref({
        module: '',
        project: '',
        tag: '',
    })

    /** 当前工作空间 ID */
    const workspaceId = computed(() => currentWorkspace.value?.workspaceId || null)

    /** 是否无任何 Memory 调用（用于空态引导，以 operation_log 为准） */
    const isEmptyUsage = computed(() => {
        if (!overview.value) {
            return false
        }
        const totalCalls = (overview.value.recallCount7d || 0)
            + (overview.value.rememberCount7d || 0)
            + (overview.value.feedbackCount7d || 0)
        return totalCalls === 0
    })

    /** helpful 占比是否偏低，展示改进建议 */
    const isLowHelpfulRate = computed(() => {
        if (!overview.value || isEmptyUsage.value) {
            return false
        }
        const feedbackTotal = overview.value.feedbackCount7d || 0
        if (feedbackTotal === 0) {
            return false
        }
        return (overview.value.helpfulRate || 0) < 0.4
    })

    /**
     * 加载全部统计数据
     * @param {{ silent?: boolean, month?: string }} [options]
     */
    async function loadStats(options = {}) {
        const targetWorkspaceId = workspaceId.value
        if (!targetWorkspaceId) {
            return
        }
        const monthValue = options.month || selectedMonth.value || getCurrentMonthValue()
        selectedMonth.value = monthValue

        if (!options.silent) {
            loading.value = true
        }
        try {
            const [overviewData, trendData, topData, graphHubData, usageData, funnelData, dimensionsData] = await Promise.all([
                fetchStatsOverview(targetWorkspaceId, 7),
                fetchStatsRecallTrend(targetWorkspaceId, 30),
                fetchStatsTopKnowledge(targetWorkspaceId, 10),
                fetchStatsGraphHubs(targetWorkspaceId, 10),
                fetchStatsUsage(targetWorkspaceId, monthValue),
                fetchStatsFunnel(targetWorkspaceId, 30),
                fetchStatsDimensions(targetWorkspaceId, {
                    days: 30,
                    module: dimensionFilters.value.module,
                    project: dimensionFilters.value.project,
                    tag: dimensionFilters.value.tag,
                }),
            ])
            overview.value = overviewData
            trendList.value = trendData || []
            topKnowledgeList.value = topData || []
            graphHubList.value = graphHubData || []
            usage.value = usageData
            funnel.value = funnelData
            dimensions.value = dimensionsData
        } catch (error) {
            if (!options.silent) {
                ElMessage.error(error.message || '加载统计数据失败')
            }
            throw error
        } finally {
            if (!options.silent) {
                loading.value = false
            }
        }
    }

    /**
     * 切换用量统计月份
     */
    async function changeUsageMonth(monthValue) {
        const targetWorkspaceId = workspaceId.value
        if (!targetWorkspaceId) {
            return
        }
        selectedMonth.value = monthValue
        try {
            usage.value = await fetchStatsUsage(targetWorkspaceId, monthValue)
        } catch (error) {
            ElMessage.error(error.message || '加载用量数据失败')
        }
    }

    /**
     * 更新多维分析筛选条件并重新加载
     */
    async function changeDimensionFilters(filters) {
        dimensionFilters.value = {
            module: filters.module || '',
            project: filters.project || '',
            tag: filters.tag || '',
        }
        const targetWorkspaceId = workspaceId.value
        if (!targetWorkspaceId) {
            return
        }
        try {
            dimensions.value = await fetchStatsDimensions(targetWorkspaceId, {
                days: 30,
                module: dimensionFilters.value.module,
                project: dimensionFilters.value.project,
                tag: dimensionFilters.value.tag,
            })
        } catch (error) {
            ElMessage.error(error.message || '加载多维分析失败')
        }
    }

    return {
        loading,
        overview,
        trendList,
        topKnowledgeList,
        graphHubList,
        usage,
        funnel,
        dimensions,
        selectedMonth,
        workspaceId,
        isEmptyUsage,
        isLowHelpfulRate,
        loadStats,
        changeUsageMonth,
        changeDimensionFilters,
        formatRatePercent,
    }
}
