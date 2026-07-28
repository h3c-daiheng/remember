package com.zhiyi.memory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.memory.entity.KnowledgeFactEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Fact Block 数据访问层
 */
@Mapper
public interface KnowledgeFactMapper extends BaseMapper<KnowledgeFactEntity> {
}
