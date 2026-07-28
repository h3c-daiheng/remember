import { gonlineApiRequestSilent } from '~/utils/gonlineRequest'

/**
 * 按 dict_data.code 拉取字典项列表（走 gonline 网关 /dict/info/dict-code）
 * 失败时返回 { success: false }，由页面侧 fallback 本地常量
 */
async function getDictByName1(dictCode) {
    const encodedCode = encodeURIComponent(String(dictCode || ''))
    const response = await gonlineApiRequestSilent(
        `/dict/info/dict-code?dictCode=${encodedCode}`,
        { method: 'GET' },
    )
    if (!response || Number(response.code) !== 200) {
        return { success: false, data: null }
    }
    return { success: true, data: response.data }
}

export default {
    getDictByName1,
}
