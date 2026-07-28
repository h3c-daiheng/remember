package com.zhiyi.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.domain.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户数据访问层
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 将指向该工作空间的 last_workspace_id 置空 */
    @org.apache.ibatis.annotations.Update(
            "UPDATE sys_user SET last_workspace_id = NULL WHERE last_workspace_id = #{workspaceId}")
    int clearLastWorkspace(@org.apache.ibatis.annotations.Param("workspaceId") String workspaceId);
}
