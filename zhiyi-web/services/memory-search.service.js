/**
 * Memory Search API：语义检索已发布知识
 * 对应后端 MemoryApiController POST /memory/search（与 Recall 共用引擎）
 */
import { apiRequest } from '~/services/http'

/**
 * 按任务上下文语义搜索经验 / Rule / Workflow
 * @param {object} recallContext RecallContext 请求体
 * @returns {Promise<{ sessionId: string, items: Array, promptBlock: string }>}
 */
export function searchMemory(recallContext) {
    return apiRequest('/memory/search', {
        method: 'POST',
        body: JSON.stringify(recallContext),
    })
}
