import { createBrowserBatchTransport, TrackerType } from '@gonline/tracker-sdk'
import { getPageTitleByPath } from '~/config/routerMenus'
import { TRACKER_PV_EVENT_NAME, TRACKER_SYSTEM_CODE } from '~/config/tracker'
import { gonlineApiRequestSilent, resolveGonlineApiAbsoluteUrl } from '~/utils/gonlineRequest'
import { getStoredToken } from '~/utils/token'
import pkg from '../package.json'

export { TrackerType }

/** 端侧持久化访客标识的 localStorage key，便于未登录场景聚合埋点 */
const VISITOR_STORAGE_KEY = 'oa_visitor_key'

/**
 * 读取或生成浏览器访客 key
 * @returns {string}
 */
function getBrowserVisitorKey() {
    if (typeof window === 'undefined') {
        return ''
    }
    try {
        let visitorKey = localStorage.getItem(VISITOR_STORAGE_KEY)
        if (!visitorKey) {
            visitorKey = `V_${Math.random().toString(36).slice(2, 10)}${Date.now().toString(36)}`
            localStorage.setItem(VISITOR_STORAGE_KEY, visitorKey)
        }
        return visitorKey
    } catch (_) {
        return ''
    }
}

/**
 * 解析 UA 作为 browserVersion 落库文案
 * @returns {string}
 */
function resolveBrowserVersionText() {
    if (typeof navigator === 'undefined') {
        return ''
    }
    try {
        return String(navigator.userAgent || '').slice(0, 255)
    } catch (_) {
        return ''
    }
}

/**
 * 粗分端类型：desktop / mobile
 * @returns {string}
 */
function resolveClientType() {
    if (typeof navigator === 'undefined') {
        return 'desktop'
    }
    try {
        const userAgent = String(navigator.userAgent || '').toLowerCase()
        if (/mobile|android|iphone|ipad|ipod/.test(userAgent)) {
            return 'mobile'
        }
        return 'desktop'
    } catch (_) {
        return 'desktop'
    }
}

/**
 * package version 转 int（browser_point.web_version 为整型）
 * @returns {number}
 */
function resolveWebVersionNumber() {
    const parsed = parseInt(String(pkg.version || '0'), 10)
    return Number.isFinite(parsed) ? parsed : 0
}

/**
 * 智忆浏览器埋点：批量上报到 gonline /point/browser/save-batch，并携带 systemCode=3
 */
class ZhiyiTracker {
    constructor() {
        this.base = {}
        this.user = {}
        this.page = {}
        this.initFlag = false
        /** @type {ReturnType<createBrowserBatchTransport>|null} */
        this._batchTransport = null
    }

    /**
     * 收集浏览器/站点公共字段
     * @param {object} options 可覆盖 ip 等
     */
    getSystemInfo(options = {}) {
        if (!import.meta.client) {
            return
        }
        this.base = {
            webVersion: resolveWebVersionNumber(),
            browserVersion: resolveBrowserVersionText(),
            domain: window.location.origin || '',
            client: resolveClientType(),
            systemCode: TRACKER_SYSTEM_CODE,
            ...options,
        }
    }

    /**
     * 懒创建批量传输（隐藏页/卸载时 keepalive 兜底）
     */
    _ensureBatchTransport() {
        if (!import.meta.client) {
            return null
        }
        if (this._batchTransport) {
            return this._batchTransport
        }
        this._batchTransport = createBrowserBatchTransport({
            maxBatch: 10,
            maxWaitMs: 5000,
            postBatch: (items) =>
                gonlineApiRequestSilent('/point/browser/save-batch', {
                    method: 'POST',
                    body: JSON.stringify({ items }),
                }),
            resolveAbsoluteBatchUrl: () => resolveGonlineApiAbsoluteUrl('/point/browser/save-batch'),
            getExtraHeaders: () => {
                const headers = {}
                const token = getStoredToken()
                if (token) {
                    headers.Authorization = `Bearer ${token}`
                }
                return headers
            },
        })
        return this._batchTransport
    }

    /**
     * 注入登录用户信息
     * @param {{ userId?: string, userName?: string }} user
     */
    addUserInfo(user = {}) {
        if (!user || typeof user !== 'object') {
            return
        }
        this.user = {
            userId: user.userId != null ? String(user.userId) : '',
            userName: user.userName != null ? String(user.userName) : '',
        }
    }

    /**
     * 清空登录用户埋点上下文
     */
    clearUserInfo() {
        this.user = {}
    }

    /**
     * 注入当前页信息；默认自动报 PV
     * @param {object} params
     * @param {boolean} [autoReport=true]
     */
    tracePageInfo(params = {}, autoReport = true) {
        if (!import.meta.client) {
            return
        }
        const path = window.location.pathname || ''
        // 页面展示名优先取入参，否则从路由级 RouterMenus 解析
        const menuName = getPageTitleByPath(path)
        this.page = {
            url: window.location.href || '',
            path,
            pName: params.pName || menuName || path,
            ...params,
        }
        if (autoReport) {
            this.reportPage()
        }
    }

    /**
     * 上报页面浏览 PV
     */
    reportPage() {
        if (!this.page.url) {
            return
        }
        this.report({ evName: TRACKER_PV_EVENT_NAME }, TrackerType.page)
    }

    /**
     * 通用事件上报（失败静默）
     * @param {{ evName?: string, evInfo?: any }} eventParams
     * @param {string} [pointType]
     * @param {object} [otherParams]
     */
    report(eventParams = {}, pointType = TrackerType.btn, otherParams = {}) {
        try {
            if (!this.initFlag || !import.meta.client) {
                return false
            }
            const page =
                pointType === TrackerType.page
                    ? this.page
                    : { pName: this.page.pName, url: this.page.url, path: this.page.path }

            const params = {
                ...this.base,
                ...this.user,
                ...page,
                target: eventParams.evName,
                e: {
                    ...eventParams,
                },
                pointType,
                pageId: page.pName,
                t: Date.now(),
                ...otherParams,
            }

            const visitorKey = getBrowserVisitorKey()
            if (visitorKey) {
                params.visitorKey = visitorKey
            }

            const {
                webVersion,
                pointType: resolvedPointType,
                target,
                pageId,
                browserVersion,
                systemCode,
                ...restParams
            } = params

            const payload = {
                webVersion,
                pointType: resolvedPointType,
                target,
                pageId,
                browserVersion,
                // 系统标识：智忆固定为 3
                systemCode: systemCode != null ? systemCode : TRACKER_SYSTEM_CODE,
                sensitiveData: JSON.stringify(restParams || {}),
            }

            this._ensureBatchTransport()?.enqueue(payload)
            return true
        } catch (_) {
            return false
        }
    }

    /**
     * 初始化埋点（可重复调用以刷新 ip 等）
     * @param {object} options
     */
    init(options = {}) {
        this.getSystemInfo(options)
        this.initFlag = true
        this._ensureBatchTransport()
    }

    /**
     * 主动 flush 队列
     */
    flush() {
        try {
            this._batchTransport && this._batchTransport.flush && this._batchTransport.flush()
        } catch (_) {}
    }
}

export default ZhiyiTracker
