package com.zhiyi.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.domain.entity.OrganizationEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 组织数据访问层
 */
@Mapper
public interface OrganizationMapper extends BaseMapper<OrganizationEntity> {
}
