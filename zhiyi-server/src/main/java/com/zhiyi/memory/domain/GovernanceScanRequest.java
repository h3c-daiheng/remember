package com.zhiyi.memory.domain;

import lombok.Data;

/**
 * 治理扫描请求
 */
@Data
public class GovernanceScanRequest {

    /** 扫描类型：full / incremental / module，默认 full */
    private String scanType;

    /** 限定知识类型，空表示全部 */
    private String knowledgeType;

    /** 模块过滤，scanType=module 时生效 */
    private String moduleFilter;

    /** 相似度阈值，默认 0.65；fragment 扫描默认 0.40 */
    private Double similarityThreshold;

    /** 扫描模式：duplicate（默认）/ fragment */
    private String scanMode;
}
