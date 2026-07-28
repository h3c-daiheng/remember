package com.zhiyi.controller;

import com.zhiyi.auth.LoginContext;
import com.zhiyi.common.Result;
import com.zhiyi.domain.vo.LoginUserVO;
import com.zhiyi.domain.vo.StatsDimensionsVO;
import com.zhiyi.domain.vo.StatsFunnelVO;
import com.zhiyi.domain.vo.StatsGraphHubVO;
import com.zhiyi.domain.vo.StatsOverviewVO;
import com.zhiyi.domain.vo.StatsTopKnowledgeVO;
import com.zhiyi.domain.vo.StatsTrendItemVO;
import com.zhiyi.domain.vo.StatsUsageVO;
import com.zhiyi.service.WorkspaceStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 工作空间数据统计接口：概览、趋势与 Top 经验（Step 1 只读聚合）
 */
@RestController
@RequestMapping("/workspace/{workspaceId}/stats")
public class WorkspaceStatsController {

    private final WorkspaceStatsService workspaceStatsService;

    public WorkspaceStatsController(WorkspaceStatsService workspaceStatsService) {
        this.workspaceStatsService = workspaceStatsService;
    }

    /**
     * 统计概览：北极星、转化率、近 N 日调用量
     */
    @GetMapping("/overview")
    public Result<StatsOverviewVO> overview(HttpServletRequest request,
                                              @PathVariable("workspaceId") String workspaceId,
                                              @RequestParam(value = "days", required = false) Integer days) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        StatsOverviewVO overview = workspaceStatsService.getOverview(loginUser, workspaceId, days);
        return Result.success(overview);
    }

    /**
     * 近 N 日 Recall / Remember / Feedback 趋势
     */
    @GetMapping("/recall-trend")
    public Result<List<StatsTrendItemVO>> recallTrend(HttpServletRequest request,
                                                      @PathVariable("workspaceId") String workspaceId,
                                                      @RequestParam(value = "days", required = false) Integer days) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        List<StatsTrendItemVO> trendList = workspaceStatsService.getRecallTrend(
                loginUser, workspaceId, days);
        return Result.success(trendList);
    }

    /**
     * 被召回次数 Top N 经验
     */
    @GetMapping("/top-knowledge")
    public Result<List<StatsTopKnowledgeVO>> topKnowledge(HttpServletRequest request,
                                                          @PathVariable("workspaceId") String workspaceId,
                                                          @RequestParam(value = "limit", required = false) Integer limit) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        List<StatsTopKnowledgeVO> topList = workspaceStatsService.getTopKnowledge(
                loginUser, workspaceId, limit);
        return Result.success(topList);
    }

    /**
     * 月用量统计：日序列 + 月累计 + Recall 配额
     */
    @GetMapping("/usage")
    public Result<StatsUsageVO> usage(HttpServletRequest request,
                                      @PathVariable("workspaceId") String workspaceId,
                                      @RequestParam(value = "month", required = false) String month) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        StatsUsageVO usageView = workspaceStatsService.getUsage(loginUser, workspaceId, month);
        return Result.success(usageView);
    }

    /**
     * Capture 闭环漏斗：Remember → Draft → Review → Publish
     */
    @GetMapping("/funnel")
    public Result<StatsFunnelVO> funnel(HttpServletRequest request,
                                        @PathVariable("workspaceId") String workspaceId,
                                        @RequestParam(value = "days", required = false) Integer days) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        StatsFunnelVO funnelView = workspaceStatsService.getFunnel(loginUser, workspaceId, days);
        return Result.success(funnelView);
    }

    /**
     * 图谱枢纽经验：按入度 + 出度识别核心节点
     */
    @GetMapping("/graph-hubs")
    public Result<List<StatsGraphHubVO>> graphHubs(HttpServletRequest request,
                                                   @PathVariable("workspaceId") String workspaceId,
                                                   @RequestParam(value = "limit", required = false) Integer limit) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        List<StatsGraphHubVO> hubList = workspaceStatsService.getGraphHubs(
                loginUser, workspaceId, limit);
        return Result.success(hubList);
    }

    /**
     * 多维分析：模块 / 标签 / API Key 用量、Recall P95
     */
    @GetMapping("/dimensions")
    public Result<StatsDimensionsVO> dimensions(HttpServletRequest request,
                                                @PathVariable("workspaceId") String workspaceId,
                                                @RequestParam(value = "days", required = false) Integer days,
                                                @RequestParam(value = "module", required = false) String module,
                                                @RequestParam(value = "project", required = false) String project,
                                                @RequestParam(value = "tag", required = false) String tag) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        StatsDimensionsVO dimensionsView = workspaceStatsService.getDimensions(
                loginUser, workspaceId, days, module, project, tag);
        return Result.success(dimensionsView);
    }
}
