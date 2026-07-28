/**
 * 经验列表页业务逻辑：分页、关键词搜索
 */
import { fetchKnowledgeList } from '~/services/knowledge.service'

export function useKnowledgeList() {
    const loading = ref(false)
    const keyword = ref('')
    const pageNum = ref(1)
    const pageSize = ref(20)
    const total = ref(0)
    const knowledgeList = ref([])

    /**
     * 加载已发布经验列表
     * @param {{ silent?: boolean }} options silent 为 true 时保留当前列表，后台拉取新数据
     */
    async function loadList(options = {}) {
        const silent = options.silent === true
        if (!silent) {
            loading.value = true
        }
        try {
            const result = await fetchKnowledgeList(pageNum.value, pageSize.value, keyword.value)
            knowledgeList.value = result.list || []
            total.value = result.total || 0
        } finally {
            if (!silent) {
                loading.value = false
            }
        }
    }

    /** 切换分页 */
    function changePage(currentPage) {
        pageNum.value = currentPage
        return loadList()
    }

    /** 搜索并重置到第一页 */
    function search() {
        pageNum.value = 1
        return loadList()
    }

    return {
        loading,
        keyword,
        pageNum,
        pageSize,
        total,
        knowledgeList,
        loadList,
        changePage,
        search,
    }
}
