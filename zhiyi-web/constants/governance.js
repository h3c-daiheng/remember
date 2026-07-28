/**
 * 记忆治理常量：工单状态、处置动作、扫描类型
 */

/** 工单状态：待处理 */
export const GOVERNANCE_ISSUE_STATUS_OPEN = 0

/** 工单状态：已解决 */
export const GOVERNANCE_ISSUE_STATUS_RESOLVED = 1

/** 工单状态：已忽略 */
export const GOVERNANCE_ISSUE_STATUS_DISMISSED = 2

export const GOVERNANCE_ISSUE_STATUS_LABELS = {
    [GOVERNANCE_ISSUE_STATUS_OPEN]: '待处理',
    [GOVERNANCE_ISSUE_STATUS_RESOLVED]: '已解决',
    [GOVERNANCE_ISSUE_STATUS_DISMISSED]: '已忽略',
}

/** 处置动作：保留主版本并下架其余 */
export const GOVERNANCE_ACTION_KEEP_PRIMARY = 'keep_primary_deprecate_others'

/** 处置动作：合并 Fact 到主版本 Rule */
export const GOVERNANCE_ACTION_MERGE_FACTS = 'merge_facts'

/** 处置动作：标记误报 */
export const GOVERNANCE_ACTION_DISMISS = 'dismiss'

/** 处置动作：合并优化为新版 Experience */
export const GOVERNANCE_ACTION_MERGE_OPTIMIZE = 'merge_optimize'

/** 处置动作：下架过时记忆 */
export const GOVERNANCE_ACTION_DEPRECATE = 'deprecate'

/** 建议动作：人工补全 */
export const GOVERNANCE_ACTION_COMPLETE_MANUALLY = 'complete_manually'

/** 建议动作：人工裁决冲突 */
export const GOVERNANCE_ACTION_REVIEW_CONFLICT = 'review_conflict'

export const GOVERNANCE_ACTION_LABELS = {
    [GOVERNANCE_ACTION_KEEP_PRIMARY]: '保留主版本，下架重复项',
    [GOVERNANCE_ACTION_MERGE_FACTS]: '合并 Fact 到主版本',
    [GOVERNANCE_ACTION_DISMISS]: '标记为误报',
    [GOVERNANCE_ACTION_MERGE_OPTIMIZE]: '合并优化为新版经验',
    [GOVERNANCE_ACTION_DEPRECATE]: '下架过时记忆',
    [GOVERNANCE_ACTION_COMPLETE_MANUALLY]: '人工补全记忆',
    [GOVERNANCE_ACTION_REVIEW_CONFLICT]: '人工裁决冲突',
}

/** 扫描类型：全库扫描 */
export const GOVERNANCE_SCAN_TYPE_FULL = 'full'

/** 扫描类型：碎片聚类扫描（与后端 GovernanceConstants.SCAN_TYPE_FRAGMENT 一致） */
export const GOVERNANCE_SCAN_TYPE_FRAGMENT = 'fragment'

/** 扫描模式：重复记忆 */
export const GOVERNANCE_SCAN_MODE_DUPLICATE = 'duplicate'

/** 扫描模式：碎片聚类 */
export const GOVERNANCE_SCAN_MODE_FRAGMENT = 'fragment'

/** 扫描模式：质量校验 */
export const GOVERNANCE_SCAN_MODE_VALIDATE = 'validate'

/** 扫描类型：增量扫描 */
export const GOVERNANCE_SCAN_TYPE_INCREMENTAL = 'incremental'

/** 扫描类型：质量校验 */
export const GOVERNANCE_SCAN_TYPE_VALIDATE = 'validate'

/** 工单类型 */
export const GOVERNANCE_ISSUE_TYPE_DUPLICATE = 'duplicate'

export const GOVERNANCE_ISSUE_TYPE_FRAGMENT_CLUSTER = 'fragment_cluster'

export const GOVERNANCE_ISSUE_TYPE_INCOMPLETE = 'incomplete'

export const GOVERNANCE_ISSUE_TYPE_OUTDATED = 'outdated'

export const GOVERNANCE_ISSUE_TYPE_CONFLICT = 'conflict'

export const GOVERNANCE_ISSUE_TYPE_LABELS = {
    [GOVERNANCE_ISSUE_TYPE_DUPLICATE]: '重复记忆',
    [GOVERNANCE_ISSUE_TYPE_FRAGMENT_CLUSTER]: '碎片聚类',
    [GOVERNANCE_ISSUE_TYPE_INCOMPLETE]: '结构不完整',
    [GOVERNANCE_ISSUE_TYPE_OUTDATED]: 'Feedback 过时',
    [GOVERNANCE_ISSUE_TYPE_CONFLICT]: 'Rule 冲突',
}

export const GOVERNANCE_SCAN_TYPE_OPTIONS = [
    { value: GOVERNANCE_SCAN_TYPE_FULL, label: '全库扫描' },
    { value: GOVERNANCE_SCAN_TYPE_FRAGMENT, label: '碎片聚类扫描' },
]

/** 扫描任务选项：mode + type 组合 */
export const GOVERNANCE_SCAN_TASK_DUPLICATE_FULL = 'duplicate_full'

export const GOVERNANCE_SCAN_TASK_DUPLICATE_INCREMENTAL = 'duplicate_incremental'

export const GOVERNANCE_SCAN_TASK_FRAGMENT = 'fragment'

export const GOVERNANCE_SCAN_TASK_VALIDATE = 'validate'

export const GOVERNANCE_SCAN_TASK_OPTIONS = [
    {
        value: GOVERNANCE_SCAN_TASK_DUPLICATE_FULL,
        label: '重复扫描（全量）',
        scanMode: GOVERNANCE_SCAN_MODE_DUPLICATE,
        scanType: GOVERNANCE_SCAN_TYPE_FULL,
    },
    {
        value: GOVERNANCE_SCAN_TASK_DUPLICATE_INCREMENTAL,
        label: '重复扫描（增量）',
        scanMode: GOVERNANCE_SCAN_MODE_DUPLICATE,
        scanType: GOVERNANCE_SCAN_TYPE_INCREMENTAL,
    },
    {
        value: GOVERNANCE_SCAN_TASK_FRAGMENT,
        label: '碎片聚类',
        scanMode: GOVERNANCE_SCAN_MODE_FRAGMENT,
        scanType: GOVERNANCE_SCAN_TYPE_FRAGMENT,
    },
    {
        value: GOVERNANCE_SCAN_TASK_VALIDATE,
        label: '质量校验',
        scanMode: GOVERNANCE_SCAN_MODE_VALIDATE,
        scanType: GOVERNANCE_SCAN_TYPE_VALIDATE,
    },
]

export const GOVERNANCE_SCAN_MODE_OPTIONS = GOVERNANCE_SCAN_TASK_OPTIONS

/** 知识类型筛选 */
export const GOVERNANCE_KNOWLEDGE_TYPE_OPTIONS = [
    { value: '', label: '全部类型' },
    { value: 'experience', label: '经验' },
    { value: 'rule', label: '规则' },
    { value: 'workflow', label: '流程' },
    { value: 'decision', label: '决策' },
]

/** 治理主路径三阶段：扫描 → 对比 → 处置 */
export const GOVERNANCE_FLOW_STEPS = ['扫描', '对比', '处置']

/** 空态能力标签：首次扫描引导 */
export const GOVERNANCE_EMPTY_TAGS = ['向量相似', '主版本推荐', '一键下架', 'Rule 合并']

/** 空态能力标签：已解决 Tab */
export const GOVERNANCE_EMPTY_RESOLVED_TAGS = ['保留主版本', 'Rule 合并', 'Timeline 追溯']

/** 空态能力标签：已忽略 Tab */
export const GOVERNANCE_EMPTY_DISMISSED_TAGS = ['误报标记', '扫描豁免', '人工确认']

/** 默认相似度阈值（与后端一致，仅用于扫描区说明） */
export const GOVERNANCE_DEFAULT_SIMILARITY_THRESHOLD = 0.65

/** 碎片聚类默认相似度阈值 */
export const GOVERNANCE_DEFAULT_FRAGMENT_THRESHOLD = 0.40

/** 合并向导主路径 */
export const GOVERNANCE_MERGE_FLOW_STEPS = ['选择记忆', '预览合并', '确认发布']
