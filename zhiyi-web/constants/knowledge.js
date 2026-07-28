/**
 * Knowledge 领域常量：Fact Block、Artifact、生命周期等枚举与中文标签
 * 与后端 knowledge_fact.block_type、knowledge_artifact 字段对齐
 */

/** Fact Block 类型（Recall 可按 type 筛选返回） */
export const FACT_BLOCK_TYPES = [
    'observation',
    'decision',
    'constraint',
    'rule',
    'evidence',
    'action',
    'outcome',
]

/** Fact Block 类型中文标签 */
export const FACT_TYPE_LABELS = {
    observation: '观察',
    decision: '决策',
    constraint: '约束',
    rule: '规则',
    evidence: '依据',
    action: '行动',
    outcome: '结果',
}

/** Fact Block 类型填写提示，用于草稿编辑空状态快捷添加 */
export const FACT_TYPE_HINTS = {
    observation: '描述具体现象、环境与报错信息',
    decision: '记录方案取舍与「为什么不用其他方案」',
    constraint: '标注适用条件、边界与前置要求',
    rule: '编写规范条文与执行标准',
    evidence: '补充支撑依据、参考链接或数据',
    action: '列出具体操作步骤或执行动作',
    outcome: '说明实施结果与验证结论',
}

/**
 * Fact Block 类型视觉主题：左侧强调色、标签背景等
 * 草稿编辑与经验详情共用，便于快速区分观察/决策/行动等段落
 */
export const FACT_TYPE_THEMES = {
    observation: {
        accent: 'bg-blue-400',
        border: 'border-blue-200',
        badge: 'bg-blue-50 text-blue-700',
        card: 'border-blue-100/80 bg-blue-50/30',
    },
    decision: {
        accent: 'bg-purple-400',
        border: 'border-purple-200',
        badge: 'bg-purple-50 text-purple-700',
        card: 'border-purple-100/80 bg-purple-50/30',
    },
    constraint: {
        accent: 'bg-amber-400',
        border: 'border-amber-200',
        badge: 'bg-amber-50 text-amber-700',
        card: 'border-amber-100/80 bg-amber-50/30',
    },
    rule: {
        accent: 'bg-orange-400',
        border: 'border-orange-200',
        badge: 'bg-orange-50 text-orange-700',
        card: 'border-orange-100/80 bg-orange-50/30',
    },
    evidence: {
        accent: 'bg-cyan-400',
        border: 'border-cyan-200',
        badge: 'bg-cyan-50 text-cyan-700',
        card: 'border-cyan-100/80 bg-cyan-50/30',
    },
    action: {
        accent: 'bg-green-400',
        border: 'border-green-200',
        badge: 'bg-green-50 text-green-700',
        card: 'border-green-100/80 bg-green-50/30',
    },
    outcome: {
        accent: 'bg-primary',
        border: 'border-primary/30',
        badge: 'bg-primary/10 text-primary',
        card: 'border-primary/20 bg-primary/5',
    },
}

/** 获取 Fact 类型主题，未知类型回退为灰色 */
export function getFactTypeTheme(type) {
    return FACT_TYPE_THEMES[type] || {
        accent: 'bg-gray-300',
        border: 'border-gray-200',
        badge: 'bg-gray-50 text-gray-600',
        card: 'border-gray-100 bg-gray-50/50',
    }
}

/** Artifact 类型 */
export const ARTIFACT_TYPES = [
    'commit',
    'pr',
    'issue',
    'conversation',
    'code',
    'test',
    'deploy',
    'screenshot',
    'manual',
]

/** Artifact 角色（origin / evidence / attachment / reference） */
export const ARTIFACT_ROLE_LABELS = {
    origin: '来源',
    evidence: '证据',
    attachment: '附件',
    reference: '引用',
}

/** Knowledge 生命周期：0-草稿 1-已发布 2-已失效 */
export const KNOWLEDGE_LIFECYCLE = {
    DRAFT: 0,
    PUBLISHED: 1,
    DEPRECATED: 2,
}

/** Knowledge 生命周期中文标签 */
export const KNOWLEDGE_LIFECYCLE_LABELS = {
    0: '草稿',
    1: '已发布',
    2: '已失效',
}

/** MVP 仅 experience 类型，Beta 扩展 decision / rule / workflow */
export const KNOWLEDGE_TYPES = {
    EXPERIENCE: 'experience',
    DECISION: 'decision',
    RULE: 'rule',
    WORKFLOW: 'workflow',
}

/** 知识类型中文标签（界面统一为「中文 (English)」） */
export const KNOWLEDGE_TYPE_LABELS = {
    experience: '经验 (Experience)',
    decision: '决策 (Decision)',
    rule: '规则 (Rule)',
    workflow: '流程 (Workflow)',
}

/** 记忆中心列表「全部类型」筛选项值，对应后端 knowledgeType=all */
export const MEMORY_TYPE_FILTER_ALL = 'all'

/** 记忆中心类型下拉选项（含全部） */
export const MEMORY_TYPE_FILTER_OPTIONS = [
    { value: MEMORY_TYPE_FILTER_ALL, label: '全部类型' },
    { value: KNOWLEDGE_TYPES.EXPERIENCE, label: KNOWLEDGE_TYPE_LABELS.experience },
    { value: KNOWLEDGE_TYPES.RULE, label: KNOWLEDGE_TYPE_LABELS.rule },
    { value: KNOWLEDGE_TYPES.WORKFLOW, label: KNOWLEDGE_TYPE_LABELS.workflow },
    { value: KNOWLEDGE_TYPES.DECISION, label: KNOWLEDGE_TYPE_LABELS.decision },
]

/**
 * 知识类型视觉主题：列表卡片类型徽章配色
 */
export const KNOWLEDGE_TYPE_THEMES = {
    experience: {
        badge: 'bg-blue-50 text-blue-700',
    },
    rule: {
        badge: 'bg-orange-50 text-orange-700',
    },
    workflow: {
        badge: 'bg-green-50 text-green-700',
    },
    decision: {
        badge: 'bg-purple-50 text-purple-700',
    },
}

/** 获取知识类型主题，未知类型回退灰色 */
export function getKnowledgeTypeTheme(knowledgeType) {
    return KNOWLEDGE_TYPE_THEMES[knowledgeType] || {
        badge: 'bg-gray-50 text-gray-600',
    }
}

/** 各知识类型新建时的默认 Fact 类型顺序（人工直达创建时使用） */
export const DEFAULT_FACT_TYPES_BY_KNOWLEDGE_TYPE = {
    experience: ['observation', 'decision', 'action', 'outcome'],
    rule: ['rule', 'constraint'],
    workflow: ['action', 'constraint'],
    decision: ['decision', 'evidence', 'constraint'],
}

/** 各知识类型可追加的 Fact 类型 */
export const ALLOWED_FACT_TYPES_BY_KNOWLEDGE_TYPE = {
    experience: ['observation', 'decision', 'constraint', 'action', 'outcome', 'evidence'],
    rule: ['rule', 'constraint', 'evidence'],
    workflow: ['action', 'constraint', 'evidence'],
    decision: ['decision', 'evidence', 'constraint'],
}

/** 各知识类型新建按钮文案 */
export const KNOWLEDGE_CREATE_LABELS = {
    experience: '新建经验 (Experience)',
    rule: '新建规则 (Rule)',
    workflow: '新建流程 (Workflow)',
    decision: '新建决策 (Decision)',
}

/** Capture 草稿审核状态：0-待确认 1-已采纳 2-已拒绝 */
export const CAPTURE_REVIEW_STATUS = {
    PENDING: 0,
    APPROVED: 1,
    REJECTED: 2,
}

/**
 * Capture Review 检查清单（经验 vs 文档/规范分流）
 * 与 MCP memory_submit 提交门一致：执行问题与规范缺口不记 experience
 */
export const CAPTURE_REVIEW_CHECKLIST = [
    {
        groupTitle: '路由检查（不通过应拒绝）',
        items: [
            { id: 'R1', text: '根因不是「未遵守既有规范」' },
            { id: 'R2', text: '根因不是「规范缺失/表述不清」（若属此类应改文档）' },
            { id: 'R3', text: '包含真实决策与取舍，而非仅操作步骤' },
            { id: 'R4', text: '不与已有 Rule/团队规范重复' },
        ],
    },
    {
        groupTitle: '内容质量',
        items: [
            { id: 'Q1', text: 'observation 具体（现象/环境/报错），非空泛完成描述' },
            { id: 'Q2', text: 'decision 独立，含「为什么不用其他方案」' },
            { id: 'Q3', text: 'action 可执行，他人可复现改动' },
            { id: 'Q4', text: '有 outcome 或 evidence 验证结果' },
            { id: 'Q5', text: 'constraint 标明适用/失效条件（或已标注风险）' },
            { id: 'Q6', text: 'task/observation/decision/action 无大段重复' },
        ],
    },
    {
        groupTitle: '上下文与产物',
        items: [
            { id: 'C1', text: '模块/仓库/标签便于 Recall 命中' },
            { id: 'C2', text: '关联产物有效（非空对象，关键改动有引用）' },
            { id: 'C3', text: '标题准确概括决策（≤80 字）' },
        ],
    },
]

/** Review 拒绝理由代码（与提交门路由检查对齐） */
export const CAPTURE_REJECT_REASONS = [
    { code: 'REJECT_RULE_VIOLATION', label: '违反既有规范 → 改 CI/Review，不记经验' },
    { code: 'REJECT_DOC_GAP', label: '应补文档/Rule/Workflow，不记经验' },
    { code: 'REJECT_DUPLICATE_RULE', label: '与 Rule 重复 → 建议提交为 Rule' },
    { code: 'REJECT_NOT_EXPERIENCE', label: '无决策/验证，仅为操作记录' },
    { code: 'REJECT_LOW_QUALITY', label: 'Fact 不完整或字段重复' },
]

/** Feedback 类型（Agent 召回效果反馈，Web MVP 仅展示统计预留） */
export const FEEDBACK_TYPES = {
    USED: 'used',
    HELPFUL: 'helpful',
    NOT_HELPFUL: 'not_helpful',
    OUTDATED: 'outdated',
    WRONG: 'wrong',
}
