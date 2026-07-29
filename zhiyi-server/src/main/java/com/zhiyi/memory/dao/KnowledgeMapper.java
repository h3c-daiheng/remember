package com.zhiyi.memory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.memory.entity.KnowledgeEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识对象数据访问层
 */
@Mapper
public interface KnowledgeMapper extends BaseMapper<KnowledgeEntity> {

    /** 查询工作空间下全部知识 id(含已逻辑删除,绕过 @TableLogic) */
    @org.apache.ibatis.annotations.Select(
            "SELECT id FROM knowledge WHERE workspace_id = #{workspaceId}")
    java.util.List<java.lang.Long> selectIdsByWorkspace(@org.apache.ibatis.annotations.Param("workspaceId") String workspaceId);

    /** 物理删除工作空间下全部知识(绕过 @TableLogic) */
    @org.apache.ibatis.annotations.Delete(
            "DELETE FROM knowledge WHERE workspace_id = #{workspaceId}")
    int deleteByWorkspacePhysical(@org.apache.ibatis.annotations.Param("workspaceId") String workspaceId);
}
