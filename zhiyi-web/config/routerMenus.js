import { PAGE_LABELS } from '~/constants/terminology'

/**
 * 智忆路由级菜单/页面配置
 * Tip: 用于埋点页面展示名（pName）；含子路径时按最长前缀匹配
 */
export const RouterMenus = [
    {
        title: '首页',
        path: '/',
    },
    {
        title: '关于我们',
        path: '/about',
        hidden: true,
    },
    {
        title: '联系我们',
        path: '/contact',
        hidden: true,
    },
    {
        title: '源码授权',
        path: '/source',
        hidden: true,
    },
    {
        title: '常见问题',
        path: '/faq',
        hidden: true,
    },
    {
        title: '登录',
        path: '/login',
        hidden: true,
    },
    {
        title: '工作台',
        path: '/dashboard',
    },
    {
        title: '快速指南',
        path: '/dashboard/guide',
        hidden: true,
    },
    {
        title: PAGE_LABELS.memory,
        path: '/memory',
    },
    {
        title: '编辑经验',
        path: '/experience/drafts',
        hidden: true,
    },
    {
        title: '编辑规范',
        path: '/rule/drafts',
        hidden: true,
    },
    {
        title: '编辑决策',
        path: '/decision/drafts',
        hidden: true,
    },
    {
        title: PAGE_LABELS.draftReview,
        path: '/capture',
    },
    {
        title: PAGE_LABELS.aiReview,
        path: '/ai-review',
    },
    {
        title: '文档导入',
        path: '/import',
    },
    {
        title: '经验搜索',
        path: '/search',
    },
    {
        title: '闭环追踪',
        path: '/trace',
    },
    {
        title: PAGE_LABELS.recalls,
        path: '/recalls',
    },
    {
        title: '数据统计',
        path: '/stats',
    },
    {
        title: '经验图谱',
        path: '/graph',
    },
    {
        title: 'Agent API Key',
        path: '/settings',
    },
]

/**
 * 去掉尾斜杠，统一 path 比较口径（首页保留 /）
 * @param {string} path
 * @returns {string}
 */
function normalizePath(path) {
    if (!path) {
        return ''
    }
    const trimmed = String(path).trim()
    if (trimmed !== '/' && trimmed.endsWith('/')) {
        return trimmed.slice(0, -1)
    }
    return trimmed
}

/**
 * 按 path 查找路由菜单项（精确匹配）
 * @param {string} path
 * @param {Array} [menus=RouterMenus]
 * @returns {object|null}
 */
export function findRouterMenuByPath(path, menus = RouterMenus) {
    const target = normalizePath(path)
    for (const item of menus || []) {
        if (normalizePath(item.path) === target) {
            return item
        }
    }
    return null
}

/**
 * 按最长前缀匹配路由菜单（详情页 /experience/xxx 等命中对应编辑/详情菜单）
 * @param {string} path
 * @param {Array} [menus=RouterMenus]
 * @returns {object|null}
 */
export function findRouterMenuByPrefix(path, menus = RouterMenus) {
    const target = normalizePath(path)
    if (!target) {
        return null
    }

    let bestMatch = null
    let bestLength = -1
    for (const item of menus || []) {
        const menuPath = normalizePath(item.path)
        if (!menuPath || menuPath === '/') {
            continue
        }
        if (target === menuPath || target.startsWith(`${menuPath}/`)) {
            if (menuPath.length > bestLength) {
                bestMatch = item
                bestLength = menuPath.length
            }
        }
    }
    return bestMatch
}

/**
 * 按 path 取页面 document / SEO title
 * @param {string} path
 * @returns {string}
 */
export function getRouterMenuTitleByPath(path) {
    const item = findRouterMenuByPath(path) || findRouterMenuByPrefix(path)
    if (!item) {
        return ''
    }
    return item.title || ''
}

/**
 * 按 path 取页面展示名（埋点等场景用）
 * @param {string} path
 * @returns {string}
 */
export function getPageTitleByPath(path) {
    return getRouterMenuTitleByPath(path)
}
