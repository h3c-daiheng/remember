package com.zhiyi.memory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.memory.entity.SystemEventEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统一 Event 数据访问层
 */
@Mapper
public interface SystemEventMapper extends BaseMapper<SystemEventEntity> {
}
