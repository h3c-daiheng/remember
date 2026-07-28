/**
 * Rule / Workflow 列表页业务逻辑：仅展示已发布内容
 */
import { fetchKnowledgeList } from '~/services/knowledge.service'
import { KNOWLEDGE_LIFECYCLE, KNOWLEDGE_TYPES } from '~/constants/knowledge'

export function useRuleList() {
    const loading = ref(false)
    const keyword = ref('')
    const pageNum = ref(1)
    const pageSize = ref(20)
    const total = ref(0)
    const ruleList = ref([])
    const knowledgeTypeFilter = ref(KNOWLEDGE_TYPES.RULE)

    /**
     * 加载 Rule / Workflow 已发布列表
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
                    knowledgeType: knowledgeTypeFilter.value,
                    lifecycleStatus: KNOWLEDGE_LIFECYCLE.PUBLISHED,
                },
            )
            ruleList.value = result.list || []
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

    function switchKnowledgeType(type) {
        knowledgeTypeFilter.value = type
        pageNum.value = 1
        return loadList()
    }

    return {
        loading,
        keyword,
        pageNum,
        pageSize,
        total,
        ruleList,
        knowledgeTypeFilter,
        loadList,
        changePage,
        search,
        switchKnowledgeType,
    }
}
