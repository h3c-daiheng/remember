package com.zhiyi.memory.controller;

import com.zhiyi.auth.AgentAuthContext;
import com.zhiyi.common.PageResult;
import com.zhiyi.common.Result;
import com.zhiyi.memory.domain.MemoryTraceView;
import com.zhiyi.memory.domain.RecallLogDetailView;
import com.zhiyi.memory.domain.RecallLogListItem;
import com.zhiyi.memory.service.MemoryOperationLogService;
import com.zhiyi.memory.service.MemoryTraceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * Memory 调试 API：闭环追踪，验证 Capture → 路由 → 发布 → Recall 命中
 */
@RestController
@RequestMapping("/memory/debug")
public class MemoryDebugController {

    private final MemoryTraceService memoryTraceService;
    private final MemoryOperationLogService memoryOperationLogService;

    public MemoryDebugController(MemoryTraceService memoryTraceService,
                                 MemoryOperationLogService memoryOperationLogService) {
        this.memoryTraceService = memoryTraceService;
        this.memoryOperationLogService = memoryOperationLogService;
    }

    /**
     * 闭环追踪：按 draftId / knowledgeId 聚合链路；两者皆空时返回最近一次 Recall 的闭环视图
     */
    @GetMapping("/trace")
    public Result<MemoryTraceView> trace(@RequestParam(required = false) Long draftId,
                                         @RequestParam(required = false) Long knowledgeId,
                                         HttpServletRequest request) {
        AgentAuthContext authContext = AgentAuthContext.requireAuth(request);
        String workspaceId = authContext.getWorkspaceId();
        MemoryTraceView traceView;
        if (draftId == null && knowledgeId == null) {
            traceView = memoryTraceService.buildTraceFromLatestRecall(workspaceId);
        } else {
            traceView = memoryTraceService.buildTrace(draftId, knowledgeId, workspaceId);
        }
        return Result.success(traceView);
    }

    /**
     * 分页查询工作空间内全部 Agent Recall / Search 请求记录
     */
    @GetMapping("/recall-logs")
    public Result<PageResult<RecallLogListItem>> recallLogs(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) Integer success,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        AgentAuthContext authContext = AgentAuthContext.requireAuth(request);
        return Result.success(memoryOperationLogService.listRecallLogs(
                pageNum, pageSize, operation, success, keyword, authContext.getWorkspaceId()));
    }

    /**
     * Recall 日志详情：查看单次召回的输入、Query、排序分解与 promptBlock 预览
     */
    @GetMapping("/recall-log")
    public Result<RecallLogDetailView> recallLog(@RequestParam Long logId,
                                                  HttpServletRequest request) {
        AgentAuthContext authContext = AgentAuthContext.requireAuth(request);
        RecallLogDetailView detailView = memoryOperationLogService.getRecallLogDetail(
                logId, authContext.getWorkspaceId());
        return Result.success(detailView);
    }
}
