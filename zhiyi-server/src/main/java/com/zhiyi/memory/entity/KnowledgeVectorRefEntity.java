package com.zhiyi.memory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 向量索引引用，对应 knowledge_vector_ref 表
 */
@Data
@TableName("knowledge_vector_ref")
public class KnowledgeVectorRefEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long knowledgeId;

    private Integer chunkIndex;

    private String chunkText;

    private String vectorId;

    private String modelName;

    private Date createTime;
}
