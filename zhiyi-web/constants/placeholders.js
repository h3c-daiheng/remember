/**
 * 未上线功能的占位配置，与产品路线图 Phase 对齐
 */

/** @typedef {Object} FeaturePlaceholderConfig
 * @property {string} title
 * @property {string} subtitle
 * @property {string} phase - MVP | Beta | V2 | GA
 * @property {string[]} capabilities - 规划能力列表
 * @property {string} [relatedApi] - 关联后端 API 说明
 */

/** @type {Record<string, FeaturePlaceholderConfig>} */
export const FEATURE_PLACEHOLDERS = {
    search: {
        title: '经验搜索',
        subtitle: '基于任务上下文的语义检索，同任务关联优先于泛化相似度',
        phase: 'Beta',
        capabilities: [
            '按任务、仓库、模块筛选经验',
            '任务上下文可视化调试',
            '搜索结果预览与效果反馈',
        ],
        relatedApi: 'POST /api/memory/search',
    },
    decision: {
        title: '决策中心',
        subtitle: '沉淀「为什么这样做、为什么不用另一种方案」的结构化 Decision',
        phase: 'V2',
        capabilities: [
            'Decision 类型 Knowledge 独立浏览',
            '与 Experience 关联展示决策依据',
            'Agent 召回时优先注入决策类经验块',
        ],
        relatedApi: 'knowledge_type = decision',
    },
    graph: {
        title: '经验图谱',
        subtitle: '经验之间的关联网络，支持从 Redis → 缓存 → 部署路径导航',
        capabilities: [
            'knowledge_relation 关系可视化',
            '模块 / 标签 / Artifact 跳转',
            'Graph 检索与 Ranking 增强',
        ],
        relatedApi: 'GET /api/graph?centerId=&depth=2',
        placeholder: false,
    },
    stats: {
        title: '数据统计',
        subtitle: '召回、沉淀与反馈的用量与质量指标，驱动经验飞轮',
        phase: 'Beta',
        capabilities: [
            '每周有效召回次数（北极星指标）',
            '草稿到发布的转化率',
            '工作空间用量与召回效果统计',
        ],
        relatedApi: 'usage_daily + memory_feedback 聚合',
    },
    settings: {
        title: 'Agent API Key',
        subtitle: '密钥管理已迁至主站工作空间设置；本页仅作入口引导',
        phase: 'Beta',
        capabilities: [
            '主站签发（召回与提交分权）',
            '主站单 Key 吊销',
        ],
        relatedApi: 'gonline /workspace/api-keys',
        placeholder: false,
    },
}

/**
 * 经验详情页「相关内容」占位块
 */
export const KNOWLEDGE_RELATED_PLACEHOLDERS = [
    {
        key: 'relatedExperience',
        title: '相关经验',
        description: '同项目 / 同模块 / 同标签的关联经验，按 Task Match 与语义相似度排序',
        phase: 'Beta',
    },
    {
        key: 'relatedDecision',
        title: '关联决策',
        description: '同模块 experience ↔ decision 的图谱关联，支持自动建边与人工关联',
        phase: 'V2',
    },
    {
        key: 'timeline',
        title: '版本时间线',
        description: '经验版本追溯、失效标记与回滚记录',
        phase: 'V2',
    },
    {
        key: 'feedback',
        title: '召回反馈',
        description: 'helpful / outdated / wrong 等 Agent 与用户反馈统计',
        phase: 'Beta',
    },
]
