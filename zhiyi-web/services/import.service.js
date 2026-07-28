/**
 * 文档导入 API：粘贴/上传文档经 AI 抽取为 Capture 草稿
 */
import { apiRequest } from '~/services/http'

/**
 * AI 抽取文档为结构化草稿预览
 */
export function extractDocumentImport(payload) {
    return apiRequest('/import/extract', {
        method: 'POST',
        body: JSON.stringify(payload),
    })
}

/**
 * 确认抽取结果并提交为 Capture 草稿
 */
export function submitDocumentImport(payload) {
    return apiRequest('/import/submit', {
        method: 'POST',
        body: JSON.stringify(payload),
    })
}
