/**
 * 工作空间 Recall 上下文字段可选值 API
 */
import { apiRequest } from '~/services/http'

/**
 * 获取仓库 / 项目 / 模块 / 标签下拉选项
 */
export function fetchWorkspaceContextOptionsRequest(workspaceId) {
    return apiRequest(`/workspace/${workspaceId}/context-options`)
}
