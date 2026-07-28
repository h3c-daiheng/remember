import { PAGE_LABELS } from '~/constants/terminology'

/**
 * 全站导航配置：MVP 业务页 + 路线图占位入口
 */

/** @typedef {Object} NavItem
 * @property {string} path
 * @property {string} label
 * @property {string} description
 * @property {string} icon
 * @property {string} [badgeKey]
 * @property {boolean} [placeholder] - 是否为占位页（展示「即将上线」）
 * @property {string} [phase]
 */

/** @typedef {Object} NavSection
 * @property {string} title
 * @property {NavItem[]} items
 */

/**
 * 导航项视觉分组：核心功能使用品牌色，探索与设置使用中性色
 * 侧栏激活态与待审徽标会覆盖默认分组样式
 */
export const NAV_ITEM_TONE_MAP = {
    '/dashboard': 'brand',
    '/memory': 'brand',
    '/capture': 'brand',
    '/governance': 'brand',
    '/ai-review': 'brand',
    '/import': 'brand',
    '/search': 'neutral',
    '/trace': 'neutral',
    '/recalls': 'neutral',
    '/graph': 'neutral',
    '/stats': 'neutral',
    // 工作空间管理在主站，侧栏外链用独立 key
    'gonline-workspace-settings': 'neutral',
}

/** 登录后主应用侧栏分组菜单（核心分组顺序：工作台 → 记忆 → 草稿 → 治理） */
export const APP_SIDEBAR_SECTIONS = [
    {
        title: '核心',
        items: [
            {
                path: '/dashboard',
                label: '工作台',
                description: '概览与快捷入口',
                icon: 'House',
            },
            {
                path: '/memory',
                label: '记忆中心',
                description: '经验、规则、流程与决策',
                icon: 'Collection',
            },
            {
                path: '/capture',
                label: PAGE_LABELS.draftReview,
                description: 'Agent 提交的待审草稿',
                icon: 'DocumentChecked',
                badgeKey: 'pendingDraftCount',
            },
            {
                path: '/governance',
                label: '记忆治理',
                description: '已发布记忆重复检测与处置',
                icon: 'SetUp',
            },
            {
                path: '/ai-review',
                label: PAGE_LABELS.aiReview,
                description: '审查记录与轨迹',
                icon: 'Cpu',
            },
            {
                path: '/import',
                label: '文档导入',
                description: '粘贴或上传文档，AI 抽取经验',
                icon: 'Upload',
            },
        ],
    },
    {
        title: '探索',
        items: [
            {
                path: '/search',
                label: '经验搜索',
                description: '按任务上下文语义检索',
                icon: 'Search',
            },
            {
                path: '/trace',
                label: '闭环追踪',
                description: '验证 Remember → Recall 全链路',
                icon: 'Connection',
            },
            {
                path: '/recalls',
                label: PAGE_LABELS.recalls,
                description: 'Agent 全部召回请求记录',
                icon: 'List',
            },
            {
                path: '/stats',
                label: '数据统计',
                description: '召回用量与质量指标',
                icon: 'DataAnalysis',
            },
            {
                path: '/graph',
                label: '经验图谱',
                description: '经验关系网络',
                icon: 'Share',
            },
        ],
    },
    {
        title: '设置',
        items: [
            {
                // 侧栏「工作空间」跳转主站管理页（新窗口），不再进入智忆本地 /settings
                path: 'gonline-workspace-settings',
                label: '工作空间',
                description: '成员与协作管理',
                icon: 'Setting',
                external: true,
                openInNewWindow: true,
            },
        ],
    },
]

/** 扁平化菜单，供顶栏小屏 Tab 使用（仅核心 + 常用占位） */
export const APP_HEADER_ITEMS = APP_SIDEBAR_SECTIONS.flatMap((section) => section.items)

/** 不需要侧栏的公开路由 */
export const PUBLIC_ROUTE_PREFIXES = ['/', '/login']

/** @deprecated 兼容旧引用，等价于核心分组 */
export const APP_SIDEBAR_ITEMS = APP_SIDEBAR_SECTIONS[0].items
