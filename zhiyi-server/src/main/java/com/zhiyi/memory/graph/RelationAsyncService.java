package com.zhiyi.memory.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 关系建边异步服务：发布经验后后台触发，不阻塞主流程
 */
@Service
public class RelationAsyncService {

    private static final Logger log = LoggerFactory.getLogger(RelationAsyncService.class);

    private final RelationEngine relationEngine;

    public RelationAsyncService(RelationEngine relationEngine) {
        this.relationEngine = relationEngine;
    }

    /**
     * 异步重建指定经验的自动关系边
     */
    @Async("relationExecutor")
    public void buildRelationsAsync(Long knowledgeId, String workspaceId) {
        try {
            relationEngine.buildRelations(knowledgeId, workspaceId);
        } catch (Exception exception) {
            log.warn("异步建边失败 knowledgeId={} workspaceId={} message={}",
                    knowledgeId, workspaceId, exception.getMessage(), exception);
        }
    }

    /**
     * 异步补建其他节点指向指定经验的规则类自动边（经验重新启用后调用）
     */
    @Async("relationExecutor")
    public void rebuildInboundAutoRelationsAsync(Long targetKnowledgeId, String workspaceId) {
        try {
            relationEngine.rebuildInboundAutoRelations(targetKnowledgeId, workspaceId);
        } catch (Exception exception) {
            log.warn("异步入向建边失败 targetKnowledgeId={} workspaceId={} message={}",
                    targetKnowledgeId, workspaceId, exception.getMessage(), exception);
        }
    }
}
