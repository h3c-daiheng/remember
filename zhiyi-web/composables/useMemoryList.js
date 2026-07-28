/**
 * 记忆中心列表页业务逻辑：全类型 / 单类型筛选、已发布 / 已失效 Tab、分页与搜索
 */
import { fetchKnowledgeList } from '~/services/knowledge.service'
import {
    KNOWLEDGE_LIFECYCLE,
    KNOWLEDGE_TYPES,
    MEMORY_TYPE_FILTER_ALL,
} from '~/constants/knowledge'

export function useMemoryList() {
    const loading = ref(false)
    const keyword = ref('')
    const pageNum = ref(1)
    const pageSize = ref(20)
    const total = ref(0)
    const memoryList = ref([])
    const typeFilter = ref(MEMORY_TYPE_FILTER_ALL)
    const activeTab = ref('published')

    /** 当前 Tab 对应的 lifecycleStatus */
    const lifecycleStatus = computed(() => {
        if (activeTab.value === 'deprecated') {
            return KNOWLEDGE_LIFECYCLE.DEPRECATED
        }
        return KNOWLEDGE_LIFECYCLE.PUBLISHED
    })

    /** 已失效 Tab 仅对经验或全部类型有意义 */
    const showDeprecatedTab = computed(() =>
        typeFilter.value === MEMORY_TYPE_FILTER_ALL
        || typeFilter.value === KNOWLEDGE_TYPES.EXPERIENCE,
    )

    /**
     * 加载记忆列表；typeFilter=all 时请求后端全类型
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
                    knowledgeType: typeFilter.value,
                    lifecycleStatus: lifecycleStatus.value,
                },
            )
            memoryList.value = result.list || []
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

    /** 切换类型筛选，非经验类型时自动退出已失效 Tab */
    function switchTypeFilter(nextType) {
        typeFilter.value = nextType
        if (
            activeTab.value === 'deprecated'
            && nextType !== MEMORY_TYPE_FILTER_ALL
            && nextType !== KNOWLEDGE_TYPES.EXPERIENCE
        ) {
            activeTab.value = 'published'
        }
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
        memoryList,
        typeFilter,
        activeTab,
        showDeprecatedTab,
        loadList,
        changePage,
        search,
        switchTypeFilter,
        switchTab,
    }
}
