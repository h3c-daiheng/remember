/**
 * 经验搜索：表单状态、调用 Memory Search API、结果与会话 ID 管理
 */
import {
    SEARCH_DEFAULT_KNOWLEDGE_TYPES,
    SEARCH_DEFAULT_LIMIT,
} from '~/constants/memorySearch'
import { searchMemory } from '~/services/memory-search.service'

/**
 * @param {object} [options]
 * @param {() => object} [options.initialQuery] 从 URL query 解析的初始值
 */
export function useMemorySearch(options = {}) {
    const loading = ref(false)
    const searched = ref(false)
    const errorMessage = ref('')
    const sessionId = ref('')
    const promptBlock = ref('')
    const resultItems = ref([])

    /** 搜索表单：与 RecallContext 字段对齐 */
    const searchForm = reactive({
        task: '',
        repository: '',
        module: '',
        project: '',
        currentFile: '',
        knowledgeTypes: [...SEARCH_DEFAULT_KNOWLEDGE_TYPES],
        factTypes: [],
        limit: SEARCH_DEFAULT_LIMIT,
    })

    /**
     * 从路由 query 回填表单（便于分享搜索链接）
     */
    function applyQueryToForm(routeQuery) {
        if (!routeQuery) {
            return
        }
        if (routeQuery.task) {
            searchForm.task = String(routeQuery.task)
        }
        if (routeQuery.repository) {
            searchForm.repository = String(routeQuery.repository)
        }
        if (routeQuery.module) {
            searchForm.module = String(routeQuery.module)
        }
        if (routeQuery.project) {
            searchForm.project = String(routeQuery.project)
        }
        if (routeQuery.currentFile) {
            searchForm.currentFile = String(routeQuery.currentFile)
        }
        if (routeQuery.limit) {
            const parsedLimit = Number(routeQuery.limit)
            if (!Number.isNaN(parsedLimit) && parsedLimit > 0) {
                searchForm.limit = parsedLimit
            }
        }
        if (routeQuery.knowledgeTypes) {
            searchForm.knowledgeTypes = String(routeQuery.knowledgeTypes)
                .split(',')
                .map((item) => item.trim())
                .filter(Boolean)
        }
        if (routeQuery.factTypes) {
            searchForm.factTypes = String(routeQuery.factTypes)
                .split(',')
                .map((item) => item.trim())
                .filter(Boolean)
        }
    }

    /**
     * 构建 RecallContext 请求体，空字段不传
     */
    function buildRecallContext() {
        const recallContext = {
            task: searchForm.task.trim(),
            limit: searchForm.limit,
        }

        if (searchForm.repository.trim()) {
            recallContext.repository = searchForm.repository.trim()
        }
        if (searchForm.module.trim()) {
            recallContext.module = searchForm.module.trim()
        }
        if (searchForm.project.trim()) {
            recallContext.project = searchForm.project.trim()
        }
        if (searchForm.currentFile.trim()) {
            recallContext.currentFile = searchForm.currentFile.trim()
        }
        if (searchForm.knowledgeTypes.length > 0) {
            recallContext.knowledgeTypes = [...searchForm.knowledgeTypes]
        }
        if (searchForm.factTypes.length > 0) {
            recallContext.factTypes = [...searchForm.factTypes]
        }

        return recallContext
    }

    /**
     * 构建 URL query，便于搜索后同步地址栏
     */
    function buildRouteQuery() {
        const query = {
            task: searchForm.task.trim(),
        }
        if (searchForm.repository.trim()) {
            query.repository = searchForm.repository.trim()
        }
        if (searchForm.module.trim()) {
            query.module = searchForm.module.trim()
        }
        if (searchForm.project.trim()) {
            query.project = searchForm.project.trim()
        }
        if (searchForm.currentFile.trim()) {
            query.currentFile = searchForm.currentFile.trim()
        }
        if (searchForm.limit !== SEARCH_DEFAULT_LIMIT) {
            query.limit = String(searchForm.limit)
        }
        if (searchForm.knowledgeTypes.length > 0
            && searchForm.knowledgeTypes.length !== SEARCH_DEFAULT_KNOWLEDGE_TYPES.length) {
            query.knowledgeTypes = searchForm.knowledgeTypes.join(',')
        }
        if (searchForm.factTypes.length > 0) {
            query.factTypes = searchForm.factTypes.join(',')
        }
        return query
    }

    /**
     * 执行语义搜索
     */
    async function executeSearch() {
        const taskText = searchForm.task.trim()
        if (!taskText) {
            throw new Error('请填写任务描述')
        }

        loading.value = true
        errorMessage.value = ''
        try {
            const response = await searchMemory(buildRecallContext())
            sessionId.value = response.sessionId || ''
            promptBlock.value = response.promptBlock || ''
            resultItems.value = (response.items || []).map((item, index) => ({
                ...item,
                rank: index + 1,
            }))
            searched.value = true
            return response
        } catch (error) {
            errorMessage.value = error.message || '搜索失败'
            sessionId.value = ''
            promptBlock.value = ''
            resultItems.value = []
            searched.value = true
            throw error
        } finally {
            loading.value = false
        }
    }

    /** 重置表单与结果 */
    function resetSearch() {
        searchForm.task = ''
        searchForm.repository = ''
        searchForm.module = ''
        searchForm.project = ''
        searchForm.currentFile = ''
        searchForm.knowledgeTypes = [...SEARCH_DEFAULT_KNOWLEDGE_TYPES]
        searchForm.factTypes = []
        searchForm.limit = SEARCH_DEFAULT_LIMIT
        sessionId.value = ''
        promptBlock.value = ''
        resultItems.value = []
        searched.value = false
        errorMessage.value = ''
    }

    /** 初始化时从外部 query 回填 */
    if (typeof options.initialQuery === 'function') {
        applyQueryToForm(options.initialQuery())
    }

    return {
        loading,
        searched,
        errorMessage,
        sessionId,
        promptBlock,
        resultItems,
        searchForm,
        applyQueryToForm,
        buildRouteQuery,
        executeSearch,
        resetSearch,
    }
}
