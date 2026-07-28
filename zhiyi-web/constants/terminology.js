/**
 * 用户可见文案术语表
 * 内部引擎使用 Remember / Capture / Review / Recall，界面统一映射为业务中文，避免困惑
 */

/** 产品飞轮标语：采集 → 确认 → 召回 */
export const PRODUCT_FLYWHEEL_TEXT = '自动采集 → 人工确认 → 智能召回'

/** 导航与页面标题 */
export const PAGE_LABELS = {
    /** 原 Capture 确认 */
    draftReview: '草稿确认',
    /** AI 审查记录页 */
    aiReview: 'AI 审查',
    /** 经验中心（兼容旧文案，列表已合并至记忆中心） */
    experience: '经验中心',
    /** 记忆中心 */
    memory: '记忆中心',
    /** Agent 召回请求列表 */
    recalls: '召回记录',
}

/** Agent API Key 权限展示名（与后端 permissionRecall / permissionRemember 对应） */
export const API_KEY_PERMISSION_LABELS = {
    recall: '召回经验',
    remember: '提交草稿',
}

/** Capture 闭环漏斗步骤展示名（内部 remember 对应 Agent 提交 Event） */
export const STATS_FUNNEL_STEP_LABELS = {
    remember: '采集',
    draft: '草稿',
    reviewed: '已审',
    approved: '采纳',
    published: '发布',
}

/** Capture 闭环漏斗转化率标签 */
export const STATS_FUNNEL_RATE_LABELS = {
    rememberToDraft: '采集 → 草稿',
    reviewedToApproved: '已审 → 采纳',
}
