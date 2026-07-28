package com.zhiyi.memory.timeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.domain.vo.KnowledgeTimelineItemVO;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.dao.KnowledgeTimelineMapper;
import com.zhiyi.memory.entity.KnowledgeEntity;
import com.zhiyi.memory.entity.KnowledgeTimelineEntity;
import com.zhiyi.memory.util.MemoryJsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 经验时间线服务：记录发布 / 失效 / 替代演进等治理事件
 */
@Service
public class KnowledgeTimelineService {

    private final KnowledgeTimelineMapper knowledgeTimelineMapper;
    private final KnowledgeMapper knowledgeMapper;

    public KnowledgeTimelineService(KnowledgeTimelineMapper knowledgeTimelineMapper,
                                    KnowledgeMapper knowledgeMapper) {
        this.knowledgeTimelineMapper = knowledgeTimelineMapper;
        this.knowledgeMapper = knowledgeMapper;
    }

    /**
     * 查询指定经验的时间线，按时间倒序
     */
    public List<KnowledgeTimelineItemVO> listTimeline(Long knowledgeId, String workspaceId) {
        LambdaQueryWrapper<KnowledgeTimelineEntity> queryWrapper = new LambdaQueryWrapper<KnowledgeTimelineEntity>();
        queryWrapper.eq(KnowledgeTimelineEntity::getKnowledgeId, knowledgeId)
                .eq(KnowledgeTimelineEntity::getWorkspaceId, workspaceId)
                .orderByDesc(KnowledgeTimelineEntity::getCreateTime)
                .orderByDesc(KnowledgeTimelineEntity::getId);
        List<KnowledgeTimelineEntity> entityList = knowledgeTimelineMapper.selectList(queryWrapper);

        Map<Long, String> titleCache = new HashMap<Long, String>();
        List<KnowledgeTimelineItemVO> itemViewList = new ArrayList<KnowledgeTimelineItemVO>();
        for (KnowledgeTimelineEntity entity : entityList) {
            itemViewList.add(toItemView(entity, titleCache));
        }
        return itemViewList;
    }

    /**
     * 记录发布事件
     */
    public void recordPublish(Long knowledgeId, String workspaceId, Long operatorId) {
        recordEvent(knowledgeId, workspaceId, TimelineConstants.EVENT_PUBLISH, null, operatorId,
                "经验已发布", null);
    }

    /**
     * 记录下架 / 失效事件
     */
    public void recordDeprecate(Long knowledgeId, String workspaceId, Long operatorId) {
        recordEvent(knowledgeId, workspaceId, TimelineConstants.EVENT_DEPRECATE, null, operatorId,
                "经验已失效", null);
    }

    /**
     * 记录重新启用事件
     */
    public void recordReactivate(Long knowledgeId, String workspaceId, Long operatorId) {
        recordEvent(knowledgeId, workspaceId, TimelineConstants.EVENT_REACTIVATE, null, operatorId,
                "经验已重新启用", null);
    }

    /**
     * 记录替代演进：新版 supersede 旧版
     */
    public void recordSupersede(Long successorId,
                                Long predecessorId,
                                String workspaceId,
                                Long operatorId,
                                String comment) {
        KnowledgeEntity predecessorEntity = knowledgeMapper.selectById(predecessorId);
        String predecessorTitle = predecessorEntity == null ? String.valueOf(predecessorId)
                : predecessorEntity.getTitle();
        KnowledgeEntity successorEntity = knowledgeMapper.selectById(successorId);
        String successorTitle = successorEntity == null ? String.valueOf(successorId)
                : successorEntity.getTitle();

        Map<String, Object> metadataMap = new HashMap<String, Object>();
        if (StringUtils.isNotBlank(comment)) {
            metadataMap.put("comment", comment);
        }

        recordEvent(successorId, workspaceId, TimelineConstants.EVENT_SUPERSEDE, predecessorId, operatorId,
                "替代旧版：" + predecessorTitle, metadataMap);
        recordEvent(predecessorId, workspaceId, TimelineConstants.EVENT_SUPERSEDE, successorId, operatorId,
                "被新版替代：" + successorTitle, metadataMap);
    }

    /**
     * 记录级联 Review 提示：依赖的经验已失效
     */
    public void recordCascadeReviewHint(Long dependentKnowledgeId,
                                        Long deprecatedKnowledgeId,
                                        String workspaceId,
                                        Long operatorId) {
        KnowledgeEntity deprecatedEntity = knowledgeMapper.selectById(deprecatedKnowledgeId);
        String deprecatedTitle = deprecatedEntity == null ? String.valueOf(deprecatedKnowledgeId)
                : deprecatedEntity.getTitle();
        recordEvent(dependentKnowledgeId, workspaceId, TimelineConstants.EVENT_CASCADE_REVIEW_HINT,
                deprecatedKnowledgeId, operatorId,
                "依赖的经验已失效，建议 Review：" + deprecatedTitle, null);
    }

    /**
     * 记录治理处置：保留主版本并下架重复记忆
     */
    public void recordGovernanceDeprecate(Long primaryKnowledgeId,
                                          Long duplicateKnowledgeId,
                                          String workspaceId,
                                          Long operatorId,
                                          String comment) {
        KnowledgeEntity duplicateEntity = knowledgeMapper.selectById(duplicateKnowledgeId);
        String duplicateTitle = duplicateEntity == null ? String.valueOf(duplicateKnowledgeId)
                : duplicateEntity.getTitle();
        Map<String, Object> metadataMap = buildCommentMetadata(comment);
        recordEvent(primaryKnowledgeId, workspaceId, TimelineConstants.EVENT_GOVERNANCE_DEPRECATE,
                duplicateKnowledgeId, operatorId,
                "治理下架重复记忆：" + duplicateTitle, metadataMap);
    }

    /**
     * 记录治理处置：合并 Fact 到主版本
     */
    public void recordGovernanceMerge(Long primaryKnowledgeId,
                                      Long sourceKnowledgeId,
                                      String workspaceId,
                                      Long operatorId,
                                      String comment) {
        KnowledgeEntity sourceEntity = knowledgeMapper.selectById(sourceKnowledgeId);
        String sourceTitle = sourceEntity == null ? String.valueOf(sourceKnowledgeId)
                : sourceEntity.getTitle();
        Map<String, Object> metadataMap = buildCommentMetadata(comment);
        recordEvent(primaryKnowledgeId, workspaceId, TimelineConstants.EVENT_GOVERNANCE_MERGE,
                sourceKnowledgeId, operatorId,
                "治理合并 Fact 来源：" + sourceTitle, metadataMap);
    }

    /**
     * 记录治理处置：忽略重复工单
     */
    public void recordGovernanceDismiss(Long knowledgeId,
                                        String workspaceId,
                                        Long operatorId,
                                        String comment) {
        Map<String, Object> metadataMap = buildCommentMetadata(comment);
        recordEvent(knowledgeId, workspaceId, TimelineConstants.EVENT_GOVERNANCE_DISMISS,
                null, operatorId, "治理标记重复误报", metadataMap);
    }

    /**
     * 记录治理合并优化：新版替代多条源记忆
     */
    public void recordMergeOptimize(Long successorId,
                                    Long predecessorId,
                                    String workspaceId,
                                    Long operatorId,
                                    String comment) {
        KnowledgeEntity predecessorEntity = knowledgeMapper.selectById(predecessorId);
        String predecessorTitle = predecessorEntity == null ? String.valueOf(predecessorId)
                : predecessorEntity.getTitle();
        Map<String, Object> metadataMap = buildCommentMetadata(comment);
        recordEvent(successorId, workspaceId, TimelineConstants.EVENT_MERGE_OPTIMIZE,
                predecessorId, operatorId,
                "合并优化替代：" + predecessorTitle, metadataMap);
    }

    private Map<String, Object> buildCommentMetadata(String comment) {
        Map<String, Object> metadataMap = new HashMap<String, Object>();
        if (StringUtils.isNotBlank(comment)) {
            metadataMap.put("comment", comment);
        }
        return metadataMap;
    }

    /**
     * 写入一条时间线事件
     */
    public void recordEvent(Long knowledgeId,
                            String workspaceId,
                            String eventType,
                            Long relatedKnowledgeId,
                            Long operatorId,
                            String eventSummary,
                            Map<String, Object> metadataMap) {
        KnowledgeTimelineEntity entity = new KnowledgeTimelineEntity();
        entity.setKnowledgeId(knowledgeId);
        entity.setWorkspaceId(workspaceId);
        entity.setEventType(eventType);
        entity.setRelatedKnowledgeId(relatedKnowledgeId);
        entity.setOperatorId(operatorId);
        entity.setEventSummary(eventSummary);
        if (metadataMap != null && !metadataMap.isEmpty()) {
            entity.setEventMetadata(MemoryJsonUtil.toJson(metadataMap));
        }
        knowledgeTimelineMapper.insert(entity);
    }

    private KnowledgeTimelineItemVO toItemView(KnowledgeTimelineEntity entity, Map<Long, String> titleCache) {
        KnowledgeTimelineItemVO itemView = new KnowledgeTimelineItemVO();
        itemView.setId(entity.getId());
        itemView.setKnowledgeId(entity.getKnowledgeId());
        itemView.setEventType(entity.getEventType());
        itemView.setRelatedKnowledgeId(entity.getRelatedKnowledgeId());
        itemView.setOperatorId(entity.getOperatorId());
        itemView.setEventSummary(entity.getEventSummary());
        itemView.setEventMetadata(entity.getEventMetadata());
        itemView.setCreateTime(entity.getCreateTime());
        if (entity.getRelatedKnowledgeId() != null) {
            itemView.setRelatedKnowledgeTitle(resolveKnowledgeTitle(entity.getRelatedKnowledgeId(), titleCache));
        }
        return itemView;
    }

    private String resolveKnowledgeTitle(Long knowledgeId, Map<Long, String> titleCache) {
        if (titleCache.containsKey(knowledgeId)) {
            return titleCache.get(knowledgeId);
        }
        KnowledgeEntity entity = knowledgeMapper.selectById(knowledgeId);
        String title = entity == null ? null : entity.getTitle();
        titleCache.put(knowledgeId, title);
        return title;
    }
}
