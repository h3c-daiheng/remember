package com.zhiyi.memory.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.zhiyi.memory.entity.MemoryFeedbackEntity;
import com.zhiyi.memory.graph.CascadeValidationService;
import com.zhiyi.memory.timeline.KnowledgeTimelineService;
import com.zhiyi.workspace.WorkspaceMemberRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceDeleteTest {

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
    void deleteKnowledge_should_clear_fact_artifact_tag_and_feedback_children() {
        Long knowledgeId = 10L;
        String workspaceId = "1";
        KnowledgeEntity entity = new KnowledgeEntity();
        entity.setId(knowledgeId);
        entity.setWorkspaceId(workspaceId);
        entity.setCreatorId(99L);
        given(knowledgeMapper.selectById(knowledgeId)).willReturn(entity);

        knowledgeService.deleteKnowledge(knowledgeId, workspaceId, 99L, WorkspaceMemberRole.OWNER);

        verify(knowledgeFactMapper).delete(any(LambdaQueryWrapper.class));
        verify(knowledgeArtifactMapper).delete(any(LambdaQueryWrapper.class));
        verify(knowledgeTagMapper).deleteByKnowledgeId(knowledgeId);
        verify(memoryFeedbackMapper).delete(any(LambdaQueryWrapper.class));
        verify(relationEngine).deleteRelationsByKnowledgeId(knowledgeId, workspaceId);
        verify(knowledgeMapper).deleteById(knowledgeId);
    }
}
