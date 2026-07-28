/**
 * 经验列表页业务逻辑：已发布 / 已失效 Tab、分页与搜索
 */
import { fetchKnowledgeList } from '~/services/knowledge.service'
import { KNOWLEDGE_LIFECYCLE, KNOWLEDGE_TYPES } from '~/constants/knowledge'

export function useExperienceList() {
    const loading = ref(false)
    const keyword = ref('')
    const pageNum = ref(1)
    const pageSize = ref(20)
    const total = ref(0)
    const knowledgeList = ref([])
    const activeTab = ref('published')

    /** 当前 Tab 对应的 lifecycleStatus（草稿统一在草稿确认页处理） */
    const lifecycleStatus = computed(() => {
        if (activeTab.value === 'deprecated') {
            return KNOWLEDGE_LIFECYCLE.DEPRECATED
        }
        return KNOWLEDGE_LIFECYCLE.PUBLISHED
    })

    /**
     * 加载经验列表，固定 knowledgeType=experience
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
                    knowledgeType: KNOWLEDGE_TYPES.EXPERIENCE,
                    lifecycleStatus: lifecycleStatus.value,
                },
            )
            knowledgeList.value = result.list || []
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

    function switchTab(tabName) {
        activeTab.value = tabName
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
        activeTab,
        loadList,
        changePage,
        search,
        switchTab,
    }
}
