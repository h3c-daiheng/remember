/**
 * Memory 闭环追踪：查询 draftId / knowledgeId 对应的 Remember → Recall 链路
 */
import { ElMessage } from 'element-plus'
import { fetchMemoryTrace } from '~/services/memory-trace.service'

export function useMemoryTrace() {
    const loading = ref(false)
    const traceResult = ref(null)
    const searchForm = reactive({
        draftId: '',
        knowledgeId: '',
    })

    /**
     * 执行 trace 查询，draftId 与 knowledgeId 至少填一项
     */
    async function loadTrace(query = {}) {
        const draftId = query.draftId ?? searchForm.draftId
        const knowledgeId = query.knowledgeId ?? searchForm.knowledgeId
        const normalizedDraftId = String(draftId || '').trim()
        const normalizedKnowledgeId = String(knowledgeId || '').trim()

        if (!normalizedDraftId && !normalizedKnowledgeId) {
            ElMessage.warning('请选择 Capture 草稿或知识')
            return null
        }

        loading.value = true
        traceResult.value = null
        try {
            const result = await fetchMemoryTrace({
                draftId: normalizedDraftId || undefined,
                knowledgeId: normalizedKnowledgeId || undefined,
            })
            traceResult.value = result
            return result
        } catch (error) {
            ElMessage.error(error.message || '查询闭环追踪失败')
            return null
        } finally {
            loading.value = false
        }
    }

    /**
     * 从 URL query 同步搜索表单并自动查询；无参数时默认加载最近一次 Recall
     */
    async function loadTraceFromRoute(route) {
        const draftId = route.query.draftId ? String(route.query.draftId) : ''
        const knowledgeId = route.query.knowledgeId ? String(route.query.knowledgeId) : ''
        searchForm.draftId = draftId
        searchForm.knowledgeId = knowledgeId
        if (draftId || knowledgeId) {
            await loadTrace({ draftId, knowledgeId })
            return
        }
        await loadLatestTrace()
    }

    /**
     * 无 draftId / knowledgeId 时，加载工作空间最近一次 Recall 的闭环追踪
     */
    async function loadLatestTrace() {
        loading.value = true
        traceResult.value = null
        try {
            const result = await fetchMemoryTrace()
            traceResult.value = result || null
            return result
        } catch (error) {
            ElMessage.error(error.message || '查询闭环追踪失败')
            return null
        } finally {
            loading.value = false
        }
    }

    function resetTrace() {
        traceResult.value = null
    }

    return {
        loading,
        traceResult,
        searchForm,
        loadTrace,
        loadLatestTrace,
        loadTraceFromRoute,
        resetTrace,
    }
}
