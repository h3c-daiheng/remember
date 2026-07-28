package com.zhiyi.auth;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.zhiyi.domain.vo.LoginUserVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * JWT 令牌签发与校验，无状态登录态，服务端重启后仍可通过令牌恢复会话
 */
@Component
public class JwtTokenProvider {

    /** 载荷：用户主键 */
    private static final String CLAIM_USER_ID = "userId";

    /** 载荷：登录账号 */
    private static final String CLAIM_USERNAME = "username";

    /** 载荷：用户昵称 */
    private static final String CLAIM_NICKNAME = "nickname";

    /** 载荷：头像地址 */
    private static final String CLAIM_AVATAR = "avatar";

    /** 载荷：权限标识列表 */
    private static final String CLAIM_PERMISSIONS = "permissions";

    /** 载荷：组织主键 */
    private static final String CLAIM_ORGANIZATION_ID = "organizationId";

    /** 载荷：工作空间主键 */
    private static final String CLAIM_WORKSPACE_ID = "workspaceId";

    /** 载荷：工作空间编码 */
    private static final String CLAIM_WORKSPACE_CODE = "workspaceCode";

    /** 载荷：工作空间名称 */
    private static final String CLAIM_WORKSPACE_NAME = "workspaceName";

    /** 载荷：成员角色 */
    private static final String CLAIM_MEMBER_ROLE = "memberRole";

    /** 供静态入口 LoginContext 调用的单例引用 */
    private static JwtTokenProvider holder;

    /** 签名密钥原文 */
    @Value("${zhiyi.auth.jwt-secret:zhiyi-default-jwt-secret-please-change-in-prod}")
    private String jwtSecret;

    /** 令牌有效时长（小时） */
    @Value("${zhiyi.auth.jwt-expire-hours:168}")
    private int jwtExpireHours;

    /** 签名密钥字节数组 */
    private byte[] secretKeyBytes;

    @PostConstruct
    public void registerHolder() {
        if (StringUtils.length(jwtSecret) < 32) {
            throw new IllegalStateException("zhiyi.auth.jwt-secret 长度至少 32 个字符");
        }
        secretKeyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        holder = this;
    }

    /**
     * 获取当前 Spring 容器中的 JWT 工具实例
     */
    public static JwtTokenProvider getHolder() {
        return holder;
    }

    /**
     * 根据登录用户信息签发访问令牌
     */
    public String createAccessToken(LoginUserVO loginUser) {
        long expireAtMillis = System.currentTimeMillis() + jwtExpireHours * 3600L * 1000L;
        return JWT.create()
                .setPayload(CLAIM_USER_ID, loginUser.getUserId())
                .setPayload(CLAIM_USERNAME, loginUser.getUsername())
                .setPayload(CLAIM_NICKNAME, loginUser.getNickname())
                .setPayload(CLAIM_AVATAR, StringUtils.defaultString(loginUser.getAvatar()))
                .setPayload(CLAIM_PERMISSIONS, loginUser.getPermissions())
                .setPayload(CLAIM_ORGANIZATION_ID, loginUser.getOrganizationId())
                .setPayload(CLAIM_WORKSPACE_ID, loginUser.getWorkspaceId())
                .setPayload(CLAIM_WORKSPACE_CODE, loginUser.getWorkspaceCode())
                .setPayload(CLAIM_WORKSPACE_NAME, loginUser.getWorkspaceName())
                .setPayload(CLAIM_MEMBER_ROLE, loginUser.getMemberRole())
                .setExpiresAt(new Date(expireAtMillis))
                .setSigner(JWTSignerUtil.hs256(secretKeyBytes))
                .sign();
    }

    /**
     * 校验访问令牌并还原登录用户信息，无效或过期时返回 null
     */
    public LoginUserVO parseAccessToken(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        try {
            JWT jwt = JWT.of(token);
            if (!jwt.setSigner(JWTSignerUtil.hs256(secretKeyBytes)).verify()) {
                return null;
            }
            if (!jwt.validate(0)) {
                return null;
            }

            LoginUserVO loginUser = new LoginUserVO();
            loginUser.setUserId(jwt.getPayloads().getLong(CLAIM_USER_ID));
            loginUser.setUsername(jwt.getPayloads().getStr(CLAIM_USERNAME));
            loginUser.setNickname(jwt.getPayloads().getStr(CLAIM_NICKNAME));
            loginUser.setAvatar(jwt.getPayloads().getStr(CLAIM_AVATAR));
            loginUser.setPermissions(parsePermissions(jwt.getPayload(CLAIM_PERMISSIONS)));
            loginUser.setOrganizationId(jwt.getPayloads().getLong(CLAIM_ORGANIZATION_ID));
            loginUser.setWorkspaceId(jwt.getPayloads().getStr(CLAIM_WORKSPACE_ID));
            loginUser.setWorkspaceCode(jwt.getPayloads().getStr(CLAIM_WORKSPACE_CODE));
            loginUser.setWorkspaceName(jwt.getPayloads().getStr(CLAIM_WORKSPACE_NAME));
            loginUser.setMemberRole(jwt.getPayloads().getStr(CLAIM_MEMBER_ROLE));
            if (loginUser.getUserId() == null || StringUtils.isBlank(loginUser.getUsername())) {
                return null;
            }
            return loginUser;
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 将 JWT 中的权限字段转为字符串列表
     */
    private List<String> parsePermissions(Object permissionsPayload) {
        if (permissionsPayload == null) {
            return Collections.emptyList();
        }
        if (permissionsPayload instanceof List) {
            List<?> rawList = (List<?>) permissionsPayload;
            List<String> permissions = new ArrayList<String>();
            for (Object item : rawList) {
                if (item != null) {
                    permissions.add(String.valueOf(item));
                }
            }
            return permissions;
        }
        if (permissionsPayload instanceof Iterable) {
            List<String> permissions = new ArrayList<String>();
            for (Object item : (Iterable<?>) permissionsPayload) {
                if (item != null) {
                    permissions.add(String.valueOf(item));
                }
            }
            return CollUtil.isEmpty(permissions) ? Collections.<String>emptyList() : permissions;
        }
        return Collections.singletonList(String.valueOf(permissionsPayload));
    }
}
