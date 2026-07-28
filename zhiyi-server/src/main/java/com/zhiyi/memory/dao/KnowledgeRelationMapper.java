package com.zhiyi.memory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.memory.entity.KnowledgeRelationEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 经验关系边数据访问层
 */
@Mapper
public interface KnowledgeRelationMapper extends BaseMapper<KnowledgeRelationEntity> {
}
