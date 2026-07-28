package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档导入质量检查结果，供前端展示 Review 提示
 */
@Data
public class DocumentImportQualityChecks {

    /** 是否包含 observation Fact */
    private boolean hasObservation;

    /** 是否包含 decision Fact */
    private boolean hasDecision;

    /** 是否包含 outcome 或 evidence Fact */
    private boolean hasOutcomeOrEvidence;

    /** 质量警告列表 */
    private List<String> warnings = new ArrayList<String>();
}
