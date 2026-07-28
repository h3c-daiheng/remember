import { joinURL } from 'ufo'
import { createError, defineEventHandler, getMethod, proxyRequest } from 'h3'

/**
 * 同源 /gonline-api/** 代理到 gonline 后端（供 SSR useRequestFetch 与本地 dev 使用）。
 * 目标根地址来自 runtimeConfig.gonlineApiServerTarget（环境变量 NUXT_GONLINE_API_SERVER_TARGET），
 * 须指向「不带 /gonline-api 前缀」的 gonline Java 服务。
 * 部署机 Node 若在容器内，勿用 127.0.0.1（会打到容器自身导致 502）。
 */
export default defineEventHandler(async (event) => {
    const { gonlineApiServerTarget: proxyUrl } = useRuntimeConfig(event)
    if (!proxyUrl) {
        throw createError({
            statusCode: 500,
            statusMessage:
                'NUXT_GONLINE_API_SERVER_TARGET / runtimeConfig.gonlineApiServerTarget is not configured',
        })
    }
    // 去掉 /gonline-api 前缀后再拼到上游根地址
    const upstreamPath = event.path.replace(/^\/gonline-api\/?/, '')
    const target = joinURL(String(proxyUrl), upstreamPath)
    if (process.env.NODE_ENV === 'development') {
        console.log('[gonline-api-proxy]', getMethod(event), event.path, '=>', target)
    }
    try {
        return await proxyRequest(event, target)
    } catch (error) {
        console.error(
            '[gonline-api-proxy] failed',
            getMethod(event),
            event.path,
            '=>',
            target,
            error?.message || error,
        )
        throw createError({
            statusCode: 502,
            statusMessage: `gonline API proxy failed (${target})`,
        })
    }
})
