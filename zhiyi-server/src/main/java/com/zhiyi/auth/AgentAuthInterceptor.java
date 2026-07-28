package com.zhiyi.auth;

import com.zhiyi.common.BusinessException;
import com.zhiyi.domain.vo.LoginUserVO;
import com.zhiyi.service.ApiKeyService;
import com.zhiyi.util.ApiKeyUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Memory API 鉴权拦截器：支持 Agent API Key 与 gonline OAuth token 两种鉴权方式。
 * gonline 路径下工作空间上下文以主站 me-context 为准，不再查本地 workspace_member。
 */
@Component
public class AgentAuthInterceptor implements HandlerInterceptor {

    private final ApiKeyService apiKeyService;

    public AgentAuthInterceptor(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    /**
     * 解析 Authorization 并注入 AgentAuthContext
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = LoginContext.resolveToken(request);
        if (StringUtils.isBlank(token)) {
            throw new BusinessException(401, "请携带 Authorization: Bearer <api_key>");
        }

        AgentAuthContext authContext;
        if (ApiKeyUtil.isApiKeyToken(token)) {
            authContext = apiKeyService.authenticateByPlainKey(token);
        } else {
            authContext = authenticateByJwt(token);
        }

        request.setAttribute(AgentAuthContext.REQUEST_ATTRIBUTE, authContext);
        return true;
    }

    /**
     * 使用本地 JWT 构建 Agent 鉴权上下文（已替代 gonline，与 LoginContext 同源）
     */
    private AgentAuthContext authenticateByJwt(String token) {
        JwtTokenProvider jwtTokenProvider = JwtTokenProvider.getHolder();
        if (jwtTokenProvider == null) {
            throw new BusinessException(500, "认证组件未就绪");
        }
        LoginUserVO loginUser = jwtTokenProvider.parseAccessToken(token);
        if (loginUser == null) {
            throw new BusinessException(401, "登录已过期或 Agent API Key 无效");
        }
        if (StringUtils.isBlank(loginUser.getWorkspaceId())) {
            throw new BusinessException(403, "请先切换工作空间后再调用 Memory API");
        }

        AgentAuthContext authContext = new AgentAuthContext();
        authContext.setAuthType(AgentAuthContext.AUTH_TYPE_JWT);
        authContext.setUserId(loginUser.getUserId());
        authContext.setWorkspaceId(loginUser.getWorkspaceId());
        authContext.setWorkspaceCode(loginUser.getWorkspaceCode());
        authContext.setOrganizationId(null);
        authContext.setPlanType("free");
        authContext.setPermissionRecall(true);
        authContext.setPermissionRemember(true);
        return authContext;
    }
}
