package com.zhiyi.memory.knowledge;

import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.domain.KnowledgeBatchPublishResult;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceBatchPublishTest {

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
        // publishByIds 通过 self 代理调用 publish;单元测试中无 Spring 容器,
        // 将 self 字段指向 spy 自身,使其调用经 spy 桩,真实权限校验 + mock mapper 逻辑被执行。
        ReflectionTestUtils.setField(knowledgeService, "self", knowledgeService);
    }

    @Test
    void publishByIds_should_publish_own_and_skip_others() {
        // viewer 仅可发布本人草稿:本人成功,他人因权限不足计入 failure
        KnowledgeEntity mine = new KnowledgeEntity();
        mine.setId(1L);
        mine.setWorkspaceId("1");
        mine.setCreatorId(99L);
        mine.setLifecycleStatus(MemoryConstants.LIFECYCLE_DRAFT);
        KnowledgeEntity others = new KnowledgeEntity();
        others.setId(2L);
        others.setWorkspaceId("1");
        others.setCreatorId(5L);
        others.setLifecycleStatus(MemoryConstants.LIFECYCLE_DRAFT);
        given(knowledgeMapper.selectById(1L)).willReturn(mine);
        given(knowledgeMapper.selectById(2L)).willReturn(others);
        // indexKnowledge -> loadAggregate 依赖,返回空集合避免 NPE
        given(knowledgeFactMapper.selectList(any())).willReturn(Collections.emptyList());
        given(knowledgeArtifactMapper.selectList(any())).willReturn(Collections.emptyList());
        given(knowledgeTagMapper.selectTagNames(any())).willReturn(Collections.emptyList());

        List<Long> ids = Arrays.asList(1L, 2L);
        KnowledgeBatchPublishResult result = knowledgeService.publishByIds(ids, "1", 99L, WorkspaceMemberRole.VIEWER);

        assertEquals(2, result.getTotal());
        assertEquals(1, result.getPublished());
        assertEquals(1, result.getFailed());
        verify(knowledgeMapper).updateById(mine);
        verify(knowledgeMapper, never()).updateById(others);
    }

    @Test
    void publishByIds_should_record_missing_id_as_failure() {
        given(knowledgeMapper.selectById(any())).willReturn(null);

        KnowledgeBatchPublishResult result = knowledgeService.publishByIds(
                Arrays.asList(9L), "1", 99L, WorkspaceMemberRole.OWNER);

        assertEquals(1, result.getTotal());
        assertEquals(0, result.getPublished());
        assertEquals(1, result.getFailed());
        verify(knowledgeMapper, never()).updateById(any(KnowledgeEntity.class));
    }

    @Test
    void publishByIds_should_skip_deprecated() {
        // 已失效经验 requireMutableKnowledge 拒绝,计入 failure 不影响其他条目
        KnowledgeEntity deprecated = new KnowledgeEntity();
        deprecated.setId(3L);
        deprecated.setWorkspaceId("1");
        deprecated.setCreatorId(99L);
        deprecated.setLifecycleStatus(MemoryConstants.LIFECYCLE_DEPRECATED);
        given(knowledgeMapper.selectById(3L)).willReturn(deprecated);

        KnowledgeBatchPublishResult result = knowledgeService.publishByIds(
                Arrays.asList(3L), "1", 99L, WorkspaceMemberRole.OWNER);

        assertEquals(1, result.getTotal());
        assertEquals(0, result.getPublished());
        assertEquals(1, result.getFailed());
        verify(knowledgeMapper, never()).updateById(any(KnowledgeEntity.class));
    }
}
