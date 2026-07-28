package com.zhiyi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.domain.vo.LoginUserVO;
import com.zhiyi.dao.ApiKeyMapper;
import com.zhiyi.dao.OrganizationMapper;
import com.zhiyi.dao.UsageDailyMapper;
import com.zhiyi.dao.WorkspaceMapper;
import com.zhiyi.domain.entity.ApiKeyEntity;
import com.zhiyi.domain.entity.OrganizationEntity;
import com.zhiyi.domain.entity.UsageDailyEntity;
import com.zhiyi.domain.entity.WorkspaceEntity;
import com.zhiyi.domain.vo.StatsApiKeyUsageItemVO;
import com.zhiyi.domain.vo.StatsDimensionsVO;
import com.zhiyi.domain.vo.StatsGraphHubVO;
import com.zhiyi.domain.vo.StatsFunnelVO;
import com.zhiyi.domain.vo.StatsModuleItemVO;
import com.zhiyi.domain.vo.StatsOverviewVO;
import com.zhiyi.domain.vo.StatsTagItemVO;
import com.zhiyi.domain.vo.StatsTopKnowledgeVO;
import com.zhiyi.domain.vo.StatsTrendItemVO;
import com.zhiyi.domain.vo.StatsUsageDailyItemVO;
import com.zhiyi.domain.vo.StatsUsageVO;
import com.zhiyi.memory.dao.KnowledgeTagMapper;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.dao.CaptureDraftMapper;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.dao.MemoryFeedbackMapper;
import com.zhiyi.memory.dao.MemoryOperationLogMapper;
import com.zhiyi.memory.entity.CaptureDraftEntity;
import com.zhiyi.memory.entity.KnowledgeEntity;
import com.zhiyi.memory.entity.MemoryFeedbackEntity;
import com.zhiyi.memory.entity.MemoryOperationLogEntity;
import com.zhiyi.memory.graph.GraphGovernanceService;
import com.zhiyi.memory.domain.KnowledgeTagRow;
import com.zhiyi.workspace.WorkspaceAccessGuard;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作空间数据统计：聚合 memory_operation_log、memory_feedback、knowledge、capture_draft
 */
@Service
public class WorkspaceStatsService {

    /** helpful 类反馈，用于北极星与占比计算 */
    private static final Set<String> HELPFUL_FEEDBACK_TYPES = new HashSet<String>(
            Arrays.asList("helpful", "used"));

    /** 草稿转化率统计窗口（天） */
    private static final int CONVERSION_WINDOW_DAYS = 30;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 模块 / 标签分布默认 Top 条数 */
    private static final int DIMENSION_TOP_LIMIT = 10;

    /** JWT 等非 API Key 调用的展示标签 */
    private static final String JWT_USAGE_LABEL = "Web / JWT";

    private final KnowledgeMapper knowledgeMapper;
    private final MemoryOperationLogMapper memoryOperationLogMapper;
    private final MemoryFeedbackMapper memoryFeedbackMapper;
    private final CaptureDraftMapper captureDraftMapper;
    private final UsageDailyMapper usageDailyMapper;
    private final WorkspaceMapper workspaceMapper;
    private final OrganizationMapper organizationMapper;
    private final KnowledgeTagMapper knowledgeTagMapper;
    private final ApiKeyMapper apiKeyMapper;
    private final GraphGovernanceService graphGovernanceService;

    public WorkspaceStatsService(KnowledgeMapper knowledgeMapper,
                                 MemoryOperationLogMapper memoryOperationLogMapper,
                                 MemoryFeedbackMapper memoryFeedbackMapper,
                                 CaptureDraftMapper captureDraftMapper,
                                 UsageDailyMapper usageDailyMapper,
                                 WorkspaceMapper workspaceMapper,
                                 OrganizationMapper organizationMapper,
                                 KnowledgeTagMapper knowledgeTagMapper,
                                 ApiKeyMapper apiKeyMapper,
                                 GraphGovernanceService graphGovernanceService) {
        this.knowledgeMapper = knowledgeMapper;
        this.memoryOperationLogMapper = memoryOperationLogMapper;
        this.memoryFeedbackMapper = memoryFeedbackMapper;
        this.captureDraftMapper = captureDraftMapper;
        this.usageDailyMapper = usageDailyMapper;
        this.workspaceMapper = workspaceMapper;
        this.organizationMapper = organizationMapper;
        this.knowledgeTagMapper = knowledgeTagMapper;
        this.apiKeyMapper = apiKeyMapper;
        this.graphGovernanceService = graphGovernanceService;
    }

    /**
     * 查询工作空间统计概览
     */
    public StatsOverviewVO getOverview(LoginUserVO loginUser, String workspaceId, Integer days) {
        requireMemberContext(loginUser, workspaceId);
        int periodDays = normalizeDays(days, 7);

        StatsOverviewVO overview = new StatsOverviewVO();
        overview.setStatsPeriodDays(periodDays);
        overview.setWeeklyHelpfulRecalls(countWeeklyHelpfulRecalls(workspaceId));
        overview.setHelpfulRate(calculateHelpfulRate(workspaceId, periodDays));
        overview.setPublishedCount(countKnowledgeByLifecycle(workspaceId, MemoryConstants.LIFECYCLE_PUBLISHED));
        overview.setPendingDraftCount(countPendingDrafts(workspaceId));
        overview.setDeprecatedCount(countKnowledgeByLifecycle(workspaceId, MemoryConstants.LIFECYCLE_DEPRECATED));
        overview.setDraftToPublishedRate(calculateDraftToPublishedRate(workspaceId));
        overview.setRememberToDraftRate(calculateRememberToDraftRate(workspaceId));

        Date periodStart = toDate(LocalDate.now().minusDays(periodDays - 1L));
        overview.setRecallCount7d(countOperationLogs(workspaceId, periodStart,
                Arrays.asList(MemoryConstants.OPERATION_RECALL, MemoryConstants.OPERATION_SEARCH)));
        overview.setRememberCount7d(countOperationLogs(workspaceId, periodStart,
                Arrays.asList(MemoryConstants.OPERATION_REMEMBER, MemoryConstants.OPERATION_SUBMIT)));
        overview.setFeedbackCount7d(countFeedbackInWorkspace(workspaceId, periodStart, null));
        return overview;
    }

    /**
     * 查询图谱枢纽经验：入度 + 出度高的核心节点
     */
    public List<StatsGraphHubVO> getGraphHubs(LoginUserVO loginUser, String workspaceId, Integer limit) {
        requireMemberContext(loginUser, workspaceId);
        return graphGovernanceService.listGraphHubs(workspaceId, limit);
    }

    /**
     * 查询近 N 日 Recall / Remember / Feedback 趋势
     */
    public List<StatsTrendItemVO> getRecallTrend(LoginUserVO loginUser, String workspaceId, Integer days) {
        requireMemberContext(loginUser, workspaceId);
        int trendDays = normalizeDays(days, 30);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(trendDays - 1L);
        Date periodStart = toDate(startDate);

        Map<String, StatsTrendItemVO> trendMap = initTrendMap(startDate, endDate);

        List<MemoryOperationLogEntity> logEntityList = memoryOperationLogMapper.selectList(
                new LambdaQueryWrapper<MemoryOperationLogEntity>()
                        .eq(MemoryOperationLogEntity::getWorkspaceId, workspaceId)
                        .eq(MemoryOperationLogEntity::getSuccess, 1)
                        .ge(MemoryOperationLogEntity::getCreateTime, periodStart));
        for (MemoryOperationLogEntity logEntity : logEntityList) {
            String dateKey = formatDate(logEntity.getCreateTime());
            StatsTrendItemVO trendItem = trendMap.get(dateKey);
            if (trendItem == null) {
                continue;
            }
            String operation = logEntity.getOperation();
            if (MemoryConstants.OPERATION_RECALL.equals(operation)
                    || MemoryConstants.OPERATION_SEARCH.equals(operation)) {
                trendItem.setRecallCount(trendItem.getRecallCount() + 1);
            } else if (MemoryConstants.OPERATION_REMEMBER.equals(operation)
                    || MemoryConstants.OPERATION_SUBMIT.equals(operation)) {
                trendItem.setRememberCount(trendItem.getRememberCount() + 1);
            }
        }

        // Feedback 以 memory_feedback 为准，避免与 operation_log 重复计数
        List<Long> knowledgeIdList = loadWorkspaceKnowledgeIds(workspaceId);
        if (!knowledgeIdList.isEmpty()) {
            List<MemoryFeedbackEntity> feedbackEntityList = memoryFeedbackMapper.selectList(
                    new LambdaQueryWrapper<MemoryFeedbackEntity>()
                            .in(MemoryFeedbackEntity::getKnowledgeId, knowledgeIdList)
                            .ge(MemoryFeedbackEntity::getCreateTime, periodStart));
            for (MemoryFeedbackEntity feedbackEntity : feedbackEntityList) {
                String dateKey = formatDate(feedbackEntity.getCreateTime());
                StatsTrendItemVO trendItem = trendMap.get(dateKey);
                if (trendItem == null) {
                    continue;
                }
                trendItem.setFeedbackCount(trendItem.getFeedbackCount() + 1);
                if (HELPFUL_FEEDBACK_TYPES.contains(feedbackEntity.getFeedbackType())) {
                    trendItem.setHelpfulCount(trendItem.getHelpfulCount() + 1);
                }
            }
        }

        List<StatsTrendItemVO> trendList = new ArrayList<StatsTrendItemVO>(trendMap.values());
        Collections.sort(trendList, new Comparator<StatsTrendItemVO>() {
            @Override
            public int compare(StatsTrendItemVO left, StatsTrendItemVO right) {
                return left.getDate().compareTo(right.getDate());
            }
        });
        return trendList;
    }

    /**
     * 查询被召回次数 Top N 经验
     */
    public List<StatsTopKnowledgeVO> getTopKnowledge(LoginUserVO loginUser, String workspaceId, Integer limit) {
        requireMemberContext(loginUser, workspaceId);
        int topLimit = normalizeLimit(limit, 10);

        List<KnowledgeEntity> knowledgeEntityList = knowledgeMapper.selectList(
                new LambdaQueryWrapper<KnowledgeEntity>()
                        .eq(KnowledgeEntity::getWorkspaceId, workspaceId)
                        .eq(KnowledgeEntity::getLifecycleStatus, MemoryConstants.LIFECYCLE_PUBLISHED)
                        .gt(KnowledgeEntity::getRecallCount, 0)
                        .orderByDesc(KnowledgeEntity::getRecallCount)
                        .last("LIMIT " + topLimit));

        List<Long> knowledgeIdList = new ArrayList<Long>();
        for (KnowledgeEntity knowledgeEntity : knowledgeEntityList) {
            knowledgeIdList.add(knowledgeEntity.getId());
        }
        Map<Long, Integer> helpfulCountMap = loadHelpfulCountByKnowledgeIds(
                knowledgeIdList, HELPFUL_FEEDBACK_TYPES, null);

        List<StatsTopKnowledgeVO> resultList = new ArrayList<StatsTopKnowledgeVO>();
        for (KnowledgeEntity knowledgeEntity : knowledgeEntityList) {
            StatsTopKnowledgeVO topKnowledge = new StatsTopKnowledgeVO();
            topKnowledge.setId(knowledgeEntity.getId());
            topKnowledge.setTitle(knowledgeEntity.getTitle());
            topKnowledge.setRecallCount(safeInt(knowledgeEntity.getRecallCount()));
            topKnowledge.setModule(knowledgeEntity.getModule());
            topKnowledge.setProject(knowledgeEntity.getProject());
            Integer helpfulCount = helpfulCountMap.get(knowledgeEntity.getId());
            topKnowledge.setHelpfulCount(helpfulCount == null ? 0 : helpfulCount);
            resultList.add(topKnowledge);
        }
        return resultList;
    }

    /**
     * 查询工作空间月用量与日序列（来自 usage_daily）
     */
    public StatsUsageVO getUsage(LoginUserVO loginUser, String workspaceId, String month) {
        requireMemberContext(loginUser, workspaceId);
        YearMonth yearMonth = parseMonth(month);
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();

        java.sql.Date startDate = java.sql.Date.valueOf(monthStart);
        java.sql.Date endDate = java.sql.Date.valueOf(monthEnd);

        List<UsageDailyEntity> usageEntityList = usageDailyMapper.selectList(
                new LambdaQueryWrapper<UsageDailyEntity>()
                        .eq(UsageDailyEntity::getWorkspaceId, workspaceId)
                        .between(UsageDailyEntity::getUsageDate, startDate, endDate)
                        .orderByAsc(UsageDailyEntity::getUsageDate));

        Map<String, UsageDailyEntity> usageByDate = new HashMap<String, UsageDailyEntity>();
        for (UsageDailyEntity usageEntity : usageEntityList) {
            usageByDate.put(formatSqlDate(usageEntity.getUsageDate()), usageEntity);
        }

        List<StatsUsageDailyItemVO> dailyList = new ArrayList<StatsUsageDailyItemVO>();
        int recallUsed = 0;
        int rememberUsed = 0;
        int feedbackUsed = 0;
        LocalDate cursor = monthStart;
        while (!cursor.isAfter(monthEnd)) {
            String dateKey = cursor.format(DATE_FORMATTER);
            UsageDailyEntity usageEntity = usageByDate.get(dateKey);
            StatsUsageDailyItemVO dailyItem = new StatsUsageDailyItemVO();
            dailyItem.setDate(dateKey);
            dailyItem.setRecallCount(usageEntity == null ? 0 : safeInt(usageEntity.getRecallCount()));
            dailyItem.setRememberCount(usageEntity == null ? 0 : safeInt(usageEntity.getRememberCount()));
            dailyItem.setFeedbackCount(usageEntity == null ? 0 : safeInt(usageEntity.getFeedbackCount()));
            dailyList.add(dailyItem);
            recallUsed += dailyItem.getRecallCount();
            rememberUsed += dailyItem.getRememberCount();
            feedbackUsed += dailyItem.getFeedbackCount();
            cursor = cursor.plusDays(1);
        }

        StatsUsageVO usageView = new StatsUsageVO();
        usageView.setMonth(yearMonth.format(MONTH_FORMATTER));
        usageView.setRecallUsed(recallUsed);
        usageView.setRememberUsed(rememberUsed);
        usageView.setFeedbackUsed(feedbackUsed);
        usageView.setRecallQuota(resolveRecallQuota(workspaceId));
        usageView.setDailyList(dailyList);
        return usageView;
    }

    /**
     * 查询 Capture 闭环漏斗：Remember → Draft → Review → Publish
     */
    public StatsFunnelVO getFunnel(LoginUserVO loginUser, String workspaceId, Integer days) {
        requireMemberContext(loginUser, workspaceId);
        int periodDays = normalizeDays(days, CONVERSION_WINDOW_DAYS);
        Date periodStart = toDate(LocalDate.now().minusDays(periodDays - 1L));

        int rememberCount = countOperationLogs(workspaceId, periodStart,
                Arrays.asList(MemoryConstants.OPERATION_REMEMBER, MemoryConstants.OPERATION_SUBMIT));
        int draftCount = captureDraftMapper.selectCount(
                new LambdaQueryWrapper<CaptureDraftEntity>()
                        .eq(CaptureDraftEntity::getWorkspaceId, workspaceId)
                        .ge(CaptureDraftEntity::getCreateTime, periodStart)).intValue();
        int approvedCount = captureDraftMapper.selectCount(
                new LambdaQueryWrapper<CaptureDraftEntity>()
                        .eq(CaptureDraftEntity::getWorkspaceId, workspaceId)
                        .eq(CaptureDraftEntity::getReviewStatus, MemoryConstants.REVIEW_APPROVED)
                        .ge(CaptureDraftEntity::getCreateTime, periodStart)).intValue();
        int rejectedCount = captureDraftMapper.selectCount(
                new LambdaQueryWrapper<CaptureDraftEntity>()
                        .eq(CaptureDraftEntity::getWorkspaceId, workspaceId)
                        .eq(CaptureDraftEntity::getReviewStatus, MemoryConstants.REVIEW_REJECTED)
                        .ge(CaptureDraftEntity::getCreateTime, periodStart)).intValue();
        int reviewedCount = approvedCount + rejectedCount;
        int publishedCount = captureDraftMapper.selectCount(
                new LambdaQueryWrapper<CaptureDraftEntity>()
                        .eq(CaptureDraftEntity::getWorkspaceId, workspaceId)
                        .eq(CaptureDraftEntity::getReviewStatus, MemoryConstants.REVIEW_APPROVED)
                        .isNotNull(CaptureDraftEntity::getKnowledgeId)
                        .ge(CaptureDraftEntity::getCreateTime, periodStart)).intValue();

        StatsFunnelVO funnelView = new StatsFunnelVO();
        funnelView.setStatsPeriodDays(periodDays);
        funnelView.setRememberCount(rememberCount);
        funnelView.setDraftCount(draftCount);
        funnelView.setReviewedCount(reviewedCount);
        funnelView.setApprovedCount(approvedCount);
        funnelView.setPublishedCount(publishedCount);
        funnelView.setDraftFromRememberRate(rememberCount == 0 ? 0D : roundRate((double) draftCount / rememberCount));
        funnelView.setApprovedFromReviewedRate(reviewedCount == 0 ? 0D : roundRate((double) approvedCount / reviewedCount));
        return funnelView;
    }

    /**
     * 多维分析：模块 / 标签分布、API Key 用量、Recall P95、筛选视图
     */
    public StatsDimensionsVO getDimensions(LoginUserVO loginUser, String workspaceId, Integer days,
                                           String module, String project, String tag) {
        requireMemberContext(loginUser, workspaceId);
        int periodDays = normalizeDays(days, CONVERSION_WINDOW_DAYS);
        Date periodStart = toDate(LocalDate.now().minusDays(periodDays - 1L));

        List<KnowledgeEntity> knowledgeEntityList = knowledgeMapper.selectList(
                new LambdaQueryWrapper<KnowledgeEntity>()
                        .eq(KnowledgeEntity::getWorkspaceId, workspaceId)
                        .eq(KnowledgeEntity::getLifecycleStatus, MemoryConstants.LIFECYCLE_PUBLISHED));

        Map<Long, Integer> helpfulCountByKnowledge = loadHelpfulCountByKnowledge(
                knowledgeEntityList, periodStart);

        StatsDimensionsVO dimensionsView = new StatsDimensionsVO();
        dimensionsView.setStatsPeriodDays(periodDays);
        dimensionsView.setRecallP95LatencyMs(calculateRecallP95Latency(workspaceId, periodStart));
        dimensionsView.setModuleBreakdown(buildModuleBreakdown(knowledgeEntityList, helpfulCountByKnowledge));
        dimensionsView.setTagBreakdown(buildTagBreakdown(workspaceId, knowledgeEntityList, helpfulCountByKnowledge));
        dimensionsView.setApiKeyUsageList(buildApiKeyUsageList(workspaceId, periodStart));

        if (StringUtils.isNotBlank(module)) {
            dimensionsView.setFilterModule(module.trim());
        }
        if (StringUtils.isNotBlank(project)) {
            dimensionsView.setFilterProject(project.trim());
        }
        if (StringUtils.isNotBlank(tag)) {
            dimensionsView.setFilterTag(tag.trim());
        }

        List<KnowledgeEntity> filteredKnowledgeList = filterKnowledgeList(
                workspaceId, knowledgeEntityList, module, project, tag);
        dimensionsView.setFilteredKnowledgeCount(filteredKnowledgeList.size());
        int filteredRecallCount = 0;
        int filteredHelpfulCount = 0;
        for (KnowledgeEntity knowledgeEntity : filteredKnowledgeList) {
            filteredRecallCount += safeInt(knowledgeEntity.getRecallCount());
            Integer helpfulCount = helpfulCountByKnowledge.get(knowledgeEntity.getId());
            filteredHelpfulCount += helpfulCount == null ? 0 : helpfulCount;
        }
        dimensionsView.setFilteredRecallCount(filteredRecallCount);
        dimensionsView.setFilteredHelpfulCount(filteredHelpfulCount);
        return dimensionsView;
    }

    /**
     * 校验路径 workspaceId 为当前登录激活空间，防止跨空间读取统计
     */
    private void requireMemberContext(LoginUserVO loginUser, String workspaceId) {
        WorkspaceAccessGuard.requireCurrentWorkspaceMember(loginUser, workspaceId);
    }

    /**
     * 统计本周 helpful / used 反馈数（周一至当前）
     */
    private int countWeeklyHelpfulRecalls(String workspaceId) {
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Date weekStart = toDate(monday);
        return countFeedbackInWorkspace(workspaceId, weekStart, HELPFUL_FEEDBACK_TYPES);
    }

    /**
     * 计算 helpful 占比
     */
    private Double calculateHelpfulRate(String workspaceId, int periodDays) {
        Date periodStart = toDate(LocalDate.now().minusDays(periodDays - 1L));
        int helpfulCount = countFeedbackInWorkspace(workspaceId, periodStart, HELPFUL_FEEDBACK_TYPES);
        int totalCount = countFeedbackInWorkspace(workspaceId, periodStart, null);
        if (totalCount == 0) {
            return 0D;
        }
        return roundRate((double) helpfulCount / totalCount);
    }

    /**
     * 统计指定生命周期状态的经验数量
     */
    private int countKnowledgeByLifecycle(String workspaceId, int lifecycleStatus) {
        return knowledgeMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeEntity>()
                        .eq(KnowledgeEntity::getWorkspaceId, workspaceId)
                        .eq(KnowledgeEntity::getLifecycleStatus, lifecycleStatus)).intValue();
    }

    /**
     * 统计待确认 Capture 草稿数
     */
    private int countPendingDrafts(String workspaceId) {
        return captureDraftMapper.selectCount(
                new LambdaQueryWrapper<CaptureDraftEntity>()
                        .eq(CaptureDraftEntity::getWorkspaceId, workspaceId)
                        .eq(CaptureDraftEntity::getReviewStatus, MemoryConstants.REVIEW_PENDING)).intValue();
    }

    /**
     * 近 30 天草稿采纳率：已采纳 / 已审（采纳 + 拒绝）
     */
    private Double calculateDraftToPublishedRate(String workspaceId) {
        Date windowStart = toDate(LocalDate.now().minusDays(CONVERSION_WINDOW_DAYS - 1L));
        int approvedCount = captureDraftMapper.selectCount(
                new LambdaQueryWrapper<CaptureDraftEntity>()
                        .eq(CaptureDraftEntity::getWorkspaceId, workspaceId)
                        .eq(CaptureDraftEntity::getReviewStatus, MemoryConstants.REVIEW_APPROVED)
                        .ge(CaptureDraftEntity::getCreateTime, windowStart)).intValue();
        int rejectedCount = captureDraftMapper.selectCount(
                new LambdaQueryWrapper<CaptureDraftEntity>()
                        .eq(CaptureDraftEntity::getWorkspaceId, workspaceId)
                        .eq(CaptureDraftEntity::getReviewStatus, MemoryConstants.REVIEW_REJECTED)
                        .ge(CaptureDraftEntity::getCreateTime, windowStart)).intValue();
        int reviewedTotal = approvedCount + rejectedCount;
        if (reviewedTotal == 0) {
            return 0D;
        }
        return roundRate((double) approvedCount / reviewedTotal);
    }

    /**
     * 近 30 天 Remember 转草稿率
     */
    private Double calculateRememberToDraftRate(String workspaceId) {
        Date windowStart = toDate(LocalDate.now().minusDays(CONVERSION_WINDOW_DAYS - 1L));
        int draftCount = captureDraftMapper.selectCount(
                new LambdaQueryWrapper<CaptureDraftEntity>()
                        .eq(CaptureDraftEntity::getWorkspaceId, workspaceId)
                        .ge(CaptureDraftEntity::getCreateTime, windowStart)).intValue();
        int rememberCount = countOperationLogs(workspaceId, windowStart,
                Arrays.asList(MemoryConstants.OPERATION_REMEMBER, MemoryConstants.OPERATION_SUBMIT));
        if (rememberCount == 0) {
            return 0D;
        }
        return roundRate((double) draftCount / rememberCount);
    }

    /**
     * 统计指定操作类型的成功日志数
     */
    private int countOperationLogs(String workspaceId, Date startTime, List<String> operationList) {
        return memoryOperationLogMapper.selectCount(
                new LambdaQueryWrapper<MemoryOperationLogEntity>()
                        .eq(MemoryOperationLogEntity::getWorkspaceId, workspaceId)
                        .eq(MemoryOperationLogEntity::getSuccess, 1)
                        .in(MemoryOperationLogEntity::getOperation, operationList)
                        .ge(MemoryOperationLogEntity::getCreateTime, startTime)).intValue();
    }

    /**
     * 统计工作空间内 feedback（须 JOIN knowledge 隔离边界）
     */
    private int countFeedbackInWorkspace(String workspaceId, Date startTime, Set<String> feedbackTypes) {
        List<Long> knowledgeIdList = loadWorkspaceKnowledgeIds(workspaceId);
        if (knowledgeIdList.isEmpty()) {
            return 0;
        }
        LambdaQueryWrapper<MemoryFeedbackEntity> queryWrapper = new LambdaQueryWrapper<MemoryFeedbackEntity>();
        queryWrapper.in(MemoryFeedbackEntity::getKnowledgeId, knowledgeIdList)
                .ge(MemoryFeedbackEntity::getCreateTime, startTime);
        if (feedbackTypes != null && !feedbackTypes.isEmpty()) {
            queryWrapper.in(MemoryFeedbackEntity::getFeedbackType, feedbackTypes);
        }
        return memoryFeedbackMapper.selectCount(queryWrapper).intValue();
    }

    /**
     * 批量统计多条经验的 helpful 反馈数，避免 Top N 查询 N+1
     */
    private Map<Long, Integer> loadHelpfulCountByKnowledgeIds(List<Long> knowledgeIdList,
                                                              Set<String> feedbackTypes,
                                                              Date periodStart) {
        Map<Long, Integer> helpfulCountMap = new HashMap<Long, Integer>();
        if (knowledgeIdList == null || knowledgeIdList.isEmpty()) {
            return helpfulCountMap;
        }
        LambdaQueryWrapper<MemoryFeedbackEntity> queryWrapper = new LambdaQueryWrapper<MemoryFeedbackEntity>();
        queryWrapper.in(MemoryFeedbackEntity::getKnowledgeId, knowledgeIdList);
        if (feedbackTypes != null && !feedbackTypes.isEmpty()) {
            queryWrapper.in(MemoryFeedbackEntity::getFeedbackType, feedbackTypes);
        }
        if (periodStart != null) {
            queryWrapper.ge(MemoryFeedbackEntity::getCreateTime, periodStart);
        }
        List<MemoryFeedbackEntity> feedbackEntityList = memoryFeedbackMapper.selectList(queryWrapper);
        for (MemoryFeedbackEntity feedbackEntity : feedbackEntityList) {
            Long knowledgeId = feedbackEntity.getKnowledgeId();
            Integer currentCount = helpfulCountMap.get(knowledgeId);
            helpfulCountMap.put(knowledgeId, currentCount == null ? 1 : currentCount + 1);
        }
        return helpfulCountMap;
    }

    /**
     * 加载工作空间下全部经验 ID，供 feedback 聚合过滤
     */
    private List<Long> loadWorkspaceKnowledgeIds(String workspaceId) {
        List<KnowledgeEntity> knowledgeEntityList = knowledgeMapper.selectList(
                new LambdaQueryWrapper<KnowledgeEntity>()
                        .eq(KnowledgeEntity::getWorkspaceId, workspaceId)
                        .select(KnowledgeEntity::getId));
        List<Long> knowledgeIdList = new ArrayList<Long>();
        for (KnowledgeEntity knowledgeEntity : knowledgeEntityList) {
            knowledgeIdList.add(knowledgeEntity.getId());
        }
        return knowledgeIdList;
    }

    /**
     * 初始化趋势 Map，保证连续日期均有条目（无数据则为 0）
     */
    private Map<String, StatsTrendItemVO> initTrendMap(LocalDate startDate, LocalDate endDate) {
        Map<String, StatsTrendItemVO> trendMap = new HashMap<String, StatsTrendItemVO>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            StatsTrendItemVO trendItem = new StatsTrendItemVO();
            trendItem.setDate(cursor.format(DATE_FORMATTER));
            trendItem.setRecallCount(0);
            trendItem.setRememberCount(0);
            trendItem.setFeedbackCount(0);
            trendItem.setHelpfulCount(0);
            trendMap.put(trendItem.getDate(), trendItem);
            cursor = cursor.plusDays(1);
        }
        return trendMap;
    }

    private int normalizeDays(Integer days, int defaultDays) {
        if (days == null || days < 1) {
            return defaultDays;
        }
        return Math.min(days, 90);
    }

    private int normalizeLimit(Integer limit, int defaultLimit) {
        if (limit == null || limit < 1) {
            return defaultLimit;
        }
        return Math.min(limit, 50);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Double roundRate(double rate) {
        return Math.round(rate * 10000D) / 10000D;
    }

    private Date toDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private String formatDate(Date date) {
        if (date == null) {
            return "";
        }
        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return localDate.format(DATE_FORMATTER);
    }

    /**
     * 统计近窗口内各经验的 helpful 反馈数
     */
    private Map<Long, Integer> loadHelpfulCountByKnowledge(List<KnowledgeEntity> knowledgeEntityList, Date periodStart) {
        if (knowledgeEntityList.isEmpty()) {
            return new HashMap<Long, Integer>();
        }
        List<Long> knowledgeIdList = new ArrayList<Long>();
        for (KnowledgeEntity knowledgeEntity : knowledgeEntityList) {
            knowledgeIdList.add(knowledgeEntity.getId());
        }
        return loadHelpfulCountByKnowledgeIds(knowledgeIdList, HELPFUL_FEEDBACK_TYPES, periodStart);
    }

    /**
     * 计算 Recall / Search P95 延迟
     */
    private Integer calculateRecallP95Latency(String workspaceId, Date periodStart) {
        List<MemoryOperationLogEntity> logEntityList = memoryOperationLogMapper.selectList(
                new LambdaQueryWrapper<MemoryOperationLogEntity>()
                        .eq(MemoryOperationLogEntity::getWorkspaceId, workspaceId)
                        .eq(MemoryOperationLogEntity::getSuccess, 1)
                        .in(MemoryOperationLogEntity::getOperation,
                                Arrays.asList(MemoryConstants.OPERATION_RECALL, MemoryConstants.OPERATION_SEARCH))
                        .ge(MemoryOperationLogEntity::getCreateTime, periodStart)
                        .isNotNull(MemoryOperationLogEntity::getLatencyMs));
        if (logEntityList.isEmpty()) {
            return 0;
        }
        List<Integer> latencyList = new ArrayList<Integer>();
        for (MemoryOperationLogEntity logEntity : logEntityList) {
            if (logEntity.getLatencyMs() != null && logEntity.getLatencyMs() > 0) {
                latencyList.add(logEntity.getLatencyMs());
            }
        }
        if (latencyList.isEmpty()) {
            return 0;
        }
        Collections.sort(latencyList);
        int percentileIndex = (int) Math.ceil(latencyList.size() * 0.95D) - 1;
        percentileIndex = Math.max(0, Math.min(percentileIndex, latencyList.size() - 1));
        return latencyList.get(percentileIndex);
    }

    /**
     * 构建模块维度分布 Top 列表
     */
    private List<StatsModuleItemVO> buildModuleBreakdown(List<KnowledgeEntity> knowledgeEntityList,
                                                         Map<Long, Integer> helpfulCountByKnowledge) {
        Map<String, StatsModuleItemVO> moduleMap = new HashMap<String, StatsModuleItemVO>();
        for (KnowledgeEntity knowledgeEntity : knowledgeEntityList) {
            String moduleName = normalizeModuleName(knowledgeEntity.getModule());
            StatsModuleItemVO moduleItem = moduleMap.get(moduleName);
            if (moduleItem == null) {
                moduleItem = new StatsModuleItemVO();
                moduleItem.setModuleName(moduleName);
                moduleItem.setKnowledgeCount(0);
                moduleItem.setRecallCount(0);
                moduleItem.setHelpfulCount(0);
                moduleMap.put(moduleName, moduleItem);
            }
            moduleItem.setKnowledgeCount(moduleItem.getKnowledgeCount() + 1);
            moduleItem.setRecallCount(moduleItem.getRecallCount() + safeInt(knowledgeEntity.getRecallCount()));
            Integer helpfulCount = helpfulCountByKnowledge.get(knowledgeEntity.getId());
            moduleItem.setHelpfulCount(moduleItem.getHelpfulCount() + (helpfulCount == null ? 0 : helpfulCount));
        }
        List<StatsModuleItemVO> moduleItemList = new ArrayList<StatsModuleItemVO>(moduleMap.values());
        Collections.sort(moduleItemList, new Comparator<StatsModuleItemVO>() {
            @Override
            public int compare(StatsModuleItemVO left, StatsModuleItemVO right) {
                return right.getRecallCount().compareTo(left.getRecallCount());
            }
        });
        return trimTopList(moduleItemList, DIMENSION_TOP_LIMIT);
    }

    /**
     * 构建标签维度分布 Top 列表
     */
    private List<StatsTagItemVO> buildTagBreakdown(String workspaceId,
                                                   List<KnowledgeEntity> knowledgeEntityList,
                                                   Map<Long, Integer> helpfulCountByKnowledge) {
        Set<Long> knowledgeIdSet = new HashSet<Long>();
        for (KnowledgeEntity knowledgeEntity : knowledgeEntityList) {
            knowledgeIdSet.add(knowledgeEntity.getId());
        }
        List<KnowledgeTagRow> tagRowList = knowledgeTagMapper.selectTagRowsByWorkspace(workspaceId);
        Map<String, StatsTagItemVO> tagMap = new HashMap<String, StatsTagItemVO>();
        for (KnowledgeTagRow tagRow : tagRowList) {
            if (tagRow == null || !knowledgeIdSet.contains(tagRow.getKnowledgeId())) {
                continue;
            }
            String tagName = tagRow.getTagName();
            StatsTagItemVO tagItem = tagMap.get(tagName);
            if (tagItem == null) {
                tagItem = new StatsTagItemVO();
                tagItem.setTagName(tagName);
                tagItem.setKnowledgeCount(0);
                tagItem.setHelpfulCount(0);
                tagMap.put(tagName, tagItem);
            }
            tagItem.setKnowledgeCount(tagItem.getKnowledgeCount() + 1);
            Integer helpfulCount = helpfulCountByKnowledge.get(tagRow.getKnowledgeId());
            tagItem.setHelpfulCount(tagItem.getHelpfulCount() + (helpfulCount == null ? 0 : helpfulCount));
        }
        List<StatsTagItemVO> tagItemList = new ArrayList<StatsTagItemVO>(tagMap.values());
        Collections.sort(tagItemList, new Comparator<StatsTagItemVO>() {
            @Override
            public int compare(StatsTagItemVO left, StatsTagItemVO right) {
                return right.getKnowledgeCount().compareTo(left.getKnowledgeCount());
            }
        });
        return trimTopList(tagItemList, DIMENSION_TOP_LIMIT);
    }

    /**
     * 构建 Agent API Key 用量列表
     */
    private List<StatsApiKeyUsageItemVO> buildApiKeyUsageList(String workspaceId, Date periodStart) {
        List<MemoryOperationLogEntity> logEntityList = memoryOperationLogMapper.selectList(
                new LambdaQueryWrapper<MemoryOperationLogEntity>()
                        .eq(MemoryOperationLogEntity::getWorkspaceId, workspaceId)
                        .eq(MemoryOperationLogEntity::getSuccess, 1)
                        .ge(MemoryOperationLogEntity::getCreateTime, periodStart));

        Map<Long, StatsApiKeyUsageItemVO> usageMap = new HashMap<Long, StatsApiKeyUsageItemVO>();
        StatsApiKeyUsageItemVO jwtUsageItem = null;
        for (MemoryOperationLogEntity logEntity : logEntityList) {
            Long apiKeyId = logEntity.getApiKeyId();
            StatsApiKeyUsageItemVO usageItem;
            if (apiKeyId == null) {
                if (jwtUsageItem == null) {
                    jwtUsageItem = createUsageItem(null, JWT_USAGE_LABEL, null);
                }
                usageItem = jwtUsageItem;
            } else {
                usageItem = usageMap.get(apiKeyId);
                if (usageItem == null) {
                    // 新密钥在主站；本地无行时按主站 Key 展示，避免统计空白
                    ApiKeyEntity apiKeyEntity = apiKeyMapper.selectById(apiKeyId);
                    String keyLabel = apiKeyEntity == null ? "主站 Agent Key" : apiKeyEntity.getKeyName();
                    String keyPrefix = apiKeyEntity == null ? null : apiKeyEntity.getKeyPrefix();
                    usageItem = createUsageItem(apiKeyId, keyLabel, keyPrefix);
                    usageMap.put(apiKeyId, usageItem);
                }
            }
            accumulateUsageByOperation(usageItem, logEntity.getOperation());
        }

        List<StatsApiKeyUsageItemVO> usageItemList = new ArrayList<StatsApiKeyUsageItemVO>(usageMap.values());
        if (jwtUsageItem != null) {
            usageItemList.add(jwtUsageItem);
        }
        Collections.sort(usageItemList, new Comparator<StatsApiKeyUsageItemVO>() {
            @Override
            public int compare(StatsApiKeyUsageItemVO left, StatsApiKeyUsageItemVO right) {
                int leftTotal = safeInt(left.getRecallCount()) + safeInt(left.getRememberCount());
                int rightTotal = safeInt(right.getRecallCount()) + safeInt(right.getRememberCount());
                return rightTotal - leftTotal;
            }
        });
        return usageItemList;
    }

    /**
     * 按 module / project / tag 筛选经验列表
     */
    private List<KnowledgeEntity> filterKnowledgeList(String workspaceId,
                                                      List<KnowledgeEntity> knowledgeEntityList,
                                                      String module, String project, String tag) {
        if (StringUtils.isBlank(module) && StringUtils.isBlank(project) && StringUtils.isBlank(tag)) {
            return knowledgeEntityList;
        }
        Set<Long> tagKnowledgeIdSet = null;
        if (StringUtils.isNotBlank(tag)) {
            tagKnowledgeIdSet = new HashSet<Long>();
            List<KnowledgeTagRow> tagRowList = knowledgeTagMapper.selectTagRowsByWorkspace(workspaceId);
            for (KnowledgeTagRow tagRow : tagRowList) {
                if (tagRow != null && tag.trim().equalsIgnoreCase(tagRow.getTagName())) {
                    tagKnowledgeIdSet.add(tagRow.getKnowledgeId());
                }
            }
        }
        List<KnowledgeEntity> filteredList = new ArrayList<KnowledgeEntity>();
        for (KnowledgeEntity knowledgeEntity : knowledgeEntityList) {
            if (StringUtils.isNotBlank(module)) {
                String entityModule = StringUtils.defaultString(knowledgeEntity.getModule());
                if (!module.trim().equalsIgnoreCase(entityModule)) {
                    continue;
                }
            }
            if (StringUtils.isNotBlank(project)) {
                String entityProject = StringUtils.defaultString(knowledgeEntity.getProject());
                if (!project.trim().equalsIgnoreCase(entityProject)) {
                    continue;
                }
            }
            if (tagKnowledgeIdSet != null && !tagKnowledgeIdSet.contains(knowledgeEntity.getId())) {
                continue;
            }
            filteredList.add(knowledgeEntity);
        }
        return filteredList;
    }

    private StatsApiKeyUsageItemVO createUsageItem(Long apiKeyId, String keyLabel, String keyPrefix) {
        StatsApiKeyUsageItemVO usageItem = new StatsApiKeyUsageItemVO();
        usageItem.setApiKeyId(apiKeyId);
        usageItem.setKeyLabel(keyLabel);
        usageItem.setKeyPrefix(keyPrefix);
        usageItem.setRecallCount(0);
        usageItem.setRememberCount(0);
        usageItem.setFeedbackCount(0);
        return usageItem;
    }

    private void accumulateUsageByOperation(StatsApiKeyUsageItemVO usageItem, String operation) {
        if (MemoryConstants.OPERATION_RECALL.equals(operation)
                || MemoryConstants.OPERATION_SEARCH.equals(operation)) {
            usageItem.setRecallCount(usageItem.getRecallCount() + 1);
        } else if (MemoryConstants.OPERATION_REMEMBER.equals(operation)
                || MemoryConstants.OPERATION_SUBMIT.equals(operation)) {
            usageItem.setRememberCount(usageItem.getRememberCount() + 1);
        } else if (MemoryConstants.OPERATION_FEEDBACK.equals(operation)) {
            usageItem.setFeedbackCount(usageItem.getFeedbackCount() + 1);
        }
    }

    private String normalizeModuleName(String module) {
        if (StringUtils.isBlank(module)) {
            return "未分类";
        }
        return module.trim();
    }

    private <T> List<T> trimTopList(List<T> sourceList, int limit) {
        if (sourceList.size() <= limit) {
            return sourceList;
        }
        return new ArrayList<T>(sourceList.subList(0, limit));
    }

    /**
     * 解析月份参数，默认当前月
     */
    private YearMonth parseMonth(String month) {
        if (org.apache.commons.lang3.StringUtils.isBlank(month)) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.trim(), MONTH_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(400, "month 格式应为 yyyy-MM");
        }
    }

    /**
     * 读取组织月 Recall 配额
     */
    private Integer resolveRecallQuota(String workspaceId) {
        WorkspaceEntity workspaceEntity = workspaceMapper.selectById(workspaceId);
        if (workspaceEntity == null || workspaceEntity.getOrganizationId() == null) {
            return 0;
        }
        OrganizationEntity organizationEntity = organizationMapper.selectById(workspaceEntity.getOrganizationId());
        if (organizationEntity == null || organizationEntity.getRecallQuota() == null) {
            return 0;
        }
        return organizationEntity.getRecallQuota();
    }

    /**
     * 格式化 usage_daily.usage_date
     */
    private String formatSqlDate(Date date) {
        if (date == null) {
            return "";
        }
        if (date instanceof java.sql.Date) {
            return date.toString();
        }
        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return localDate.format(DATE_FORMATTER);
    }
}
