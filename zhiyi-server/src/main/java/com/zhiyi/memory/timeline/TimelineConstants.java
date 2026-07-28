package com.zhiyi.memory.timeline;

/**
 * 经验时间线事件类型常量
 */
public final class TimelineConstants {

    /** 发布经验 */
    public static final String EVENT_PUBLISH = "publish";

    /** 下架 / 失效 */
    public static final String EVENT_DEPRECATE = "deprecate";

    /** 重新启用 */
    public static final String EVENT_REACTIVATE = "reactivate";

    /** 新版替代旧版（supersedes 边联动） */
    public static final String EVENT_SUPERSEDE = "supersede";

    /** 依赖经验失效触发的 Review 提示 */
    public static final String EVENT_CASCADE_REVIEW_HINT = "cascade_review_hint";

    /** 治理处置：保留主版本并下架重复项 */
    public static final String EVENT_GOVERNANCE_DEPRECATE = "governance_deprecate";

    /** 治理处置：合并 Fact 到主版本 */
    public static final String EVENT_GOVERNANCE_MERGE = "governance_merge";

    /** 治理处置：忽略重复工单 */
    public static final String EVENT_GOVERNANCE_DISMISS = "governance_dismiss";

    /** 治理处置：合并优化为新版经验 */
    public static final String EVENT_MERGE_OPTIMIZE = "merge_optimize";

    /** 级联 Review 提示默认追溯跳数 */
    public static final int CASCADE_REVIEW_MAX_DEPTH = 2;

    private TimelineConstants() {
    }
}
