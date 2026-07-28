package com.zhiyi.auth;

import com.zhiyi.common.BusinessException;
import lombok.Data;

import javax.servlet.http.HttpServletRequest;

/**
 * Agent 鉴权上下文：Memory API 通过 API Key 或 JWT 注入工作空间信息
 */
@Data
public class AgentAuthContext {

    /** 请求属性名，供 Controller 读取 */
    public static final String REQUEST_ATTRIBUTE = "agentAuthContext";

    /** 鉴权方式：api_key */
    public static final String AUTH_TYPE_API_KEY = "api_key";

    /** 鉴权方式：jwt */
    public static final String AUTH_TYPE_JWT = "jwt";

    /** 鉴权方式 */
    private String authType;

    /** API Key 主键，JWT 鉴权时为 null */
    private Long apiKeyId;

    /** 工作空间主键 */
    private String workspaceId;

    /** 工作空间编码 */
    private String workspaceCode;

    /** 组织主键 */
    private Long organizationId;

    /** 套餐类型 */
    private String planType;

    /** 是否允许 Recall */
    private boolean permissionRecall;

    /** 是否允许 Remember */
    private boolean permissionRemember;

    /** JWT 鉴权时的本地用户 ID（sys_user.id） */
    private Long userId;

    /** API Key 签发人在 gonline 侧的用户 ID，用于 MCP 仅 Key 鉴权时关联提交人 */
    private String apiKeyCreatorGonlineUserId;

    /**
     * 从请求中读取 Agent 鉴权上下文
     */
    public static AgentAuthContext requireAuth(HttpServletRequest request) {
        Object contextObject = request.getAttribute(REQUEST_ATTRIBUTE);
        if (!(contextObject instanceof AgentAuthContext)) {
            throw new BusinessException(401, "请先配置 Agent API Key 或登录后再调用");
        }
        return (AgentAuthContext) contextObject;
    }

    /**
     * 校验当前上下文是否具备 Recall 权限
     */
    public void requireRecallPermission() {
        if (!permissionRecall) {
            throw new BusinessException(403, "当前 API Key 未授权 Recall");
        }
    }

    /**
     * 校验当前上下文是否具备 Remember 权限
     */
    public void requireRememberPermission() {
        if (!permissionRemember) {
            throw new BusinessException(403, "当前 API Key 未授权 Remember");
        }
    }
}
