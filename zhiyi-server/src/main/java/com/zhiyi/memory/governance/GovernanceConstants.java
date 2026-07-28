package com.zhiyi.memory.governance;

/**
 * 记忆治理模块常量
 */
public final class GovernanceConstants {

    /** 工单类型：重复记忆 */
    public static final String ISSUE_TYPE_DUPLICATE = "duplicate";

    /** 工单类型：碎片聚类（同模块零散不完整记忆） */
    public static final String ISSUE_TYPE_FRAGMENT_CLUSTER = "fragment_cluster";

    /** 工单类型：结构不完整 */
    public static final String ISSUE_TYPE_INCOMPLETE = "incomplete";

    /** 工单类型：Feedback 标记过时 */
    public static final String ISSUE_TYPE_OUTDATED = "outdated";

    /** 工单类型：Rule 约束冲突 */
    public static final String ISSUE_TYPE_CONFLICT = "conflict";

    /** 扫描类型：碎片聚类扫描 */
    public static final String SCAN_TYPE_FRAGMENT = "fragment";

    /** 扫描类型：质量校验扫描 */
    public static final String SCAN_TYPE_VALIDATE = "validate";

    /** 扫描模式：重复扫描 */
    public static final String SCAN_MODE_DUPLICATE = "duplicate";

    /** 扫描模式：碎片聚类 */
    public static final String SCAN_MODE_FRAGMENT = "fragment";

    /** 扫描模式：质量校验 */
    public static final String SCAN_MODE_VALIDATE = "validate";

    /** 工单状态：待处理 */
    public static final int ISSUE_STATUS_OPEN = 0;

    /** 工单状态：已解决 */
    public static final int ISSUE_STATUS_RESOLVED = 1;

    /** 工单状态：已忽略（误报） */
    public static final int ISSUE_STATUS_DISMISSED = 2;

    /** 扫描类型：全库扫描 */
    public static final String SCAN_TYPE_FULL = "full";

    /** 扫描类型：增量扫描 */
    public static final String SCAN_TYPE_INCREMENTAL = "incremental";

    /** 扫描类型：按模块扫描 */
    public static final String SCAN_TYPE_MODULE = "module";

    /** 扫描批次状态：进行中 */
    public static final int SCAN_STATUS_RUNNING = 0;

    /** 扫描批次状态：完成 */
    public static final int SCAN_STATUS_DONE = 1;

    /** 扫描批次状态：失败 */
    public static final int SCAN_STATUS_FAILED = 2;

    /** 建议动作：保留主版本并下架其余 */
    public static final String ACTION_KEEP_PRIMARY_DEPRECATE_OTHERS = "keep_primary_deprecate_others";

    /** 建议动作：合并 Fact 到主版本 Rule */
    public static final String ACTION_MERGE_FACTS = "merge_facts";

    /** 处置动作：标记误报 */
    public static final String ACTION_DISMISS = "dismiss";

    /** 建议/处置动作：合并优化为新版 Experience */
    public static final String ACTION_MERGE_OPTIMIZE = "merge_optimize";

    /** 建议动作：下架过时记忆 */
    public static final String ACTION_DEPRECATE = "deprecate";

    /** 建议动作：人工补全不完整记忆 */
    public static final String ACTION_COMPLETE_MANUALLY = "complete_manually";

    /** 建议动作：人工裁决 Rule 冲突 */
    public static final String ACTION_REVIEW_CONFLICT = "review_conflict";

    /** 自动处置备注前缀 */
    public static final String AUTO_RESOLVE_COMMENT_PREFIX = "[系统自动]";

    /** 默认重复判定阈值 */
    public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.65D;

    /** 碎片聚类相似度阈值（低于重复扫描，便于发现同主题零散记忆） */
    public static final double DEFAULT_FRAGMENT_SIMILARITY_THRESHOLD = 0.40D;

    /** 判定为碎片的最大 Fact 条数 */
    public static final int FRAGMENT_MAX_FACT_COUNT = 2;

    /** 碎片聚类最小成员数 */
    public static final int FRAGMENT_MIN_CLUSTER_SIZE = 3;

    private GovernanceConstants() {
    }
}
