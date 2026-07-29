package com.zhiyi.memory.knowledge;

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

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceExportTest {

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

    @Test
    void exportMarkdown_empty_workspace_should_return_header_only() {
        given(knowledgeMapper.selectList(any())).willReturn(Collections.<KnowledgeEntity>emptyList());

        String md = knowledgeService.exportMarkdown("1", "空空间", "all", "2026-07-28");

        assertTrue(md.contains("# 工作空间「空空间」知识导出"), md);
        assertTrue(md.contains("共 0 条"), md);
    }

    @Test
    void exportMarkdown_should_include_published_knowledge_title() {
        KnowledgeEntity entity = new KnowledgeEntity();
        entity.setId(7L);
        entity.setWorkspaceId("1");
        entity.setKnowledgeType("experience");
        entity.setLifecycleStatus(1);
        entity.setTitle("经验A");
        entity.setCreatorId(1L);
        given(knowledgeMapper.selectList(any())).willReturn(Collections.singletonList(entity));
        given(knowledgeFactMapper.selectList(any())).willReturn(Collections.emptyList());
        given(knowledgeArtifactMapper.selectList(any())).willReturn(Collections.emptyList());
        given(knowledgeTagMapper.selectTagNames(7L)).willReturn(Collections.emptyList());

        String md = knowledgeService.exportMarkdown("1", "空间", "all", "2026-07-28");

        assertTrue(md.contains("经验A"), md);
    }
}
