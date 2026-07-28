package com.zhiyi.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.domain.entity.UsageDailyEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Date;

/**
 * 日用量统计数据访问层
 */
@Mapper
public interface UsageDailyMapper extends BaseMapper<UsageDailyEntity> {

    /**
     * 按日累加用量，存在则 UPDATE，不存在则 INSERT
     */
    @Insert("INSERT INTO usage_daily (workspace_id, usage_date, recall_count, remember_count, feedback_count, embedding_tokens) "
            + "VALUES (#{workspaceId}, #{usageDate}, #{recallIncrement}, #{rememberIncrement}, #{feedbackIncrement}, #{embeddingTokens}) "
            + "ON DUPLICATE KEY UPDATE "
            + "recall_count = recall_count + #{recallIncrement}, "
            + "remember_count = remember_count + #{rememberIncrement}, "
            + "feedback_count = feedback_count + #{feedbackIncrement}, "
            + "embedding_tokens = embedding_tokens + #{embeddingTokens}")
    int upsertIncrement(@Param("workspaceId") String workspaceId,
                        @Param("usageDate") Date usageDate,
                        @Param("recallIncrement") int recallIncrement,
                        @Param("rememberIncrement") int rememberIncrement,
                        @Param("feedbackIncrement") int feedbackIncrement,
                        @Param("embeddingTokens") long embeddingTokens);
}
