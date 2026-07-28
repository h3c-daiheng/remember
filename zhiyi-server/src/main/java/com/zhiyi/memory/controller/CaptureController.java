package com.zhiyi.memory.controller;

import com.zhiyi.auth.LoginContext;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.PageResult;
import com.zhiyi.common.Result;
import com.zhiyi.domain.vo.LoginUserVO;
import com.zhiyi.memory.domain.CaptureAiReviewDetailView;
import com.zhiyi.memory.domain.CaptureAiReviewListItem;
import com.zhiyi.memory.domain.CaptureAiReviewView;
import com.zhiyi.memory.domain.CaptureDraftView;
import com.zhiyi.memory.domain.CaptureMergeRuleRequest;
import com.zhiyi.memory.domain.CaptureRejectRequest;
import com.zhiyi.memory.domain.CaptureRouteRequest;
import com.zhiyi.memory.domain.CaptureRouteResult;
import com.zhiyi.memory.domain.CaptureRouteSuggestion;
import com.zhiyi.memory.domain.KnowledgeDraftContent;
import com.zhiyi.memory.knowledge.CaptureService;
import com.zhiyi.workspace.WorkspaceMemberRole;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Capture 草稿 Review API，按当前工作空间隔离
 */
@RestController
@RequestMapping("/capture")
public class CaptureController {

    private final CaptureService captureService;

    public CaptureController(CaptureService captureService) {
        this.captureService = captureService;
    }

    /**
     * 查询当前工作空间待确认草稿列表；可按 aiReviewDecision 筛选（如 escalate_human）
     */
    @GetMapping("/drafts")
    public Result<List<CaptureDraftView>> listDrafts(@RequestParam(required = false) String aiReviewDecision,
                                                     HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        return Result.success(captureService.listPendingDrafts(
                requireWorkspaceId(loginUser), aiReviewDecision));
    }

    /**
     * 按关键词搜索 Capture 草稿，供闭环追踪等场景选用
     */
    @GetMapping("/drafts/search")
    public Result<List<CaptureDraftView>> searchDrafts(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false, defaultValue = "20") Integer limit,
                                                       HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        return Result.success(captureService.searchDrafts(requireWorkspaceId(loginUser), keyword, limit));
    }

    /**
     * 查询草稿详情
     */
    @GetMapping("/drafts/{id}")
    public Result<CaptureDraftView> draftDetail(@PathVariable Long id,
                                                HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        return Result.success(captureService.getDraft(id, requireWorkspaceId(loginUser)));
    }

    /**
     * 获取草稿归类建议；forceLlm=true 时由 Review 人主动触发 AI 深度分析
     */
    @GetMapping("/drafts/{id}/route-suggestion")
    public Result<CaptureRouteSuggestion> routeSuggestion(@PathVariable Long id,
                                                          @RequestParam(required = false, defaultValue = "false") Boolean forceLlm,
                                                          HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        return Result.success(captureService.getRouteSuggestion(
                id, requireWorkspaceId(loginUser), Boolean.TRUE.equals(forceLlm)));
    }

    /**
     * 分页查询当前工作空间 AI Review 记录（全量历史）
     */
    @GetMapping("/ai-reviews")
    public Result<PageResult<CaptureAiReviewListItem>> listAiReviews(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) Boolean similarHit,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        return Result.success(captureService.listAiReviews(
                pageNum, pageSize, status, decision, similarHit, keyword,
                requireWorkspaceId(loginUser)));
    }

    /**
     * 按记录主键查询 AI Review 详情（含草稿摘要）
     */
    @GetMapping("/ai-reviews/{id}")
    public Result<CaptureAiReviewDetailView> aiReviewById(@PathVariable Long id,
                                                          HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        return Result.success(captureService.getAiReviewById(id, requireWorkspaceId(loginUser)));
    }

    /**
     * 查询草稿最新 AI Review 记录
     */
    @GetMapping("/drafts/{id}/ai-review")
    public Result<CaptureAiReviewView> aiReviewDetail(@PathVariable Long id,
                                                      HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        return Result.success(captureService.getAiReview(id, requireWorkspaceId(loginUser)));
    }

    /**
     * 重跑 AI Review（仅待确认草稿）；传入当前登录用户供自动执行路径写入审核人与 creator_id
     */
    @PostMapping("/drafts/{id}/ai-review/retry")
    public Result<CaptureAiReviewView> retryAiReview(@PathVariable Long id,
                                                     HttpServletRequest request) {
        LoginUserVO loginUser = requireKnowledgeEditor(request);
        return Result.success(captureService.retryAiReview(
                id, requireWorkspaceId(loginUser), loginUser.getUserId()));
    }

    /**
     * 采纳草稿：按 Agent 声明类型确认并发布或写入对应知识草稿
     */
    @PostMapping("/drafts/{id}/approve")
    public Result<CaptureRouteResult> approve(@PathVariable Long id,
                                              @RequestBody(required = false) KnowledgeDraftContent editedContent,
                                              HttpServletRequest request) {
        LoginUserVO loginUser = requireKnowledgeEditor(request);
        CaptureRouteResult result = captureService.approveDraft(
                id, editedContent, loginUser.getUserId(), requireWorkspaceId(loginUser));
        return Result.success(result);
    }

    /**
     * 拒绝草稿，携带拒绝码与备注
     */
    @PostMapping("/drafts/{id}/reject")
    public Result<Void> reject(@PathVariable Long id,
                               @Validated @RequestBody CaptureRejectRequest rejectRequest,
                               HttpServletRequest request) {
        LoginUserVO loginUser = requireKnowledgeEditor(request);
        captureService.rejectDraft(id, rejectRequest, loginUser.getUserId(), requireWorkspaceId(loginUser));
        return Result.success(null);
    }

    /**
     * 路由草稿为 Rule / Workflow 草稿
     */
    @PostMapping("/drafts/{id}/route")
    public Result<CaptureRouteResult> route(@PathVariable Long id,
                                            @RequestBody CaptureRouteRequest routeRequest,
                                            HttpServletRequest request) {
        LoginUserVO loginUser = requireKnowledgeEditor(request);
        return Result.success(captureService.routeDraft(
                id, routeRequest, loginUser.getUserId(), requireWorkspaceId(loginUser)));
    }

    /**
     * 合并 Capture 草稿到已有 Rule
     */
    @PostMapping("/drafts/{id}/merge-into-rule")
    public Result<CaptureRouteResult> mergeIntoRule(@PathVariable Long id,
                                                    @RequestBody CaptureMergeRuleRequest mergeRequest,
                                                    HttpServletRequest request) {
        LoginUserVO loginUser = requireKnowledgeEditor(request);
        return Result.success(captureService.mergeIntoRule(
                id, mergeRequest, loginUser.getUserId(), requireWorkspaceId(loginUser)));
    }

    /**
     * 校验当前用户具备内容协作权限（Owner / Admin / Editor 才可 Review）
     */
    private LoginUserVO requireKnowledgeEditor(HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        if (!WorkspaceMemberRole.canEditKnowledge(loginUser.getMemberRole())) {
            throw new BusinessException(403, "当前角色无权限审核草稿");
        }
        return loginUser;
    }

    /**
     * 解析当前登录用户的工作空间主键
     */
    private String requireWorkspaceId(LoginUserVO loginUser) {
        if (StringUtils.isBlank(loginUser.getWorkspaceId())) {
            throw new BusinessException(400, "当前未选择工作空间");
        }
        return loginUser.getWorkspaceId();
    }
}
