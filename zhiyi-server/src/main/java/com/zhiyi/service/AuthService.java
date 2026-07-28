package com.zhiyi.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhiyi.auth.JwtTokenProvider;
import com.zhiyi.common.BusinessException;
import com.zhiyi.dao.SysUserMapper;
import com.zhiyi.dao.WorkspaceMapper;
import com.zhiyi.dao.WorkspaceMemberMapper;
import com.zhiyi.domain.entity.SysUser;
import com.zhiyi.domain.entity.WorkspaceEntity;
import com.zhiyi.domain.entity.WorkspaceMemberEntity;
import com.zhiyi.domain.vo.LoginUserVO;
import com.zhiyi.util.PasswordUtil;
import com.zhiyi.workspace.WorkspaceMemberRole;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证服务：本地账号密码登录 + JWT 签发（不依赖 gonline）
 */
@Service
public class AuthService {

    private static final String DEFAULT_WORKSPACE_ID = "1";

    private final SysUserMapper sysUserMapper;
    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(SysUserMapper sysUserMapper,
                       WorkspaceMapper workspaceMapper,
                       WorkspaceMemberMapper workspaceMemberMapper,
                       JwtTokenProvider jwtTokenProvider) {
        this.sysUserMapper = sysUserMapper;
        this.workspaceMapper = workspaceMapper;
        this.workspaceMemberMapper = workspaceMemberMapper;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 本地账号密码登录：校验 sys_user 后签发 JWT
     */
    public Map<String, Object> login(String username, String password) {
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            throw new BusinessException(400, "用户名或密码不能为空");
        }
        SysUser user = sysUserMapper.selectOne(new QueryWrapper<SysUser>()
                .eq("username", username)
                .eq("status", 1));
        if (user == null || !PasswordUtil.matches(password, user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        LoginUserVO loginUser = buildLoginUser(user);
        String token = jwtTokenProvider.createAccessToken(loginUser);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", loginUser);
        return result;
    }

    /**
     * 组装登录用户（含默认 workspace 上下文）
     */
    private LoginUserVO buildLoginUser(SysUser user) {
        String workspaceId = StringUtils.defaultIfBlank(user.getLastWorkspaceId(), DEFAULT_WORKSPACE_ID);
        String workspaceCode = workspaceId;
        String workspaceName = null;
        WorkspaceEntity workspace = workspaceMapper.selectById(workspaceId);
        if (workspace != null) {
            workspaceCode = StringUtils.defaultIfBlank(workspace.getWorkspaceCode(), workspaceId);
            workspaceName = workspace.getWorkspaceName();
        }

        String memberRole = WorkspaceMemberRole.VIEWER;
        WorkspaceMemberEntity member = workspaceMemberMapper.selectOne(new QueryWrapper<WorkspaceMemberEntity>()
                .eq("workspace_id", workspaceId)
                .eq("user_id", user.getId()));
        if (member != null) {
            String normalized = WorkspaceMemberRole.normalize(member.getMemberRole());
            if (StringUtils.isNotBlank(normalized)) {
                memberRole = normalized;
            }
        }

        LoginUserVO vo = new LoginUserVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatarUrl());
        vo.setWorkspaceId(workspaceId);
        vo.setWorkspaceCode(workspaceCode);
        vo.setWorkspaceName(workspaceName);
        vo.setMemberRole(memberRole);
        vo.setPermissions(Collections.emptyList());
        return vo;
    }

    /**
     * 切换工作空间后重签 JWT：workspace 上下文已变更，须用新空间重新签发，
     * 否则后续 Memory API 仍按 JWT 旧 workspaceId 查询，导致跨空间数据错乱。
     */
    public String reissueToken(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        LoginUserVO loginUser = buildLoginUser(user);
        return jwtTokenProvider.createAccessToken(loginUser);
    }

    /**
     * 退出登录：无状态 JWT，由前端清除 Cookie
     */
    public void logout(String token) {
        // 保留接口兼容，实际登出逻辑在前端删除 Cookie
    }
}
