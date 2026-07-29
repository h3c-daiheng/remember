package com.zhiyi.workspace;

import com.zhiyi.common.BusinessException;
import com.zhiyi.controller.WorkspaceService;
import com.zhiyi.dao.ApiKeyMapper;
import com.zhiyi.dao.SysUserMapper;
import com.zhiyi.dao.UsageDailyMapper;
import com.zhiyi.dao.WorkspaceGovernanceConfigMapper;
import com.zhiyi.dao.WorkspaceMapper;
import com.zhiyi.dao.WorkspaceMemberMapper;
import com.zhiyi.domain.entity.WorkspaceEntity;
import com.zhiyi.domain.entity.WorkspaceMemberEntity;
import com.zhiyi.memory.dao.CaptureAiReviewMapper;
import com.zhiyi.memory.dao.CaptureDraftMapper;
import com.zhiyi.memory.dao.GovernanceIssueMapper;
import com.zhiyi.memory.dao.GovernanceScanBatchMapper;
import com.zhiyi.memory.dao.KnowledgeArtifactMapper;
import com.zhiyi.memory.dao.KnowledgeFactMapper;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.dao.KnowledgeRelationMapper;
import com.zhiyi.memory.dao.KnowledgeTagMapper;
import com.zhiyi.memory.dao.KnowledgeTimelineMapper;
import com.zhiyi.memory.dao.KnowledgeVectorRefMapper;
import com.zhiyi.memory.dao.MemoryFeedbackMapper;
import com.zhiyi.memory.dao.MemoryOperationLogMapper;
import com.zhiyi.memory.dao.SystemEventMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock private WorkspaceMapper workspaceMapper;
    @Mock private WorkspaceMemberMapper workspaceMemberMapper;
    @Mock private WorkspaceGovernanceConfigMapper workspaceGovernanceConfigMapper;
    @Mock private ApiKeyMapper apiKeyMapper;
    @Mock private UsageDailyMapper usageDailyMapper;
    @Mock private KnowledgeMapper knowledgeMapper;
    @Mock private KnowledgeFactMapper knowledgeFactMapper;
    @Mock private KnowledgeArtifactMapper knowledgeArtifactMapper;
    @Mock private KnowledgeTagMapper knowledgeTagMapper;
    @Mock private KnowledgeVectorRefMapper knowledgeVectorRefMapper;
    @Mock private KnowledgeRelationMapper knowledgeRelationMapper;
    @Mock private KnowledgeTimelineMapper knowledgeTimelineMapper;
    @Mock private MemoryFeedbackMapper memoryFeedbackMapper;
    @Mock private MemoryOperationLogMapper memoryOperationLogMapper;
    @Mock private CaptureDraftMapper captureDraftMapper;
    @Mock private CaptureAiReviewMapper captureAiReviewMapper;
    @Mock private SystemEventMapper systemEventMapper;
    @Mock private GovernanceIssueMapper governanceIssueMapper;
    @Mock private GovernanceScanBatchMapper governanceScanBatchMapper;
    @Mock private SysUserMapper sysUserMapper;

    @InjectMocks private WorkspaceService workspaceService;

    @Test
    void deleteWorkspace_should_require_owner() {
        // C1:校验目标空间成员资格。非成员(即使当前激活空间角色为 OWNER)禁止删除,先于 selectById,无需 stub workspaceMapper
        given(workspaceMemberMapper.selectOne(any())).willReturn(null);
        assertThrows(BusinessException.class,
                () -> workspaceService.deleteWorkspace("1", 2L, WorkspaceMemberRole.OWNER, "默认"));
    }

    @Test
    void deleteWorkspace_should_require_confirm_name_match() {
        given(workspaceMemberMapper.selectOne(any())).willReturn(ownerMember());
        given(workspaceMapper.selectById("1")).willReturn(workspace("默认", "1"));
        assertThrows(BusinessException.class,
                () -> workspaceService.deleteWorkspace("1", 1L, WorkspaceMemberRole.OWNER, "错的名称"));
    }

    @Test
    void deleteWorkspace_should_cascade_delete_all_tables() {
        given(workspaceMemberMapper.selectOne(any())).willReturn(ownerMember());
        given(workspaceMapper.selectById("1")).willReturn(workspace("默认", "1"));
        // 让 knowledge 子表清理分支进入(否则 IN () 空集跳过)
        given(knowledgeMapper.selectIdsByWorkspace("1")).willReturn(java.util.Collections.singletonList(1L));

        workspaceService.deleteWorkspace("1", 1L, WorkspaceMemberRole.OWNER, "默认");

        verify(governanceIssueMapper).delete(any());
        verify(governanceScanBatchMapper).delete(any());
        verify(captureAiReviewMapper).delete(any());
        verify(captureDraftMapper).delete(any());
        verify(systemEventMapper).delete(any());
        verify(knowledgeRelationMapper).delete(any());
        verify(knowledgeTimelineMapper).delete(any());
        verify(knowledgeTagMapper).deleteByWorkspace("1");
        verify(knowledgeFactMapper).delete(any());
        verify(knowledgeArtifactMapper).delete(any());
        verify(knowledgeVectorRefMapper).delete(any());
        verify(memoryFeedbackMapper).delete(any());
        verify(knowledgeMapper).deleteByWorkspacePhysical("1");
        verify(memoryOperationLogMapper).delete(any());
        verify(usageDailyMapper).delete(any());
        verify(apiKeyMapper).delete(any());
        verify(workspaceGovernanceConfigMapper).delete(any());
        verify(workspaceMemberMapper).delete(any());
        verify(sysUserMapper).clearLastWorkspace("1");
        verify(workspaceMapper).deleteById("1");
    }

    private WorkspaceEntity workspace(String name, String id) {
        WorkspaceEntity ws = new WorkspaceEntity();
        ws.setId(id);
        ws.setWorkspaceName(name);
        ws.setOrganizationId(1L);
        return ws;
    }

    private WorkspaceMemberEntity ownerMember() {
        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.setMemberRole(WorkspaceMemberRole.OWNER);
        return member;
    }
}
