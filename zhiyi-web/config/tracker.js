/**
 * 智忆埋点常量
 * system_code：1-portal；2-zhijian；3-zhiyi；4-huajing
 */
export const TRACKER_SYSTEM_CODE = 3

/** 页面浏览事件名（与 gonline PC / 小程序口径一致） */
export const TRACKER_PV_EVENT_NAME = '页面浏览'

/** 首页营销埋点事件名 */
export const HOME_TRACK_EVENTS = {
    CLICK_LOGIN: '点击登录',
    CLICK_ENTER_DASHBOARD: '进入工作台',
    CLICK_PRIMARY_CTA: '点击首页主按钮',
    CLICK_HOME_NAV: '首页锚点导航',
}

/** 站点通用埋点事件名（顶栏、侧栏、FAQ 等） */
export const SITE_TRACK_EVENTS = {
    CLICK_LOGIN: '点击登录',
    CLICK_LOGOUT: '点击退出',
    CLICK_ENTER_DASHBOARD: '进入工作台',
    CLICK_SIDEBAR_NAV: '侧栏导航',
    CLICK_TRY_NOW: '立即体验',
    CLICK_SOURCE_LICENSE: '点击源码授权',
    EXPAND_FAQ: '展开常见问题',
}

/** 经验中心埋点事件名 */
export const EXPERIENCE_TRACK_EVENTS = {
    CREATE: '新建经验',
    VIEW_DETAIL: '查看经验详情',
    EDIT: '编辑经验',
    PUBLISH: '发布经验',
    DELETE: '删除经验',
}

/** 规则中心埋点事件名 */
export const RULE_TRACK_EVENTS = {
    CREATE: '新建规则',
    VIEW_DETAIL: '查看规则详情',
    EDIT: '编辑规则',
    PUBLISH: '发布规则',
    DELETE: '删除规则',
}

/** 决策中心埋点事件名 */
export const DECISION_TRACK_EVENTS = {
    CREATE: '新建决策',
    VIEW_DETAIL: '查看决策详情',
    EDIT: '编辑决策',
    PUBLISH: '发布决策',
    DELETE: '删除决策',
}

/** 记忆中心埋点事件名（合并经验/规则/决策列表） */
export const MEMORY_TRACK_EVENTS = {
    CREATE: '新建记忆',
    VIEW_DETAIL: '查看记忆详情',
}

/** 草稿确认埋点事件名 */
export const CAPTURE_TRACK_EVENTS = {
    APPROVE: '确认草稿',
    REJECT: '驳回草稿',
    VIEW_DETAIL: '查看待审草稿',
}

/** 文档导入埋点事件名 */
export const IMPORT_TRACK_EVENTS = {
    SUBMIT: '提交文档导入',
    UPLOAD_FILE: '上传导入文件',
}

/** 搜索与追踪埋点事件名 */
export const SEARCH_TRACK_EVENTS = {
    SEARCH: '执行经验搜索',
}

export const TRACE_TRACK_EVENTS = {
    RUN_TRACE: '执行闭环追踪',
}
