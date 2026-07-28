package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.Map;

/**
 * Fact Block：知识最小单元，Recall 按 type 返回
 */
@Data
public class FactBlock {

    /** 块类型：observation/decision/constraint/rule/evidence/action/outcome */
    private String type;

    /** 事实文本 */
    private String text;

    /** 扩展元数据 */
    private Map<String, Object> metadata;

    /** 排序序号 */
    private Integer sortOrder;
}
