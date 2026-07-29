package com.zhiyi.memory.knowledge;

import com.zhiyi.memory.domain.KnowledgeBatchDeleteResult;
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
import com.zhiyi.workspace.WorkspaceMemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceBatchDeleteTest {

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

    @Spy @InjectMocks private KnowledgeService knowledgeService;

    @BeforeEach
    void wireSelfReference() {
        // deleteByIds 通过 self 代理调用 deleteKnowledge;单元测试中无 Spring 容器,
        // 将 self 字段指向 spy 自身,使其调用经 spy 桩,真实权限校验 + mock mapper 逻辑被执行。
        ReflectionTestUtils.setField(knowledgeService, "self", knowledgeService);
    }

    @Test
    void deleteByIds_should_delete_own_and_skip_others() {
        KnowledgeEntity mine = new KnowledgeEntity();
        mine.setId(1L);
        mine.setWorkspaceId("1");
        mine.setCreatorId(99L);
        KnowledgeEntity others = new KnowledgeEntity();
        others.setId(2L);
        others.setWorkspaceId("1");
        others.setCreatorId(5L);
        given(knowledgeMapper.selectById(1L)).willReturn(mine);
        given(knowledgeMapper.selectById(2L)).willReturn(others);

        List<Long> ids = Arrays.asList(1L, 2L);
        // editor 删自己的可以,删他人不行
        KnowledgeBatchDeleteResult result = knowledgeService.deleteByIds(ids, "1", 99L, WorkspaceMemberRole.EDITOR);

        assertEquals(2, result.getTotal());
        assertEquals(1, result.getDeleted());
        assertEquals(1, result.getFailed());
        verify(knowledgeMapper).deleteById(1L);
        verify(knowledgeMapper, never()).deleteById(2L);
    }

    @Test
    void deleteByIds_should_record_missing_id_as_failure() {
        given(knowledgeMapper.selectById(anyLong())).willReturn(null);

        KnowledgeBatchDeleteResult result = knowledgeService.deleteByIds(
                Arrays.asList(9L), "1", 99L, WorkspaceMemberRole.OWNER);

        assertEquals(1, result.getTotal());
        assertEquals(0, result.getDeleted());
        assertEquals(1, result.getFailed());
        verify(knowledgeMapper, never()).deleteById(anyLong());
    }
}
