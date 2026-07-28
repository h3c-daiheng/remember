package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 治理合并 LLM 返回结构
 */
@Data
public class GovernanceMergeLlmResult {

    private String title;

    private List<FactBlock> facts = new ArrayList<FactBlock>();

    private String mergeSummary;
}
