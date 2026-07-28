package com.zhiyi.memory.graph;

/**
 * 经验图谱关系类型与建边来源常量
 */
public final class RelationConstants {

    /** 建边来源：规则 / 向量自动建边 */
    public static final String RELATION_SOURCE_AUTO = "auto";

    /** 建边来源：用户手工建边 */
    public static final String RELATION_SOURCE_MANUAL = "manual";

    /** 建边来源：合并导入 */
    public static final String RELATION_SOURCE_MERGE = "merge";

    /** 同模块 */
    public static final String TYPE_SAME_MODULE = "same_module";

    /** 共享标签 */
    public static final String TYPE_SAME_TAG = "same_tag";

    /** 语义相近 */
    public static final String TYPE_RELATED_SEMANTIC = "related_semantic";

    /** 同源 Artifact */
    public static final String TYPE_SAME_ARTIFACT = "same_artifact";

    /** 关联决策 */
    public static final String TYPE_RELATED_DECISION = "related_decision";

    /** 显式引用 */
    public static final String TYPE_REFERENCES = "references";

    /** 依赖关系 */
    public static final String TYPE_DEPENDS_ON = "depends_on";

    /** 替代演进 */
    public static final String TYPE_SUPERSEDES = "supersedes";

    /** 自动建边默认 UI 展示阈值 */
    public static final double AUTO_EDGE_DISPLAY_CONFIDENCE = 0.7D;

    /** 语义相近建边最低相似度 */
    public static final double SEMANTIC_EDGE_MIN_SIMILARITY = 0.85D;

    /** 语义相近建边 Top-K */
    public static final int SEMANTIC_EDGE_TOP_K = 5;

    private RelationConstants() {
    }
}
