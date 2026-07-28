package com.zhiyi.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.domain.entity.WorkspaceMemberEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作空间成员数据访问层
 */
@Mapper
public interface WorkspaceMemberMapper extends BaseMapper<WorkspaceMemberEntity> {
}
