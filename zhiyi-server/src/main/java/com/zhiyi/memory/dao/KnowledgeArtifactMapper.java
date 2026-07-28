package com.zhiyi.memory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.memory.entity.KnowledgeArtifactEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Artifact 数据访问层
 */
@Mapper
public interface KnowledgeArtifactMapper extends BaseMapper<KnowledgeArtifactEntity> {
}
