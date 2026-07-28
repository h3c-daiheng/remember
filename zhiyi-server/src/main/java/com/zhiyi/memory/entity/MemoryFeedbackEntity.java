package com.zhiyi.memory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * Recall 效果反馈，对应 memory_feedback 表
 */
@Data
@TableName("memory_feedback")
public class MemoryFeedbackEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long knowledgeId;

    private String recallSession;

    private String feedbackType;

    private String contextJson;

    private Long actorId;

    private Date createTime;
}
