package com.zhiyi.memory.governance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.dao.KnowledgeFactMapper;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.domain.CaptureSimilarKnowledgeHint;
import com.zhiyi.memory.domain.FactBlock;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 碎片聚类引擎：识别同模块下零散、不完整的已发布记忆并聚类
 */
@Component
public class ClusterEngine {

    private static final Logger log = LoggerFactory.getLogger(ClusterEngine.class);

    /** 聚类相似检索 TopK */
    private static final int CLUSTER_TOP_K = 5;

    private final KnowledgeMapper knowledgeMapper;
    private final KnowledgeFactMapper knowledgeFactMapper;
    private final KnowledgeService knowledgeService;
    private final SimilarKnowledgeFinder similarKnowledgeFinder;

    public ClusterEngine(KnowledgeMapper knowledgeMapper,
                         KnowledgeFactMapper knowledgeFactMapper,
                         KnowledgeService knowledgeService,
                         SimilarKnowledgeFinder similarKnowledgeFinder) {
        this.knowledgeMapper = knowledgeMapper;
        this.knowledgeFactMapper = knowledgeFactMapper;
        this.knowledgeService = knowledgeService;
        this.similarKnowledgeFinder = similarKnowledgeFinder;
    }

    /**
     * 扫描工作空间内碎片聚类
     *
     * @param workspaceId         工作空间
     * @param knowledgeTypeFilter 限定类型，默认 experience
     * @param moduleFilter        限定模块
     * @param threshold           聚类相似度阈值
     */
    public List<FragmentClusterScanResult> scanFragmentClusters(String workspaceId,
                                                                String knowledgeTypeFilter,
                                                                String moduleFilter,
                                                                double threshold) {
        String effectiveType = StringUtils.isNotBlank(knowledgeTypeFilter)
                ? knowledgeTypeFilter
                : MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE;
        List<KnowledgeEntity> publishedList = listPublishedKnowledge(workspaceId, effectiveType, moduleFilter);
        if (publishedList.size() < GovernanceConstants.FRAGMENT_MIN_CLUSTER_SIZE) {
            return Collections.emptyList();
        }

        Map<Long, Integer> factCountCache = buildFactCountCache(publishedList);
        Map<Long, KnowledgeAggregate> aggregateCache = new HashMap<Long, KnowledgeAggregate>();

        // 仅保留碎片记忆作为聚类节点
        List<KnowledgeEntity> fragmentList = new ArrayList<KnowledgeEntity>();
        for (KnowledgeEntity entity : publishedList) {
            if (isFragment(entity.getId(), factCountCache, workspaceId, aggregateCache)) {
                fragmentList.add(entity);
            }
        }
        if (fragmentList.size() < GovernanceConstants.FRAGMENT_MIN_CLUSTER_SIZE) {
            return Collections.emptyList();
        }

        // 按 module 分组
        Map<String, List<KnowledgeEntity>> moduleGroupMap = new HashMap<String, List<KnowledgeEntity>>();
        for (KnowledgeEntity entity : fragmentList) {
            String moduleName = StringUtils.trimToEmpty(entity.getModule());
            if (StringUtils.isBlank(moduleName)) {
                continue;
            }
            List<KnowledgeEntity> moduleList = moduleGroupMap.get(moduleName);
            if (moduleList == null) {
                moduleList = new ArrayList<KnowledgeEntity>();
                moduleGroupMap.put(moduleName, moduleList);
            }
            moduleList.add(entity);
        }

        List<FragmentClusterScanResult> resultList = new ArrayList<FragmentClusterScanResult>();
        for (Map.Entry<String, List<KnowledgeEntity>> entry : moduleGroupMap.entrySet()) {
            List<FragmentClusterScanResult> moduleResultList = clusterWithinModule(
                    entry.getValue(), entry.getKey(), workspaceId, threshold, aggregateCache, factCountCache);
            resultList.addAll(moduleResultList);
        }

        Collections.sort(resultList, new Comparator<FragmentClusterScanResult>() {
            @Override
            public int compare(FragmentClusterScanResult left, FragmentClusterScanResult right) {
                int leftSize = left.getMemberKnowledgeIds().size() + 1;
                int rightSize = right.getMemberKnowledgeIds().size() + 1;
                if (leftSize != rightSize) {
                    return rightSize - leftSize;
                }
                return Double.compare(right.getMaxSimilarityScore(), left.getMaxSimilarityScore());
            }
        });
        log.info("碎片聚类扫描完成 workspaceId={} fragmentCount={} clusterCount={}",
                workspaceId, fragmentList.size(), resultList.size());
        return resultList;
    }

    /**
     * 判断单条记忆是否为碎片：Fact 过少或 Experience 结构不完整
     */
    public boolean isFragment(Long knowledgeId,
                              Map<Long, Integer> factCountCache,
                              String workspaceId,
                              Map<Long, KnowledgeAggregate> aggregateCache) {
        int factCount = factCountCache.containsKey(knowledgeId) ? factCountCache.get(knowledgeId) : 0;
        if (factCount <= GovernanceConstants.FRAGMENT_MAX_FACT_COUNT) {
            return true;
        }
        KnowledgeAggregate aggregate = loadAggregateCached(knowledgeId, workspaceId, aggregateCache);
        if (aggregate == null) {
            return false;
        }
        if (!MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE.equals(aggregate.getKnowledgeType())) {
            return factCount <= GovernanceConstants.FRAGMENT_MAX_FACT_COUNT;
        }
        return !hasCompleteExperienceStructure(aggregate.getFacts());
    }

    /**
     * Experience 完整性：至少含 observation + decision + action 各一条
     */
    private boolean hasCompleteExperienceStructure(List<FactBlock> factList) {
        if (factList == null || factList.isEmpty()) {
            return false;
        }
        boolean hasObservation = false;
        boolean hasDecision = false;
        boolean hasAction = false;
        for (FactBlock factBlock : factList) {
            if (factBlock == null || StringUtils.isBlank(factBlock.getType())) {
                continue;
            }
            String type = factBlock.getType();
            if ("observation".equals(type) || "outcome".equals(type) || "evidence".equals(type)) {
                hasObservation = true;
            }
            if ("decision".equals(type)) {
                hasDecision = true;
            }
            if ("action".equals(type)) {
                hasAction = true;
            }
        }
        return hasObservation && hasDecision && hasAction;
    }

    /**
     * 在同一 module 内对碎片记忆做相似聚类
     */
    private List<FragmentClusterScanResult> clusterWithinModule(List<KnowledgeEntity> moduleEntityList,
                                                                String moduleName,
                                                                String workspaceId,
                                                                double threshold,
                                                                Map<Long, KnowledgeAggregate> aggregateCache,
                                                                Map<Long, Integer> factCountCache) {
        if (moduleEntityList.size() < GovernanceConstants.FRAGMENT_MIN_CLUSTER_SIZE) {
            return Collections.emptyList();
        }

        Map<Long, Map<Long, Double>> similarityEdgeMap = new HashMap<Long, Map<Long, Double>>();
        for (KnowledgeEntity entity : moduleEntityList) {
            KnowledgeAggregate aggregate = loadAggregateCached(entity.getId(), workspaceId, aggregateCache);
            List<CaptureSimilarKnowledgeHint> hintList = similarKnowledgeFinder.findSimilarForKnowledge(
                    aggregate, workspaceId, CLUSTER_TOP_K, threshold);
            Set<Long> moduleIdSet = buildIdSet(moduleEntityList);
            for (CaptureSimilarKnowledgeHint hint : hintList) {
                if (hint == null || hint.getKnowledgeId() == null || !moduleIdSet.contains(hint.getKnowledgeId())) {
                    continue;
                }
                addSimilarityEdge(similarityEdgeMap, entity.getId(), hint.getKnowledgeId(), hint.getSimilarityScore());
            }
        }

        UnionFind unionFind = new UnionFind();
        for (KnowledgeEntity entity : moduleEntityList) {
            unionFind.add(entity.getId());
        }
        for (Map.Entry<Long, Map<Long, Double>> entry : similarityEdgeMap.entrySet()) {
            for (Long targetId : entry.getValue().keySet()) {
                unionFind.union(entry.getKey(), targetId);
            }
        }

        Map<Long, List<Long>> groupMap = new HashMap<Long, List<Long>>();
        for (KnowledgeEntity entity : moduleEntityList) {
            Long rootId = unionFind.find(entity.getId());
            List<Long> memberList = groupMap.get(rootId);
            if (memberList == null) {
                memberList = new ArrayList<Long>();
                groupMap.put(rootId, memberList);
            }
            memberList.add(entity.getId());
        }

        List<FragmentClusterScanResult> resultList = new ArrayList<FragmentClusterScanResult>();
        for (List<Long> memberIdList : groupMap.values()) {
            if (memberIdList.size() < GovernanceConstants.FRAGMENT_MIN_CLUSTER_SIZE) {
                continue;
            }
            FragmentClusterScanResult clusterResult = buildClusterResult(
                    memberIdList, similarityEdgeMap, moduleName, workspaceId, aggregateCache, factCountCache);
            if (clusterResult != null) {
                resultList.add(clusterResult);
            }
        }
        return resultList;
    }

    private FragmentClusterScanResult buildClusterResult(List<Long> memberIdList,
                                                         Map<Long, Map<Long, Double>> similarityEdgeMap,
                                                         String moduleName,
                                                         String workspaceId,
                                                         Map<Long, KnowledgeAggregate> aggregateCache,
                                                         Map<Long, Integer> factCountCache) {
        Long primaryId = recommendPrimaryKnowledgeId(memberIdList, workspaceId, aggregateCache, factCountCache);
        if (primaryId == null) {
            return null;
        }

        double maxScore = 0D;
        String knowledgeType = MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE;
        for (Long memberId : memberIdList) {
            KnowledgeAggregate aggregate = loadAggregateCached(memberId, workspaceId, aggregateCache);
            if (aggregate != null && StringUtils.isNotBlank(aggregate.getKnowledgeType())) {
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

        List<Long> memberIds = new ArrayList<Long>();
        for (Long memberId : memberIdList) {
            if (!memberId.equals(primaryId)) {
                memberIds.add(memberId);
            }
        }

        FragmentClusterScanResult clusterResult = new FragmentClusterScanResult();
        clusterResult.setPrimaryKnowledgeId(primaryId);
        clusterResult.setMemberKnowledgeIds(memberIds);
        clusterResult.setKnowledgeType(knowledgeType);
        clusterResult.setMaxSimilarityScore(maxScore);
        clusterResult.setModuleName(moduleName);
        clusterResult.setSuggestedAction(GovernanceConstants.ACTION_MERGE_OPTIMIZE);
        return clusterResult;
    }

    private Long recommendPrimaryKnowledgeId(List<Long> memberIdList,
                                             String workspaceId,
                                             Map<Long, KnowledgeAggregate> aggregateCache,
                                             Map<Long, Integer> factCountCache) {
        Long bestId = null;
        int bestRecallCount = -1;
        int bestFactCount = -1;
        long bestUpdateTime = -1L;
        for (Long memberId : memberIdList) {
            KnowledgeAggregate aggregate = loadAggregateCached(memberId, workspaceId, aggregateCache);
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

    private Set<Long> buildIdSet(List<KnowledgeEntity> entityList) {
        Set<Long> idSet = new HashSet<Long>();
        for (KnowledgeEntity entity : entityList) {
            idSet.add(entity.getId());
        }
        return idSet;
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

    private KnowledgeAggregate loadAggregateCached(Long knowledgeId,
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
