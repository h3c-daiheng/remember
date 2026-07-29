package com.zhiyi.memory.dao;

import com.zhiyi.memory.domain.KnowledgeTagRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识标签数据访问层
 */
@Mapper
public interface KnowledgeTagMapper {

    /**
     * 查询知识关联的全部标签
     */
    @Select("SELECT tag_name FROM knowledge_tag WHERE knowledge_id = #{knowledgeId}")
    List<String> selectTagNames(@Param("knowledgeId") Long knowledgeId);

    /**
     * 查询工作空间下全部标签行，供统计聚合
     */
    @Select("SELECT kt.knowledge_id AS knowledgeId, kt.tag_name AS tagName "
            + "FROM knowledge_tag kt INNER JOIN knowledge k ON kt.knowledge_id = k.id "
            + "WHERE k.workspace_id = #{workspaceId} AND k.deleted = 0")
    List<KnowledgeTagRow> selectTagRowsByWorkspace(@Param("workspaceId") String workspaceId);

    /**
     * 删除知识下的全部标签
     */
    @Delete("DELETE FROM knowledge_tag WHERE knowledge_id = #{knowledgeId}")
    int deleteByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    /** 物理删除工作空间下全部知识标签(通过 knowledge 子查询) */
    @Delete("DELETE FROM knowledge_tag WHERE knowledge_id IN "
            + "(SELECT id FROM knowledge WHERE workspace_id = #{workspaceId})")
    int deleteByWorkspace(@Param("workspaceId") String workspaceId);

    /**
     * 插入单条标签
     */
    @Insert("INSERT INTO knowledge_tag (knowledge_id, tag_name) VALUES (#{knowledgeId}, #{tagName})")
    int insertTag(@Param("knowledgeId") Long knowledgeId, @Param("tagName") String tagName);
}
