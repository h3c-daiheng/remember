package com.zhiyi.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 经验下架结果，含级联 Review 提示影响的经验
 */
@Data
public class KnowledgeDeprecateResultVO {

    private Long knowledgeId;

    /** 因 depends_on 反向追溯需 Review 的经验 ID 列表 */
    private List<Long> cascadeReviewKnowledgeIds = new ArrayList<Long>();

    private Integer cascadeReviewCount;
}
