package com.zhiyi.domain.vo;

import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 相关经验列表项：供经验详情页展示关联推荐
 */
@Data
public class KnowledgeRelatedItemVO {

    /** 经验主键 */
    private Long id;

    /** 经验标题 */
    private String title;

    /** 知识类型 */
    private String knowledgeType;

    /** 所属项目 */
    private String project;

    /** 所属模块 */
    private String module;

    /** 代码仓库 */
    private String repository;

    /** 标签列表 */
    private List<String> tags;

    /** 与源经验共有的标签 */
    private List<String> sharedTags;

    /** 累计召回次数 */
    private Integer recallCount;

    /** 最近更新时间 */
    private Date updateTime;

    /** 综合相关度分数 */
    private Double score;

    /** 打分因子明细 */
    private Map<String, Double> scoreBreakdown;

    /** 显式关系类型列表 */
    private List<String> relationTypes;

    /** 匹配来源：implicit / explicit / both / mixed */
    private String matchSource;
}
