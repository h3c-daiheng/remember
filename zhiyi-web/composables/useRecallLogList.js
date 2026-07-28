/**
 * 召回记录页：分页列表与筛选（Recall / Search 操作日志）
 */
import { fetchRecallLogList } from '~/services/memory-trace.service'

export function useRecallLogList() {
    const loading = ref(false)
    const keyword = ref('')
    /** 空字符串表示全部（recall + search） */
    const operationFilter = ref('')
    /** 空字符串表示全部；'1' 成功 / '0' 失败 */
    const successFilter = ref('')
    const pageNum = ref(1)
    const pageSize = ref(20)
    const total = ref(0)
    const recordList = ref([])

    /**
     * 加载召回请求列表
     */
    async function loadList() {
        loading.value = true
        try {
            const result = await fetchRecallLogList({
                pageNum: pageNum.value,
                pageSize: pageSize.value,
                keyword: keyword.value,
                operation: operationFilter.value || undefined,
                success: successFilter.value === '' ? undefined : successFilter.value,
            })
            recordList.value = result?.list || []
            total.value = result?.total || 0
        } finally {
            loading.value = false
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

    return {
        loading,
        keyword,
        operationFilter,
        successFilter,
        pageNum,
        pageSize,
        total,
        recordList,
        loadList,
        changePage,
        search,
    }
}
