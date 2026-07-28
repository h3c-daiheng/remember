package com.zhiyi.memory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 记忆治理扫描批次，对应 governance_scan_batch 表
 */
@Data
@TableName("governance_scan_batch")
public class GovernanceScanBatchEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工作空间主键 */
    private String workspaceId;

    /** 扫描类型：full / incremental / module */
    private String scanType;

    /** 限定知识类型，空表示全部类型 */
    private String knowledgeType;

    /** 限定模块名 */
    private String moduleFilter;

    /** 批次状态：0 进行中 / 1 完成 / 2 失败 */
    private Integer status;

    /** 扫描的已发布记忆条数 */
    private Integer scannedCount;

    /** 本次扫描发现的治理工单数 */
    private Integer issueCount;

    /** 重复判定相似度阈值 */
    private Double similarityThreshold;

    /** 失败原因 */
    private String errorMessage;

    /** 触发扫描的操作者 */
    private Long operatorId;

    /** 本次扫描自动处置工单数 */
    private Integer autoResolvedCount;

    private Date createTime;

    private Date finishTime;
}
