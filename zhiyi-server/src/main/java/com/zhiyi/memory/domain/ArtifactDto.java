package com.zhiyi.memory.domain;

import lombok.Data;

/**
 * 统一 Artifact 传输对象，区分 origin / evidence 等角色
 */
@Data
public class ArtifactDto {

    /** 类型：commit/pr/issue/conversation/code/test/deploy/manual */
    private String artifactType;

    /** 角色：origin/evidence/attachment/reference */
    private String artifactRole;

    /** 外链地址 */
    private String artifactUrl;

    /** 内容引用 */
    private String contentRef;

    /** 作者 ID */
    private Long authorId;

    /** artifact 时间（ISO 字符串或时间戳） */
    private String artifactTime;
}
