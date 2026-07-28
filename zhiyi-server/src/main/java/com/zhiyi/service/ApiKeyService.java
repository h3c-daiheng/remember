package com.zhiyi.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhiyi.auth.AgentAuthContext;
import com.zhiyi.common.BusinessException;
import com.zhiyi.dao.ApiKeyMapper;
import com.zhiyi.dao.WorkspaceMapper;
import com.zhiyi.domain.entity.ApiKeyEntity;
import com.zhiyi.domain.entity.WorkspaceEntity;
import com.zhiyi.util.ApiKeyUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Agent API Key 鉴权服务：本地查表校验（不依赖 gonline）
 */
@Service
public class ApiKeyService {

    private final ApiKeyMapper apiKeyMapper;
    private final WorkspaceMapper workspaceMapper;

    public ApiKeyService(ApiKeyMapper apiKeyMapper, WorkspaceMapper workspaceMapper) {
        this.apiKeyMapper = apiKeyMapper;
        this.workspaceMapper = workspaceMapper;
    }

    /**
     * 本地校验 Agent API Key：按 key_hash 查 api_key 表
     */
    public AgentAuthContext authenticateByPlainKey(String plainKey) {
        if (!ApiKeyUtil.isApiKeyToken(plainKey)) {
            throw new BusinessException(401, "无效的 Agent API Key");
        }
        String keyHash = ApiKeyUtil.hashPlainKey(plainKey);
        ApiKeyEntity apiKey = apiKeyMapper.selectOne(new QueryWrapper<ApiKeyEntity>()
                .eq("key_hash", keyHash)
                .eq("status", 1));
        if (apiKey == null) {
            throw new BusinessException(401, "Agent API Key 无效或已吊销");
        }
        String workspaceId = StringUtils.defaultIfBlank(apiKey.getWorkspaceId(), "1");
        String workspaceCode = workspaceId;
        WorkspaceEntity workspace = workspaceMapper.selectById(workspaceId);
        if (workspace != null) {
            workspaceCode = StringUtils.defaultIfBlank(workspace.getWorkspaceCode(), workspaceId);
        }

        AgentAuthContext authContext = new AgentAuthContext();
        authContext.setAuthType(AgentAuthContext.AUTH_TYPE_API_KEY);
        authContext.setApiKeyId(apiKey.getId());
        authContext.setWorkspaceId(workspaceId);
        authContext.setWorkspaceCode(workspaceCode);
        authContext.setOrganizationId(null);
        authContext.setPlanType("free");
        authContext.setPermissionRecall(apiKey.getPermissionRecall() != null && apiKey.getPermissionRecall() == 1);
        authContext.setPermissionRemember(apiKey.getPermissionRemember() != null && apiKey.getPermissionRemember() == 1);
        return authContext;
    }
}
