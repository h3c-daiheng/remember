/**
 * 经验详情页业务逻辑
 */
import { toValue } from 'vue'
import { fetchKnowledgeDetail } from '~/services/knowledge.service'
import { groupFactsByType } from '~/utils/knowledge'

/**
 * @param {import('vue').MaybeRefOrGetter<string|number>} knowledgeIdSource 路由参数或响应式 ID
 */
export function useKnowledgeDetail(knowledgeIdSource) {
    const loading = ref(false)
    const detail = ref(null)

    /** 按 Fact 类型分组后的展示数据 */
    const factGroups = computed(() => groupFactsByType(detail.value?.facts))

    /** 解析当前应请求的经验 ID */
    function resolveKnowledgeId() {
        const knowledgeId = toValue(knowledgeIdSource)
        if (knowledgeId === undefined || knowledgeId === null || knowledgeId === '') {
            return null
        }
        return knowledgeId
    }

    /**
     * 加载经验详情
     * @param {{ silent?: boolean }} options silent 为 true 时保留当前内容，后台拉取新数据
     */
    async function loadDetail(options = {}) {
        const silent = options.silent === true
        const knowledgeId = resolveKnowledgeId()
        if (!knowledgeId) {
            detail.value = null
            throw new Error('经验 ID 无效')
        }
        if (!silent) {
            loading.value = true
        }
        try {
            detail.value = await fetchKnowledgeDetail(knowledgeId)
        } catch (error) {
            detail.value = null
            throw error
        } finally {
            if (!silent) {
                loading.value = false
            }
        }
    }

    return {
        loading,
        detail,
        factGroups,
        loadDetail,
    }
}
