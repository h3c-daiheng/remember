package com.zhiyi.memory.engine;

import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.context.ContextNormalizer;
import com.zhiyi.memory.context.ContextQueryBuilder;
import com.zhiyi.memory.dao.KnowledgeArtifactMapper;
import com.zhiyi.memory.domain.KnowledgeAggregate;
import com.zhiyi.memory.domain.RecallContext;
import com.zhiyi.memory.domain.RecallResponse;
import com.zhiyi.memory.domain.RecallResult;
import com.zhiyi.memory.knowledge.KnowledgeService;
import com.zhiyi.memory.retrieval.GraphRetrievalClient;
import com.zhiyi.memory.retrieval.RetrievalEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * RecallEngine 可靠性透出测试：验证 reliability 写入 RecallItem，promptBlock 含档位标注与使用指引。
 */
@ExtendWith(MockitoExtension.class)
class RecallEngineTest {

    @Mock
    private ContextNormalizer contextNormalizer;

    @Mock
    private ContextQueryBuilder contextQueryBuilder;

    @Mock
    private RetrievalEngine retrievalEngine;

    @Mock
    private GraphRetrievalClient graphRetrievalClient;

    @Mock
    private RankingEngine rankingEngine;

    @Mock
    private KnowledgeService knowledgeService;

    @Mock
    private KnowledgeArtifactMapper knowledgeArtifactMapper;

    @InjectMocks
    private RecallEngine recallEngine;

    @Test
    void recall_fillsReliabilityAndAnnotatesPromptBlock() {
        RecallContext context = new RecallContext();
        context.setTask("修复登录 bug");
        context.setWorkspaceId("ws1");
        context.setKnowledgeTypes(Arrays.asList("rule", "experience"));

        when(contextNormalizer.normalize(any())).thenReturn(context);
        when(contextQueryBuilder.buildQueryText(any())).thenReturn("query");
        when(knowledgeService.listPublishedKnowledgeIds(anyString(), any()))
                .thenReturn(Arrays.asList(1L));
        when(retrievalEngine.toAllowedKnowledgeIdSet(any()))
                .thenReturn(new HashSet<Long>(Arrays.asList(1L)));

        RetrievalEngine.RetrievalCandidate candidate = new RetrievalEngine.RetrievalCandidate();
        candidate.setKnowledgeId(1L);
        candidate.setSimilarityScore(0.9D);
        when(retrievalEngine.search(anyString(), any(), anyInt(), any()))
                .thenReturn(Arrays.asList(candidate));

        KnowledgeAggregate aggregate = new KnowledgeAggregate();
        aggregate.setId(1L);
        aggregate.setTitle("经验A");
        aggregate.setKnowledgeType(MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE);
        aggregate.setLifecycleStatus(MemoryConstants.LIFECYCLE_PUBLISHED);
        aggregate.setFacts(Collections.emptyList());
        when(knowledgeService.getDetail(anyLong(), anyString())).thenReturn(aggregate);
        when(knowledgeArtifactMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Double> breakdown = new HashMap<String, Double>();
        breakdown.put("feedback", 0.8D);
        breakdown.put("freshness", 0.5D);
        breakdown.put("trust", 0.6D);
        RankingEngine.RankingScore rankingScore = new RankingEngine.RankingScore();
        rankingScore.setFinalScore(0.5D);
        rankingScore.setBreakdown(breakdown);
        when(rankingEngine.rank(any(), any(), anyDouble(), any(), anyDouble()))
                .thenReturn(rankingScore);
        when(rankingEngine.calculateReliability(any(), any())).thenReturn(
                new RankingEngine.ReliabilityResult(MemoryConstants.RELIABILITY_LOW,
                        Arrays.asList("freshness=0.50")));

        RecallResult recallResult = recallEngine.recall(context);
        RecallResponse response = recallResult.getResponse();

        assertEquals(1, response.getItems().size());
        RecallResponse.RecallItem item = response.getItems().get(0);
        assertEquals("经验A", item.getTitle());
        assertEquals(MemoryConstants.RELIABILITY_LOW, item.getReliability());
        assertNotNull(item.getReliabilityReason());

        String promptBlock = response.getPromptBlock();
        assertNotNull(response.getSessionId());
        assertTrue(promptBlock.contains("可靠性分三档"), "promptBlock 应含可靠性使用指引");
        assertTrue(promptBlock.contains("🔴"), "promptBlock 应含低置信档位标注");
        assertTrue(promptBlock.contains("memory_feedback"), "promptBlock 应引导 Agent 回填 feedback");
        assertTrue(promptBlock.contains("经验A"), "promptBlock 应含经验标题");
    }
}
