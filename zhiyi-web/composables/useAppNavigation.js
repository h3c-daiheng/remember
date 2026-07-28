/**
 * 全站导航状态：待确认草稿角标等
 */
import { fetchCaptureDraftList } from '~/services/capture.service'

export function useAppNavigation() {
    const pendingDraftCount = useState('pendingDraftCount', () => 0)

    /** 刷新 Capture 待确认数量，供侧栏/顶栏角标展示 */
    async function refreshPendingDraftCount() {
        try {
            const draftList = await fetchCaptureDraftList()
            syncPendingDraftCountFromList(draftList)
        } catch (error) {
            pendingDraftCount.value = 0
        }
    }

    /** 由业务页列表结果同步角标，避免与首屏列表重复请求 */
    function syncPendingDraftCountFromList(draftList) {
        pendingDraftCount.value = Array.isArray(draftList) ? draftList.length : 0
    }

    /** 角标值映射，供 navigation 配置 badgeKey 引用 */
    const badgeValues = computed(() => ({
        pendingDraftCount: pendingDraftCount.value,
    }))

    return {
        pendingDraftCount,
        badgeValues,
        refreshPendingDraftCount,
        syncPendingDraftCountFromList,
    }
}
