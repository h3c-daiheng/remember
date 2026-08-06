package com.zhiyi.memory.engine;

import com.zhiyi.config.AiReviewProperties;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.dao.CaptureAiReviewMapper;
import com.zhiyi.memory.dao.CaptureDraftMapper;
import com.zhiyi.memory.domain.CaptureRouteSuggestion;
import com.zhiyi.memory.entity.CaptureAiReviewEntity;
import com.zhiyi.memory.entity.CaptureDraftEntity;
import com.zhiyi.memory.knowledge.CaptureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiReviewEngine 裁决策略单测：LLM 可用必调、reject/低置信/规则不一致/相似交叉冲突转人工、
 * 高置信无冲突自动执行；LLM 不可用回退规则。
 */
class AiReviewEngineTest {

    private AiReviewProperties properties;
    private CaptureAiReviewMapper aiReviewMapper;
    private CaptureDraftMapper draftMapper;
    private SimilarKnowledgeFinder similarFinder;
    private CaptureRouteEngine routeEngine;
    private CaptureService captureService;
    private AiReviewEngine engine;

    @BeforeEach
    void setUp() {
        properties = new AiReviewProperties();
        aiReviewMapper = Mockito.mock(CaptureAiReviewMapper.class);
        draftMapper = Mockito.mock(CaptureDraftMapper.class);
        similarFinder = Mockito.mock(SimilarKnowledgeFinder.class);
        routeEngine = Mockito.mock(CaptureRouteEngine.class);
        captureService = Mockito.mock(CaptureService.class);
        engine = new AiReviewEngine(properties, aiReviewMapper, draftMapper,
                similarFinder, routeEngine, captureService);

        CaptureDraftEntity draftEntity = Mockito.mock(CaptureDraftEntity.class);
        when(draftEntity.getId()).thenReturn(1L);
        when(draftEntity.getWorkspaceId()).thenReturn("ws-1");
        when(draftEntity.getEventId()).thenReturn(100L);
        when(draftEntity.getReviewStatus()).thenReturn(MemoryConstants.REVIEW_PENDING);
        when(draftEntity.getDraftJson()).thenReturn("{\"title\":\"测试草稿\",\"facts\":[]}");
        when(draftMapper.selectById(1L)).thenReturn(draftEntity);

        when(similarFinder.findSimilar(any(), anyString())).thenReturn(Collections.emptyList());
        when(similarFinder.hasSimilarHit(any(), anyDouble())).thenReturn(false);
    }

    private CaptureRouteSuggestion suggestion(String action, double confidence,
                                              String source, String targetType) {
        CaptureRouteSuggestion s = new CaptureRouteSuggestion();
        s.setRecommendedAction(action);
        s.setConfidence(confidence);
        s.setSuggestionSource(source);
        s.setTargetKnowledgeType(targetType);
        return s;
    }

    /** 捕获写回的审查记录 */
    private CaptureAiReviewEntity captureReviewEntity() {
        ArgumentCaptor<CaptureAiReviewEntity> captor = ArgumentCaptor.forClass(CaptureAiReviewEntity.class);
        verify(aiReviewMapper).updateById(captor.capture());
        return captor.getValue();
    }

    /** 捕获写回的审查记录决策 */
    private String captureDecision() {
        return captureReviewEntity().getDecision();
    }

    @Test
    void llmReject_escalatesHumanAndNoAutoApprove() {
        when(routeEngine.suggest(any(), anyString(), eq(true)))
                .thenReturn(suggestion(MemoryConstants.REVIEW_ACTION_REJECT, 0.9D, "llm", null));
        engine.reviewDraft(1L, 9L);
        assertEquals(MemoryConstants.AI_REVIEW_DECISION_ESCALATE_HUMAN, captureDecision());
        verify(captureService, never()).approveDraft(anyLong(), any(), anyLong(), anyString());
    }

    @Test
    void lowConfidence_escalatesHuman() {
        when(routeEngine.suggest(any(), anyString(), eq(true)))
                .thenReturn(suggestion(MemoryConstants.REVIEW_ACTION_APPROVE_EXPERIENCE,
                        0.5D, "hybrid", MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE));
        engine.reviewDraft(1L, 9L);
        assertEquals(MemoryConstants.AI_REVIEW_DECISION_ESCALATE_HUMAN, captureDecision());
    }

    @Test
    void lowConfidence_reasonCarriesConfidenceAndAction() {
        when(routeEngine.suggest(any(), anyString(), eq(true)))
                .thenReturn(suggestion(MemoryConstants.REVIEW_ACTION_APPROVE_EXPERIENCE,
                        0.5D, "hybrid", MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE));
        engine.reviewDraft(1L, 9L);
        String message = captureReviewEntity().getErrorMessage();
        assertTrue(message.contains("置信度 50%"));
        assertTrue(message.contains("发布为经验"));
    }

    @Test
    void llmDiffersFromRule_escalatesHuman() {
        when(routeEngine.suggest(any(), anyString(), eq(true)))
                .thenReturn(suggestion(MemoryConstants.REVIEW_ACTION_APPROVE_EXPERIENCE,
                        0.9D, "llm", MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE));
        engine.reviewDraft(1L, 9L);
        assertEquals(MemoryConstants.AI_REVIEW_DECISION_ESCALATE_HUMAN, captureDecision());
    }

    @Test
    void extremelySimilar_escalatesBeforeLlm() {
        when(similarFinder.hasSimilarHit(any(), eq(properties.getSimilarDuplicateThreshold())))
                .thenReturn(true);
        engine.reviewDraft(1L, 9L);
        assertEquals(MemoryConstants.AI_REVIEW_DECISION_ESCALATE_HUMAN, captureDecision());
        verify(routeEngine, never()).suggest(any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void approveWithSimilar_escalatesAsDuplicate() {
        when(similarFinder.hasSimilarHit(any(), eq(properties.getSimilarThreshold())))
                .thenReturn(true);
        when(routeEngine.suggest(any(), anyString(), eq(true)))
                .thenReturn(suggestion(MemoryConstants.REVIEW_ACTION_APPROVE_EXPERIENCE,
                        0.9D, "hybrid", MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE));
        engine.reviewDraft(1L, 9L);
        assertEquals(MemoryConstants.AI_REVIEW_DECISION_ESCALATE_HUMAN, captureDecision());
        verify(captureService, never()).approveDraft(anyLong(), any(), anyLong(), anyString());
    }

    @Test
    void hybridHighConfidenceNoSimilar_autoApproves() {
        when(routeEngine.suggest(any(), anyString(), eq(true)))
                .thenReturn(suggestion(MemoryConstants.REVIEW_ACTION_APPROVE_EXPERIENCE,
                        0.9D, "hybrid", MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE));
        engine.reviewDraft(1L, 9L);
        verify(captureService).approveDraft(eq(1L), any(), eq(9L), eq("ws-1"));
    }

    @Test
    void ruleFallbackHighConfidence_autoApproves() {
        when(routeEngine.suggest(any(), anyString(), eq(true)))
                .thenReturn(suggestion(MemoryConstants.REVIEW_ACTION_APPROVE_EXPERIENCE,
                        0.9D, "rule", MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE));
        engine.reviewDraft(1L, 9L);
        verify(captureService).approveDraft(eq(1L), any(), eq(9L), eq("ws-1"));
    }
}
