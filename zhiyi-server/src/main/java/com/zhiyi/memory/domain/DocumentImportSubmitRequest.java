package com.zhiyi.memory.domain;

import lombok.Data;

/**
 * 文档导入提交请求：用户确认抽取结果后写入 Capture 草稿
 */
@Data
public class DocumentImportSubmitRequest {

    /** 来源类型：paste / upload */
    private String sourceType;

    /** 原始文档标题提示 */
    private String title;

    /** 原始文档正文，用于溯源元数据 */
    private String content;

    /** 上传文件名 */
    private String fileName;

    /** LLM 建议的知识类型 */
    private String suggestedType;

    /** LLM 建议的路由提示 */
    private String routeHint;

    /** 用户编辑后的草稿内容 */
    private KnowledgeDraftContent draftContent;
}
