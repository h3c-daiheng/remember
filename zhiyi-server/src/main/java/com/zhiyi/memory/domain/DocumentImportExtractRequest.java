package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.List;

/**
 * 文档导入抽取请求：用户粘贴或上传的原始文档内容
 */
@Data
public class DocumentImportExtractRequest {

    /** 来源类型：paste / upload */
    private String sourceType;

    /** 文档标题提示，可选 */
    private String title;

    /** 文档正文（纯文本或 Markdown） */
    private String content;

    /** 转化目标：auto / experience / decision / rule / workflow */
    private String targetType;

    /** 上传文件名，sourceType=upload 时可选 */
    private String fileName;

    /** 召回上下文：项目 */
    private String project;

    /** 召回上下文：模块 */
    private String module;

    /** 召回上下文：仓库 */
    private String repository;

    /** 标签 */
    private List<String> tags;
}
