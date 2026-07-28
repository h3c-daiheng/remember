package com.zhiyi.memory.controller;

import com.zhiyi.auth.LoginContext;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.PageResult;
import com.zhiyi.common.Result;
import com.zhiyi.domain.vo.LoginUserVO;
import com.zhiyi.memory.domain.*;
import com.zhiyi.memory.governance.GovernanceService;
import com.zhiyi.workspace.WorkspaceMemberRole;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 已发布记忆治理 API：重复扫描、碎片聚类、合并优化与工单处置
 */
@RestController
@RequestMapping("/governance")
public class GovernanceController {

    private final GovernanceService governanceService;

    public GovernanceController(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    /**
     * 触发治理扫描（重复 / 碎片聚类）
     */
    @PostMapping("/scans")
    public Result<GovernanceScanBatchView> runScan(@RequestBody(required = false) GovernanceScanRequest scanRequest,
                                                   HttpServletRequest request) {
        LoginUserVO loginUser = requireKnowledgeEditor(request);
        return Result.success(governanceService.runScan(
                scanRequest, requireWorkspaceId(loginUser), loginUser.getUserId()));
    }

    /**
     * 分页查询治理工单
     */
    @GetMapping("/issues")
    public Result<PageResult<GovernanceIssueView>> listIssues(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String issueType,
            HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        return Result.success(governanceService.listIssues(
                pageNum, pageSize, status, issueType, requireWorkspaceId(loginUser)));
    }

    /**
     * 查询治理工单详情
     */
    @GetMapping("/issues/{id}")
    public Result<GovernanceIssueView> issueDetail(@PathVariable Long id, HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        return Result.success(governanceService.getIssueDetail(id, requireWorkspaceId(loginUser)));
    }

    /**
     * 处置治理工单
     */
    @PostMapping("/issues/{id}/resolve")
    public Result<GovernanceIssueView> resolveIssue(@PathVariable Long id,
                                                    @RequestBody GovernanceResolveRequest resolveRequest,
                                                    HttpServletRequest request) {
        LoginUserVO loginUser = requireKnowledgeEditor(request);
        return Result.success(governanceService.resolveIssue(
                id, resolveRequest, requireWorkspaceId(loginUser),
                loginUser.getUserId(), loginUser.getMemberRole()));
    }

    /**
     * 治理统计摘要
     */
    @GetMapping("/stats")
    public Result<GovernanceStatsView> stats(HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        return Result.success(governanceService.getStats(requireWorkspaceId(loginUser)));
    }

    /**
     * 查询工作空间治理配置
     */
    @GetMapping("/config")
    public Result<GovernanceConfigView> config(HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        return Result.success(governanceService.getConfig(requireWorkspaceId(loginUser)));
    }

    /**
     * 更新工作空间治理配置
     */
    @PostMapping("/config")
    public Result<GovernanceConfigView> updateConfig(@RequestBody GovernanceConfigUpdateRequest updateRequest,
                                                     HttpServletRequest request) {
        LoginUserVO loginUser = requireWorkspaceAdmin(request);
        return Result.success(governanceService.updateConfig(
                updateRequest, requireWorkspaceId(loginUser)));
    }

    /**
     * 生成合并预览（工单或手动多选）
     */
    @PostMapping("/merge/preview")
    public Result<GovernanceMergePreviewView> previewMerge(@RequestBody GovernanceMergePreviewRequest previewRequest,
                                                           HttpServletRequest request) {
        LoginUserVO loginUser = requireKnowledgeEditor(request);
        return Result.success(governanceService.previewMerge(
                previewRequest, requireWorkspaceId(loginUser)));
    }

    /**
     * 工单级合并预览
     */
    @PostMapping("/issues/{id}/preview-merge")
    public Result<GovernanceMergePreviewView> previewMergeByIssue(@PathVariable Long id,
                                                                  @RequestBody(required = false) GovernanceMergePreviewRequest previewRequest,
                                                                  HttpServletRequest request) {
        LoginUserVO loginUser = requireKnowledgeEditor(request);
        GovernanceMergePreviewRequest effectiveRequest = previewRequest == null
                ? new GovernanceMergePreviewRequest() : previewRequest;
        effectiveRequest.setIssueId(id);
        return Result.success(governanceService.previewMerge(
                effectiveRequest, requireWorkspaceId(loginUser)));
    }

    /**
     * 确认合并优化：发布新版并替代源记忆
     */
    @PostMapping("/merge/confirm")
    public Result<Long> confirmMerge(@RequestBody GovernanceMergeConfirmRequest confirmRequest,
                                     HttpServletRequest request) {
        LoginUserVO loginUser = requireKnowledgeEditor(request);
        return Result.success(governanceService.confirmMerge(
                confirmRequest, requireWorkspaceId(loginUser),
                loginUser.getUserId(), loginUser.getMemberRole()));
    }

    private LoginUserVO requireKnowledgeEditor(HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        if (!WorkspaceMemberRole.canEditKnowledge(loginUser.getMemberRole())) {
            throw new BusinessException(403, "当前角色无权限执行治理操作");
        }
        return loginUser;
    }

    private LoginUserVO requireWorkspaceAdmin(HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        if (!WorkspaceMemberRole.canManageWorkspace(loginUser.getMemberRole())) {
            throw new BusinessException(403, "当前角色无权限修改治理配置");
        }
        return loginUser;
    }

    private String requireWorkspaceId(LoginUserVO loginUser) {
        if (StringUtils.isBlank(loginUser.getWorkspaceId())) {
            throw new BusinessException(400, "当前未选择工作空间");
        }
        return loginUser.getWorkspaceId();
    }
}
