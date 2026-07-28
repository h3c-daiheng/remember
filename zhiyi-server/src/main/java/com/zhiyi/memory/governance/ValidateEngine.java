package com.zhiyi.memory.governance;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.dao.KnowledgeFactMapper;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.domain.FactBlock;
import com.zhiyi.memory.domain.KnowledgeAggregate;
import com.zhiyi.memory.entity.KnowledgeEntity;
import com.zhiyi.memory.entity.KnowledgeFactEntity;
import com.zhiyi.memory.knowledge.KnowledgeService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 已发布记忆质量校验引擎：完整性检测与 Rule 约束冲突检测
 */
@Component
public class ValidateEngine {

    private static final Logger log = LoggerFactory.getLogger(ValidateEngine.class);

    /** 约束冲突文本重叠比例阈值 */
    private static final double CONFLICT_OVERLAP_THRESHOLD = 0.45D;

    /** 否定词：一方含否定另一方不含时视为潜在冲突 */
    private static final String[] NEGATION_KEYWORDS = {
            "禁止", "不要", "不可", "不能", "勿", "不得", "不应", "避免"
    };

    private final KnowledgeMapper knowledgeMapper;
    private final KnowledgeFactMapper knowledgeFactMapper;
    private final KnowledgeService knowledgeService;
    private final ClusterEngine clusterEngine;

    public ValidateEngine(KnowledgeMapper knowledgeMapper,
                          KnowledgeFactMapper knowledgeFactMapper,
                          KnowledgeService knowledgeService,
                          ClusterEngine clusterEngine) {
        this.knowledgeMapper = knowledgeMapper;
        this.knowledgeFactMapper = knowledgeFactMapper;
        this.knowledgeService = knowledgeService;
        this.clusterEngine = clusterEngine;
    }

    /**
     * 扫描工作空间内已发布记忆的质量问题
     */
    public List<ValidationScanResult> scanValidationIssues(String workspaceId,
                                                         String knowledgeTypeFilter,
                                                         String moduleFilter) {
        List<KnowledgeEntity> publishedList = listPublishedKnowledge(workspaceId, knowledgeTypeFilter, moduleFilter);
        if (publishedList.isEmpty()) {
            return new ArrayList<ValidationScanResult>();
        }

        Map<Long, Integer> factCountCache = buildFactCountCache(publishedList);
        Map<Long, KnowledgeAggregate> aggregateCache = new HashMap<Long, KnowledgeAggregate>();
        List<ValidationScanResult> resultList = new ArrayList<ValidationScanResult>();

        for (KnowledgeEntity entity : publishedList) {
            ValidationScanResult incompleteResult = validateIncomplete(entity, workspaceId, factCountCache, aggregateCache);
            if (incompleteResult != null) {
                resultList.add(incompleteResult);
            }
        }

        resultList.addAll(scanRuleConflicts(publishedList, workspaceId, aggregateCache));
        log.info("质量校验扫描完成 workspaceId={} publishedCount={} issueCount={}",
                workspaceId, publishedList.size(), resultList.size());
        return resultList;
    }

    /**
     * 检测单条记忆结构是否不完整
     */
    private ValidationScanResult validateIncomplete(KnowledgeEntity entity,
                                                    String workspaceId,
                                                    Map<Long, Integer> factCountCache,
                                                    Map<Long, KnowledgeAggregate> aggregateCache) {
        if (clusterEngine.isFragment(entity.getId(), factCountCache, workspaceId, aggregateCache)) {
            KnowledgeAggregate aggregate = loadAggregateCached(entity.getId(), workspaceId, aggregateCache);
            ValidationScanResult result = new ValidationScanResult();
            result.setIssueType(GovernanceConstants.ISSUE_TYPE_INCOMPLETE);
            result.setPrimaryKnowledgeId(entity.getId());
            result.setKnowledgeType(entity.getKnowledgeType());
            result.setScore(calculateIncompleteScore(aggregate, factCountCache.get(entity.getId())));
            result.setSuggestedAction(resolveIncompleteSuggestedAction(entity.getKnowledgeType()));
            JSONObject metadata = new JSONObject();
            metadata.set("missingFactTypes", listMissingFactTypes(aggregate));
            metadata.set("factCount", factCountCache.get(entity.getId()) == null ? 0 : factCountCache.get(entity.getId()));
            result.setMetadataJson(metadata.toString());
            return result;
        }
        return null;
    }

    /**
     * 同模块 Rule 约束冲突检测：高文本重叠且否定语义不一致
     */
    private List<ValidationScanResult> scanRuleConflicts(List<KnowledgeEntity> publishedList,
                                                           String workspaceId,
                                                           Map<Long, KnowledgeAggregate> aggregateCache) {
        Map<String, List<KnowledgeEntity>> moduleRuleMap = new HashMap<String, List<KnowledgeEntity>>();
        for (KnowledgeEntity entity : publishedList) {
            if (!MemoryConstants.KNOWLEDGE_TYPE_RULE.equals(entity.getKnowledgeType())) {
                continue;
            }
            String moduleName = StringUtils.trimToEmpty(entity.getModule());
            if (StringUtils.isBlank(moduleName)) {
                continue;
            }
            List<KnowledgeEntity> moduleRuleList = moduleRuleMap.get(moduleName);
            if (moduleRuleList == null) {
                moduleRuleList = new ArrayList<KnowledgeEntity>();
                moduleRuleMap.put(moduleName, moduleRuleList);
            }
            moduleRuleList.add(entity);
        }

        List<ValidationScanResult> conflictList = new ArrayList<ValidationScanResult>();
        Set<String> pairSignatureSet = new HashSet<String>();
        for (List<KnowledgeEntity> moduleRuleList : moduleRuleMap.values()) {
            if (moduleRuleList.size() < 2) {
                continue;
            }
            for (int leftIndex = 0; leftIndex < moduleRuleList.size(); leftIndex++) {
                for (int rightIndex = leftIndex + 1; rightIndex < moduleRuleList.size(); rightIndex++) {
                    KnowledgeEntity leftEntity = moduleRuleList.get(leftIndex);
                    KnowledgeEntity rightEntity = moduleRuleList.get(rightIndex);
                    ValidationScanResult conflictResult = detectRuleConflict(
                            leftEntity, rightEntity, workspaceId, aggregateCache, pairSignatureSet);
                    if (conflictResult != null) {
                        conflictList.add(conflictResult);
                    }
                }
            }
        }
        return conflictList;
    }

    private ValidationScanResult detectRuleConflict(KnowledgeEntity leftEntity,
                                                    KnowledgeEntity rightEntity,
                                                    String workspaceId,
                                                    Map<Long, KnowledgeAggregate> aggregateCache,
                                                    Set<String> pairSignatureSet) {
        String pairSignature = buildPairSignature(leftEntity.getId(), rightEntity.getId());
        if (pairSignatureSet.contains(pairSignature)) {
            return null;
        }

        KnowledgeAggregate leftAggregate = loadAggregateCached(leftEntity.getId(), workspaceId, aggregateCache);
        KnowledgeAggregate rightAggregate = loadAggregateCached(rightEntity.getId(), workspaceId, aggregateCache);
        if (leftAggregate == null || rightAggregate == null) {
            return null;
        }

        List<String> leftConstraintList = extractConstraintTexts(leftAggregate.getFacts());
        List<String> rightConstraintList = extractConstraintTexts(rightAggregate.getFacts());
        if (leftConstraintList.isEmpty() || rightConstraintList.isEmpty()) {
            return null;
        }

        double bestOverlap = 0D;
        String leftSnippet = null;
        String rightSnippet = null;
        boolean negationMismatch = false;
        for (String leftText : leftConstraintList) {
            for (String rightText : rightConstraintList) {
                double overlap = calculateTextOverlap(leftText, rightText);
                if (overlap >= CONFLICT_OVERLAP_THRESHOLD) {
                    boolean leftNegation = containsNegation(leftText);
                    boolean rightNegation = containsNegation(rightText);
                    if (leftNegation != rightNegation) {
                        if (overlap > bestOverlap) {
                            bestOverlap = overlap;
                            leftSnippet = StringUtils.left(leftText, 120);
                            rightSnippet = StringUtils.left(rightText, 120);
                            negationMismatch = true;
                        }
                    }
                }
            }
        }

        if (!negationMismatch) {
            return null;
        }

        pairSignatureSet.add(pairSignature);
        ValidationScanResult result = new ValidationScanResult();
        result.setIssueType(GovernanceConstants.ISSUE_TYPE_CONFLICT);
        result.setPrimaryKnowledgeId(leftEntity.getId());
        List<Long> relatedIdList = new ArrayList<Long>();
        relatedIdList.add(rightEntity.getId());
        result.setRelatedKnowledgeIds(relatedIdList);
        result.setKnowledgeType(MemoryConstants.KNOWLEDGE_TYPE_RULE);
        result.setScore(bestOverlap);
        result.setSuggestedAction(GovernanceConstants.ACTION_REVIEW_CONFLICT);
        JSONObject metadata = new JSONObject();
        metadata.set("module", leftEntity.getModule());
        metadata.set("leftConstraintSnippet", leftSnippet);
        metadata.set("rightConstraintSnippet", rightSnippet);
        metadata.set("overlapScore", bestOverlap);
        result.setMetadataJson(metadata.toString());
        return result;
    }

    private List<String> extractConstraintTexts(List<FactBlock> factList) {
        List<String> textList = new ArrayList<String>();
        if (factList == null) {
            return textList;
        }
        for (FactBlock factBlock : factList) {
            if (factBlock == null || StringUtils.isBlank(factBlock.getText())) {
                continue;
            }
            String blockType = StringUtils.defaultString(factBlock.getType());
            if ("constraint".equals(blockType) || "rule".equals(blockType)) {
                textList.add(factBlock.getText().trim());
            }
        }
        return textList;
    }

    private boolean containsNegation(String text) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        for (String keyword : NEGATION_KEYWORDS) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算两段文本的字符级 Jaccard 相似度（简化版）
     */
    private double calculateTextOverlap(String leftText, String rightText) {
        if (StringUtils.isBlank(leftText) || StringUtils.isBlank(rightText)) {
            return 0D;
        }
        String normalizedLeft = normalizeText(leftText);
        String normalizedRight = normalizeText(rightText);
        if (normalizedLeft.length() < 8 || normalizedRight.length() < 8) {
            return 0D;
        }
        Set<String> leftGramSet = buildCharacterGramSet(normalizedLeft, 3);
        Set<String> rightGramSet = buildCharacterGramSet(normalizedRight, 3);
        if (leftGramSet.isEmpty() || rightGramSet.isEmpty()) {
            return 0D;
        }
        int intersectionCount = 0;
        for (String gram : leftGramSet) {
            if (rightGramSet.contains(gram)) {
                intersectionCount++;
            }
        }
        int unionCount = leftGramSet.size() + rightGramSet.size() - intersectionCount;
        return unionCount == 0 ? 0D : (double) intersectionCount / (double) unionCount;
    }

    private Set<String> buildCharacterGramSet(String text, int gramSize) {
        Set<String> gramSet = new HashSet<String>();
        if (text.length() < gramSize) {
            gramSet.add(text);
            return gramSet;
        }
        for (int index = 0; index <= text.length() - gramSize; index++) {
            gramSet.add(text.substring(index, index + gramSize));
        }
        return gramSet;
    }

    private String normalizeText(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private double calculateIncompleteScore(KnowledgeAggregate aggregate, Integer factCount) {
        int safeFactCount = factCount == null ? 0 : factCount.intValue();
        if (aggregate == null) {
            return 0.8D;
        }
        List<String> missingList = listMissingFactTypes(aggregate);
        double baseScore = 0.5D + missingList.size() * 0.15D;
        if (safeFactCount <= GovernanceConstants.FRAGMENT_MAX_FACT_COUNT) {
            baseScore += 0.2D;
        }
        return Math.min(baseScore, 0.99D);
    }

    private List<String> listMissingFactTypes(KnowledgeAggregate aggregate) {
        List<String> missingList = new ArrayList<String>();
        if (aggregate == null) {
            missingList.add("facts");
            return missingList;
        }
        String knowledgeType = aggregate.getKnowledgeType();
        if (MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE.equals(knowledgeType)) {
            if (!hasFactType(aggregate.getFacts(), "observation", "outcome", "evidence")) {
                missingList.add("observation");
            }
            if (!hasFactType(aggregate.getFacts(), "decision")) {
                missingList.add("decision");
            }
            if (!hasFactType(aggregate.getFacts(), "action")) {
                missingList.add("action");
            }
        } else if (MemoryConstants.KNOWLEDGE_TYPE_RULE.equals(knowledgeType)) {
            if (!hasFactType(aggregate.getFacts(), "constraint", "rule")) {
                missingList.add("constraint");
            }
        } else if (MemoryConstants.KNOWLEDGE_TYPE_WORKFLOW.equals(knowledgeType)) {
            if (!hasFactType(aggregate.getFacts(), "action")) {
                missingList.add("action");
            }
        }
        if (aggregate.getArtifacts() == null || aggregate.getArtifacts().isEmpty()) {
            missingList.add("artifact");
        }
        if (StringUtils.isBlank(aggregate.getModule()) && StringUtils.isBlank(aggregate.getRepository())) {
            missingList.add("scope");
        }
        return missingList;
    }

    private boolean hasFactType(List<FactBlock> factList, String... types) {
        if (factList == null || types == null) {
            return false;
        }
        for (FactBlock factBlock : factList) {
            if (factBlock == null || StringUtils.isBlank(factBlock.getType())) {
                continue;
            }
            for (String type : types) {
                if (type.equals(factBlock.getType())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String resolveIncompleteSuggestedAction(String knowledgeType) {
        if (MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE.equals(knowledgeType)) {
            return GovernanceConstants.ACTION_MERGE_OPTIMIZE;
        }
        return GovernanceConstants.ACTION_COMPLETE_MANUALLY;
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
            return null;
        }
    }

    private String buildPairSignature(Long leftId, Long rightId) {
        long smallerId = leftId < rightId ? leftId : rightId;
        long largerId = leftId < rightId ? rightId : leftId;
        return smallerId + ":" + largerId;
    }
}
