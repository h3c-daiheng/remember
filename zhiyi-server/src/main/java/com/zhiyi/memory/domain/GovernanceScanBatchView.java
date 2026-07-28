package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 治理扫描批次视图
 */
@Data
public class GovernanceScanBatchView {

    private Long id;

    private String scanType;

    private String knowledgeType;

    private String moduleFilter;

    private Integer status;

    private Integer scannedCount;

    private Integer issueCount;

    private Double similarityThreshold;

    /** 本次扫描自动处置工单数 */
    private Integer autoResolvedCount;

    private String errorMessage;

    private Long operatorId;

    private Date createTime;

    private Date finishTime;
}
