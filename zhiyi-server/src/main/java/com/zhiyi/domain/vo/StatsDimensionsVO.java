package com.zhiyi.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 工作空间多维统计分析
 */
@Data
public class StatsDimensionsVO {

    private Integer statsPeriodDays;

    /** Recall + Search P95 延迟（毫秒） */
    private Integer recallP95LatencyMs;

    /** 当前筛选：模块 */
    private String filterModule;

    /** 当前筛选：项目 */
    private String filterProject;

    /** 当前筛选：标签 */
    private String filterTag;

    /** 筛选后经验条数 */
    private Integer filteredKnowledgeCount;

    /** 筛选后 Recall 累计次数 */
    private Integer filteredRecallCount;

    /** 筛选后近窗口 helpful 反馈数 */
    private Integer filteredHelpfulCount;

    /** 模块分布 Top 列表 */
    private List<StatsModuleItemVO> moduleBreakdown;

    /** 标签分布 Top 列表 */
    private List<StatsTagItemVO> tagBreakdown;

    /** Agent API Key 用量列表 */
    private List<StatsApiKeyUsageItemVO> apiKeyUsageList;
}
