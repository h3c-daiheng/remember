package com.zhiyi.auth;

import com.zhiyi.domain.vo.LoginUserVO;
import com.zhiyi.service.GonlineUserSyncService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 登录用户解析器：校验 gonline OAuth token 并同步本地用户与工作空间上下文
 */
@Component
public class LoginUserResolver {

    /** 供静态入口 LoginContext 调用的单例引用 */
    private static LoginUserResolver holder;

    private final GonlineUserSyncService gonlineUserSyncService;

    public LoginUserResolver(GonlineUserSyncService gonlineUserSyncService) {
        this.gonlineUserSyncService = gonlineUserSyncService;
    }

    @PostConstruct
    public void registerHolder() {
        holder = this;
    }

    /**
     * 获取当前 Spring 容器中的解析器实例
     */
    public static LoginUserResolver getHolder() {
        return holder;
    }

    /**
     * 校验 access_token 并返回完整登录用户（含 Workspace 上下文）
     */
    public LoginUserVO resolveLoginUser(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        GonlineOAuthTokenValidator validator = GonlineOAuthTokenValidator.getHolder();
        if (validator == null) {
            return null;
        }
        GonlineOAuthUserProfile gonlineProfile = validator.parseAccessToken(token);
        if (gonlineProfile == null) {
            return null;
        }
        // 透传 access_token，供同步服务调用主站 /workspace/me-context
        return gonlineUserSyncService.resolveLoginUser(gonlineProfile, token);
    }
}
