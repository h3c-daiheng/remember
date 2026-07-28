/**
 * AI Review 记录页：分页列表、筛选与静默刷新
 */
import { fetchAiReviewList } from '~/services/capture.service'
import { AI_REVIEW_STATUS } from '~/utils/aiReview'

export function useAiReviewRecords() {
    const loading = ref(false)
    const keyword = ref('')
    const statusFilter = ref('')
    const decisionFilter = ref('')
    const similarHitFilter = ref('')
    const pageNum = ref(1)
    const pageSize = ref(20)
    const total = ref(0)
    const recordList = ref([])

    /**
     * 加载 AI Review 记录列表
     * @param {{ silent?: boolean }} [options]
     */
    async function loadList(options = {}) {
        const silent = options.silent === true
        if (!silent) {
            loading.value = true
        }
        try {
            const result = await fetchAiReviewList({
                pageNum: pageNum.value,
                pageSize: pageSize.value,
                keyword: keyword.value,
                status: statusFilter.value === '' ? undefined : statusFilter.value,
                decision: decisionFilter.value || undefined,
                similarHit: similarHitFilter.value === '' ? undefined : similarHitFilter.value,
            })
            recordList.value = result?.list || []
            total.value = result?.total || 0
        } finally {
            if (!silent) {
                loading.value = false
            }
        }
    }

    /** 切换页码 */
    function changePage(currentPage) {
        pageNum.value = currentPage
        return loadList()
    }

    /** 应用筛选并回到第一页 */
    function search() {
        pageNum.value = 1
        return loadList()
    }

    /** 当前页是否包含排队/进行中记录（用于轮询） */
    const hasRunningRecords = computed(() =>
        recordList.value.some((item) =>
            item?.status === AI_REVIEW_STATUS.QUEUED
            || item?.status === AI_REVIEW_STATUS.RUNNING),
    )

    return {
        loading,
        keyword,
        statusFilter,
        decisionFilter,
        similarHitFilter,
        pageNum,
        pageSize,
        total,
        recordList,
        hasRunningRecords,
        loadList,
        changePage,
        search,
    }
}
