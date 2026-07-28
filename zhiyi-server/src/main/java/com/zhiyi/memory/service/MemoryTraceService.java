package com.zhiyi.memory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.memory.dao.CaptureDraftMapper;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.dao.SystemEventMapper;
import com.zhiyi.memory.domain.CaptureDraftView;
import com.zhiyi.memory.domain.MemoryTraceView;
import com.zhiyi.memory.entity.CaptureDraftEntity;
import com.zhiyi.memory.entity.KnowledgeEntity;
import com.zhiyi.memory.entity.MemoryOperationLogEntity;
import com.zhiyi.memory.entity.SystemEventEntity;
import com.zhiyi.memory.knowledge.CaptureService;
import com.zhiyi.memory.util.MemoryJsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Memory 闭环追踪服务：聚合 Capture → 路由/发布 → Recall 命中
 */
@Service
public class MemoryTraceService {

    private final CaptureService captureService;
    private final CaptureDraftMapper captureDraftMapper;
    private final SystemEventMapper systemEventMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final MemoryOperationLogService memoryOperationLogService;

    public MemoryTraceService(CaptureService captureService,
                              CaptureDraftMapper captureDraftMapper,
                              SystemEventMapper systemEventMapper,
                              KnowledgeMapper knowledgeMapper,
                              MemoryOperationLogService memoryOperationLogService) {
        this.captureService = captureService;
        this.captureDraftMapper = captureDraftMapper;
        this.systemEventMapper = systemEventMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.memoryOperationLogService = memoryOperationLogService;
    }

    /**
     * 无 draftId / knowledgeId 时，按工作空间最近一次 Recall 构建闭环追踪视图
     */
    public MemoryTraceView buildTraceFromLatestRecall(String workspaceId) {
        requireWorkspaceId(workspaceId);
        MemoryOperationLogEntity latestRecallLog = memoryOperationLogService.findLatestRecallLog(workspaceId);
        if (latestRecallLog == null) {
            return null;
        }
        Long topKnowledgeId = memoryOperationLogService.extractTopKnowledgeIdFromRecallLog(latestRecallLog);
        if (topKnowledgeId == null) {
            return null;
        }
        MemoryTraceView traceView = buildTrace(null, topKnowledgeId, workspaceId);
        traceView.setEntryType("recall");
        traceView.setEntryId(latestRecallLog.getId());
        prioritizeRecallHit(traceView, latestRecallLog.getId());
        return traceView;
    }

    /**
     * 按 draftId 或 knowledgeId 构建闭环追踪视图，至少传一个参数
     */
    public MemoryTraceView buildTrace(Long draftId, Long knowledgeId, String workspaceId) {
        requireWorkspaceId(workspaceId);
        if (draftId == null && knowledgeId == null) {
            throw new BusinessException(400, "draftId 与 knowledgeId 至少传一个");
        }

        MemoryTraceView traceView = new MemoryTraceView();
        CaptureDraftEntity draftEntity = null;
        KnowledgeEntity entryKnowledgeEntity = null;

        if (draftId != null) {
            draftEntity = requireDraftInWorkspace(draftId, workspaceId);
            traceView.setEntryType("draft");
            traceView.setEntryId(draftId);
        } else {
            entryKnowledgeEntity = requireKnowledgeInWorkspace(knowledgeId, workspaceId);
            traceView.setEntryType("knowledge");
            traceView.setEntryId(knowledgeId);
            if (entryKnowledgeEntity.getSourceCaptureDraftId() != null) {
                draftEntity = captureDraftMapper.selectById(entryKnowledgeEntity.getSourceCaptureDraftId());
                if (draftEntity != null && !workspaceId.equals(draftEntity.getWorkspaceId())) {
                    draftEntity = null;
                }
            }
            if (draftEntity == null) {
                draftEntity = findDraftByKnowledgeId(entryKnowledgeEntity.getId(), workspaceId);
            }
        }

        if (draftEntity != null) {
            CaptureDraftView draftView = captureService.getDraft(draftEntity.getId(), workspaceId);
            traceView.setCaptureDraft(draftView);
            traceView.setSystemEvent(buildSystemEventSummary(draftEntity.getEventId(), workspaceId));
        }

        List<MemoryTraceView.KnowledgeTraceItem> knowledgeList = buildKnowledgeList(
                draftEntity, entryKnowledgeEntity, workspaceId);
        traceView.setKnowledgeList(knowledgeList);

        Set<Long> knowledgeIdSet = memoryOperationLogService.collectKnowledgeIdsForTrace(
                knowledgeList,
                draftEntity == null ? null : draftEntity.getRoutedKnowledgeId(),
                draftEntity == null ? null : draftEntity.getKnowledgeId());
        traceView.setRecallHits(collectRecallHits(workspaceId, knowledgeIdSet));
        return traceView;
    }

    /**
     * 汇总与当前 trace 相关的 Recall 命中（按 knowledge 去重，保留最新一条）
     */
    private List<MemoryTraceView.RecallHitItem> collectRecallHits(String workspaceId, Set<Long> knowledgeIdSet) {
        List<MemoryTraceView.RecallHitItem> recallHitList = new ArrayList<MemoryTraceView.RecallHitItem>();
        if (knowledgeIdSet == null || knowledgeIdSet.isEmpty()) {
            return recallHitList;
        }
        Set<String> seenSessionSet = new LinkedHashSet<String>();
        for (Long targetKnowledgeId : knowledgeIdSet) {
            List<MemoryTraceView.RecallHitItem> hitList =
                    memoryOperationLogService.findRecallHits(workspaceId, targetKnowledgeId, 100);
            for (MemoryTraceView.RecallHitItem hitItem : hitList) {
                String sessionKey = hitItem.getRecallSession() == null
                        ? String.valueOf(hitItem.getLogId())
                        : hitItem.getRecallSession();
                if (seenSessionSet.add(sessionKey)) {
                    recallHitList.add(hitItem);
                }
            }
        }
        sortRecallHitsByTimeDesc(recallHitList);
        return recallHitList;
    }

    /**
     * 将指定 Recall 日志对应的命中记录置顶，便于默认展示最近一次 Recall
     */
    private void prioritizeRecallHit(MemoryTraceView traceView, Long recallLogId) {
        if (traceView == null || recallLogId == null) {
            return;
        }
        List<MemoryTraceView.RecallHitItem> recallHitList = traceView.getRecallHits();
        if (recallHitList == null || recallHitList.isEmpty()) {
            return;
        }
        sortRecallHitsByTimeDesc(recallHitList);
        for (int index = 0; index < recallHitList.size(); index++) {
            MemoryTraceView.RecallHitItem hitItem = recallHitList.get(index);
            if (hitItem != null && recallLogId.equals(hitItem.getLogId())) {
                if (index > 0) {
                    recallHitList.remove(index);
                    recallHitList.add(0, hitItem);
                }
                return;
            }
        }
    }

    /**
     * Recall 命中按时间倒序排列，最新在前
     */
    private void sortRecallHitsByTimeDesc(List<MemoryTraceView.RecallHitItem> recallHitList) {
        if (recallHitList == null || recallHitList.size() <= 1) {
            return;
        }
        Collections.sort(recallHitList, new Comparator<MemoryTraceView.RecallHitItem>() {
            @Override
            public int compare(MemoryTraceView.RecallHitItem left, MemoryTraceView.RecallHitItem right) {
                if (left.getCreateTime() == null && right.getCreateTime() == null) {
                    return 0;
                }
                if (left.getCreateTime() == null) {
                    return 1;
                }
                if (right.getCreateTime() == null) {
                    return -1;
                }
                return right.getCreateTime().compareTo(left.getCreateTime());
            }
        });
    }

    /**
     * 构建 trace 关联的知识列表：路由产出、直接发布、反向溯源
     */
    private List<MemoryTraceView.KnowledgeTraceItem> buildKnowledgeList(CaptureDraftEntity draftEntity,
                                                                         KnowledgeEntity entryKnowledgeEntity,
                                                                         String workspaceId) {
        List<MemoryTraceView.KnowledgeTraceItem> knowledgeList = new ArrayList<MemoryTraceView.KnowledgeTraceItem>();
        Set<Long> addedKnowledgeIdSet = new LinkedHashSet<Long>();

        if (entryKnowledgeEntity != null) {
            appendKnowledgeItem(knowledgeList, addedKnowledgeIdSet, entryKnowledgeEntity);
        }
        if (draftEntity != null) {
            if (draftEntity.getKnowledgeId() != null) {
                KnowledgeEntity knowledgeEntity = knowledgeMapper.selectById(draftEntity.getKnowledgeId());
                appendKnowledgeItem(knowledgeList, addedKnowledgeIdSet, knowledgeEntity);
            }
            if (draftEntity.getRoutedKnowledgeId() != null) {
                KnowledgeEntity routedEntity = knowledgeMapper.selectById(draftEntity.getRoutedKnowledgeId());
                appendKnowledgeItem(knowledgeList, addedKnowledgeIdSet, routedEntity);
            }
            appendKnowledgeBySourceDraft(knowledgeList, addedKnowledgeIdSet, draftEntity.getId(), workspaceId);
        }
        return knowledgeList;
    }

    /**
     * 按 source_capture_draft_id 查找由该草稿产出的全部 knowledge
     */
    private void appendKnowledgeBySourceDraft(List<MemoryTraceView.KnowledgeTraceItem> knowledgeList,
                                              Set<Long> addedKnowledgeIdSet,
                                              Long draftId, String workspaceId) {
        LambdaQueryWrapper<KnowledgeEntity> queryWrapper = new LambdaQueryWrapper<KnowledgeEntity>();
        queryWrapper.eq(KnowledgeEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeEntity::getSourceCaptureDraftId, draftId)
                .orderByDesc(KnowledgeEntity::getCreateTime);
        List<KnowledgeEntity> entityList = knowledgeMapper.selectList(queryWrapper);
        for (KnowledgeEntity entity : entityList) {
            appendKnowledgeItem(knowledgeList, addedKnowledgeIdSet, entity);
        }
    }

    private void appendKnowledgeItem(List<MemoryTraceView.KnowledgeTraceItem> knowledgeList,
                                     Set<Long> addedKnowledgeIdSet,
                                     KnowledgeEntity entity) {
        if (entity == null || entity.getId() == null || !addedKnowledgeIdSet.add(entity.getId())) {
            return;
        }
        MemoryTraceView.KnowledgeTraceItem knowledgeItem = new MemoryTraceView.KnowledgeTraceItem();
        knowledgeItem.setKnowledgeId(entity.getId());
        knowledgeItem.setKnowledgeType(entity.getKnowledgeType());
        knowledgeItem.setTitle(entity.getTitle());
        knowledgeItem.setLifecycleStatus(entity.getLifecycleStatus());
        knowledgeItem.setSourceCaptureDraftId(entity.getSourceCaptureDraftId());
        knowledgeItem.setRecallCount(entity.getRecallCount());
        knowledgeItem.setCreateTime(entity.getCreateTime());
        knowledgeItem.setUpdateTime(entity.getUpdateTime());
        knowledgeList.add(knowledgeItem);
    }

    /**
     * 通过 knowledge_id / routed_knowledge_id 反查 Capture 草稿
     */
    private CaptureDraftEntity findDraftByKnowledgeId(Long knowledgeId, String workspaceId) {
        LambdaQueryWrapper<CaptureDraftEntity> byApproved = new LambdaQueryWrapper<CaptureDraftEntity>();
        byApproved.eq(CaptureDraftEntity::getWorkspaceId, workspaceId)
                .eq(CaptureDraftEntity::getKnowledgeId, knowledgeId)
                .orderByDesc(CaptureDraftEntity::getCreateTime)
                .last("LIMIT 1");
        CaptureDraftEntity draftEntity = captureDraftMapper.selectOne(byApproved);
        if (draftEntity != null) {
            return draftEntity;
        }
        LambdaQueryWrapper<CaptureDraftEntity> byRouted = new LambdaQueryWrapper<CaptureDraftEntity>();
        byRouted.eq(CaptureDraftEntity::getWorkspaceId, workspaceId)
                .eq(CaptureDraftEntity::getRoutedKnowledgeId, knowledgeId)
                .orderByDesc(CaptureDraftEntity::getCreateTime)
                .last("LIMIT 1");
        return captureDraftMapper.selectOne(byRouted);
    }

    private MemoryTraceView.SystemEventSummary buildSystemEventSummary(Long eventId, String workspaceId) {
        if (eventId == null) {
            return null;
        }
        SystemEventEntity eventEntity = systemEventMapper.selectById(eventId);
        if (eventEntity == null || !workspaceId.equals(eventEntity.getWorkspaceId())) {
            return null;
        }
        MemoryTraceView.SystemEventSummary summary = new MemoryTraceView.SystemEventSummary();
        summary.setEventId(eventEntity.getId());
        summary.setEventType(eventEntity.getEventType());
        summary.setActor(eventEntity.getActor());
        summary.setRepository(eventEntity.getRepository());
        summary.setModule(eventEntity.getModule());
        summary.setProcessStatus(eventEntity.getProcessStatus());
        summary.setEventTime(eventEntity.getEventTime());
        summary.setCreateTime(eventEntity.getCreateTime());
        return summary;
    }

    private CaptureDraftEntity requireDraftInWorkspace(Long draftId, String workspaceId) {
        CaptureDraftEntity draftEntity = captureDraftMapper.selectById(draftId);
        if (draftEntity == null) {
            throw new BusinessException(404, "草稿不存在");
        }
        if (!workspaceId.equals(draftEntity.getWorkspaceId())) {
            throw new BusinessException(403, "无权访问该草稿");
        }
        return draftEntity;
    }

    private KnowledgeEntity requireKnowledgeInWorkspace(Long knowledgeId, String workspaceId) {
        KnowledgeEntity entity = knowledgeMapper.selectById(knowledgeId);
        if (entity == null) {
            throw new BusinessException(404, "知识不存在");
        }
        if (!workspaceId.equals(entity.getWorkspaceId())) {
            throw new BusinessException(403, "无权访问该知识");
        }
        return entity;
    }

    private void requireWorkspaceId(String workspaceId) {
        if (StringUtils.isBlank(workspaceId)) {
            throw new BusinessException(400, "当前未选择工作空间");
        }
    }
}
