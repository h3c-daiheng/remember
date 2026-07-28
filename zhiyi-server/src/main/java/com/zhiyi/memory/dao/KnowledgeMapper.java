package com.zhiyi.memory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.memory.entity.KnowledgeEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识对象数据访问层
 */
@Mapper
public interface KnowledgeMapper extends BaseMapper<KnowledgeEntity> {
}
