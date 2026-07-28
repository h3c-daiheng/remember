package com.zhiyi.memory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.dao.SysUserMapper;
import com.zhiyi.dao.WorkspaceMemberMapper;
import com.zhiyi.domain.entity.SysUser;
import com.zhiyi.domain.entity.WorkspaceMemberEntity;
import com.zhiyi.workspace.WorkspaceMemberRole;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Agent Memory API 创建者解析：API Key 鉴权无登录用户时，回退到工作空间成员作为 creator_id。
 */
@Component
public class AgentCreatorResolver {

    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final SysUserMapper sysUserMapper;

    public AgentCreatorResolver(WorkspaceMemberMapper workspaceMemberMapper,
                                SysUserMapper sysUserMapper) {
        this.workspaceMemberMapper = workspaceMemberMapper;
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 解析 knowledge / 其他需 creator_id 的写入场景。
     * 优先使用已登录用户；否则按 owner → admin → editor → 任意成员 回退。
     */
    public Long resolveCreatorId(Long loginUserId, String workspaceId) {
        if (loginUserId != null) {
            return loginUserId;
        }
        return resolveWorkspaceCreatorId(workspaceId);
    }

    /**
     * 按 gonline 用户 ID 查找本地 sys_user.id，供 API Key 签发人映射为提交人。
     */
    public Long resolveLocalUserIdByGonlineUserId(String gonlineUserId) {
        if (StringUtils.isBlank(gonlineUserId)) {
            return null;
        }
        SysUser sysUser = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getGonlineUserId, gonlineUserId.trim())
                .last("limit 1"));
        if (sysUser == null || sysUser.getId() == null) {
            return null;
        }
        return sysUser.getId();
    }

    /**
     * Agent 仅 API Key 鉴权时，取工作空间 owner 等成员作为 knowledge.creator_id，满足非空约束。
     */
    public Long resolveWorkspaceCreatorId(String workspaceId) {
        if (StringUtils.isBlank(workspaceId)) {
            throw new BusinessException(400, "工作空间不能为空");
        }
        Long ownerUserId = findMemberUserIdByRole(workspaceId, WorkspaceMemberRole.OWNER);
        if (ownerUserId != null) {
            return ownerUserId;
        }
        Long adminUserId = findMemberUserIdByRole(workspaceId, WorkspaceMemberRole.ADMIN);
        if (adminUserId != null) {
            return adminUserId;
        }
        Long editorUserId = findMemberUserIdByRole(workspaceId, WorkspaceMemberRole.EDITOR);
        if (editorUserId != null) {
            return editorUserId;
        }
        WorkspaceMemberEntity anyMemberEntity = workspaceMemberMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceMemberEntity>()
                        .eq(WorkspaceMemberEntity::getWorkspaceId, workspaceId)
                        .last("limit 1"));
        if (anyMemberEntity != null && anyMemberEntity.getUserId() != null) {
            return anyMemberEntity.getUserId();
        }
        throw new BusinessException(400, "工作空间无成员，无法确定知识创建者");
    }

    /** 按角色查找首个成员用户 ID */
    private Long findMemberUserIdByRole(String workspaceId, String memberRole) {
        WorkspaceMemberEntity memberEntity = workspaceMemberMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceMemberEntity>()
                        .eq(WorkspaceMemberEntity::getWorkspaceId, workspaceId)
                        .eq(WorkspaceMemberEntity::getMemberRole, memberRole)
                        .last("limit 1"));
        if (memberEntity == null) {
            return null;
        }
        return memberEntity.getUserId();
    }
}
