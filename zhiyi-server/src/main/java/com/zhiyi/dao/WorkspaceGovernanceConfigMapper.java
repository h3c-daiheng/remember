package com.zhiyi.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.domain.entity.WorkspaceGovernanceConfigEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作空间治理配置数据访问层
 */
@Mapper
public interface WorkspaceGovernanceConfigMapper extends BaseMapper<WorkspaceGovernanceConfigEntity> {
}
