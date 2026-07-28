package com.zhiyi.memory.engine;

import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.domain.ArtifactDto;
import com.zhiyi.memory.domain.CaptureChecklistEvidenceSpan;
import com.zhiyi.memory.domain.CaptureRouteChecklistHint;
import com.zhiyi.memory.domain.CaptureSimilarKnowledgeHint;
import com.zhiyi.memory.domain.FactBlock;
import com.zhiyi.memory.domain.KnowledgeDraftContent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Capture Review 检查清单规则预审引擎：对 R1–R4 / Q1–Q6 / C1–C3 做 AI 预填，
 * 不替代人工勾选，仅输出倾向结论与证据片段供 Review 人复核
 */
@Component
public class CaptureReviewAssistEngine {

    /** R1：根因为「未遵守既有规范」时的常见表述 */
    private static final String[] RULE_VIOLATION_KEYWORDS = {
            "违反规范", "未遵守", "没有遵循", "不按规范", "违反了", "未按规范", "违背规范"
    };

    /** R2：根因为「规范缺失/不清」时的常见表述 */
    private static final String[] DOC_GAP_KEYWORDS = {
            "规范缺失", "文档缺失", "没有说明", "缺少规范", "文档不清", "应写入规范",
            "应补充文档", "文档未说明", "规范未覆盖", "没有文档"
    };

    /** Q1：空泛 observation 的常见开头 */
    private static final String[] VAGUE_OBSERVATION_PREFIXES = {
            "完成了", "已修复", "已解决", "done", "fixed", "已完成", "处理完毕"
    };

    /** Q5：constraint 中含适用/失效条件的常见词 */
    private static final String[] CONSTRAINT_CONDITION_KEYWORDS = {
            "适用", "失效", "当", "若", "除非", "条件", "场景", "仅在", "不适用于"
    };

    /** Q6 / Q2：参与重复检测的 Fact 类型 */
    private static final List<String> DEDUP_FACT_TYPES = Arrays.asList(
            "task", "observation", "decision", "action"
    );

    /** 文本重复判定：重叠比例阈值 */
    private static final double DUPLICATE_OVERLAP_THRESHOLD = 0.55D;

    /** 文本重复判定：公共片段最小长度 */
    private static final int DUPLICATE_SNIPPET_MIN_LENGTH = 20;

    /** R4：与已有 Rule 高相似时倾向 fail */
    private static final double RULE_DUPLICATE_FAIL_THRESHOLD = 0.85D;

    /** R4：与已有 Rule 中等相似时倾向 warn */
    private static final double RULE_DUPLICATE_WARN_THRESHOLD = 0.65D;

    /** C3：标题最大建议长度 */
    private static final int TITLE_MAX_LENGTH = 80;

    /**
     * 基于规则分析草稿，产出全量检查清单预审（13 项）
     */
    public List<CaptureRouteChecklistHint> analyze(KnowledgeDraftContent content,
                                                   List<CaptureSimilarKnowledgeHint> similarKnowledgeList) {
        List<CaptureRouteChecklistHint> hintList = new ArrayList<CaptureRouteChecklistHint>();
        hintList.add(analyzeRouteR1(content));
        hintList.add(analyzeRouteR2(content));
        hintList.add(analyzeRouteR3(content));
        hintList.add(analyzeRouteR4(similarKnowledgeList));
        hintList.add(analyzeQualityQ1(content));
        hintList.add(analyzeQualityQ2(content));
        hintList.add(analyzeQualityQ3(content));
        hintList.add(analyzeQualityQ4(content));
        hintList.add(analyzeQualityQ5(content));
        hintList.add(analyzeQualityQ6(content));
        hintList.add(analyzeContextC1(content));
        hintList.add(analyzeContextC2(content));
        hintList.add(analyzeContextC3(content));
        return hintList;
    }

    /**
     * R1：根因是否为「未遵守既有规范」
     */
    private CaptureRouteChecklistHint analyzeRouteR1(KnowledgeDraftContent content) {
        CaptureRouteChecklistHint hint = createHint("R1");
        if (content == null || content.getFacts() == null || content.getFacts().isEmpty()) {
            hint.setStatus("warn");
            hint.setEvidence("草稿无 Fact 内容，无法判断根因类型");
            return hint;
        }

        for (int index = 0; index < content.getFacts().size(); index++) {
            FactBlock factBlock = content.getFacts().get(index);
            if (factBlock == null || StringUtils.isBlank(factBlock.getText())) {
                continue;
            }
            String matchedKeyword = findFirstKeyword(factBlock.getText(), RULE_VIOLATION_KEYWORDS);
            if (matchedKeyword != null) {
                hint.setStatus("fail");
                hint.setEvidence("observation/decision 含「" + matchedKeyword + "」，更像执行问题而非经验");
                hint.getEvidenceSpans().add(buildEvidenceSpan(index, factBlock, matchedKeyword, "R1"));
                return hint;
            }
        }

        hint.setStatus("pass");
        hint.setEvidence("未发现「违反/未遵守规范」类表述");
        return hint;
    }

    /**
     * R2：根因是否为「规范缺失/不清」
     */
    private CaptureRouteChecklistHint analyzeRouteR2(KnowledgeDraftContent content) {
        CaptureRouteChecklistHint hint = createHint("R2");
        if (content == null || content.getFacts() == null || content.getFacts().isEmpty()) {
            hint.setStatus("warn");
            hint.setEvidence("草稿无 Fact 内容，无法判断是否为文档缺口");
            return hint;
        }

        for (int index = 0; index < content.getFacts().size(); index++) {
            FactBlock factBlock = content.getFacts().get(index);
            if (factBlock == null || StringUtils.isBlank(factBlock.getText())) {
                continue;
            }
            String matchedKeyword = findFirstKeyword(factBlock.getText(), DOC_GAP_KEYWORDS);
            if (matchedKeyword != null) {
                hint.setStatus("fail");
                hint.setEvidence("内容含「" + matchedKeyword + "」，更宜补 Rule/文档而非记经验");
                hint.getEvidenceSpans().add(buildEvidenceSpan(index, factBlock, matchedKeyword, "R2"));
                return hint;
            }
        }

        hint.setStatus("pass");
        hint.setEvidence("未发现「规范/文档缺失」类表述");
        return hint;
    }

    /**
     * R3：是否包含真实决策与取舍
     */
    private CaptureRouteChecklistHint analyzeRouteR3(KnowledgeDraftContent content) {
        CaptureRouteChecklistHint hint = createHint("R3");
        List<FactBlock> facts = content == null ? null : content.getFacts();
        if (facts == null || facts.isEmpty()) {
            hint.setStatus("fail");
            hint.setEvidence("无 Fact Block，缺少 decision 决策信息");
            return hint;
        }

        int decisionCount = 0;
        int actionCount = 0;
        FactBlock strongestDecision = null;
        int strongestDecisionIndex = -1;

        for (int index = 0; index < facts.size(); index++) {
            FactBlock factBlock = facts.get(index);
            if (factBlock == null || StringUtils.isBlank(factBlock.getType())) {
                continue;
            }
            if ("decision".equals(factBlock.getType())) {
                decisionCount++;
                if (strongestDecision == null
                        || StringUtils.length(factBlock.getText()) > StringUtils.length(strongestDecision.getText())) {
                    strongestDecision = factBlock;
                    strongestDecisionIndex = index;
                }
            }
            if ("action".equals(factBlock.getType())) {
                actionCount++;
            }
        }

        if (decisionCount == 0) {
            hint.setStatus(actionCount >= 2 ? "fail" : "warn");
            hint.setEvidence(actionCount >= 2
                    ? "以 action 步骤为主，缺少独立 decision"
                    : "未发现 decision Fact，需人工确认是否有方案取舍");
            return hint;
        }

        if (strongestDecision != null && StringUtils.length(StringUtils.trim(strongestDecision.getText())) < 15) {
            hint.setStatus("warn");
            hint.setEvidence("decision 过短，可能未说明「为什么不用其他方案」");
            hint.getEvidenceSpans().add(buildEvidenceSpan(
                    strongestDecisionIndex, strongestDecision,
                    truncateSnippet(strongestDecision.getText(), 80), "R3"));
            return hint;
        }

        hint.setStatus("pass");
        hint.setEvidence("含 " + decisionCount + " 条 decision，具备方案取舍信息");
        return hint;
    }

    /**
     * R4：是否与已有 Rule 重复（依赖向量检索结果）
     */
    private CaptureRouteChecklistHint analyzeRouteR4(List<CaptureSimilarKnowledgeHint> similarKnowledgeList) {
        CaptureRouteChecklistHint hint = createHint("R4");
        if (similarKnowledgeList == null || similarKnowledgeList.isEmpty()) {
            hint.setStatus("pass");
            hint.setEvidence("工作空间内未检索到高相似 Rule/知识");
            return hint;
        }

        CaptureSimilarKnowledgeHint topRuleHint = null;
        for (CaptureSimilarKnowledgeHint similarHint : similarKnowledgeList) {
            if (similarHint == null) {
                continue;
            }
            if (!MemoryConstants.KNOWLEDGE_TYPE_RULE.equals(similarHint.getKnowledgeType())) {
                continue;
            }
            if (topRuleHint == null
                    || similarHint.getSimilarityScore() > topRuleHint.getSimilarityScore()) {
                topRuleHint = similarHint;
            }
        }

        if (topRuleHint == null) {
            hint.setStatus("pass");
            hint.setEvidence("未发现高相似 Rule，仅有其他类型知识相近");
            return hint;
        }

        double similarityScore = topRuleHint.getSimilarityScore();
        String ruleTitle = StringUtils.defaultIfBlank(topRuleHint.getTitle(), "未命名 Rule");
        if (similarityScore >= RULE_DUPLICATE_FAIL_THRESHOLD) {
            hint.setStatus("fail");
            hint.setEvidence("与 Rule #" + topRuleHint.getKnowledgeId() + "「" + ruleTitle
                    + "」相似度 " + Math.round(similarityScore * 100) + "%，建议合并");
        } else if (similarityScore >= RULE_DUPLICATE_WARN_THRESHOLD) {
            hint.setStatus("warn");
            hint.setEvidence("与 Rule #" + topRuleHint.getKnowledgeId() + "「" + ruleTitle
                    + "」相似度 " + Math.round(similarityScore * 100) + "%，请核对是否重复");
        } else {
            hint.setStatus("pass");
            hint.setEvidence("与已有 Rule 相似度较低（最高 "
                    + Math.round(similarityScore * 100) + "%）");
        }
        return hint;
    }

    /**
     * Q1：observation 是否具体
     */
    private CaptureRouteChecklistHint analyzeQualityQ1(KnowledgeDraftContent content) {
        CaptureRouteChecklistHint hint = createHint("Q1");
        FactBlock observationFact = findFirstFactByType(content, "observation");
        if (observationFact == null) {
            hint.setStatus("warn");
            hint.setEvidence("缺少 observation Fact，无法验证现象是否具体");
            return hint;
        }

        int observationIndex = findFirstFactIndexByType(content, "observation");
        String observationText = StringUtils.trim(observationFact.getText());
        if (StringUtils.length(observationText) < 20) {
            hint.setStatus("fail");
            hint.setEvidence("observation 过短，可能缺少现象/环境/报错细节");
            hint.getEvidenceSpans().add(buildEvidenceSpan(
                    observationIndex, observationFact, truncateSnippet(observationText, 80), "Q1"));
            return hint;
        }

        String lowerText = observationText.toLowerCase(Locale.ROOT);
        for (String prefix : VAGUE_OBSERVATION_PREFIXES) {
            if (lowerText.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                hint.setStatus("warn");
                hint.setEvidence("observation 以「" + prefix + "」开头，可能过于空泛");
                hint.getEvidenceSpans().add(buildEvidenceSpan(
                        observationIndex, observationFact, truncateSnippet(observationText, 80), "Q1"));
                return hint;
            }
        }

        hint.setStatus("pass");
        hint.setEvidence("observation 长度与表述较具体");
        return hint;
    }

    /**
     * Q2：decision 是否独立（不与 action 大段重复）
     */
    private CaptureRouteChecklistHint analyzeQualityQ2(KnowledgeDraftContent content) {
        CaptureRouteChecklistHint hint = createHint("Q2");
        FactBlock decisionFact = findFirstFactByType(content, "decision");
        FactBlock actionFact = findFirstFactByType(content, "action");

        if (decisionFact == null) {
            hint.setStatus("warn");
            hint.setEvidence("缺少 decision，无法比对是否与 action 重复");
            return hint;
        }
        if (actionFact == null || StringUtils.isBlank(actionFact.getText())) {
            hint.setStatus("pass");
            hint.setEvidence("无 action Fact，decision 独立性暂无冲突");
            return hint;
        }

        int decisionIndex = findFirstFactIndexByType(content, "decision");
        int actionIndex = findFirstFactIndexByType(content, "action");
        DuplicateMatch duplicateMatch = findDuplicateMatch(decisionFact.getText(), actionFact.getText());
        if (duplicateMatch != null) {
            hint.setStatus("fail");
            hint.setEvidence("decision 与 action 存在 "
                    + Math.round(duplicateMatch.getOverlapRatio() * 100) + "% 文本重复");
            hint.getEvidenceSpans().add(buildEvidenceSpan(
                    decisionIndex, decisionFact, duplicateMatch.getSnippet(), "Q2"));
            hint.getEvidenceSpans().add(buildEvidenceSpan(
                    actionIndex, actionFact, duplicateMatch.getSnippet(), "Q2"));
            return hint;
        }

        hint.setStatus("pass");
        hint.setEvidence("decision 与 action 无明显大段重复");
        return hint;
    }

    /**
     * Q3：action 是否可执行
     */
    private CaptureRouteChecklistHint analyzeQualityQ3(KnowledgeDraftContent content) {
        CaptureRouteChecklistHint hint = createHint("Q3");
        FactBlock actionFact = findFirstFactByType(content, "action");
        if (actionFact == null) {
            hint.setStatus("warn");
            hint.setEvidence("缺少 action Fact，他人可能难以复现改动");
            return hint;
        }

        int actionIndex = findFirstFactIndexByType(content, "action");
        String actionText = StringUtils.trim(actionFact.getText());
        if (StringUtils.length(actionText) < 15) {
            hint.setStatus("warn");
            hint.setEvidence("action 过短，步骤可能不够可执行");
            hint.getEvidenceSpans().add(buildEvidenceSpan(
                    actionIndex, actionFact, truncateSnippet(actionText, 80), "Q3"));
            return hint;
        }

        hint.setStatus("pass");
        hint.setEvidence("含 action 且步骤描述较完整");
        return hint;
    }

    /**
     * Q4：是否有 outcome 或 evidence 验证
     */
    private CaptureRouteChecklistHint analyzeQualityQ4(KnowledgeDraftContent content) {
        CaptureRouteChecklistHint hint = createHint("Q4");
        boolean hasOutcome = findFirstFactByType(content, "outcome") != null;
        boolean hasEvidence = findFirstFactByType(content, "evidence") != null;
        boolean hasObservation = findFirstFactByType(content, "observation") != null;

        if (hasOutcome || hasEvidence) {
            hint.setStatus("pass");
            hint.setEvidence(hasOutcome ? "含 outcome 验证结果" : "含 evidence 支撑依据");
            return hint;
        }

        if (hasObservation) {
            hint.setStatus("warn");
            hint.setEvidence("仅有 observation，缺少 outcome/evidence 验证段落");
            return hint;
        }

        hint.setStatus("fail");
        hint.setEvidence("缺少 outcome/evidence，无法确认实施结果");
        return hint;
    }

    /**
     * Q5：constraint 是否标明适用/失效条件
     */
    private CaptureRouteChecklistHint analyzeQualityQ5(KnowledgeDraftContent content) {
        CaptureRouteChecklistHint hint = createHint("Q5");
        FactBlock constraintFact = findFirstFactByType(content, "constraint");
        if (constraintFact == null) {
            hint.setStatus("warn");
            hint.setEvidence("无 constraint Fact；若方案有边界条件建议补充");
            return hint;
        }

        int constraintIndex = findFirstFactIndexByType(content, "constraint");
        String constraintText = StringUtils.defaultString(constraintFact.getText());
        String matchedKeyword = findFirstKeyword(constraintText, CONSTRAINT_CONDITION_KEYWORDS);
        if (matchedKeyword != null) {
            hint.setStatus("pass");
            hint.setEvidence("constraint 含「" + matchedKeyword + "」等条件表述");
            return hint;
        }

        hint.setStatus("warn");
        hint.setEvidence("constraint 未明确适用/失效条件，建议补充");
        hint.getEvidenceSpans().add(buildEvidenceSpan(
                constraintIndex, constraintFact, truncateSnippet(constraintText, 80), "Q5"));
        return hint;
    }

    /**
     * Q6：task/observation/decision/action 是否大段重复
     */
    private CaptureRouteChecklistHint analyzeQualityQ6(KnowledgeDraftContent content) {
        CaptureRouteChecklistHint hint = createHint("Q6");
        List<FactBlock> facts = content == null ? null : content.getFacts();
        if (facts == null || facts.isEmpty()) {
            hint.setStatus("warn");
            hint.setEvidence("无 Fact 内容，无法检测字段重复");
            return hint;
        }

        List<Integer> dedupFactIndexList = new ArrayList<Integer>();
        for (int index = 0; index < facts.size(); index++) {
            FactBlock factBlock = facts.get(index);
            if (factBlock != null && DEDUP_FACT_TYPES.contains(factBlock.getType())) {
                dedupFactIndexList.add(index);
            }
        }

        DuplicatePair duplicatePair = findFirstDuplicatePair(facts, dedupFactIndexList);
        if (duplicatePair != null) {
            hint.setStatus("fail");
            hint.setEvidence(duplicatePair.getLeftType() + " 与 " + duplicatePair.getRightType()
                    + " 存在 " + Math.round(duplicatePair.getOverlapRatio() * 100) + "% 文本重复");
            hint.getEvidenceSpans().add(buildEvidenceSpan(
                    duplicatePair.getLeftIndex(), facts.get(duplicatePair.getLeftIndex()),
                    duplicatePair.getSnippet(), "Q6"));
            hint.getEvidenceSpans().add(buildEvidenceSpan(
                    duplicatePair.getRightIndex(), facts.get(duplicatePair.getRightIndex()),
                    duplicatePair.getSnippet(), "Q6"));
            return hint;
        }

        hint.setStatus("pass");
        hint.setEvidence("task/observation/decision/action 未发现大段复制");
        return hint;
    }

    /**
     * C1：模块/仓库/标签是否便于 Recall 命中
     */
    private CaptureRouteChecklistHint analyzeContextC1(KnowledgeDraftContent content) {
        CaptureRouteChecklistHint hint = createHint("C1");
        if (content == null) {
            hint.setStatus("fail");
            hint.setEvidence("缺少上下文元信息");
            return hint;
        }

        boolean hasModule = StringUtils.isNotBlank(content.getModule());
        boolean hasRepository = StringUtils.isNotBlank(content.getRepository());
        boolean hasProject = StringUtils.isNotBlank(content.getProject());
        boolean hasTags = content.getTags() != null && !content.getTags().isEmpty();

        if (hasModule || hasRepository || hasTags) {
            hint.setStatus("pass");
            hint.setEvidence("已填写模块/仓库/标签中的至少一项");
            return hint;
        }
        if (hasProject) {
            hint.setStatus("warn");
            hint.setEvidence("仅有 project，建议补充 module/repository/tag 提升 Recall 命中");
            return hint;
        }

        hint.setStatus("fail");
        hint.setEvidence("缺少 module/repository/tag，Recall 可能难以命中");
        return hint;
    }

    /**
     * C2：关联产物是否有效（非空对象）
     */
    private CaptureRouteChecklistHint analyzeContextC2(KnowledgeDraftContent content) {
        CaptureRouteChecklistHint hint = createHint("C2");
        List<ArtifactDto> artifacts = content == null ? null : content.getArtifacts();
        if (artifacts == null || artifacts.isEmpty()) {
            hint.setStatus("warn");
            hint.setEvidence("未关联产物；关键改动建议补充 PR/commit 引用");
            return hint;
        }

        int validCount = 0;
        int emptyCount = 0;
        for (ArtifactDto artifact : artifacts) {
            if (artifact == null) {
                emptyCount++;
                continue;
            }
            if (isEmptyArtifact(artifact)) {
                emptyCount++;
                continue;
            }
            validCount++;
        }

        if (validCount == 0) {
            hint.setStatus("fail");
            hint.setEvidence("关联产物均为空或无效（" + emptyCount + " 项）");
            return hint;
        }
        if (emptyCount > 0) {
            hint.setStatus("warn");
            hint.setEvidence("有效产物 " + validCount + " 项，另有 " + emptyCount + " 项为空需清理");
            return hint;
        }

        hint.setStatus("pass");
        hint.setEvidence("关联产物 " + validCount + " 项均有效");
        return hint;
    }

    /**
     * C3：标题是否准确且 ≤80 字
     */
    private CaptureRouteChecklistHint analyzeContextC3(KnowledgeDraftContent content) {
        CaptureRouteChecklistHint hint = createHint("C3");
        String title = content == null ? "" : StringUtils.trim(content.getTitle());
        if (StringUtils.isBlank(title)) {
            hint.setStatus("fail");
            hint.setEvidence("标题为空，无法概括决策");
            return hint;
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            hint.setStatus("warn");
            hint.setEvidence("标题 " + title.length() + " 字，超过建议上限 " + TITLE_MAX_LENGTH + " 字");
            return hint;
        }

        hint.setStatus("pass");
        hint.setEvidence("标题 " + title.length() + " 字，长度合理");
        return hint;
    }

    private CaptureRouteChecklistHint createHint(String checklistId) {
        CaptureRouteChecklistHint hint = new CaptureRouteChecklistHint();
        hint.setId(checklistId);
        return hint;
    }

    private CaptureChecklistEvidenceSpan buildEvidenceSpan(int factIndex,
                                                           FactBlock factBlock,
                                                           String snippet,
                                                           String checklistId) {
        CaptureChecklistEvidenceSpan evidenceSpan = new CaptureChecklistEvidenceSpan();
        evidenceSpan.setFactIndex(factIndex);
        evidenceSpan.setFactType(factBlock == null ? null : factBlock.getType());
        evidenceSpan.setSnippet(snippet);
        evidenceSpan.setChecklistId(checklistId);
        return evidenceSpan;
    }

    private FactBlock findFirstFactByType(KnowledgeDraftContent content, String factType) {
        if (content == null || content.getFacts() == null) {
            return null;
        }
        for (FactBlock factBlock : content.getFacts()) {
            if (factBlock != null && factType.equals(factBlock.getType())) {
                return factBlock;
            }
        }
        return null;
    }

    private int findFirstFactIndexByType(KnowledgeDraftContent content, String factType) {
        if (content == null || content.getFacts() == null) {
            return -1;
        }
        for (int index = 0; index < content.getFacts().size(); index++) {
            FactBlock factBlock = content.getFacts().get(index);
            if (factBlock != null && factType.equals(factBlock.getType())) {
                return index;
            }
        }
        return -1;
    }

    private String findFirstKeyword(String text, String[] keywords) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return keyword;
            }
        }
        return null;
    }

    /**
     * 判断 Artifact 是否为空对象或无实质引用
     */
    private boolean isEmptyArtifact(ArtifactDto artifact) {
        String contentRef = StringUtils.trimToEmpty(artifact.getContentRef());
        String artifactUrl = StringUtils.trimToEmpty(artifact.getArtifactUrl());
        String artifactType = StringUtils.trimToEmpty(artifact.getArtifactType());
        if ("{}".equals(contentRef) || "{}".equals(artifactUrl)) {
            return true;
        }
        return StringUtils.isBlank(contentRef)
                && StringUtils.isBlank(artifactUrl)
                && StringUtils.isBlank(artifactType);
    }

    /**
     * 在指定 Fact 索引集合中查找第一对重复段落
     */
    private DuplicatePair findFirstDuplicatePair(List<FactBlock> facts, List<Integer> indexList) {
        for (int leftPointer = 0; leftPointer < indexList.size(); leftPointer++) {
            for (int rightPointer = leftPointer + 1; rightPointer < indexList.size(); rightPointer++) {
                int leftIndex = indexList.get(leftPointer);
                int rightIndex = indexList.get(rightPointer);
                FactBlock leftFact = facts.get(leftIndex);
                FactBlock rightFact = facts.get(rightIndex);
                if (leftFact == null || rightFact == null) {
                    continue;
                }
                DuplicateMatch duplicateMatch = findDuplicateMatch(leftFact.getText(), rightFact.getText());
                if (duplicateMatch != null) {
                    DuplicatePair duplicatePair = new DuplicatePair();
                    duplicatePair.setLeftIndex(leftIndex);
                    duplicatePair.setRightIndex(rightIndex);
                    duplicatePair.setLeftType(leftFact.getType());
                    duplicatePair.setRightType(rightFact.getType());
                    duplicatePair.setOverlapRatio(duplicateMatch.getOverlapRatio());
                    duplicatePair.setSnippet(duplicateMatch.getSnippet());
                    return duplicatePair;
                }
            }
        }
        return null;
    }

    /**
     * 检测两段文本是否存在大段重复，返回重叠片段与比例
     */
    private DuplicateMatch findDuplicateMatch(String leftText, String rightText) {
        String normalizedLeft = normalizeText(leftText);
        String normalizedRight = normalizeText(rightText);
        if (StringUtils.isBlank(normalizedLeft) || StringUtils.isBlank(normalizedRight)) {
            return null;
        }

        if (normalizedLeft.contains(normalizedRight) || normalizedRight.contains(normalizedLeft)) {
            String snippet = normalizedLeft.length() <= normalizedRight.length()
                    ? normalizedLeft : normalizedRight;
            if (snippet.length() < DUPLICATE_SNIPPET_MIN_LENGTH) {
                return null;
            }
            double overlapRatio = (double) Math.min(normalizedLeft.length(), normalizedRight.length())
                    / (double) Math.max(normalizedLeft.length(), normalizedRight.length());
            if (overlapRatio >= DUPLICATE_OVERLAP_THRESHOLD) {
                DuplicateMatch duplicateMatch = new DuplicateMatch();
                duplicateMatch.setOverlapRatio(overlapRatio);
                duplicateMatch.setSnippet(truncateSnippet(snippet, 120));
                return duplicateMatch;
            }
            return null;
        }

        String commonSnippet = findLongestCommonSubstring(normalizedLeft, normalizedRight);
        if (commonSnippet.length() < DUPLICATE_SNIPPET_MIN_LENGTH) {
            return null;
        }
        double overlapRatio = (double) commonSnippet.length()
                / (double) Math.min(normalizedLeft.length(), normalizedRight.length());
        if (overlapRatio < DUPLICATE_OVERLAP_THRESHOLD) {
            return null;
        }

        DuplicateMatch duplicateMatch = new DuplicateMatch();
        duplicateMatch.setOverlapRatio(overlapRatio);
        duplicateMatch.setSnippet(truncateSnippet(commonSnippet, 120));
        return duplicateMatch;
    }

    /**
     * 最长公共子串，用于 Q2/Q6 重复检测
     */
    private String findLongestCommonSubstring(String leftText, String rightText) {
        int leftLength = leftText.length();
        int rightLength = rightText.length();
        int[][] dynamicTable = new int[leftLength + 1][rightLength + 1];
        int maxLength = 0;
        int endIndex = 0;

        for (int leftIndex = 1; leftIndex <= leftLength; leftIndex++) {
            for (int rightIndex = 1; rightIndex <= rightLength; rightIndex++) {
                if (leftText.charAt(leftIndex - 1) == rightText.charAt(rightIndex - 1)) {
                    dynamicTable[leftIndex][rightIndex] = dynamicTable[leftIndex - 1][rightIndex - 1] + 1;
                    if (dynamicTable[leftIndex][rightIndex] > maxLength) {
                        maxLength = dynamicTable[leftIndex][rightIndex];
                        endIndex = leftIndex;
                    }
                } else {
                    dynamicTable[leftIndex][rightIndex] = 0;
                }
            }
        }

        if (maxLength <= 0) {
            return "";
        }
        return leftText.substring(endIndex - maxLength, endIndex);
    }

    private String normalizeText(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private String truncateSnippet(String text, int maxLength) {
        String normalizedText = normalizeText(text);
        if (normalizedText.length() <= maxLength) {
            return normalizedText;
        }
        return normalizedText.substring(0, maxLength) + "…";
    }

    /**
     * 重复匹配结果
     */
    private static final class DuplicateMatch {
        private double overlapRatio;
        private String snippet;

        public double getOverlapRatio() {
            return overlapRatio;
        }

        public void setOverlapRatio(double overlapRatio) {
            this.overlapRatio = overlapRatio;
        }

        public String getSnippet() {
            return snippet;
        }

        public void setSnippet(String snippet) {
            this.snippet = snippet;
        }
    }

    /**
     * 跨 Fact 重复对
     */
    private static final class DuplicatePair {
        private int leftIndex;
        private int rightIndex;
        private String leftType;
        private String rightType;
        private double overlapRatio;
        private String snippet;

        public int getLeftIndex() {
            return leftIndex;
        }

        public void setLeftIndex(int leftIndex) {
            this.leftIndex = leftIndex;
        }

        public int getRightIndex() {
            return rightIndex;
        }

        public void setRightIndex(int rightIndex) {
            this.rightIndex = rightIndex;
        }

        public String getLeftType() {
            return leftType;
        }

        public void setLeftType(String leftType) {
            this.leftType = leftType;
        }

        public String getRightType() {
            return rightType;
        }

        public void setRightType(String rightType) {
            this.rightType = rightType;
        }

        public double getOverlapRatio() {
            return overlapRatio;
        }

        public void setOverlapRatio(double overlapRatio) {
            this.overlapRatio = overlapRatio;
        }

        public String getSnippet() {
            return snippet;
        }

        public void setSnippet(String snippet) {
            this.snippet = snippet;
        }
    }
}
