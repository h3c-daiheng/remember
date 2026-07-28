package com.zhiyi.memory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 统一 Artifact 实体，对应 knowledge_artifact 表
 */
@Data
@TableName("knowledge_artifact")
public class KnowledgeArtifactEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long knowledgeId;

    private String artifactType;

    private String artifactRole;

    private String artifactUrl;

    private String contentRef;

    private Long authorId;

    private Date artifactTime;

    private Date createTime;
}
