/**
 * 全局轻量 Toast：统一 ElMessage 默认项（plain / 时长 / 样式类）。
 * 位置（顶部居中）与视觉由 assets/css/main.css 中的 .app-toast 控制。
 */
import { isVNode } from 'vue'
import { ElMessage } from 'element-plus'

/** Toast 根节点 class，供全局样式定位与换肤 */
const TOAST_CLASS = 'app-toast'

/**
 * 各类型提示的默认行为：成功短、错误稍长并可关闭
 */
const TYPE_DEFAULTS = {
    success: {
        duration: 2000,
        plain: true,
        customClass: TOAST_CLASS,
    },
    warning: {
        duration: 2500,
        plain: true,
        customClass: TOAST_CLASS,
    },
    info: {
        duration: 2500,
        plain: true,
        customClass: TOAST_CLASS,
    },
    error: {
        duration: 3500,
        plain: true,
        showClose: true,
        customClass: TOAST_CLASS,
    },
}

/**
 * 合并调用方参数与类型默认项；保留业务侧显式覆盖能力
 * @param {'success'|'warning'|'info'|'error'} type 提示类型
 * @param {string|object|import('vue').VNode|Function|undefined} options 原始入参
 */
function mergeMessageOptions(type, options) {
    const typeDefaults = TYPE_DEFAULTS[type] || TYPE_DEFAULTS.info
    // 字符串 / VNode / 渲染函数：作为 message 正文，再套上 Toast 默认项
    if (
        typeof options === 'string'
        || options == null
        || typeof options === 'function'
        || isVNode(options)
    ) {
        return {
            ...typeDefaults,
            message: options ?? '',
        }
    }
    if (typeof options !== 'object' || Array.isArray(options)) {
        return {
            ...typeDefaults,
            message: options,
        }
    }
    const customClass = [typeDefaults.customClass, options.customClass]
        .filter(Boolean)
        .join(' ')
    return {
        ...typeDefaults,
        ...options,
        customClass,
    }
}

/**
 * 包装 ElMessage 某一类型方法，注入全局 Toast 默认项
 * @param {'success'|'warning'|'info'|'error'} type 提示类型
 */
function patchMessageMethod(type) {
    const original = ElMessage[type].bind(ElMessage)
    ElMessage[type] = (options) => original(mergeMessageOptions(type, options))
}

export default defineNuxtPlugin(() => {
    ;['success', 'warning', 'info', 'error'].forEach(patchMessageMethod)
})
