package com.zhiyi.memory.engine;

import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.dao.MemoryFeedbackMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RankingEngine 可靠性评估单元测试：覆盖三档边界与短板取档。
 */
class RankingEngineTest {

    private RankingEngine rankingEngine;

    @BeforeEach
    void setUp() {
        rankingEngine = new RankingEngine(Mockito.mock(MemoryFeedbackMapper.class));
    }

    private Map<String, Double> breakdown(double feedback, double freshness, double trust) {
        Map<String, Double> map = new HashMap<String, Double>();
        map.put("feedback", feedback);
        map.put("freshness", freshness);
        map.put("trust", trust);
        return map;
    }

    @Test
    void allFactorsHigh_ruleType_returnsReliableWithoutReasons() {
        RankingEngine.ReliabilityResult result = rankingEngine.calculateReliability(
                breakdown(1.0D, 0.9D, 1.0D), MemoryConstants.KNOWLEDGE_TYPE_RULE);
        assertEquals(MemoryConstants.RELIABILITY_RELIABLE, result.getLevel());
        assertTrue(result.getReasons().isEmpty());
    }

    @Test
    void feedbackBelowReliable_returnsUncertain() {
        RankingEngine.ReliabilityResult result = rankingEngine.calculateReliability(
                breakdown(0.8D, 0.9D, 1.0D), MemoryConstants.KNOWLEDGE_TYPE_RULE);
        assertEquals(MemoryConstants.RELIABILITY_UNCERTAIN, result.getLevel());
        assertTrue(result.getReasons().stream().anyMatch(reason -> reason.startsWith("feedback=")));
    }

    @Test
    void feedbackBelowUncertain_returnsLow() {
        RankingEngine.ReliabilityResult result = rankingEngine.calculateReliability(
                breakdown(0.4D, 0.9D, 1.0D), MemoryConstants.KNOWLEDGE_TYPE_RULE);
        assertEquals(MemoryConstants.RELIABILITY_LOW, result.getLevel());
    }

    @Test
    void shortBoard_lowBeatsUncertain() {
        // freshness=0.5 -> low，feedback=0.8 -> uncertain，取最严 low
        RankingEngine.ReliabilityResult result = rankingEngine.calculateReliability(
                breakdown(0.8D, 0.5D, 1.0D), MemoryConstants.KNOWLEDGE_TYPE_RULE);
        assertEquals(MemoryConstants.RELIABILITY_LOW, result.getLevel());
    }

    @Test
    void freshnessBoundary_reliableAtThreshold() {
        // freshness=0.8 恰好 >= 可靠阈值
        RankingEngine.ReliabilityResult result = rankingEngine.calculateReliability(
                breakdown(1.0D, 0.8D, 1.0D), MemoryConstants.KNOWLEDGE_TYPE_RULE);
        assertEquals(MemoryConstants.RELIABILITY_RELIABLE, result.getLevel());
    }

    @Test
    void experienceType_addsVerifyHintEvenWhenReliable() {
        RankingEngine.ReliabilityResult result = rankingEngine.calculateReliability(
                breakdown(1.0D, 0.9D, 1.0D), MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE);
        assertEquals(MemoryConstants.RELIABILITY_RELIABLE, result.getLevel());
        assertTrue(result.getReasons().stream().anyMatch(reason -> reason.contains("experience")));
    }
}
