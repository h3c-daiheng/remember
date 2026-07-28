package com.zhiyi.memory.governance;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 重复记忆扫描结果：一组连通相似簇
 */
@Data
public class DuplicateGroupScanResult {

    /** 推荐保留的主版本 ID */
    private Long primaryKnowledgeId;

    /** 需处置的重复记忆 ID（不含主版本） */
    private List<Long> duplicateKnowledgeIds = new ArrayList<Long>();

    /** 知识类型 */
    private String knowledgeType;

    /** 组内最高相似度 */
    private double maxSimilarityScore;

    /** 建议处置动作 */
    private String suggestedAction;
}
