package com.zhiyi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.auth.GonlineOAuthUserProfile;
import com.zhiyi.auth.GonlineWorkspaceClient;
import com.zhiyi.common.BusinessException;
import com.zhiyi.dao.SysUserMapper;
import com.zhiyi.domain.entity.SysUser;
import com.zhiyi.domain.vo.LoginUserVO;
import com.zhiyi.util.PasswordUtil;
import com.zhiyi.workspace.WorkspaceMemberRole;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * gonline 用户同步服务：首次登录仅注册本地账号；工作空间上下文从主站 me-context 解析。
 */
@Service
public class GonlineUserSyncService {

    /** 用户状态：正常 */
    private static final int USER_STATUS_ENABLED = 1;

    private final SysUserMapper sysUserMapper;
    private final GonlineWorkspaceClient gonlineWorkspaceClient;

    public GonlineUserSyncService(SysUserMapper sysUserMapper,
                                  GonlineWorkspaceClient gonlineWorkspaceClient) {
        this.sysUserMapper = sysUserMapper;
        this.gonlineWorkspaceClient = gonlineWorkspaceClient;
    }

    /**
     * 根据 gonline 用户信息解析本地登录用户，并附带主站工作空间上下文。
     *
     * @param gonlineProfile check_token 解析出的用户摘要
     * @param accessToken    原始 Bearer token，用于调用主站 /workspace/me-context
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginUserVO resolveLoginUser(GonlineOAuthUserProfile gonlineProfile, String accessToken) {
        if (gonlineProfile == null || StringUtils.isBlank(gonlineProfile.getGonlineUserId())) {
            throw new BusinessException(401, "登录已过期，请重新登录");
        }

        SysUser sysUser = findUserByGonlineId(gonlineProfile.getGonlineUserId());
        if (sysUser == null && StringUtils.isNotBlank(gonlineProfile.getUsername())) {
            sysUser = findUserByUsername(gonlineProfile.getUsername());
            if (sysUser != null && StringUtils.isBlank(sysUser.getGonlineUserId())) {
                sysUser.setGonlineUserId(gonlineProfile.getGonlineUserId());
                sysUserMapper.updateById(sysUser);
            }
        }
        if (sysUser == null) {
            sysUser = registerGonlineUser(gonlineProfile);
        } else {
            syncUserProfile(sysUser, gonlineProfile);
        }

        if (!Integer.valueOf(USER_STATUS_ENABLED).equals(sysUser.getStatus())) {
            throw new BusinessException(403, "账号已被禁用");
        }

        return buildLoginUserWithWorkspace(sysUser, accessToken);
    }

    /**
     * 根据本地用户构建带 Workspace 上下文的登录用户信息（从主站拉取）。
     */
    public LoginUserVO buildLoginUserWithWorkspace(SysUser sysUser, String accessToken) {
        LoginUserVO loginUser = buildBaseLoginUser(sysUser);
        applyMainSiteUserAvatar(loginUser, accessToken, sysUser);
        applyMainSiteWorkspaceContext(loginUser, accessToken);
        // 缓存最近空间 ID 到本地，便于排查；真相源仍是主站 preference
        if (StringUtils.isNotBlank(loginUser.getWorkspaceId())
                && !loginUser.getWorkspaceId().equals(sysUser.getLastWorkspaceId())) {
            sysUser.setLastWorkspaceId(loginUser.getWorkspaceId());
            sysUser.setUpdateTime(new Date());
            sysUserMapper.updateById(sysUser);
        }
        return loginUser;
    }

    /**
     * 从主站 /user/self 补全头像；失败时不阻断登录，前端可回退昵称首字。
     * 同步写入 sys_user.avatar_url，供知识卡片/详情展示提交人头像。
     */
    private void applyMainSiteUserAvatar(LoginUserVO loginUser, String accessToken, SysUser sysUser) {
        String avatarUrl = gonlineWorkspaceClient.fetchUserAvatarUrl(accessToken);
        if (StringUtils.isBlank(avatarUrl)) {
            return;
        }
        loginUser.setAvatar(avatarUrl);
        if (sysUser != null && !avatarUrl.equals(sysUser.getAvatarUrl())) {
            sysUser.setAvatarUrl(avatarUrl);
            sysUser.setUpdateTime(new Date());
            sysUserMapper.updateById(sysUser);
        }
    }

    /**
     * 从主站 me-context 写入登录用户的空间字段；失败时不阻断登录（业务接口再校验）。
     */
    private void applyMainSiteWorkspaceContext(LoginUserVO loginUser, String accessToken) {
        GonlineWorkspaceClient.GonlineWorkspaceContext workspaceContext =
                gonlineWorkspaceClient.fetchMeContext(accessToken);
        if (workspaceContext == null || StringUtils.isBlank(workspaceContext.getWorkspaceId())) {
            return;
        }
        loginUser.setOrganizationId(null);
        loginUser.setWorkspaceId(workspaceContext.getWorkspaceId());
        loginUser.setWorkspaceCode(workspaceContext.getWorkspaceCode());
        loginUser.setWorkspaceName(workspaceContext.getWorkspaceName());
        // GonlineWorkspaceClient 已归一为 owner/admin/editor/viewer，此处再兜底一次
        loginUser.setMemberRole(WorkspaceMemberRole.normalize(workspaceContext.getMemberRole()));
    }

    /**
     * 按 gonline 用户 ID 查找本地账号
     */
    private SysUser findUserByGonlineId(String gonlineUserId) {
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getGonlineUserId, gonlineUserId)
                .last("limit 1"));
    }

    /**
     * 按用户名查找本地账号（兼容历史种子数据绑定 gonline）
     */
    private SysUser findUserByUsername(String username) {
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("limit 1"));
    }

    /**
     * 首次 gonline 登录：仅创建本地用户（不再本地建 organization / workspace）。
     * 个人空间由主站 me-context / list 自动开通。
     */
    private SysUser registerGonlineUser(GonlineOAuthUserProfile gonlineProfile) {
        String username = resolveUniqueUsername(gonlineProfile.getUsername(), gonlineProfile.getGonlineUserId());
        String nickname = StringUtils.defaultIfBlank(gonlineProfile.getNickname(), username);

        SysUser sysUser = new SysUser();
        sysUser.setGonlineUserId(gonlineProfile.getGonlineUserId());
        sysUser.setUsername(username);
        sysUser.setPassword(PasswordUtil.encode("gonline-sso-" + UUID.randomUUID().toString()));
        sysUser.setNickname(nickname);
        sysUser.setStatus(USER_STATUS_ENABLED);
        sysUser.setCreateTime(new Date());
        sysUser.setUpdateTime(new Date());
        sysUserMapper.insert(sysUser);
        return sysUser;
    }

    /**
     * 同步 gonline 侧昵称等基础资料到本地用户
     */
    private void syncUserProfile(SysUser sysUser, GonlineOAuthUserProfile gonlineProfile) {
        boolean changed = false;
        if (StringUtils.isBlank(sysUser.getGonlineUserId())) {
            sysUser.setGonlineUserId(gonlineProfile.getGonlineUserId());
            changed = true;
        }
        if (StringUtils.isNotBlank(gonlineProfile.getNickname())
                && !gonlineProfile.getNickname().equals(sysUser.getNickname())) {
            sysUser.setNickname(gonlineProfile.getNickname());
            changed = true;
        }
        if (changed) {
            sysUser.setUpdateTime(new Date());
            sysUserMapper.updateById(sysUser);
        }
    }

    /**
     * 构建不含 Workspace 的基础登录用户
     */
    private LoginUserVO buildBaseLoginUser(SysUser sysUser) {
        LoginUserVO loginUser = new LoginUserVO();
        loginUser.setUserId(sysUser.getId());
        loginUser.setUsername(sysUser.getUsername());
        loginUser.setNickname(sysUser.getNickname());
        loginUser.setAvatar(StringUtils.defaultString(sysUser.getAvatarUrl()));
        loginUser.setPermissions(buildDefaultPermissions());
        return loginUser;
    }

    /**
     * 确保用户名唯一，冲突时追加 gonline 用户 ID 后缀
     */
    private String resolveUniqueUsername(String preferredUsername, String gonlineUserId) {
        String baseUsername = StringUtils.defaultIfBlank(preferredUsername, "user_" + gonlineUserId);
        baseUsername = baseUsername.trim();
        if (baseUsername.length() > 32) {
            baseUsername = baseUsername.substring(0, 32);
        }
        SysUser existingUser = findUserByUsername(baseUsername);
        if (existingUser == null) {
            return baseUsername;
        }
        String suffix = "_" + gonlineUserId;
        if (suffix.length() >= 64) {
            suffix = suffix.substring(0, 8);
        }
        int maxBaseLength = 64 - suffix.length();
        String truncatedBase = baseUsername.length() > maxBaseLength
                ? baseUsername.substring(0, maxBaseLength) : baseUsername;
        return truncatedBase + suffix;
    }

    /**
     * C 端当前采用固定权限标识，后续可接入 RBAC
     */
    private List<String> buildDefaultPermissions() {
        return Collections.unmodifiableList(Arrays.asList("user"));
    }
}
