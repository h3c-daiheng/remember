/**
 * 关于我们 / 联系我们页 — 字典读取与 JSON 解析
 * 页面可见内容仅取 dict JSON 的 content（一整篇 Markdown），title/description/keywords 供 SEO
 * GET /dict/info/dict-code?dictCode=site_about_zhiyi | site_contact_zhiyi
 */
import {
    SITE_ABOUT_DICT_CODE,
    SITE_CONTACT_DICT_CODE,
} from '~/config/sitePageDict'

export { SITE_ABOUT_DICT_CODE, SITE_CONTACT_DICT_CODE }

/** 解析接口返回的字典行列表 */
function unwrapDictList(response) {
    if (!response?.success && response?.data == null && !Array.isArray(response)) {
        return []
    }
    const data = response?.data ?? response
    return Array.isArray(data) ? data : []
}

/** 判断字典行是否启用 */
function isDictRowEnabled(row) {
    const enabledValue = row?.enabled
    return enabledValue === true || enabledValue === 1 || enabledValue === '1'
}

/** 解析 dict_info.value JSON 对象 */
function parseDictJsonValue(rawValue) {
    if (rawValue == null || String(rawValue).trim() === '') {
        return null
    }
    try {
        const parsed = JSON.parse(String(rawValue))
        return parsed && typeof parsed === 'object' ? parsed : null
    } catch {
        return null
    }
}

/** 取首条有效字典行（单页场景每 dict_data 仅一条 dict_info） */
function pickFirstEnabledDictRow(dictRows) {
    if (!Array.isArray(dictRows)) {
        return null
    }
    return dictRows.find((row) => isDictRowEnabled(row) && row?.value) ?? null
}

/**
 * 解析站点静态页字典 JSON（关于 / 联系结构一致）
 * @param {object|null} dictRow 字典行
 * @param {string} defaultSlug 默认 slug
 */
function parseSitePageFromDictRow(dictRow, defaultSlug) {
    const parsed = parseDictJsonValue(dictRow?.value)
    if (!parsed) {
        return null
    }
    const title = String(parsed.title ?? dictRow?.name ?? '').trim()
    const content = String(parsed.content ?? '').trim()
    if (!title || !content) {
        return null
    }
    return {
        title,
        slug: String(parsed.slug ?? defaultSlug).trim(),
        content,
        description: String(parsed.description ?? '').trim(),
        keywords: String(parsed.keywords ?? '').trim(),
    }
}

/** 从字典行解析关于我们页 JSON */
export function parseAboutPageFromDictRow(dictRow) {
    return parseSitePageFromDictRow(dictRow, 'about')
}

/** 从字典行解析联系我们页 JSON（整篇 content Markdown） */
export function parseContactPageFromDictRow(dictRow) {
    return parseSitePageFromDictRow(dictRow, 'contact')
}

/** SSR/CSR 拉取关于我们页配置 */
export async function fetchSiteAboutPage(configApi, dictCode = SITE_ABOUT_DICT_CODE) {
    try {
        const response = await configApi.getDictByName1(dictCode)
        const dictRows = unwrapDictList(response)
        return parseAboutPageFromDictRow(pickFirstEnabledDictRow(dictRows))
    } catch {
        return null
    }
}

/** SSR/CSR 拉取联系我们页配置 */
export async function fetchSiteContactPage(configApi, dictCode = SITE_CONTACT_DICT_CODE) {
    try {
        const response = await configApi.getDictByName1(dictCode)
        const dictRows = unwrapDictList(response)
        return parseContactPageFromDictRow(pickFirstEnabledDictRow(dictRows))
    } catch {
        return null
    }
}
