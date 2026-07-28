/**
 * useAsyncData 封装：客户端水合时优先复用有效 SSR payload；
 * null / 空壳视为无效缓存，清掉后触发客户端重拉（对齐 gonline-ssr 博客详情）。
 * @param {string} key useAsyncData 固定字符串 key
 * @param {(...args: unknown[]) => Promise<unknown>} handler 数据拉取函数
 * @param {import('nuxt/app').AsyncDataOptions} [options]
 */
export function useSsrAsyncData(key, handler, options = {}) {
    const { getCachedData: userGetCachedData, ...rest } = options
    return useAsyncData(key, handler, {
        ...rest,
        getCachedData(cachedKey, nuxtApp, context) {
            if (import.meta.server) return undefined
            const app = nuxtApp || useNuxtApp()
            if (typeof userGetCachedData === 'function') {
                const custom = userGetCachedData(cachedKey, nuxtApp, context)
                if (isUsableAsyncDataCache(custom)) {
                    return custom
                }
            }
            // 仅首屏水合复用 SSR payload；客户端路由切换必须重新请求
            if (!app.isHydrating) return undefined
            const cached = app.payload?.data?.[cachedKey] ?? app.static?.data?.[cachedKey]
            return resolveHydrationCachedData(cachedKey, app, cached)
        },
    })
}

/**
 * 判断 payload._errors 中是否存在真实 SSR 错误（排除 Nuxt 默认的 null 占位）。
 * @param {import('nuxt/app').NuxtApp} nuxtApp
 * @param {string} cachedKey
 */
function hasPayloadAsyncDataError(nuxtApp, cachedKey) {
    const errorValue = nuxtApp?.payload?._errors?.[cachedKey]
    return errorValue != null && errorValue !== false
}

/**
 * SSR 失败时清掉 _errors，让客户端水合可走重拉（Nuxt 默认有 error 则跳过重试）。
 * @param {import('nuxt/app').NuxtApp} nuxtApp
 * @param {string} cachedKey
 */
function clearPayloadAsyncDataError(nuxtApp, cachedKey) {
    if (!nuxtApp?.payload?._errors) return
    nuxtApp.payload._errors[cachedKey] = null
}

/**
 * 判断 payload 缓存是否可复用。
 * null、以及 pick 后的空壳 { data: null } 均视为无效，强制客户端重拉。
 * @param {unknown} cached
 */
function isUsableAsyncDataCache(cached) {
    if (cached === undefined || cached === null) {
        return false
    }
    if (
        typeof cached === 'object'
        && !Array.isArray(cached)
        && Object.prototype.hasOwnProperty.call(cached, 'data')
        && cached.data === null
    ) {
        return false
    }
    return true
}

/**
 * 客户端水合：有有效 SSR data 则复用；无效则清错误/空壳并返回 undefined 触发重拉。
 * @param {string} cachedKey
 * @param {import('nuxt/app').NuxtApp} nuxtApp
 * @param {unknown} [cached]
 */
function resolveHydrationCachedData(cachedKey, nuxtApp, cached) {
    if (isUsableAsyncDataCache(cached)) {
        return cached
    }
    if (
        nuxtApp?.payload?.data
        && Object.prototype.hasOwnProperty.call(nuxtApp.payload.data, cachedKey)
        && !isUsableAsyncDataCache(nuxtApp.payload.data[cachedKey])
    ) {
        nuxtApp.payload.data[cachedKey] = undefined
    }
    if (hasPayloadAsyncDataError(nuxtApp, cachedKey)) {
        clearPayloadAsyncDataError(nuxtApp, cachedKey)
    }
    return undefined
}
