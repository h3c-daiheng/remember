package com.zhiyi.memory.service;

import com.zhiyi.common.BusinessException;
import com.zhiyi.domain.vo.GraphViewVO;
import com.zhiyi.memory.domain.FeedbackRequest;
import com.zhiyi.memory.domain.GraphExploreRequest;
import com.zhiyi.memory.domain.RecallContext;
import com.zhiyi.memory.domain.RecallResult;
import com.zhiyi.memory.domain.SystemEventRequest;
import com.zhiyi.memory.engine.CaptureEngine;
import com.zhiyi.memory.engine.LearningEngine;
import com.zhiyi.memory.engine.RecallEngine;
import com.zhiyi.memory.graph.GraphQueryService;
import com.zhiyi.memory.knowledge.KnowledgeService;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.util.MemoryJsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Memory 门面服务，聚合 Recall / Submit / Search / Feedback
 */
@Service
public class MemoryService {

    private final RecallEngine recallEngine;
    private final CaptureEngine captureEngine;
    private final LearningEngine learningEngine;
    private final KnowledgeService knowledgeService;
    private final GraphQueryService graphQueryService;

    public MemoryService(RecallEngine recallEngine,
                         CaptureEngine captureEngine,
                         LearningEngine learningEngine,
                         KnowledgeService knowledgeService,
                         GraphQueryService graphQueryService) {
        this.recallEngine = recallEngine;
        this.captureEngine = captureEngine;
        this.learningEngine = learningEngine;
        this.knowledgeService = knowledgeService;
        this.graphQueryService = graphQueryService;
    }

    /**
     * Recall：根据任务上下文召回相关经验
     */
    public RecallResult recall(RecallContext recallContext) {
        return recallEngine.recall(recallContext);
    }

    /**
     * Search：MVP 与 Recall 共用实现，语义检索经验
     */
    public RecallResult search(RecallContext recallContext) {
        return recallEngine.recall(recallContext);
    }

    /**
     * Submit：Agent 统一提交记忆，全部进入 Capture 草稿待人工确认
     */
    public Map<String, Object> submit(SystemEventRequest eventRequest, Long creatorId) {
        normalizeSubmitRequest(eventRequest);
        validateSubmitRequest(eventRequest);
        return captureEngine.submit(eventRequest, creatorId);
    }

    /**
     * Feedback：记录 Recall 效果反馈
     */
    public void feedback(FeedbackRequest feedbackRequest, RecallContext recallContext, Long actorId) {
        learningEngine.recordFeedback(feedbackRequest, recallContext, actorId);
    }

    /**
     * Graph Explore：以 centerId 为中心查询子图，供 Agent 探索经验关系网络
     */
    public GraphViewVO graphExplore(GraphExploreRequest exploreRequest, String workspaceId) {
        if (exploreRequest == null || exploreRequest.getCenterId() == null) {
            throw new BusinessException(400, "centerId 不能为空");
        }
        int depth = exploreRequest.getDepth() == null ? 2 : exploreRequest.getDepth();
        int limit = exploreRequest.getLimit() == null ? 50 : exploreRequest.getLimit();
        return graphQueryService.querySubGraph(
                exploreRequest.getCenterId(),
                workspaceId,
                depth,
                limit,
                exploreRequest.getRelationTypes());
    }

    /**
     * 补全 Submit 请求的默认 event type 与 knowledgeType
     */
    private void normalizeSubmitRequest(SystemEventRequest eventRequest) {
        if (eventRequest == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        if (StringUtils.isBlank(eventRequest.getType())) {
            eventRequest.setType("agent_finished");
        }
        if (StringUtils.isBlank(eventRequest.getKnowledgeType())) {
            eventRequest.setKnowledgeType(MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE);
        }
    }

    /**
     * 按 knowledgeType 校验 Submit 载荷
     */
    private void validateSubmitRequest(SystemEventRequest eventRequest) {
        knowledgeService.validateKnowledgeType(eventRequest.getKnowledgeType());
        Map<String, Object> payload = eventRequest.getPayload();
        if (payload == null) {
            payload = new HashMap<String, Object>();
            eventRequest.setPayload(payload);
        }

        if (MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE.equals(eventRequest.getKnowledgeType())) {
            validateExperiencePayload(payload);
            return;
        }

        validateStructuredPayload(payload, eventRequest.getKnowledgeType());
    }

    /**
     * Experience 须具备 observation/decision/action 或有效 facts
     */
    private void validateExperiencePayload(Map<String, Object> payload) {
        if (hasValidFacts(payload)) {
            return;
        }
        if (StringUtils.isBlank(MemoryJsonUtil.readString(payload, "observation"))
                || StringUtils.isBlank(MemoryJsonUtil.readString(payload, "decision"))
                || StringUtils.isBlank(MemoryJsonUtil.readString(payload, "action"))) {
            throw new BusinessException(400, "experience 类型须填写 observation、decision、action，或提供 facts 数组");
        }
    }

    /**
     * Rule / Workflow / Decision 须具备标题与至少一条有效 Fact
     */
    private void validateStructuredPayload(Map<String, Object> payload, String knowledgeType) {
        String title = MemoryJsonUtil.readString(payload, "title");
        if (StringUtils.isBlank(title)) {
            title = MemoryJsonUtil.readString(payload, "task");
        }
        if (StringUtils.isBlank(title)) {
            throw new BusinessException(400, knowledgeType + " 类型须填写 title 或 task 作为标题");
        }
        if (!hasValidFacts(payload)) {
            throw new BusinessException(400, knowledgeType + " 类型须提供至少一条非空 facts");
        }
    }

    /**
     * 判断 payload.facts 是否包含有效内容
     */
    @SuppressWarnings("unchecked")
    private boolean hasValidFacts(Map<String, Object> payload) {
        if (payload == null || !(payload.get("facts") instanceof List)) {
            return false;
        }
        List<Object> factList = (List<Object>) payload.get("facts");
        for (Object factItem : factList) {
            if (!(factItem instanceof Map)) {
                continue;
            }
            Map<String, Object> factMap = (Map<String, Object>) factItem;
            if (StringUtils.isNotBlank(MemoryJsonUtil.readString(factMap, "text"))) {
                return true;
            }
        }
        return false;
    }
}
