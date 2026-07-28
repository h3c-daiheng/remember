package com.zhiyi.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.domain.entity.ApiKeyEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent API Key 数据访问层
 */
@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKeyEntity> {
}
