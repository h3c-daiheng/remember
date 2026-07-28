package com.zhiyi.auth;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zhiyi.workspace.WorkspaceMemberRole;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 调用主站工作空间开放 API（Bearer 与前端 /gonline-api 一致）。
 */
@Component
public class GonlineWorkspaceClient {

    private static final Logger logger = LoggerFactory.getLogger(GonlineWorkspaceClient.class);

    private final GonlineOAuthProperties gonlineOAuthProperties;

    public GonlineWorkspaceClient(GonlineOAuthProperties gonlineOAuthProperties) {
        this.gonlineOAuthProperties = gonlineOAuthProperties;
    }

    /**
     * 向主站校验 Agent API Key，成功返回空间与权限上下文。
     * 密钥管理已迁至主站，智忆 Memory API 鉴权时调用本方法。
     *
     * @param plainKey 明文 Agent API Key
     * @return 鉴权上下文；失败时返回 null
     */
    public GonlineApiKeyAuthContext authenticateApiKey(String plainKey) {
        if (StringUtils.isBlank(plainKey)) {
            return null;
        }
        String apiBaseUrl = StringUtils.trimToEmpty(gonlineOAuthProperties.getApiBaseUrl());
        if (StringUtils.isBlank(apiBaseUrl)) {
            logger.warn("未配置 zhiyi.auth.gonline.api-base-url，无法校验主站 Agent API Key");
            return null;
        }
        String requestUrl = joinUrl(apiBaseUrl, "/workspace/api-keys/authenticate");
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.set("plainKey", plainKey.trim());
            HttpResponse response = HttpRequest.post(requestUrl)
                    .header("Content-Type", "application/json")
                    .body(requestBody.toString())
                    .timeout(8000)
                    .execute();
            if (response.getStatus() != 200) {
                logger.warn("主站 api-keys/authenticate HTTP 状态异常 status={} body={}",
                        response.getStatus(), response.body());
                return null;
            }
            JSONObject payload = JSONUtil.parseObj(response.body());
            Integer code = payload.getInt("code");
            if (code == null || code.intValue() != 200) {
                logger.warn("主站 api-keys/authenticate 业务失败 code={} message={}",
                        code, payload.getStr("message"));
                return null;
            }
            JSONObject data = payload.getJSONObject("data");
            if (data == null) {
                return null;
            }
            GonlineApiKeyAuthContext authContext = new GonlineApiKeyAuthContext();
            authContext.setApiKeyId(StringUtils.trimToNull(data.getStr("apiKeyId")));
            authContext.setWorkspaceId(StringUtils.trimToNull(data.getStr("workspaceId")));
            authContext.setWorkspaceCode(StringUtils.defaultString(data.getStr("workspaceCode")));
            authContext.setWorkspaceName(StringUtils.defaultString(data.getStr("workspaceName")));
            authContext.setKeyName(StringUtils.defaultString(data.getStr("keyName")));
            authContext.setKeyPrefix(StringUtils.defaultString(data.getStr("keyPrefix")));
            authContext.setPermissionRecall(Boolean.TRUE.equals(data.getBool("permissionRecall")));
            authContext.setPermissionRemember(Boolean.TRUE.equals(data.getBool("permissionRemember")));
            authContext.setCreatorGonlineUserId(StringUtils.trimToNull(data.getStr("creatorUserId")));
            return authContext;
        } catch (Exception exception) {
            logger.warn("调用主站 api-keys/authenticate 异常: {}", exception.getMessage());
            return null;
        }
    }

    /**
     * 拉取主站当前用户头像地址，用于补全智忆顶栏展示。
     *
     * @param accessToken gonline OAuth access_token
     * @return 头像 URL；失败或无头像时返回 null
     */
    public String fetchUserAvatarUrl(String accessToken) {
        if (StringUtils.isBlank(accessToken)) {
            return null;
        }
        String apiBaseUrl = StringUtils.trimToEmpty(gonlineOAuthProperties.getApiBaseUrl());
        if (StringUtils.isBlank(apiBaseUrl)) {
            logger.warn("未配置 zhiyi.auth.gonline.api-base-url，无法拉取主站用户头像");
            return null;
        }
        String requestUrl = joinUrl(apiBaseUrl, "/user/self");
        try {
            HttpResponse response = HttpRequest.get(requestUrl)
                    .header("Authorization", "Bearer " + accessToken.trim())
                    .timeout(8000)
                    .execute();
            if (response.getStatus() != 200) {
                logger.warn("主站 /user/self HTTP 状态异常 status={} body={}",
                        response.getStatus(), response.body());
                return null;
            }
            JSONObject payload = JSONUtil.parseObj(response.body());
            Integer code = payload.getInt("code");
            if (code == null || code.intValue() != 200) {
                logger.warn("主站 /user/self 业务失败 code={} message={}",
                        code, payload.getStr("message"));
                return null;
            }
            JSONObject data = payload.getJSONObject("data");
            if (data == null) {
                return null;
            }
            return StringUtils.trimToNull(data.getStr("avatarUrl"));
        } catch (Exception exception) {
            logger.warn("调用主站 /user/self 异常: {}", exception.getMessage());
            return null;
        }
    }

    /**
     * 拉取当前用户工作空间上下文；主站会按需自动开通个人空间。
     *
     * @param accessToken gonline OAuth access_token
     * @return 上下文；失败时返回 null（由调用方决定是否降级）
     */
    public GonlineWorkspaceContext fetchMeContext(String accessToken) {
        if (StringUtils.isBlank(accessToken)) {
            return null;
        }
        String apiBaseUrl = StringUtils.trimToEmpty(gonlineOAuthProperties.getApiBaseUrl());
        if (StringUtils.isBlank(apiBaseUrl)) {
            logger.warn("未配置 zhiyi.auth.gonline.api-base-url，无法拉取主站工作空间上下文");
            return null;
        }
        String requestUrl = joinUrl(apiBaseUrl, "/workspace/me-context");
        try {
            HttpResponse response = HttpRequest.get(requestUrl)
                    .header("Authorization", "Bearer " + accessToken.trim())
                    .timeout(8000)
                    .execute();
            if (response.getStatus() != 200) {
                logger.warn("主站 me-context HTTP 状态异常 status={} body={}",
                        response.getStatus(), response.body());
                return null;
            }
            JSONObject payload = JSONUtil.parseObj(response.body());
            Integer code = payload.getInt("code");
            if (code == null || code.intValue() != 200) {
                logger.warn("主站 me-context 业务失败 code={} message={}",
                        code, payload.getStr("message"));
                return null;
            }
            JSONObject data = payload.getJSONObject("data");
            if (data == null) {
                return null;
            }
            GonlineWorkspaceContext context = new GonlineWorkspaceContext();
            context.setWorkspaceId(StringUtils.trimToNull(data.getStr("workspaceId")));
            context.setWorkspaceCode(StringUtils.defaultString(data.getStr("workspaceCode")));
            context.setWorkspaceName(StringUtils.defaultString(data.getStr("workspaceName")));
            context.setWorkspaceType(data.getInt("workspaceType"));
            // 主站返回整型 1~4，统一归一为智忆内部字符串角色
            context.setMemberRole(resolveMemberRole(data));
            return context;
        } catch (Exception exception) {
            logger.warn("调用主站 me-context 异常: {}", exception.getMessage());
            return null;
        }
    }

    /**
     * 从主站 JSON 解析成员角色：优先整型，兼容历史字符串，并归一为 owner/admin/editor/viewer
     */
    private String resolveMemberRole(JSONObject data) {
        if (data == null) {
            return null;
        }
        Integer memberRoleCode = data.getInt("memberRole");
        if (memberRoleCode != null) {
            return WorkspaceMemberRole.normalize(memberRoleCode);
        }
        return WorkspaceMemberRole.normalize(data.getStr("memberRole"));
    }

    /**
     * 拼接 API base 与 path，避免双斜杠或漏斜杠。
     */
    private String joinUrl(String baseUrl, String path) {
        String normalizedBase = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + normalizedPath;
    }

    /**
     * 主站 Agent API Key 鉴权结果（与 WorkspaceAgentApiKeyAuthVO 对齐）。
     */
    public static class GonlineApiKeyAuthContext {

        private String apiKeyId;
        private String workspaceId;
        private String workspaceCode;
        private String workspaceName;
        private String keyName;
        private String keyPrefix;
        private boolean permissionRecall;
        private boolean permissionRemember;
        private String creatorGonlineUserId;

        public String getCreatorGonlineUserId() {
            return creatorGonlineUserId;
        }

        public void setCreatorGonlineUserId(String creatorGonlineUserId) {
            this.creatorGonlineUserId = creatorGonlineUserId;
        }

        public String getApiKeyId() {
            return apiKeyId;
        }

        public void setApiKeyId(String apiKeyId) {
            this.apiKeyId = apiKeyId;
        }

        public String getWorkspaceId() {
            return workspaceId;
        }

        public void setWorkspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
        }

        public String getWorkspaceCode() {
            return workspaceCode;
        }

        public void setWorkspaceCode(String workspaceCode) {
            this.workspaceCode = workspaceCode;
        }

        public String getWorkspaceName() {
            return workspaceName;
        }

        public void setWorkspaceName(String workspaceName) {
            this.workspaceName = workspaceName;
        }

        public String getKeyName() {
            return keyName;
        }

        public void setKeyName(String keyName) {
            this.keyName = keyName;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public boolean isPermissionRecall() {
            return permissionRecall;
        }

        public void setPermissionRecall(boolean permissionRecall) {
            this.permissionRecall = permissionRecall;
        }

        public boolean isPermissionRemember() {
            return permissionRemember;
        }

        public void setPermissionRemember(boolean permissionRemember) {
            this.permissionRemember = permissionRemember;
        }
    }

    /**
     * 主站工作空间上下文（与 WorkspaceContextVO 对齐）。
     */
    public static class GonlineWorkspaceContext {

        private String workspaceId;
        private String workspaceCode;
        private String workspaceName;
        private Integer workspaceType;
        private String memberRole;

        public String getWorkspaceId() {
            return workspaceId;
        }

        public void setWorkspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
        }

        public String getWorkspaceCode() {
            return workspaceCode;
        }

        public void setWorkspaceCode(String workspaceCode) {
            this.workspaceCode = workspaceCode;
        }

        public String getWorkspaceName() {
            return workspaceName;
        }

        public void setWorkspaceName(String workspaceName) {
            this.workspaceName = workspaceName;
        }

        public Integer getWorkspaceType() {
            return workspaceType;
        }

        public void setWorkspaceType(Integer workspaceType) {
            this.workspaceType = workspaceType;
        }

        public String getMemberRole() {
            return memberRole;
        }

        public void setMemberRole(String memberRole) {
            this.memberRole = memberRole;
        }
    }
}
