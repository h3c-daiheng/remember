package com.zhiyi.memory.controller;

import com.zhiyi.auth.LoginContext;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.PageResult;
import com.zhiyi.common.Result;
import com.zhiyi.domain.vo.KnowledgeDeprecateResultVO;
import com.zhiyi.domain.vo.KnowledgeFeedbackSummaryVO;
import com.zhiyi.domain.vo.KnowledgeRelatedItemVO;
import com.zhiyi.domain.vo.KnowledgeRelationCreateRequest;
import com.zhiyi.domain.vo.KnowledgeRelationVO;
import com.zhiyi.domain.vo.KnowledgeSupersedeRequest;
import com.zhiyi.domain.vo.KnowledgeTimelineItemVO;
import com.zhiyi.domain.vo.LoginUserVO;
import com.zhiyi.memory.domain.KnowledgeAggregate;
import com.zhiyi.memory.domain.KnowledgeSaveRequest;
import com.zhiyi.memory.knowledge.KnowledgeRelatedService;
import com.zhiyi.memory.knowledge.KnowledgeRelationService;
import com.zhiyi.memory.knowledge.KnowledgeService;
import com.zhiyi.memory.graph.GraphGovernanceService;
import com.zhiyi.memory.timeline.KnowledgeTimelineService;
import com.zhiyi.workspace.WorkspaceMemberRole;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Web 端知识管理 API：列表、详情、创建、更新、发布、下架与删除，按工作空间隔离
 */
@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final KnowledgeRelatedService knowledgeRelatedService;
    private final KnowledgeRelationService knowledgeRelationService;
    private final KnowledgeTimelineService knowledgeTimelineService;
    private final GraphGovernanceService graphGovernanceService;

    public KnowledgeController(KnowledgeService knowledgeService,
                               KnowledgeRelatedService knowledgeRelatedService,
                               KnowledgeRelationService knowledgeRelationService,
                               KnowledgeTimelineService knowledgeTimelineService,
                               GraphGovernanceService graphGovernanceService) {
        this.knowledgeService = knowledgeService;
        this.knowledgeRelatedService = knowledgeRelatedService;
        this.knowledgeRelationService = knowledgeRelationService;
        this.knowledgeTimelineService = knowledgeTimelineService;
        this.graphGovernanceService = graphGovernanceService;
    }

    /**
     * 分页查询当前工作空间知识列表，支持按类型与生命周期筛选
     */
    @GetMapping("/list")
    public Result<PageResult<KnowledgeAggregate>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                       @RequestParam(defaultValue = "20") int pageSize,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) String knowledgeType,
                                                       @RequestParam(required = false) Integer lifecycleStatus,
                                                       HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        return Result.success(knowledgeService.list(
                pageNum, pageSize, keyword, knowledgeType, lifecycleStatus, requireWorkspaceId(loginUser)));
    }

    /**
     * 查询经验详情
     */
    @GetMapping("/{id}")
    public Result<KnowledgeAggregate> detail(@PathVariable Long id, HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        return Result.success(knowledgeService.getDetail(id, requireWorkspaceId(loginUser)));
    }

    /**
     * 查询单条经验的 Feedback 统计摘要
     */
    @GetMapping("/{id}/feedback-summary")
    public Result<KnowledgeFeedbackSummaryVO> feedbackSummary(@PathVariable Long id,
                                                              HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        return Result.success(knowledgeService.getFeedbackSummary(id, requireWorkspaceId(loginUser)));
    }

    /**
     * 查询与指定经验相关的已发布经验，合并隐式排序与显式关系边
     */
    @GetMapping("/{id}/related")
    public Result<List<KnowledgeRelatedItemVO>> related(@PathVariable Long id,
                                                        @RequestParam(defaultValue = "10") int limit,
                                                        HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        return Result.success(knowledgeRelatedService.listRelated(
                id, requireWorkspaceId(loginUser), limit));
    }

    /**
     * 查询指定经验的显式关系边
     */
    @GetMapping("/{id}/relations")
    public Result<List<KnowledgeRelationVO>> relations(@PathVariable Long id,
                                                       @RequestParam(required = false) String types,
                                                       HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        return Result.success(knowledgeRelationService.listRelations(
                id, requireWorkspaceId(loginUser), parseRelationTypes(types)));
    }

    /**
     * 查询指定经验的版本时间线
     */
    @GetMapping("/{id}/timeline")
    public Result<List<KnowledgeTimelineItemVO>> timeline(@PathVariable Long id, HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        return Result.success(knowledgeTimelineService.listTimeline(id, requireWorkspaceId(loginUser)));
    }

    /**
     * 新版经验替代旧版：建立 supersedes 边并下架 predecessor
     */
    @PostMapping("/{id}/supersede")
    public Result<Void> supersede(@PathVariable Long id,
                                  @RequestBody KnowledgeSupersedeRequest supersedeRequest,
                                  HttpServletRequest request) {
        LoginUserVO loginUser = requireKnowledgeEditor(request);
        graphGovernanceService.supersede(id, supersedeRequest, requireWorkspaceId(loginUser),
                loginUser.getUserId(), loginUser.getMemberRole());
        return Result.success(null);
    }

    /**
     * 人工创建关系边
     */
    @PostMapping("/{id}/relations")
    public Result<Long> createRelation(@PathVariable Long id,
                                       @RequestBody KnowledgeRelationCreateRequest createRequest,
                                       HttpServletRequest request) {
        LoginUserVO loginUser = requireKnowledgeEditor(request);
        return Result.success(knowledgeRelationService.createManualRelation(
                id, createRequest, requireWorkspaceId(loginUser), loginUser.getUserId()));
    }

    /**
     * 删除关系边
     */
    @DeleteMapping("/relations/{relationId}")
    public Result<Void> deleteRelation(@PathVariable Long relationId, HttpServletRequest request) {
        LoginUserVO loginUser = requireKnowledgeEditor(request);
        knowledgeRelationService.deleteRelation(relationId, requireWorkspaceId(loginUser));
        return Result.success(null);
    }

    /**
     * 人工创建知识（经验 / 规则 / 流程 / 决策），可指定 knowledgeType 与是否直接发布
     */
    @PostMapping
    public Result<Long> create(@RequestBody KnowledgeSaveRequest saveRequest, HttpServletRequest request) {
        LoginUserVO loginUser = requireKnowledgeEditor(request);
        return Result.success(knowledgeService.create(
                saveRequest, loginUser.getUserId(), requireWorkspaceId(loginUser)));
    }

    /**
     * 更新经验内容；编辑角色可改任意经验，发布者可改本人创建的经验
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @RequestBody KnowledgeSaveRequest saveRequest,
                               HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        knowledgeService.update(id, saveRequest, requireWorkspaceId(loginUser),
                loginUser.getUserId(), loginUser.getMemberRole());
        return Result.success(null);
    }

    /**
     * 发布经验；编辑角色可发布任意经验，发布者可发布本人创建的草稿
     */
    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id, HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        knowledgeService.publish(id, requireWorkspaceId(loginUser),
                loginUser.getUserId(), loginUser.getMemberRole());
        return Result.success(null);
    }

    /**
     * 下架已发布经验；返回级联 Review 提示影响的经验列表
     */
    @PostMapping("/{id}/deprecate")
    public Result<KnowledgeDeprecateResultVO> deprecate(@PathVariable Long id, HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        return Result.success(knowledgeService.deprecate(id, requireWorkspaceId(loginUser),
                loginUser.getUserId(), loginUser.getMemberRole()));
    }

    /**
     * 重新启用已失效经验；恢复为已发布并重建 Recall 索引
     */
    @PostMapping("/{id}/reactivate")
    public Result<Void> reactivate(@PathVariable Long id, HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        knowledgeService.reactivate(id, requireWorkspaceId(loginUser),
                loginUser.getUserId(), loginUser.getMemberRole());
        return Result.success(null);
    }

    /**
     * 删除经验（逻辑删除）；管理者可删任意经验，其余角色仅可删本人创建的经验
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        knowledgeService.deleteKnowledge(
                id, requireWorkspaceId(loginUser), loginUser.getUserId(), loginUser.getMemberRole());
        return Result.success(null);
    }

    /**
     * 校验当前用户具备经验编辑权限（Owner / Admin / Editor）
     */
    private LoginUserVO requireKnowledgeEditor(HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        if (!WorkspaceMemberRole.canEditKnowledge(loginUser.getMemberRole())) {
            throw new BusinessException(403, "当前角色无权限编辑经验");
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

    private List<String> parseRelationTypes(String types) {
        if (StringUtils.isBlank(types)) {
            return Collections.emptyList();
        }
        return Arrays.asList(types.split(","));
    }
}
