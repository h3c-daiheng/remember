package com.zhiyi.memory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.memory.entity.CaptureDraftEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Capture 草稿数据访问层
 */
@Mapper
public interface CaptureDraftMapper extends BaseMapper<CaptureDraftEntity> {
}
