/**
 * 记忆治理页业务逻辑：扫描、工单列表、合并优化
 */
import {
    confirmGovernanceMerge,
    fetchGovernanceConfig,
    fetchGovernanceIssueDetail,
    fetchGovernanceIssueList,
    fetchGovernanceStats,
    previewGovernanceMerge,
    previewGovernanceMergeByIssue,
    resolveGovernanceIssue,
    runGovernanceScan,
    updateGovernanceConfig,
} from '~/services/governance.service'

export function useGovernance() {
    const loading = ref(false)
    const scanning = ref(false)
    const issueList = ref([])
    const total = ref(0)
    const stats = ref(null)
    const config = ref(null)
    const configSaving = ref(false)
    const resolvingIssueId = ref(null)
    const mergePreviewLoading = ref(false)
    const mergeConfirmLoading = ref(false)
    const mergePreview = ref(null)

    async function loadIssueList(options = {}) {
        loading.value = true
        try {
            const pageResult = await fetchGovernanceIssueList({
                pageNum: options.pageNum || 1,
                pageSize: options.pageSize || 20,
                status: options.status,
                issueType: options.issueType,
            })
            issueList.value = pageResult.list || []
            total.value = pageResult.total || 0
        } finally {
            loading.value = false
        }
    }

    async function loadStats() {
        stats.value = await fetchGovernanceStats()
    }

    async function loadConfig() {
        config.value = await fetchGovernanceConfig()
    }

    async function saveConfig(configRequest) {
        configSaving.value = true
        try {
            config.value = await updateGovernanceConfig(configRequest)
            return config.value
        } finally {
            configSaving.value = false
        }
    }

    async function runScan(scanRequest) {
        scanning.value = true
        try {
            return await runGovernanceScan(scanRequest)
        } finally {
            scanning.value = false
        }
    }

    async function loadIssueDetail(issueId) {
        return fetchGovernanceIssueDetail(issueId)
    }

    async function resolveIssue(issueId, resolveRequest) {
        resolvingIssueId.value = issueId
        try {
            return await resolveGovernanceIssue(issueId, resolveRequest)
        } finally {
            resolvingIssueId.value = null
        }
    }

    /**
     * 加载合并预览：支持工单 ID 或手动多选
     */
    async function loadMergePreview(options = {}) {
        mergePreviewLoading.value = true
        try {
            if (options.issueId) {
                mergePreview.value = await previewGovernanceMergeByIssue(options.issueId, {
                    useLlm: options.useLlm,
                })
            } else {
                mergePreview.value = await previewGovernanceMerge({
                    knowledgeIds: options.knowledgeIds,
                    useLlm: options.useLlm,
                })
            }
            return mergePreview.value
        } finally {
            mergePreviewLoading.value = false
        }
    }

    /**
     * 确认合并并发布新版
     */
    async function confirmMerge(confirmRequest) {
        mergeConfirmLoading.value = true
        try {
            return await confirmGovernanceMerge(confirmRequest)
        } finally {
            mergeConfirmLoading.value = false
        }
    }

    return {
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
        mergePreview,
        loadIssueList,
        loadStats,
        loadConfig,
        saveConfig,
        runScan,
        loadIssueDetail,
        resolveIssue,
        loadMergePreview,
        confirmMerge,
    }
}
