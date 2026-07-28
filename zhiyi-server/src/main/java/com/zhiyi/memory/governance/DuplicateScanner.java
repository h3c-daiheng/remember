package com.zhiyi.memory.governance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.dao.KnowledgeFactMapper;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.domain.CaptureSimilarKnowledgeHint;
import com.zhiyi.memory.domain.KnowledgeAggregate;
import com.zhiyi.memory.engine.SimilarKnowledgeFinder;
import com.zhiyi.memory.entity.KnowledgeEntity;
import com.zhiyi.memory.entity.KnowledgeFactEntity;
import com.zhiyi.memory.knowledge.KnowledgeService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 已发布记忆重复扫描器：向量相似 + 连通分量聚类，产出重复组
 */
@Component
public class DuplicateScanner {

    private static final Logger log = LoggerFactory.getLogger(DuplicateScanner.class);

    /** 每条记忆检索相似 TopK */
    private static final int SCAN_TOP_K = 5;

    private final KnowledgeMapper knowledgeMapper;
    private final KnowledgeFactMapper knowledgeFactMapper;
    private final KnowledgeService knowledgeService;
    private final SimilarKnowledgeFinder similarKnowledgeFinder;

    public DuplicateScanner(KnowledgeMapper knowledgeMapper,
                            KnowledgeFactMapper knowledgeFactMapper,
                            KnowledgeService knowledgeService,
                            SimilarKnowledgeFinder similarKnowledgeFinder) {
        this.knowledgeMapper = knowledgeMapper;
        this.knowledgeFactMapper = knowledgeFactMapper;
        this.knowledgeService = knowledgeService;
        this.similarKnowledgeFinder = similarKnowledgeFinder;
    }

    /**
     * 扫描工作空间内已发布记忆的重复组
     *
     * @param workspaceId         工作空间
     * @param knowledgeTypeFilter 限定类型，空表示全部
     * @param moduleFilter        限定模块，空表示不限
     * @param threshold           相似度阈值
     */
    public List<DuplicateGroupScanResult> scanDuplicateGroups(String workspaceId,
                                                              String knowledgeTypeFilter,
                                                              String moduleFilter,
                                                              double threshold) {
        List<KnowledgeEntity> publishedList = listPublishedKnowledge(workspaceId, knowledgeTypeFilter, moduleFilter);
        if (publishedList.size() < 2) {
            return Collections.emptyList();
        }

        Map<Long, KnowledgeAggregate> aggregateCache = new HashMap<Long, KnowledgeAggregate>();
        Map<Long, Integer> factCountCache = buildFactCountCache(publishedList);

        // 构建相似边：id -> 相似邻居及分数
        Map<Long, Map<Long, Double>> similarityEdgeMap = new HashMap<Long, Map<Long, Double>>();
        for (KnowledgeEntity entity : publishedList) {
            KnowledgeAggregate aggregate = loadAggregateCached(entity, workspaceId, aggregateCache);
            List<CaptureSimilarKnowledgeHint> hintList = similarKnowledgeFinder.findSimilarForKnowledge(
                    aggregate, workspaceId, SCAN_TOP_K, threshold);
            for (CaptureSimilarKnowledgeHint hint : hintList) {
                if (hint == null || hint.getKnowledgeId() == null) {
                    continue;
                }
                addSimilarityEdge(similarityEdgeMap, entity.getId(), hint.getKnowledgeId(), hint.getSimilarityScore());
            }
        }

        // 并查集合并连通分量
        UnionFind unionFind = new UnionFind();
        for (KnowledgeEntity entity : publishedList) {
            unionFind.add(entity.getId());
        }
        for (Map.Entry<Long, Map<Long, Double>> entry : similarityEdgeMap.entrySet()) {
            Long sourceId = entry.getKey();
            for (Long targetId : entry.getValue().keySet()) {
                unionFind.union(sourceId, targetId);
            }
        }

        Map<Long, List<Long>> groupMap = new HashMap<Long, List<Long>>();
        for (KnowledgeEntity entity : publishedList) {
            Long rootId = unionFind.find(entity.getId());
            List<Long> memberList = groupMap.get(rootId);
            if (memberList == null) {
                memberList = new ArrayList<Long>();
                groupMap.put(rootId, memberList);
            }
            memberList.add(entity.getId());
        }

        List<DuplicateGroupScanResult> resultList = new ArrayList<DuplicateGroupScanResult>();
        for (List<Long> memberIdList : groupMap.values()) {
            if (memberIdList.size() < 2) {
                continue;
            }
            DuplicateGroupScanResult groupResult = buildGroupResult(
                    memberIdList, similarityEdgeMap, workspaceId, aggregateCache, factCountCache);
            if (groupResult != null) {
                resultList.add(groupResult);
            }
        }

        Collections.sort(resultList, new Comparator<DuplicateGroupScanResult>() {
            @Override
            public int compare(DuplicateGroupScanResult left, DuplicateGroupScanResult right) {
                return Double.compare(right.getMaxSimilarityScore(), left.getMaxSimilarityScore());
            }
        });
        log.info("治理重复扫描完成 workspaceId={} publishedCount={} groupCount={}",
                workspaceId, publishedList.size(), resultList.size());
        return resultList;
    }

    /**
     * 增量重复扫描：仅关注自 changedSince 以来有更新的记忆及其相似邻居
     */
    public List<DuplicateGroupScanResult> scanDuplicateGroupsIncremental(String workspaceId,
                                                                        String knowledgeTypeFilter,
                                                                        String moduleFilter,
                                                                        double threshold,
                                                                        Date changedSince) {
        if (changedSince == null) {
            return scanDuplicateGroups(workspaceId, knowledgeTypeFilter, moduleFilter, threshold);
        }

        List<KnowledgeEntity> publishedList = listPublishedKnowledge(workspaceId, knowledgeTypeFilter, moduleFilter);
        if (publishedList.size() < 2) {
            return Collections.emptyList();
        }

        Set<Long> changedKnowledgeIdSet = new HashSet<Long>();
        for (KnowledgeEntity entity : publishedList) {
            if (entity.getUpdateTime() != null && !entity.getUpdateTime().before(changedSince)) {
                changedKnowledgeIdSet.add(entity.getId());
            }
        }
        if (changedKnowledgeIdSet.isEmpty()) {
            log.info("增量重复扫描无变更 workspaceId={} changedSince={}", workspaceId, changedSince);
            return Collections.emptyList();
        }

        Map<Long, KnowledgeAggregate> aggregateCache = new HashMap<Long, KnowledgeAggregate>();
        Map<Long, Integer> factCountCache = buildFactCountCache(publishedList);
        Map<Long, Map<Long, Double>> similarityEdgeMap = new HashMap<Long, Map<Long, Double>>();

        for (KnowledgeEntity entity : publishedList) {
            if (!changedKnowledgeIdSet.contains(entity.getId())) {
                continue;
            }
            KnowledgeAggregate aggregate = loadAggregateCached(entity, workspaceId, aggregateCache);
            List<CaptureSimilarKnowledgeHint> hintList = similarKnowledgeFinder.findSimilarForKnowledge(
                    aggregate, workspaceId, SCAN_TOP_K, threshold);
            for (CaptureSimilarKnowledgeHint hint : hintList) {
                if (hint == null || hint.getKnowledgeId() == null) {
                    continue;
                }
                addSimilarityEdge(similarityEdgeMap, entity.getId(), hint.getKnowledgeId(), hint.getSimilarityScore());
            }
        }

        UnionFind unionFind = new UnionFind();
        for (Long changedKnowledgeId : changedKnowledgeIdSet) {
            unionFind.add(changedKnowledgeId);
        }
        for (Map.Entry<Long, Map<Long, Double>> entry : similarityEdgeMap.entrySet()) {
            Long sourceId = entry.getKey();
            unionFind.add(sourceId);
            for (Long targetId : entry.getValue().keySet()) {
                unionFind.add(targetId);
                unionFind.union(sourceId, targetId);
            }
        }

        Map<Long, List<Long>> groupMap = new HashMap<Long, List<Long>>();
        for (Map.Entry<Long, Map<Long, Double>> entry : similarityEdgeMap.entrySet()) {
            Long sourceId = entry.getKey();
            for (Long targetId : entry.getValue().keySet()) {
                Long rootId = unionFind.find(sourceId);
                List<Long> memberList = groupMap.get(rootId);
                if (memberList == null) {
                    memberList = new ArrayList<Long>();
                    groupMap.put(rootId, memberList);
                }
                if (!memberList.contains(sourceId)) {
                    memberList.add(sourceId);
                }
                if (!memberList.contains(targetId)) {
                    memberList.add(targetId);
                }
            }
        }

        List<DuplicateGroupScanResult> resultList = new ArrayList<DuplicateGroupScanResult>();
        Set<String> groupSignatureSet = new HashSet<String>();
        for (List<Long> memberIdList : groupMap.values()) {
            if (memberIdList.size() < 2) {
                continue;
            }
            boolean touchesChanged = false;
            for (Long memberId : memberIdList) {
                if (changedKnowledgeIdSet.contains(memberId)) {
                    touchesChanged = true;
                    break;
                }
            }
            if (!touchesChanged) {
                continue;
            }
            DuplicateGroupScanResult groupResult = buildGroupResult(
                    memberIdList, similarityEdgeMap, workspaceId, aggregateCache, factCountCache);
            if (groupResult == null) {
                continue;
            }
            String signature = buildGroupSignature(memberIdList);
            if (groupSignatureSet.contains(signature)) {
                continue;
            }
            groupSignatureSet.add(signature);
            resultList.add(groupResult);
        }

        Collections.sort(resultList, new Comparator<DuplicateGroupScanResult>() {
            @Override
            public int compare(DuplicateGroupScanResult left, DuplicateGroupScanResult right) {
                return Double.compare(right.getMaxSimilarityScore(), left.getMaxSimilarityScore());
            }
        });
        log.info("增量重复扫描完成 workspaceId={} changedCount={} groupCount={}",
                workspaceId, changedKnowledgeIdSet.size(), resultList.size());
        return resultList;
    }

    private String buildGroupSignature(List<Long> memberIdList) {
        List<Long> sortedIdList = new ArrayList<Long>(memberIdList);
        Collections.sort(sortedIdList);
        StringBuilder builder = new StringBuilder();
        for (Long memberId : sortedIdList) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(memberId);
        }
        return builder.toString();
    }

    /**
     * 查询已发布知识列表，支持类型与模块过滤
     */
    private List<KnowledgeEntity> listPublishedKnowledge(String workspaceId,
                                                         String knowledgeTypeFilter,
                                                         String moduleFilter) {
        LambdaQueryWrapper<KnowledgeEntity> queryWrapper = new LambdaQueryWrapper<KnowledgeEntity>();
        queryWrapper.eq(KnowledgeEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeEntity::getLifecycleStatus, MemoryConstants.LIFECYCLE_PUBLISHED);
        if (StringUtils.isNotBlank(knowledgeTypeFilter)) {
            queryWrapper.eq(KnowledgeEntity::getKnowledgeType, knowledgeTypeFilter);
        }
        if (StringUtils.isNotBlank(moduleFilter)) {
            queryWrapper.eq(KnowledgeEntity::getModule, moduleFilter.trim());
        }
        queryWrapper.orderByDesc(KnowledgeEntity::getUpdateTime);
        return knowledgeMapper.selectList(queryWrapper);
    }

    /**
     * 批量统计 Fact 条数，供主版本推荐算法使用
     */
    private Map<Long, Integer> buildFactCountCache(List<KnowledgeEntity> entityList) {
        Map<Long, Integer> countMap = new HashMap<Long, Integer>();
        if (entityList == null || entityList.isEmpty()) {
            return countMap;
        }
        Set<Long> knowledgeIdSet = new HashSet<Long>();
        for (KnowledgeEntity entity : entityList) {
            knowledgeIdSet.add(entity.getId());
        }
        LambdaQueryWrapper<KnowledgeFactEntity> factWrapper = new LambdaQueryWrapper<KnowledgeFactEntity>();
        factWrapper.in(KnowledgeFactEntity::getKnowledgeId, knowledgeIdSet);
        List<KnowledgeFactEntity> factEntityList = knowledgeFactMapper.selectList(factWrapper);
        for (KnowledgeFactEntity factEntity : factEntityList) {
            Long knowledgeId = factEntity.getKnowledgeId();
            Integer currentCount = countMap.get(knowledgeId);
            countMap.put(knowledgeId, currentCount == null ? 1 : currentCount + 1);
        }
        return countMap;
    }

    /**
     * 构建一组重复记忆的扫描结果
     */
    private DuplicateGroupScanResult buildGroupResult(List<Long> memberIdList,
                                                      Map<Long, Map<Long, Double>> similarityEdgeMap,
                                                      String workspaceId,
                                                      Map<Long, KnowledgeAggregate> aggregateCache,
                                                      Map<Long, Integer> factCountCache) {
        Long primaryId = recommendPrimaryKnowledgeId(memberIdList, workspaceId, aggregateCache, factCountCache);
        if (primaryId == null) {
            return null;
        }

        double maxScore = 0D;
        String knowledgeType = null;
        for (Long memberId : memberIdList) {
            KnowledgeAggregate aggregate = loadAggregateCachedById(memberId, workspaceId, aggregateCache);
            if (aggregate != null && knowledgeType == null) {
                knowledgeType = aggregate.getKnowledgeType();
            }
            Map<Long, Double> neighborMap = similarityEdgeMap.get(memberId);
            if (neighborMap == null) {
                continue;
            }
            for (Long neighborId : memberIdList) {
                Double score = neighborMap.get(neighborId);
                if (score != null && score > maxScore) {
                    maxScore = score;
                }
            }
        }

        List<Long> duplicateIdList = new ArrayList<Long>();
        for (Long memberId : memberIdList) {
            if (!memberId.equals(primaryId)) {
                duplicateIdList.add(memberId);
            }
        }

        DuplicateGroupScanResult groupResult = new DuplicateGroupScanResult();
        groupResult.setPrimaryKnowledgeId(primaryId);
        groupResult.setDuplicateKnowledgeIds(duplicateIdList);
        groupResult.setKnowledgeType(StringUtils.defaultString(knowledgeType, MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE));
        groupResult.setMaxSimilarityScore(maxScore);
        groupResult.setSuggestedAction(resolveSuggestedAction(groupResult.getKnowledgeType()));
        return groupResult;
    }

    /**
     * 推荐主版本：Recall 次数 > Fact 条数 > 更新时间
     */
    private Long recommendPrimaryKnowledgeId(List<Long> memberIdList,
                                             String workspaceId,
                                             Map<Long, KnowledgeAggregate> aggregateCache,
                                             Map<Long, Integer> factCountCache) {
        Long bestId = null;
        int bestRecallCount = -1;
        int bestFactCount = -1;
        long bestUpdateTime = -1L;
        for (Long memberId : memberIdList) {
            KnowledgeAggregate aggregate = loadAggregateCachedById(memberId, workspaceId, aggregateCache);
            if (aggregate == null) {
                continue;
            }
            int recallCount = aggregate.getRecallCount() == null ? 0 : aggregate.getRecallCount();
            int factCount = factCountCache.containsKey(memberId) ? factCountCache.get(memberId) : 0;
            long updateTime = aggregate.getUpdateTime() == null ? 0L : aggregate.getUpdateTime().getTime();
            if (bestId == null
                    || recallCount > bestRecallCount
                    || (recallCount == bestRecallCount && factCount > bestFactCount)
                    || (recallCount == bestRecallCount && factCount == bestFactCount && updateTime > bestUpdateTime)) {
                bestId = memberId;
                bestRecallCount = recallCount;
                bestFactCount = factCount;
                bestUpdateTime = updateTime;
            }
        }
        return bestId;
    }

    /**
     * Rule 类型建议合并 Fact，其余类型建议保留主版本并下架副本
     */
    private String resolveSuggestedAction(String knowledgeType) {
        if (MemoryConstants.KNOWLEDGE_TYPE_RULE.equals(knowledgeType)) {
            return GovernanceConstants.ACTION_MERGE_FACTS;
        }
        return GovernanceConstants.ACTION_KEEP_PRIMARY_DEPRECATE_OTHERS;
    }

    private void addSimilarityEdge(Map<Long, Map<Long, Double>> edgeMap,
                                   Long sourceId,
                                   Long targetId,
                                   double score) {
        putEdge(edgeMap, sourceId, targetId, score);
        putEdge(edgeMap, targetId, sourceId, score);
    }

    private void putEdge(Map<Long, Map<Long, Double>> edgeMap, Long sourceId, Long targetId, double score) {
        Map<Long, Double> neighborMap = edgeMap.get(sourceId);
        if (neighborMap == null) {
            neighborMap = new HashMap<Long, Double>();
            edgeMap.put(sourceId, neighborMap);
        }
        Double existingScore = neighborMap.get(targetId);
        if (existingScore == null || score > existingScore) {
            neighborMap.put(targetId, score);
        }
    }

    private KnowledgeAggregate loadAggregateCached(KnowledgeEntity entity,
                                                   String workspaceId,
                                                   Map<Long, KnowledgeAggregate> aggregateCache) {
        return loadAggregateCachedById(entity.getId(), workspaceId, aggregateCache);
    }

    private KnowledgeAggregate loadAggregateCachedById(Long knowledgeId,
                                                       String workspaceId,
                                                       Map<Long, KnowledgeAggregate> aggregateCache) {
        if (aggregateCache.containsKey(knowledgeId)) {
            return aggregateCache.get(knowledgeId);
        }
        try {
            KnowledgeAggregate aggregate = knowledgeService.getDetail(knowledgeId, workspaceId);
            aggregateCache.put(knowledgeId, aggregate);
            return aggregate;
        } catch (Exception exception) {
            log.debug("加载知识详情失败 knowledgeId={}", knowledgeId);
            return null;
        }
    }

    /**
     * 并查集：合并相似连通分量
     */
    private static class UnionFind {

        private final Map<Long, Long> parentMap = new HashMap<Long, Long>();

        void add(Long nodeId) {
            if (!parentMap.containsKey(nodeId)) {
                parentMap.put(nodeId, nodeId);
            }
        }

        Long find(Long nodeId) {
            Long parentId = parentMap.get(nodeId);
            if (parentId == null) {
                return nodeId;
            }
            if (!parentId.equals(nodeId)) {
                parentId = find(parentId);
                parentMap.put(nodeId, parentId);
            }
            return parentId;
        }

        void union(Long leftId, Long rightId) {
            Long leftRoot = find(leftId);
            Long rightRoot = find(rightId);
            if (!leftRoot.equals(rightRoot)) {
                parentMap.put(rightRoot, leftRoot);
            }
        }
    }
}
