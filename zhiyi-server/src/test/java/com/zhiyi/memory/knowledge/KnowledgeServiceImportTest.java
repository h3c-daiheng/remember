package com.zhiyi.memory.knowledge;

import com.zhiyi.memory.domain.KnowledgeImportResult;
import com.zhiyi.memory.entity.KnowledgeEntity;
import com.zhiyi.memory.retrieval.RetrievalEngine;
import com.zhiyi.memory.graph.RelationAsyncService;
import com.zhiyi.memory.graph.RelationEngine;
import com.zhiyi.memory.context.ContextNormalizer;
import com.zhiyi.memory.dao.KnowledgeArtifactMapper;
import com.zhiyi.memory.dao.KnowledgeFactMapper;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.dao.KnowledgeTagMapper;
import com.zhiyi.memory.dao.MemoryFeedbackMapper;
import com.zhiyi.memory.graph.CascadeValidationService;
import com.zhiyi.memory.timeline.KnowledgeTimelineService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceImportTest {

    @Mock private KnowledgeMapper knowledgeMapper;
    @Mock private KnowledgeFactMapper knowledgeFactMapper;
    @Mock private KnowledgeArtifactMapper knowledgeArtifactMapper;
    @Mock private KnowledgeTagMapper knowledgeTagMapper;
    @Mock private MemoryFeedbackMapper memoryFeedbackMapper;
    @Mock private RetrievalEngine retrievalEngine;
    @Mock private ContextNormalizer contextNormalizer;
    @Mock private RelationAsyncService relationAsyncService;
    @Mock private RelationEngine relationEngine;
    @Mock private KnowledgeTimelineService knowledgeTimelineService;
    @Mock private CascadeValidationService cascadeValidationService;
    @Mock private com.zhiyi.service.UserProfileService userProfileService;

    @InjectMocks private KnowledgeService knowledgeService;

    private static final String GOOD = ""
            + "<!-- KNOWLEDGE START -->\n---\ntitle: \"t1\"\nknowledgeType: experience\n---\n\n"
            + "## Facts\n\n### observation\n内容\n<!-- KNOWLEDGE END -->\n";

    @Test
    void importMarkdown_should_create_draft_for_each_valid_block() {
        given(knowledgeMapper.insert(any(KnowledgeEntity.class))).willAnswer(invocation -> {
            ((KnowledgeEntity) invocation.getArgument(0)).setId(1L);
            return 1;
        });

        KnowledgeImportResult result = knowledgeService.importMarkdown(GOOD + GOOD, 99L, "1");

        assertEquals(2, result.getTotal());
        assertEquals(2, result.getImported());
        assertEquals(0, result.getFailed());
        verify(knowledgeMapper, times(2)).insert(any(KnowledgeEntity.class));
    }

    @Test
    void importMarkdown_should_collect_invalid_block_as_failure() {
        String bad = "<!-- KNOWLEDGE START -->\n---\nknowledgeType: experience\n---\n\n## Facts\n\n### observation\nx\n<!-- KNOWLEDGE END -->\n";
        given(knowledgeMapper.insert(any(KnowledgeEntity.class))).willAnswer(invocation -> {
            ((KnowledgeEntity) invocation.getArgument(0)).setId(1L);
            return 1;
        });

        KnowledgeImportResult result = knowledgeService.importMarkdown(GOOD + bad, 99L, "1");

        assertEquals(2, result.getTotal());
        assertEquals(1, result.getImported());
        assertEquals(1, result.getFailed());
        // parseBlock 抛出「知识块格式错误:缺少 title」,reason 文案以源码为准
        assertTrue(result.getFailures().get(0).getReason().contains("title"),
                result.getFailures().get(0).getReason());
        // 合法块仍被创建
        verify(knowledgeMapper, times(1)).insert(any(KnowledgeEntity.class));
    }
}
