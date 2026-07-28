import { getPageTitleByPath } from '~/config/routerMenus'
import { useTracker } from '~/composables/useTracker'

/**
 * 页面级埋点封装：默认 onMounted 上报 PV，并透出统一业务上报能力
 *
 * @param {object|string} options 页面标题字符串，或配置对象
 * @param {string} [options.pageTitle] 页面中文名（可选；未传则从 RouterMenus 按 path 解析）
 * @param {boolean} [options.isManual] true=不自动报 PV，由业务手动 reportPage
 * @param {(extra: object, route: object) => object} [options.buildEventInfo] 透传给 useTracker
 * @returns {{ reportPage: Function, reportEvent: Function, wrapTrackedAsync: Function, tracker: any }}
 */
export function usePageTracker(options = {}) {
    const normalized =
        typeof options === 'string'
            ? { pageTitle: options, isManual: false }
            : {
                  pageTitle: options.pageTitle ? String(options.pageTitle) : '',
                  isManual: options.isManual === true,
                  buildEventInfo: options.buildEventInfo,
              }

    const route = useRoute()
    const {
        reportEvent,
        buildEventInfo,
        wrapTrackedAsync,
        tracker: $tracker,
    } = useTracker({
        buildEventInfo: normalized.buildEventInfo,
    })

    /**
     * 解析当前页展示名：入参 > 调用时传入 > RouterMenus > path
     * @param {string} [title]
     * @returns {string}
     */
    const resolvePageTitle = (title) => {
        if (title) {
            return String(title)
        }
        if (normalized.pageTitle) {
            return normalized.pageTitle
        }
        return getPageTitleByPath(route.path) || route.path || ''
    }

    /**
     * 仅更新页面上下文，不上报
     * @param {string} [title]
     */
    const tracePageInfo = (title) => {
        try {
            $tracker?.tracePageInfo?.(
                {
                    pName: resolvePageTitle(title),
                    url: typeof location !== 'undefined' ? location.href : '',
                },
                false
            )
        } catch (_) {}
    }

    /**
     * 上报 PV
     * @param {string} [title]
     */
    const reportPage = (title) => {
        try {
            $tracker?.tracePageInfo?.({
                pName: resolvePageTitle(title),
                url: typeof location !== 'undefined' ? location.href : '',
            })
        } catch (_) {}
    }

    onMounted(() => {
        if (normalized.isManual) {
            tracePageInfo(normalized.pageTitle)
            return
        }
        reportPage(normalized.pageTitle)
    })

    return {
        reportPage,
        tracePageInfo,
        reportEvent,
        buildEventInfo,
        wrapTrackedAsync,
        tracker: $tracker,
    }
}
