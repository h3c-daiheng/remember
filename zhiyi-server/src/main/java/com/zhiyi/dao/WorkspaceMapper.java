package com.zhiyi.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.domain.entity.WorkspaceEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作空间数据访问层
 */
@Mapper
public interface WorkspaceMapper extends BaseMapper<WorkspaceEntity> {
}
