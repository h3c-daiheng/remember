package com.zhiyi.memory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.memory.entity.KnowledgeTimelineEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 经验时间线数据访问层
 */
@Mapper
public interface KnowledgeTimelineMapper extends BaseMapper<KnowledgeTimelineEntity> {
}
