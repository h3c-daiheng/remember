package com.zhiyi.auth;

import com.zhiyi.common.BusinessException;
import com.zhiyi.domain.vo.LoginUserVO;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * 登录上下文工具：C 端走 gonline OAuth token，校验后同步本地用户与工作空间
 */
public final class LoginContext {

    /** 请求头中携带令牌的字段名 */
    public static final String TOKEN_HEADER = "Authorization";

    /** 令牌前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    private LoginContext() {
    }

    /**
     * C 端接口：校验 gonline OAuth access_token 并返回当前用户（含 Workspace 上下文）
     */
    public static LoginUserVO requireLoginUser(HttpServletRequest request) {
        String token = resolveToken(request);
        if (StringUtils.isBlank(token)) {
            throw new BusinessException(401, "请先登录");
        }
        JwtTokenProvider jwtTokenProvider = JwtTokenProvider.getHolder();
        if (jwtTokenProvider == null) {
            throw new BusinessException(500, "认证组件未就绪");
        }
        LoginUserVO loginUser = jwtTokenProvider.parseAccessToken(token);
        if (loginUser == null) {
            throw new BusinessException(401, "登录已过期，请重新登录");
        }
        return loginUser;
    }

    /**
     * 从 Authorization 请求头或 query token 中提取令牌（SSE 无法携带 Header）
     */
    public static String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(TOKEN_HEADER);
        if (StringUtils.isNotBlank(authorization)) {
            if (authorization.startsWith(TOKEN_PREFIX)) {
                return authorization.substring(TOKEN_PREFIX.length()).trim();
            }
            return authorization.trim();
        }
        return StringUtils.trimToNull(request.getParameter("token"));
    }
}
