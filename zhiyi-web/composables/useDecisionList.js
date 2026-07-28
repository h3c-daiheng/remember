/**
 * Decision 决策列表页业务逻辑：仅展示已发布内容
 */
import { fetchKnowledgeList } from '~/services/knowledge.service'
import { KNOWLEDGE_LIFECYCLE, KNOWLEDGE_TYPES } from '~/constants/knowledge'

export function useDecisionList() {
    const loading = ref(false)
    const keyword = ref('')
    const pageNum = ref(1)
    const pageSize = ref(20)
    const total = ref(0)
    const decisionList = ref([])

    /**
     * 加载 Decision 已发布列表
     */
    async function loadList(options = {}) {
        const silent = options.silent === true
        if (!silent) {
            loading.value = true
        }
        try {
            const result = await fetchKnowledgeList(
                pageNum.value,
                pageSize.value,
                keyword.value,
                {
                    knowledgeType: KNOWLEDGE_TYPES.DECISION,
                    lifecycleStatus: KNOWLEDGE_LIFECYCLE.PUBLISHED,
                },
            )
            decisionList.value = result.list || []
            total.value = result.total || 0
        } finally {
            if (!silent) {
                loading.value = false
            }
        }
    }

    function changePage(currentPage) {
        pageNum.value = currentPage
        return loadList()
    }

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
        decisionList,
        loadList,
        changePage,
        search,
    }
}
