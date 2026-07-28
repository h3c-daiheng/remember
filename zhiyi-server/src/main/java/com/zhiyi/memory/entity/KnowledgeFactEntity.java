package com.zhiyi.memory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * Fact Block 实体，对应 knowledge_fact 表
 */
@Data
@TableName("knowledge_fact")
public class KnowledgeFactEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long knowledgeId;

    private String blockType;

    private String blockText;

    /** JSON 字符串 */
    private String blockMetadata;

    private Integer sortOrder;

    private Date createTime;
}
