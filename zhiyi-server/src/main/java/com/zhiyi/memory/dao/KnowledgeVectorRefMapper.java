package com.zhiyi.memory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.memory.entity.KnowledgeVectorRefEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 向量索引引用数据访问层
 */
@Mapper
public interface KnowledgeVectorRefMapper extends BaseMapper<KnowledgeVectorRefEntity> {
}
