package com.zhiyi.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhiyi.auth.LoginContext;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.Result;
import com.zhiyi.dao.ApiKeyMapper;
import com.zhiyi.domain.entity.ApiKeyEntity;
import com.zhiyi.domain.vo.ApiKeyVO;
import com.zhiyi.domain.vo.LoginUserVO;
import com.zhiyi.util.ApiKeyUtil;
import com.zhiyi.workspace.WorkspaceMemberRole;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent API Key 本地管理：列表/签发/吊销（不依赖 gonline 主站）。
 * 与本地账号登录同走 /api 通道，Result.code=0；鉴权用本地 JWT。
 */
@RestController
@RequestMapping("/workspace/api-keys")
public class WorkspaceApiKeyController {

    private final ApiKeyMapper apiKeyMapper;

    public WorkspaceApiKeyController(ApiKeyMapper apiKeyMapper) {
        this.apiKeyMapper = apiKeyMapper;
    }

    /**
     * 当前工作空间的 Agent API Key 列表（全体成员可查；不含 hash）
     */
    @GetMapping
    public Result<Map<String, Object>> list(HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        String workspaceId = loginUser.getWorkspaceId();
        if (StringUtils.isBlank(workspaceId)) {
            throw new BusinessException(400, "请先选择工作空间");
        }
        List<ApiKeyEntity> entities = apiKeyMapper.selectList(
                new QueryWrapper<ApiKeyEntity>()
                        .eq("workspace_id", workspaceId)
                        .orderByDesc("create_time"));
        List<ApiKeyVO> keyList = entities.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        Map<String, Object> data = new HashMap<>();
        data.put("keyList", keyList);
        return Result.success(data);
    }

    /**
     * 签发新 Agent API Key（仅 owner/admin）；明文仅返回一次
     */
    @PostMapping
    public Result<Map<String, Object>> create(HttpServletRequest request,
                                               @RequestBody CreateRequest req) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        String workspaceId = loginUser.getWorkspaceId();
        if (StringUtils.isBlank(workspaceId)) {
            throw new BusinessException(400, "请先选择工作空间");
        }
        if (!WorkspaceMemberRole.canManageWorkspace(loginUser.getMemberRole())) {
            throw new BusinessException(403, "仅拥有者或管理员可签发 Agent API Key");
        }
        String keyName = req == null ? null : StringUtils.trimToNull(req.getKeyName());
        if (StringUtils.isBlank(keyName)) {
            throw new BusinessException(400, "密钥名称不能为空");
        }
        boolean permissionRecall = req == null || req.getPermissionRecall() == null || req.getPermissionRecall();
        boolean permissionRemember = req == null || req.getPermissionRemember() == null || req.getPermissionRemember();

        String plainKey = ApiKeyUtil.generatePlainKey();
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setKeyName(keyName);
        entity.setKeyHash(ApiKeyUtil.hashPlainKey(plainKey));
        entity.setKeyPrefix(ApiKeyUtil.extractStoredPrefix(plainKey));
        entity.setPermissionRecall(permissionRecall ? 1 : 0);
        entity.setPermissionRemember(permissionRemember ? 1 : 0);
        entity.setStatus(1);
        apiKeyMapper.insert(entity);

        // 明文仅此一次返回；同时返回元数据供前端直接展示
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("plainKey", plainKey);
        data.put("id", entity.getId());
        data.put("keyName", entity.getKeyName());
        data.put("keyPrefix", entity.getKeyPrefix());
        data.put("permissionRecall", permissionRecall);
        data.put("permissionRemember", permissionRemember);
        data.put("enabled", true);
        data.put("createTime", entity.getCreateTime());
        return Result.success(data);
    }

    /**
     * 吊销 Agent API Key（仅 owner/admin）
     */
    @DeleteMapping("/{id}")
    public Result<Void> revoke(HttpServletRequest request, @PathVariable("id") Long id) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        String workspaceId = loginUser.getWorkspaceId();
        if (StringUtils.isBlank(workspaceId)) {
            throw new BusinessException(400, "请先选择工作空间");
        }
        if (!WorkspaceMemberRole.canManageWorkspace(loginUser.getMemberRole())) {
            throw new BusinessException(403, "仅拥有者或管理员可吊销 Agent API Key");
        }
        ApiKeyEntity entity = apiKeyMapper.selectById(id);
        if (entity == null || !workspaceId.equals(entity.getWorkspaceId())) {
            throw new BusinessException(404, "API Key 不存在");
        }
        entity.setStatus(0);
        apiKeyMapper.updateById(entity);
        return Result.success(null);
    }

    private ApiKeyVO toVO(ApiKeyEntity entity) {
        ApiKeyVO vo = new ApiKeyVO();
        vo.setId(entity.getId());
        vo.setKeyName(entity.getKeyName());
        vo.setKeyPrefix(entity.getKeyPrefix());
        vo.setPermissionRecall(entity.getPermissionRecall() != null && entity.getPermissionRecall() == 1);
        vo.setPermissionRemember(entity.getPermissionRemember() != null && entity.getPermissionRemember() == 1);
        vo.setEnabled(entity.getStatus() != null && entity.getStatus() == 1);
        vo.setLastUsedTime(entity.getLastUsedTime());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /** 签发入参 */
    @Data
    public static class CreateRequest {
        private String keyName;
        private Boolean permissionRecall;
        private Boolean permissionRemember;
    }
}
