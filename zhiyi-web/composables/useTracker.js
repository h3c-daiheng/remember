/**
 * 统一业务埋点：各页面共用，失败静默，不影响业务主流程
 */

import { TrackerType } from '~/utils/tracker'

/**
 * @param {object} [options]
 * @param {(extra: object, route: object) => object} [options.buildEventInfo] 自定义公共字段组装
 */
export function useTracker(options = {}) {
    const route = useRoute()
    const { $tracker } = useNuxtApp()

    /**
     * 组装事件附加字段
     * @param {object} [extra]
     */
    const buildEventInfo = (extra = {}) => {
        if (typeof options.buildEventInfo === 'function') {
            return options.buildEventInfo(extra, route) || {}
        }
        return extra || {}
    }

    /**
     * 上报业务事件
     * @param {string} eventName
     * @param {object} [eventInfo]
     * @param {string} [eventType]
     */
    const reportEvent = (eventName, eventInfo = {}, eventType = TrackerType.btn) => {
        try {
            const name = eventName != null ? String(eventName) : ''
            if (!name) {
                return
            }
            $tracker?.report?.(
                {
                    evName: name,
                    evInfo: buildEventInfo(eventInfo),
                },
                eventType,
            )
        } catch (_) {
            // 埋点失败不影响业务
        }
    }

    /**
     * 包装异步操作：开始前报一次，结束后报结果（含 success）
     * @param {(...args: any[]) => Promise<any>} runner
     * @param {{
     *   startEvent: string,
     *   resultEvent: string,
     *   buildInfo?: (...args: any[]) => object,
     *   resolveResultInfo?: (result: any, info: object) => object,
     * }} wrapOptions
     */
    const wrapTrackedAsync = (runner, wrapOptions) => {
        return async (...args) => {
            const baseInfo = typeof wrapOptions.buildInfo === 'function'
                ? (wrapOptions.buildInfo(...args) || {})
                : {}
            reportEvent(wrapOptions.startEvent, baseInfo)
            const result = await runner(...args)
            const resultInfo = typeof wrapOptions.resolveResultInfo === 'function'
                ? (wrapOptions.resolveResultInfo(result, baseInfo) || {})
                : { ...baseInfo, success: !!result }
            reportEvent(wrapOptions.resultEvent, resultInfo)
            return result
        }
    }

    return {
        reportEvent,
        buildEventInfo,
        wrapTrackedAsync,
        tracker: $tracker,
    }
}
