/**
 * 闭环追踪搜索筛选：将前端表单字段写入 URLSearchParams
 * 供 knowledge selector、Capture 草稿搜索等下拉选择场景复用
 */

/** 支持的筛选字段，与后端 selector API 查询参数对齐 */
const TRACE_SEARCH_FILTER_KEYS = ['title', 'project', 'module', 'repository', 'tag']

/**
 * 将闭环追踪筛选条件追加到查询参数，空值自动跳过
 * @param {URLSearchParams} query 目标查询参数对象
 * @param {{ title?: string, project?: string, module?: string, repository?: string, tag?: string }} filters 筛选条件
 */
export function appendTraceSearchFilters(query, filters = {}) {
    for (const filterKey of TRACE_SEARCH_FILTER_KEYS) {
        const filterValue = String(filters[filterKey] || '').trim()
        if (filterValue) {
            query.set(filterKey, filterValue)
        }
    }
}
