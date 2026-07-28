/**
 * 标记客户端已完成首次 mount（水合结束）
 * 依赖 Cookie 的 UI 须等 mount 后再按鉴权结果渲染，避免 SSR 与客户端 DOM 不一致
 */
export function useClientMounted() {
    const clientMounted = useState('clientMounted', () => false)

    if (import.meta.client && !clientMounted.value) {
        onMounted(() => {
            clientMounted.value = true
        })
    }

    return clientMounted
}
