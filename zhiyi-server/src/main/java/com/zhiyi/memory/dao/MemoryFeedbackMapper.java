package com.zhiyi.memory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.memory.entity.MemoryFeedbackEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Recall 反馈数据访问层
 */
@Mapper
public interface MemoryFeedbackMapper extends BaseMapper<MemoryFeedbackEntity> {
}
