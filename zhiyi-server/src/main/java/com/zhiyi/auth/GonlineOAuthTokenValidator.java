package com.zhiyi.auth;

import cn.hutool.core.codec.Base64;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

/**
 * 调用巨人肩膀 OAuth /oauth/check_token 校验 C 端 access_token，并还原 gonline 用户标识
 */
@Component
public class GonlineOAuthTokenValidator {

    /** 供静态入口 LoginContext 调用的单例引用 */
    private static GonlineOAuthTokenValidator holder;

    private final GonlineOAuthProperties gonlineOAuthProperties;

    /** Basic 认证头缓存 */
    private String basicAuthorizationHeader;

    public GonlineOAuthTokenValidator(GonlineOAuthProperties gonlineOAuthProperties) {
        this.gonlineOAuthProperties = gonlineOAuthProperties;
    }

    @PostConstruct
    public void registerHolder() {
        String clientCredentials = gonlineOAuthProperties.getClientId()
                + ":" + gonlineOAuthProperties.getClientSecret();
        basicAuthorizationHeader = "Basic "
                + Base64.encode(clientCredentials.getBytes(StandardCharsets.UTF_8));
        holder = this;
    }

    /**
     * 获取当前 Spring 容器中的校验器实例
     */
    public static GonlineOAuthTokenValidator getHolder() {
        return holder;
    }

    /**
     * 校验 gonline access_token，有效时返回用户摘要，无效或过期返回 null
     */
    public GonlineOAuthUserProfile parseAccessToken(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        try {
            HttpResponse response = HttpRequest.post(gonlineOAuthProperties.getCheckTokenUrl())
                    .header("Authorization", basicAuthorizationHeader)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("token=" + token.trim())
                    .timeout(8000)
                    .execute();
            if (response.getStatus() != 200) {
                return null;
            }
            JSONObject payload = JSONUtil.parseObj(response.body());
            if (!payload.getBool("active", false)) {
                return null;
            }
            String gonlineUserId = resolveUserId(payload);
            if (StringUtils.isBlank(gonlineUserId)) {
                return null;
            }
            GonlineOAuthUserProfile userProfile = new GonlineOAuthUserProfile();
            userProfile.setGonlineUserId(gonlineUserId);
            userProfile.setUsername(StringUtils.defaultString(payload.getStr("user_name")));
            userProfile.setNickname(StringUtils.defaultString(payload.getStr("user_name")));
            return userProfile;
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 从 check_token 响应中提取用户主键，兼容 user_id / userId 字段
     */
    private String resolveUserId(JSONObject payload) {
        Object userIdValue = payload.get("user_id");
        if (userIdValue == null) {
            userIdValue = payload.get("userId");
        }
        if (userIdValue == null) {
            return null;
        }
        return StringUtils.trimToNull(String.valueOf(userIdValue));
    }
}
