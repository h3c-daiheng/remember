package com.zhiyi.memory.controller;

import com.zhiyi.auth.AgentAuthContext;
import com.zhiyi.auth.LoginContext;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.Result;
import com.zhiyi.domain.vo.GraphViewVO;
import com.zhiyi.domain.vo.LoginUserVO;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.domain.FeedbackRequest;
import com.zhiyi.memory.domain.GraphExploreRequest;
import com.zhiyi.memory.domain.RecallContext;
import com.zhiyi.memory.domain.RecallResponse;
import com.zhiyi.memory.domain.RecallResult;
import com.zhiyi.memory.domain.SystemEventRequest;
import com.zhiyi.memory.service.MemoryOperationLogService;
import com.zhiyi.memory.service.MemoryService;
import com.zhiyi.memory.service.AgentCreatorResolver;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Memory API：供 Agent / MCP 调用的 Recall / Submit / Search / Feedback 接口
 */
@RestController
@RequestMapping("/memory")
public class MemoryApiController {

    private final MemoryService memoryService;
    private final MemoryOperationLogService memoryOperationLogService;
    private final AgentCreatorResolver agentCreatorResolver;

    public MemoryApiController(MemoryService memoryService,
                               MemoryOperationLogService memoryOperationLogService,
                               AgentCreatorResolver agentCreatorResolver) {
        this.memoryService = memoryService;
        this.memoryOperationLogService = memoryOperationLogService;
        this.agentCreatorResolver = agentCreatorResolver;
    }

    /**
     * Recall：任务上下文召回相关 Fact Blocks
     */
    @PostMapping("/recall")
    public Result<RecallResponse> recall(@RequestBody RecallContext recallContext,
                                         HttpServletRequest request) {
        AgentAuthContext authContext = AgentAuthContext.requireAuth(request);
        authContext.requireRecallPermission();
        applyWorkspaceContext(recallContext, authContext);
        String traceId = memoryOperationLogService.resolveTraceId(request.getHeader(MemoryConstants.HEADER_TRACE_ID));
        long startTime = System.currentTimeMillis();
        try {
            RecallResult recallResult = memoryService.recall(recallContext);
            memoryOperationLogService.recordRecallSuccess(authContext, traceId,
                    MemoryConstants.OPERATION_RECALL, recallContext, recallResult,
                    System.currentTimeMillis() - startTime);
            return Result.success(recallResult.getResponse());
        } catch (Exception exception) {
            memoryOperationLogService.recordFailure(authContext, traceId, MemoryConstants.OPERATION_RECALL,
                    recallContext, null, exception.getMessage(), System.currentTimeMillis() - startTime);
            throw exception;
        }
    }

    /**
     * Search：语义搜索经验，MVP 与 Recall 共用引擎
     */
    @PostMapping("/search")
    public Result<RecallResponse> search(@RequestBody RecallContext recallContext,
                                         HttpServletRequest request) {
        AgentAuthContext authContext = AgentAuthContext.requireAuth(request);
        authContext.requireRecallPermission();
        applyWorkspaceContext(recallContext, authContext);
        String traceId = memoryOperationLogService.resolveTraceId(request.getHeader(MemoryConstants.HEADER_TRACE_ID));
        long startTime = System.currentTimeMillis();
        try {
            RecallResult recallResult = memoryService.search(recallContext);
            memoryOperationLogService.recordRecallSuccess(authContext, traceId,
                    MemoryConstants.OPERATION_SEARCH, recallContext, recallResult,
                    System.currentTimeMillis() - startTime);
            return Result.success(recallResult.getResponse());
        } catch (Exception exception) {
            memoryOperationLogService.recordFailure(authContext, traceId, MemoryConstants.OPERATION_SEARCH,
                    recallContext, null, exception.getMessage(), System.currentTimeMillis() - startTime);
            throw exception;
        }
    }

    /**
     * Submit：Agent 统一提交记忆，全部进入 Capture 草稿待人工确认
     */
    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@RequestBody SystemEventRequest eventRequest,
                                             HttpServletRequest request) {
        AgentAuthContext authContext = AgentAuthContext.requireAuth(request);
        authContext.requireRememberPermission();
        applyWorkspaceContext(eventRequest, authContext);
        String traceId = memoryOperationLogService.resolveTraceId(request.getHeader(MemoryConstants.HEADER_TRACE_ID));
        long startTime = System.currentTimeMillis();
        try {
            Long creatorId = resolveActorUserId(authContext, request);
            Map<String, Object> responseData = memoryService.submit(eventRequest, creatorId);
            memoryOperationLogService.recordSubmitSuccess(authContext, traceId,
                    eventRequest, responseData, System.currentTimeMillis() - startTime);
            return Result.success(responseData);
        } catch (Exception exception) {
            memoryOperationLogService.recordFailure(authContext, traceId, MemoryConstants.OPERATION_SUBMIT,
                    eventRequest, null, exception.getMessage(), System.currentTimeMillis() - startTime);
            throw exception;
        }
    }

    /**
     * Feedback：记录 Recall 使用效果
     */
    @PostMapping("/feedback")
    public Result<Void> feedback(@RequestBody FeedbackRequest feedbackRequest,
                                 HttpServletRequest request) {
        AgentAuthContext authContext = AgentAuthContext.requireAuth(request);
        authContext.requireRecallPermission();
        String traceId = memoryOperationLogService.resolveTraceId(request.getHeader(MemoryConstants.HEADER_TRACE_ID));
        long startTime = System.currentTimeMillis();
        try {
            Long actorId = resolveActorUserId(authContext, request);
            memoryService.feedback(feedbackRequest, null, actorId);
            memoryOperationLogService.recordFeedbackSuccess(authContext, traceId,
                    feedbackRequest, System.currentTimeMillis() - startTime);
            return Result.success(null);
        } catch (Exception exception) {
            memoryOperationLogService.recordFailure(authContext, traceId, MemoryConstants.OPERATION_FEEDBACK,
                    feedbackRequest,
                    feedbackRequest == null ? null : feedbackRequest.getSessionId(),
                    exception.getMessage(), System.currentTimeMillis() - startTime);
            throw exception;
        }
    }

    /**
     * Graph Explore：以 centerId 为中心返回子图 JSON，供 Agent 探索经验关系
     */
    @PostMapping("/graph-explore")
    public Result<GraphViewVO> graphExplore(@RequestBody GraphExploreRequest exploreRequest,
                                              HttpServletRequest request) {
        AgentAuthContext authContext = AgentAuthContext.requireAuth(request);
        authContext.requireRecallPermission();
        GraphViewVO graphView = memoryService.graphExplore(exploreRequest, authContext.getWorkspaceId());
        return Result.success(graphView);
    }

    /**
     * 将鉴权上下文中的 workspace 写入 RecallContext，并校验跨空间访问
     */
    private void applyWorkspaceContext(RecallContext recallContext, AgentAuthContext authContext) {
        if (recallContext == null) {
            return;
        }
        if (StringUtils.isNotBlank(recallContext.getWorkspace())
                && !authContext.getWorkspaceCode().equals(recallContext.getWorkspace())) {
            throw new BusinessException(403, "无权访问指定工作空间的数据");
        }
        recallContext.setWorkspace(authContext.getWorkspaceCode());
        recallContext.setWorkspaceId(authContext.getWorkspaceId());
    }

    /**
     * 将鉴权上下文中的 workspace 写入 Event 请求，并校验跨空间访问
     */
    private void applyWorkspaceContext(SystemEventRequest eventRequest, AgentAuthContext authContext) {
        if (eventRequest == null) {
            return;
        }
        if (StringUtils.isNotBlank(eventRequest.getWorkspace())
                && !authContext.getWorkspaceCode().equals(eventRequest.getWorkspace())) {
            throw new BusinessException(403, "无权访问指定工作空间的数据");
        }
        eventRequest.setWorkspace(authContext.getWorkspaceCode());
        eventRequest.setWorkspaceId(authContext.getWorkspaceId());
    }

    /**
     * 解析 Memory API 操作者本地用户 ID：优先 JWT/token，其次 API Key 签发人，最后回退工作空间成员。
     * 写入 system_event.creator_id 后，卡片/详情从库中读取，不再依赖当前请求 token。
     */
    private Long resolveActorUserId(AgentAuthContext authContext, HttpServletRequest request) {
        if (authContext.getUserId() != null) {
            return authContext.getUserId();
        }
        try {
            LoginUserVO loginUser = LoginContext.requireLoginUser(request);
            if (loginUser != null && loginUser.getUserId() != null) {
                return loginUser.getUserId();
            }
        } catch (Exception exception) {
            // API Key 鉴权时 token 非 OAuth，继续走 Key 签发人 / 工作空间回退
        }
        if (StringUtils.isNotBlank(authContext.getApiKeyCreatorGonlineUserId())) {
            Long mappedUserId = agentCreatorResolver.resolveLocalUserIdByGonlineUserId(
                    authContext.getApiKeyCreatorGonlineUserId());
            if (mappedUserId != null) {
                return mappedUserId;
            }
        }
        return agentCreatorResolver.resolveCreatorId(null, authContext.getWorkspaceId());
    }
}
