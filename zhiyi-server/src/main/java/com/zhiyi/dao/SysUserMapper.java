package com.zhiyi.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.domain.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户数据访问层
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
