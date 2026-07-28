package com.zhiyi.memory.governance;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 碎片聚类扫描结果：同模块下多条零散不完整记忆
 */
@Data
public class FragmentClusterScanResult {

    /** 推荐作为合并基准的主版本 ID */
    private Long primaryKnowledgeId;

    /** 聚类成员 ID（不含主版本） */
    private List<Long> memberKnowledgeIds = new ArrayList<Long>();

    /** 知识类型 */
    private String knowledgeType;

    /** 聚类内最高相似度 */
    private double maxSimilarityScore;

    /** 所属模块 */
    private String moduleName;

    /** 建议处置动作 */
    private String suggestedAction;
}
