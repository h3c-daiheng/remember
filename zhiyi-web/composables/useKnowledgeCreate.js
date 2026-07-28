/**
 * 知识直达创建：各中心「新建」按钮调用 POST /knowledge 生成草稿后跳转编辑页
 */
import { ElMessage } from 'element-plus'
import { createKnowledge } from '~/services/knowledge.service'
import {
    buildEmptyKnowledgeTemplate,
    buildKnowledgeSaveRequest,
    resolveKnowledgeDraftPath,
} from '~/utils/knowledge'

export function useKnowledgeCreate() {
    const creating = ref(false)

    /**
     * 创建空白知识草稿并返回编辑页路径
     * @param {string} knowledgeType experience / rule / workflow / decision
     */
    async function createDraftAndResolvePath(knowledgeType) {
        creating.value = true
        try {
            const template = buildEmptyKnowledgeTemplate(knowledgeType)
            const knowledgeId = await createKnowledge(buildKnowledgeSaveRequest(template))
            return resolveKnowledgeDraftPath(knowledgeType, knowledgeId)
        } finally {
            creating.value = false
        }
    }

    /**
     * 创建空白知识草稿并跳转编辑页
     */
    async function createDraftAndNavigate(router, knowledgeType) {
        try {
            const draftPath = await createDraftAndResolvePath(knowledgeType)
            await router.push(draftPath)
        } catch (error) {
            ElMessage.error(error.message || '创建失败')
            throw error
        }
    }

    return {
        creating,
        createDraftAndResolvePath,
        createDraftAndNavigate,
    }
}
