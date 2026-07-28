package com.zhiyi.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 巨人肩膀 OAuth2 校验配置，用于 C 端 access_token 远程验签
 */
@Component
@ConfigurationProperties(prefix = "zhiyi.auth.gonline")
public class GonlineOAuthProperties {

    /** OAuth check_token 地址 */
    private String checkTokenUrl = "http://127.0.0.1:3902/oauth/check_token";

    /** OAuth 客户端 ID */
    private String clientId = "your-client-id";

    /** OAuth 客户端密钥 */
    private String clientSecret = "your-client-secret";

    /**
     * 主站开放 API 根地址（不含尾斜杠），用于拉取 /workspace/me-context 等。
     * 本地默认与 check-token 同机端口；测试/生产按环境配置。
     */
    private String apiBaseUrl = "http://127.0.0.1:3902";

    public String getCheckTokenUrl() {
        return checkTokenUrl;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public void setCheckTokenUrl(String checkTokenUrl) {
        this.checkTokenUrl = checkTokenUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
}
