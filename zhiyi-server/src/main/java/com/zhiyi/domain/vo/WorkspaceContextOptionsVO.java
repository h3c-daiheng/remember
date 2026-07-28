package com.zhiyi.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 工作空间 Recall 上下文字段可选值：供 Web 下拉选择与筛选
 */
@Data
public class WorkspaceContextOptionsVO {

    /** 仓库名称列表（去重、排序） */
    private List<String> repositories;

    /** 项目名称列表 */
    private List<String> projects;

    /** 模块名称列表 */
    private List<String> modules;

    /** 标签名称列表 */
    private List<String> tags;
}
