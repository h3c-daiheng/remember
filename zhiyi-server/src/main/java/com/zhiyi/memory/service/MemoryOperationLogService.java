package com.zhiyi.memory.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.zhiyi.auth.AgentAuthContext;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.PageResult;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.dao.MemoryOperationLogMapper;
import com.zhiyi.memory.domain.FeedbackRequest;
import com.zhiyi.memory.domain.MemoryTraceView;
import com.zhiyi.memory.domain.RecallContext;
import com.zhiyi.memory.domain.RecallExecutionMeta;
import com.zhiyi.memory.domain.RecallLogDetailView;
import com.zhiyi.memory.domain.RecallLogListItem;
import com.zhiyi.memory.domain.RecallResponse;
import com.zhiyi.memory.domain.RecallResult;
import com.zhiyi.memory.entity.MemoryOperationLogEntity;
import com.zhiyi.memory.util.MemoryJsonUtil;
import com.zhiyi.service.UsageDailyService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Memory API 操作追踪服务：落库 remember/recall/feedback 调用，支撑闭环验证
 */
@Service
public class MemoryOperationLogService {

    private static final Logger log = LoggerFactory.getLogger(MemoryOperationLogService.class);

    /** 查询 Recall 命中时默认回溯条数 */
    private static final int DEFAULT_RECALL_SCAN_LIMIT = 100;

    /** promptBlock 写入操作日志时的预览最大长度 */
    private static final int PROMPT_BLOCK_PREVIEW_LENGTH = 500;

    private final MemoryOperationLogMapper memoryOperationLogMapper;
    private final UsageDailyService usageDailyService;

    public MemoryOperationLogService(MemoryOperationLogMapper memoryOperationLogMapper,
                                     UsageDailyService usageDailyService) {
        this.memoryOperationLogMapper = memoryOperationLogMapper;
        this.usageDailyService = usageDailyService;
    }

    /**
     * 解析或生成 trace ID：优先使用请求头 X-Trace-Id
     */
    public String resolveTraceId(String headerTraceId) {
        if (StringUtils.isNotBlank(headerTraceId)) {
            return headerTraceId.trim();
        }
        return "tr_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 记录 Recall / Search 成功调用，持久化输入与质量分析摘要
     */
    public void recordRecallSuccess(AgentAuthContext authContext, String traceId, String operation,
                                    RecallContext recallContext, RecallResult recallResult, long latencyMs) {
        RecallResponse recallResponse = recallResult == null ? null : recallResult.getResponse();
        RecallExecutionMeta executionMeta = recallResult == null ? null : recallResult.getExecutionMeta();
        MemoryOperationLogEntity logEntity = buildBaseEntity(authContext, traceId, operation, latencyMs);
        logEntity.setRecallSession(recallResponse == null ? null : recallResponse.getSessionId());
        logEntity.setRequestJson(MemoryJsonUtil.toJson(recallContext));
        logEntity.setResponseSummaryJson(MemoryJsonUtil.toJson(
                buildRecallSummary(recallResponse, executionMeta)));
        logEntity.setSuccess(1);
        if (insertQuietly(logEntity)) {
            notifyUsageIncrement(logEntity.getWorkspaceId(), operation, 0L);
        }
    }

    /**
     * 记录 Submit 成功调用
     */
    public void recordSubmitSuccess(AgentAuthContext authContext, String traceId,
                                    Object submitRequest, Map<String, Object> responseData, long latencyMs) {
        MemoryOperationLogEntity logEntity = buildBaseEntity(authContext, traceId,
                MemoryConstants.OPERATION_SUBMIT, latencyMs);
        logEntity.setRequestJson(MemoryJsonUtil.toJson(submitRequest));
        logEntity.setResponseSummaryJson(MemoryJsonUtil.toJson(responseData));
        logEntity.setSuccess(1);
        if (insertQuietly(logEntity)) {
            notifyUsageIncrement(logEntity.getWorkspaceId(), MemoryConstants.OPERATION_SUBMIT, 0L);
        }
    }

    /**
     * 记录 Feedback 成功调用
     */
    public void recordFeedbackSuccess(AgentAuthContext authContext, String traceId,
                                      FeedbackRequest feedbackRequest, long latencyMs) {
        MemoryOperationLogEntity logEntity = buildBaseEntity(authContext, traceId,
                MemoryConstants.OPERATION_FEEDBACK, latencyMs);
        logEntity.setRecallSession(feedbackRequest == null ? null : feedbackRequest.getSessionId());
        logEntity.setRequestJson(MemoryJsonUtil.toJson(feedbackRequest));
        Map<String, Object> summary = new HashMap<String, Object>();
        if (feedbackRequest != null) {
            summary.put("knowledgeId", feedbackRequest.getKnowledgeId());
            summary.put("sessionId", feedbackRequest.getSessionId());
            summary.put("feedbackType", feedbackRequest.getFeedbackType());
        }
        logEntity.setResponseSummaryJson(MemoryJsonUtil.toJson(summary));
        logEntity.setSuccess(1);
        if (insertQuietly(logEntity)) {
            notifyUsageIncrement(logEntity.getWorkspaceId(), MemoryConstants.OPERATION_FEEDBACK, 0L);
        }
    }

    /**
     * 记录 Memory API 失败调用
     */
    public void recordFailure(AgentAuthContext authContext, String traceId, String operation,
                              Object requestBody, String recallSession, String errorMessage, long latencyMs) {
        MemoryOperationLogEntity logEntity = buildBaseEntity(authContext, traceId, operation, latencyMs);
        logEntity.setRecallSession(recallSession);
        logEntity.setRequestJson(MemoryJsonUtil.toJson(requestBody));
        logEntity.setSuccess(0);
        logEntity.setErrorMessage(StringUtils.left(errorMessage, 512));
        insertQuietly(logEntity);
    }

    /**
     * 查询工作空间内最近一次成功的 Recall / Search 操作日志
     */
    public MemoryOperationLogEntity findLatestRecallLog(String workspaceId) {
        if (StringUtils.isBlank(workspaceId)) {
            return null;
        }
        LambdaQueryWrapper<MemoryOperationLogEntity> queryWrapper = new LambdaQueryWrapper<MemoryOperationLogEntity>();
        queryWrapper.eq(MemoryOperationLogEntity::getWorkspaceId, workspaceId)
                .in(MemoryOperationLogEntity::getOperation,
                        MemoryConstants.OPERATION_RECALL, MemoryConstants.OPERATION_SEARCH)
                .eq(MemoryOperationLogEntity::getSuccess, 1)
                .orderByDesc(MemoryOperationLogEntity::getCreateTime)
                .last("LIMIT 1");
        return memoryOperationLogMapper.selectOne(queryWrapper);
    }

    /**
     * 从 Recall 日志摘要中提取 Top1 命中的 knowledgeId，若无 Top1 则取首条
     */
    public Long extractTopKnowledgeIdFromRecallLog(MemoryOperationLogEntity logEntity) {
        if (logEntity == null || StringUtils.isBlank(logEntity.getResponseSummaryJson())) {
            return null;
        }
        JSONObject summaryObject = JSONUtil.parseObj(logEntity.getResponseSummaryJson());
        JSONArray itemArray = summaryObject.getJSONArray("items");
        if (itemArray == null || itemArray.isEmpty()) {
            return null;
        }
        Long topKnowledgeId = null;
        Long fallbackKnowledgeId = null;
        for (int index = 0; index < itemArray.size(); index++) {
            JSONObject itemObject = itemArray.getJSONObject(index);
            if (itemObject == null) {
                continue;
            }
            Long knowledgeId = itemObject.getLong("knowledgeId");
            if (knowledgeId == null) {
                continue;
            }
            if (fallbackKnowledgeId == null) {
                fallbackKnowledgeId = knowledgeId;
            }
            Integer rank = itemObject.getInt("rank");
            if (rank != null && rank == 1) {
                topKnowledgeId = knowledgeId;
                break;
            }
        }
        return topKnowledgeId != null ? topKnowledgeId : fallbackKnowledgeId;
    }

    /**
     * 查询指定 knowledge 在近期 Recall 中的命中记录
     */
    public List<MemoryTraceView.RecallHitItem> findRecallHits(String workspaceId, Long knowledgeId, int scanLimit) {
        if (StringUtils.isBlank(workspaceId) || knowledgeId == null) {
            return new ArrayList<MemoryTraceView.RecallHitItem>();
        }
        int effectiveLimit = scanLimit <= 0 ? DEFAULT_RECALL_SCAN_LIMIT : scanLimit;
        LambdaQueryWrapper<MemoryOperationLogEntity> queryWrapper = new LambdaQueryWrapper<MemoryOperationLogEntity>();
        queryWrapper.eq(MemoryOperationLogEntity::getWorkspaceId, workspaceId)
                .in(MemoryOperationLogEntity::getOperation,
                        MemoryConstants.OPERATION_RECALL, MemoryConstants.OPERATION_SEARCH)
                .eq(MemoryOperationLogEntity::getSuccess, 1)
                .orderByDesc(MemoryOperationLogEntity::getCreateTime)
                .last("LIMIT " + effectiveLimit);
        List<MemoryOperationLogEntity> logEntityList = memoryOperationLogMapper.selectList(queryWrapper);
        List<MemoryTraceView.RecallHitItem> hitItemList = new ArrayList<MemoryTraceView.RecallHitItem>();
        for (MemoryOperationLogEntity logEntity : logEntityList) {
            MemoryTraceView.RecallHitItem hitItem = extractRecallHit(logEntity, knowledgeId);
            if (hitItem != null) {
                hitItemList.add(hitItem);
            }
        }
        return hitItemList;
    }

    /**
     * 从单条 Recall 日志中提取对指定 knowledge 的命中信息
     */
    private MemoryTraceView.RecallHitItem extractRecallHit(MemoryOperationLogEntity logEntity, Long knowledgeId) {
        if (StringUtils.isBlank(logEntity.getResponseSummaryJson())) {
            return null;
        }
        JSONObject summaryObject = JSONUtil.parseObj(logEntity.getResponseSummaryJson());
        JSONArray itemArray = summaryObject.getJSONArray("items");
        if (itemArray == null || itemArray.isEmpty()) {
            return null;
        }
        for (int index = 0; index < itemArray.size(); index++) {
            JSONObject itemObject = itemArray.getJSONObject(index);
            if (itemObject == null || !knowledgeId.equals(itemObject.getLong("knowledgeId"))) {
                continue;
            }
            MemoryTraceView.RecallHitItem hitItem = new MemoryTraceView.RecallHitItem();
            hitItem.setLogId(logEntity.getId());
            hitItem.setTraceId(logEntity.getTraceId());
            hitItem.setRecallSession(logEntity.getRecallSession());
            hitItem.setRank(itemObject.getInt("rank"));
            hitItem.setKnowledgeId(itemObject.getLong("knowledgeId"));
            hitItem.setTitle(itemObject.getStr("title"));
            hitItem.setScore(itemObject.getDouble("score"));
            hitItem.setScoreBreakdown(parseScoreBreakdown(itemObject.getJSONObject("scoreBreakdown")));
            hitItem.setCreateTime(logEntity.getCreateTime());
            fillRequestContext(hitItem, logEntity.getRequestJson());
            fillRecallSummaryContext(hitItem, summaryObject);
            return hitItem;
        }
        return null;
    }

    /**
     * 从 request_json 提取 task/module/repository 便于人工核对 Recall 上下文
     */
    private void fillRequestContext(MemoryTraceView.RecallHitItem hitItem, String requestJson) {
        if (StringUtils.isBlank(requestJson)) {
            return;
        }
        JSONObject requestObject = JSONUtil.parseObj(requestJson);
        hitItem.setTask(requestObject.getStr("task"));
        hitItem.setModule(requestObject.getStr("module"));
        hitItem.setRepository(requestObject.getStr("repository"));
    }

    /**
     * 从 response_summary_json 提取召回质量摘要字段
     */
    private void fillRecallSummaryContext(MemoryTraceView.RecallHitItem hitItem, JSONObject summaryObject) {
        if (summaryObject == null) {
            return;
        }
        hitItem.setQueryText(summaryObject.getStr("queryText"));
        hitItem.setFallbackUsed(summaryObject.getBool("fallbackUsed", false));
        hitItem.setItemCount(summaryObject.getInt("itemCount"));
    }

    /**
     * 分页查询工作空间内 Recall / Search 操作日志，供「召回记录」列表页
     */
    public PageResult<RecallLogListItem> listRecallLogs(int pageNum, int pageSize,
                                                        String operation, Integer success,
                                                        String keyword, String workspaceId) {
        if (StringUtils.isBlank(workspaceId)) {
            throw new BusinessException(400, "工作空间不能为空");
        }
        int safePageNum = pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize < 1 ? 20 : Math.min(pageSize, 100);

        PageHelper.startPage(safePageNum, safePageSize);
        LambdaQueryWrapper<MemoryOperationLogEntity> queryWrapper =
                new LambdaQueryWrapper<MemoryOperationLogEntity>();
        queryWrapper.eq(MemoryOperationLogEntity::getWorkspaceId, workspaceId);
        applyRecallListOperationFilter(queryWrapper, operation);
        if (success != null) {
            queryWrapper.eq(MemoryOperationLogEntity::getSuccess, success);
        }
        if (StringUtils.isNotBlank(keyword)) {
            String keywordText = keyword.trim();
            queryWrapper.and(wrapper -> wrapper
                    .like(MemoryOperationLogEntity::getRequestJson, keywordText)
                    .or()
                    .like(MemoryOperationLogEntity::getRecallSession, keywordText)
                    .or()
                    .like(MemoryOperationLogEntity::getTraceId, keywordText)
                    .or()
                    .like(MemoryOperationLogEntity::getErrorMessage, keywordText));
        }
        queryWrapper.orderByDesc(MemoryOperationLogEntity::getCreateTime)
                .orderByDesc(MemoryOperationLogEntity::getId);
        List<MemoryOperationLogEntity> entityList = memoryOperationLogMapper.selectList(queryWrapper);
        PageInfo<MemoryOperationLogEntity> pageInfo = new PageInfo<MemoryOperationLogEntity>(entityList);
        return PageResult.of(pageInfo.getTotal(), toRecallLogListItems(entityList));
    }

    /**
     * 列表操作类型过滤：未传时默认 recall + search
     */
    private void applyRecallListOperationFilter(LambdaQueryWrapper<MemoryOperationLogEntity> queryWrapper,
                                                String operation) {
        if (StringUtils.isBlank(operation)) {
            queryWrapper.in(MemoryOperationLogEntity::getOperation,
                    MemoryConstants.OPERATION_RECALL, MemoryConstants.OPERATION_SEARCH);
            return;
        }
        String normalizedOperation = operation.trim();
        if (!MemoryConstants.OPERATION_RECALL.equals(normalizedOperation)
                && !MemoryConstants.OPERATION_SEARCH.equals(normalizedOperation)) {
            throw new BusinessException(400, "operation 仅支持 recall 或 search");
        }
        queryWrapper.eq(MemoryOperationLogEntity::getOperation, normalizedOperation);
    }

    /**
     * 将操作日志实体转为列表项，轻量解析 request / response 摘要
     */
    private List<RecallLogListItem> toRecallLogListItems(List<MemoryOperationLogEntity> entityList) {
        List<RecallLogListItem> itemList = new ArrayList<RecallLogListItem>();
        if (entityList == null || entityList.isEmpty()) {
            return itemList;
        }
        for (MemoryOperationLogEntity logEntity : entityList) {
            itemList.add(toRecallLogListItem(logEntity));
        }
        return itemList;
    }

    /**
     * 组装单条召回日志列表项
     */
    private RecallLogListItem toRecallLogListItem(MemoryOperationLogEntity logEntity) {
        RecallLogListItem listItem = new RecallLogListItem();
        listItem.setLogId(logEntity.getId());
        listItem.setCreateTime(logEntity.getCreateTime());
        listItem.setOperation(logEntity.getOperation());
        listItem.setSuccess(logEntity.getSuccess());
        listItem.setLatencyMs(logEntity.getLatencyMs());
        listItem.setRecallSession(logEntity.getRecallSession());
        listItem.setTraceId(logEntity.getTraceId());
        listItem.setAuthType(logEntity.getAuthType());
        listItem.setApiKeyId(logEntity.getApiKeyId());
        listItem.setErrorMessage(logEntity.getErrorMessage());
        listItem.setTask(extractTaskFromRequestJson(logEntity.getRequestJson()));

        if (StringUtils.isNotBlank(logEntity.getResponseSummaryJson())) {
            JSONObject summaryObject = JSONUtil.parseObj(logEntity.getResponseSummaryJson());
            listItem.setItemCount(summaryObject.getInt("itemCount"));
            listItem.setFallbackUsed(summaryObject.getBool("fallbackUsed"));
            listItem.setTopTitle(extractTopTitleFromSummary(summaryObject));
        }
        return listItem;
    }

    /**
     * 从 requestJson 提取 task 字段
     */
    private String extractTaskFromRequestJson(String requestJson) {
        if (StringUtils.isBlank(requestJson)) {
            return null;
        }
        try {
            JSONObject requestObject = JSONUtil.parseObj(requestJson);
            return StringUtils.trimToNull(requestObject.getStr("task"));
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 从响应摘要 items 中取 Top1 标题
     */
    private String extractTopTitleFromSummary(JSONObject summaryObject) {
        if (summaryObject == null) {
            return null;
        }
        JSONArray itemArray = summaryObject.getJSONArray("items");
        if (itemArray == null || itemArray.isEmpty()) {
            return null;
        }
        JSONObject firstItem = itemArray.getJSONObject(0);
        if (firstItem == null) {
            return null;
        }
        return StringUtils.trimToNull(firstItem.getStr("title"));
    }

    /**
     * 按 logId 查询 Recall 操作详情，供 Web 召回调试
     */
    public RecallLogDetailView getRecallLogDetail(Long logId, String workspaceId) {
        if (logId == null || StringUtils.isBlank(workspaceId)) {
            throw new BusinessException(400, "logId 与工作空间不能为空");
        }
        MemoryOperationLogEntity logEntity = memoryOperationLogMapper.selectById(logId);
        if (logEntity == null) {
            throw new BusinessException(404, "Recall 日志不存在");
        }
        if (!workspaceId.equals(logEntity.getWorkspaceId())) {
            throw new BusinessException(403, "无权访问该 Recall 日志");
        }
        if (!MemoryConstants.OPERATION_RECALL.equals(logEntity.getOperation())
                && !MemoryConstants.OPERATION_SEARCH.equals(logEntity.getOperation())) {
            throw new BusinessException(400, "该日志不是 Recall 操作");
        }
        return buildRecallLogDetail(logEntity);
    }

    /**
     * 组装 Recall 日志详情视图
     */
    private RecallLogDetailView buildRecallLogDetail(MemoryOperationLogEntity logEntity) {
        RecallLogDetailView detailView = new RecallLogDetailView();
        detailView.setLogId(logEntity.getId());
        detailView.setTraceId(logEntity.getTraceId());
        detailView.setRecallSession(logEntity.getRecallSession());
        detailView.setOperation(logEntity.getOperation());
        detailView.setCreateTime(logEntity.getCreateTime());
        detailView.setLatencyMs(logEntity.getLatencyMs());
        detailView.setRequestContext(parseRecallContext(logEntity.getRequestJson()));

        if (StringUtils.isBlank(logEntity.getResponseSummaryJson())) {
            detailView.setItems(new ArrayList<RecallLogDetailView.RecallItemSummary>());
            return detailView;
        }

        JSONObject summaryObject = JSONUtil.parseObj(logEntity.getResponseSummaryJson());
        detailView.setQueryText(summaryObject.getStr("queryText"));
        detailView.setFallbackUsed(summaryObject.getBool("fallbackUsed", false));
        detailView.setRetrievalCandidateCount(summaryObject.getInt("retrievalCandidateCount", 0));
        detailView.setRankedCandidateCount(summaryObject.getInt("rankedCandidateCount", 0));
        detailView.setItemCount(summaryObject.getInt("itemCount", 0));
        detailView.setPromptBlockLength(summaryObject.getInt("promptBlockLength", 0));
        detailView.setPromptBlockPreview(summaryObject.getStr("promptBlockPreview"));
        detailView.setItems(parseRecallItemSummaries(summaryObject.getJSONArray("items")));
        return detailView;
    }

    private RecallContext parseRecallContext(String requestJson) {
        if (StringUtils.isBlank(requestJson)) {
            return new RecallContext();
        }
        return JSONUtil.toBean(requestJson, RecallContext.class);
    }

    private List<RecallLogDetailView.RecallItemSummary> parseRecallItemSummaries(JSONArray itemArray) {
        List<RecallLogDetailView.RecallItemSummary> itemSummaryList =
                new ArrayList<RecallLogDetailView.RecallItemSummary>();
        if (itemArray == null || itemArray.isEmpty()) {
            return itemSummaryList;
        }
        for (int index = 0; index < itemArray.size(); index++) {
            JSONObject itemObject = itemArray.getJSONObject(index);
            if (itemObject == null) {
                continue;
            }
            RecallLogDetailView.RecallItemSummary itemSummary = new RecallLogDetailView.RecallItemSummary();
            itemSummary.setRank(itemObject.getInt("rank"));
            itemSummary.setKnowledgeId(itemObject.getLong("knowledgeId"));
            itemSummary.setTitle(itemObject.getStr("title"));
            itemSummary.setKnowledgeType(itemObject.getStr("knowledgeType"));
            itemSummary.setScore(itemObject.getDouble("score"));
            itemSummary.setScoreBreakdown(parseScoreBreakdown(itemObject.getJSONObject("scoreBreakdown")));
            itemSummaryList.add(itemSummary);
        }
        return itemSummaryList;
    }

    private Map<String, Double> parseScoreBreakdown(JSONObject breakdownObject) {
        Map<String, Double> breakdown = new HashMap<String, Double>();
        if (breakdownObject == null || breakdownObject.isEmpty()) {
            return breakdown;
        }
        for (String key : breakdownObject.keySet()) {
            breakdown.put(key, breakdownObject.getDouble(key));
        }
        return breakdown;
    }

    /**
     * 构建 Recall 响应摘要，保留质量分析所需字段
     */
    private Map<String, Object> buildRecallSummary(RecallResponse recallResponse,
                                                     RecallExecutionMeta executionMeta) {
        Map<String, Object> summary = new HashMap<String, Object>();
        if (executionMeta != null) {
            summary.put("queryText", executionMeta.getQueryText());
            summary.put("fallbackUsed", executionMeta.isFallbackUsed());
            summary.put("retrievalCandidateCount", executionMeta.getRetrievalCandidateCount());
            summary.put("rankedCandidateCount", executionMeta.getRankedCandidateCount());
        }
        if (recallResponse == null) {
            summary.put("itemCount", 0);
            summary.put("items", new ArrayList<Map<String, Object>>());
            summary.put("promptBlockLength", 0);
            summary.put("promptBlockPreview", "");
            return summary;
        }
        summary.put("sessionId", recallResponse.getSessionId());
        String promptBlock = recallResponse.getPromptBlock();
        summary.put("promptBlockLength", promptBlock == null ? 0 : promptBlock.length());
        summary.put("promptBlockPreview", buildPromptBlockPreview(promptBlock));
        List<Map<String, Object>> itemSummaryList = new ArrayList<Map<String, Object>>();
        if (recallResponse.getItems() != null) {
            int rank = 1;
            for (RecallResponse.RecallItem recallItem : recallResponse.getItems()) {
                Map<String, Object> itemSummary = new HashMap<String, Object>();
                itemSummary.put("rank", rank++);
                itemSummary.put("knowledgeId", recallItem.getKnowledgeId());
                itemSummary.put("title", recallItem.getTitle());
                itemSummary.put("knowledgeType", recallItem.getKnowledgeType());
                itemSummary.put("score", recallItem.getScore());
                itemSummary.put("scoreBreakdown", recallItem.getScoreBreakdown());
                itemSummaryList.add(itemSummary);
            }
        }
        summary.put("itemCount", itemSummaryList.size());
        summary.put("items", itemSummaryList);
        return summary;
    }

    /**
     * 截断 promptBlock 预览，避免 operation log JSON 过大
     */
    private String buildPromptBlockPreview(String promptBlock) {
        if (StringUtils.isBlank(promptBlock)) {
            return "";
        }
        if (promptBlock.length() <= PROMPT_BLOCK_PREVIEW_LENGTH) {
            return promptBlock;
        }
        return promptBlock.substring(0, PROMPT_BLOCK_PREVIEW_LENGTH) + "...";
    }

    private MemoryOperationLogEntity buildBaseEntity(AgentAuthContext authContext, String traceId,
                                                       String operation, long latencyMs) {
        MemoryOperationLogEntity logEntity = new MemoryOperationLogEntity();
        logEntity.setWorkspaceId(authContext.getWorkspaceId());
        logEntity.setOperation(operation);
        logEntity.setTraceId(traceId);
        logEntity.setApiKeyId(authContext.getApiKeyId());
        logEntity.setAuthType(authContext.getAuthType());
        logEntity.setLatencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE));
        return logEntity;
    }

    /**
     * 落库失败不影响主流程，仅打 warn 日志；返回是否写入成功，供 usage_daily 累加判断
     */
    private boolean insertQuietly(MemoryOperationLogEntity logEntity) {
        try {
            return memoryOperationLogMapper.insert(logEntity) > 0;
        } catch (Exception exception) {
            log.warn("Memory 操作追踪落库失败 operation={} traceId={}",
                    logEntity.getOperation(), logEntity.getTraceId(), exception);
            return false;
        }
    }

    /**
     * 操作日志落库成功后异步累加 usage_daily
     */
    private void notifyUsageIncrement(String workspaceId, String operation, long embeddingTokens) {
        try {
            usageDailyService.incrementAsync(workspaceId, operation, embeddingTokens);
        } catch (Exception exception) {
            log.warn("usage_daily 异步累加触发失败 workspaceId={} operation={}", workspaceId, operation, exception);
        }
    }

    /**
     * 收集 trace 中需要查询 Recall 命中的 knowledgeId 集合
     */
    public Set<Long> collectKnowledgeIdsForTrace(List<MemoryTraceView.KnowledgeTraceItem> knowledgeList,
                                                  Long routedKnowledgeId, Long approvedKnowledgeId) {
        Set<Long> knowledgeIdSet = new LinkedHashSet<Long>();
        if (routedKnowledgeId != null) {
            knowledgeIdSet.add(routedKnowledgeId);
        }
        if (approvedKnowledgeId != null) {
            knowledgeIdSet.add(approvedKnowledgeId);
        }
        if (knowledgeList != null) {
            for (MemoryTraceView.KnowledgeTraceItem knowledgeItem : knowledgeList) {
                if (knowledgeItem != null && knowledgeItem.getKnowledgeId() != null) {
                    knowledgeIdSet.add(knowledgeItem.getKnowledgeId());
                }
            }
        }
        return knowledgeIdSet;
    }
}
