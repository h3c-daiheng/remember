/**
 * 首页文案与区块配置
 * 未登录：营销落地页；已登录：工作台概览
 */

/** 首页顶栏锚点导航（与营销页各区块标题一一对应） */
export const HOME_ANCHOR_NAV = [
    { label: '经验不会积累', targetId: 'home-pain' },
    { label: '经验飞轮', targetId: 'home-flywheel' },
    { label: '核心能力', targetId: 'home-features' },
    { label: '为谁而建', targetId: 'home-audience' },
    { label: '不是 AI 版知乎', targetId: 'home-platform-compare' },
    { label: '接入 Agent', targetId: 'home-integration' },
    { label: '源码授权', targetId: 'home-source-license' },
]

/** Hero 区主标题与副标题 */
export const HOME_HERO = {
    badge: 'AI Experience Cloud',
    title: 'AI 时代的经验基础设施',
    subtitle: '把真实做过的事，沉淀成可被人和 AI 持续复用的经验。让 Agent 像工作五年的老员工一样工作。',
    primaryCta: '免费开始',
    loggedInPrimaryCta: '进入工作台',
    secondaryCta: '了解产品飞轮',
}

/** 痛点区块：AI 时代经验缺失的三类典型问题 */
export const HOME_PAIN_POINTS = [
    {
        title: 'AI 不会积累经验',
        description: '每次对话结束，Context 清空。同类 Bug、同类方案，团队与 Agent 反复踩坑。',
        icon: 'Refresh',
    },
    {
        title: 'Wiki 写了 Agent 也用不好',
        description: '文档为人阅读优化，Recall 噪音大，缺少「为什么」与适用条件，Agent 难以按任务精准取用。',
        icon: 'Document',
    },
    {
        title: '组织记忆随人走',
        description: '架构决策、排障经验、上线教训只存在于个人脑中，人员流动后团队从零开始。',
        icon: 'User',
    },
]

/** 产品飞轮四步说明 */
export const HOME_FLYWHEEL_STEPS = [
    {
        step: '01',
        title: '自动采集',
        description: 'Agent 完成任务后 Remember 草稿，或从 Git / 对话中自动提炼经验。',
    },
    {
        step: '02',
        title: '人工确认',
        description: '负责人 Review 草稿，校验可信度后发布为结构化 Experience。',
    },
    {
        step: '03',
        title: '智能召回',
        description: '新任务开始前，Agent 按 Task Match 自动 Recall 相关经验与决策。',
    },
    {
        step: '04',
        title: '持续进化',
        description: 'Feedback 驱动 Ranking，失效经验自动标记，越用越准。',
    },
]

/** 核心能力卡片 */
export const HOME_FEATURES = [
    {
        title: '结构化经验卡',
        description: '背景、方案、结果、适用/失效条件——不是 Markdown 堆叠，而是 Agent 可消费的 Experience。',
        tag: 'Experience',
    },
    {
        title: '草稿确认',
        description: 'Agent 提交的 Remember 草稿经人工 Review 后发布，保证组织 Memory 的可信度。',
        tag: 'Review',
    },
    {
        title: '语义召回',
        description: '按任务上下文 Recall，而非关键词搜索；Fact Block 级精准注入 Agent Context。',
        tag: 'Recall',
    },
    {
        title: '决策与规则',
        description: '记录「为什么这样做」，让 Agent 永远知道架构决策与团队规范。',
        tag: 'Decision',
    },
    {
        title: 'MCP 原生接入',
        description: 'Cursor、Claude Code、自研 Agent 通过 MCP / API 统一访问同一套经验层。',
        tag: 'MCP',
    },
    {
        title: '公开经验池',
        description: '通用最佳实践可设为公开，Fork 到本 Workspace；未来成为 AI 时代的经验社区。',
        tag: 'Open',
    },
]

/** 与旧时代平台的对比（C 端长期愿景） */
export const HOME_PLATFORM_COMPARE = {
    oldLabel: 'CSDN / 知乎',
    newLabel: '智忆',
    rows: [
        { dimension: '内容形态', old: '文章、问答、帖子', new: '结构化 Experience' },
        { dimension: '消费方式', old: '人读全文', new: '人浏览 + Agent 按任务 Recall' },
        { dimension: '质量信号', old: '点赞、粉丝', new: '验证、采纳、失效标记' },
        { dimension: 'AI 关系', old: '外挂搜索 / 摘要', new: 'MCP 一等公民，经验即 Context' },
    ],
}

/** 接入生态展示 */
export const HOME_INTEGRATIONS = [
    { name: 'Cursor', description: 'IDE 内 Recall / Remember' },
    { name: 'Claude Code', description: 'MCP 经验接入' },
    { name: 'VS Code', description: '插件浏览与搜索' },
    { name: '自研 Agent', description: 'Memory API / SDK' },
]

/** 面向对象 */
export const HOME_AUDIENCES = [
    {
        title: '研发团队',
        description: '10~200 人、已全面使用 AI Coding，需要组织级 Memory 与多 Agent 一致。',
        highlight: true,
    },
    {
        title: '个人开发者',
        description: 'Free 档体验 Capture + Recall 闭环，沉淀个人最佳实践。',
        highlight: false,
    },
    {
        title: '经验创作者',
        description: '未来将公开沉淀通用经验，被全网 Agent 与人类共同消费。',
        highlight: false,
    },
]
