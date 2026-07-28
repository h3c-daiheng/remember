package com.zhiyi.domain.vo;

import lombok.Data;

/**
 * 模块维度召回与反馈分布项
 */
@Data
public class StatsModuleItemVO {

    /** 模块名称，空值展示为「未分类」 */
    private String moduleName;

    /** 经验条数 */
    private Integer knowledgeCount;

    /** Recall 累计次数（knowledge.recall_count 之和） */
    private Integer recallCount;

    /** 近窗口内 helpful / used 反馈数 */
    private Integer helpfulCount;
}
