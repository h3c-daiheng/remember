package com.zhiyi.memory.engine;

import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.domain.DocumentImportQualityChecks;
import com.zhiyi.memory.domain.FactBlock;
import com.zhiyi.memory.domain.KnowledgeDraftContent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档抽取质量门控：对照经验成立门检查 Fact Blocks 完整性
 */
@Component
public class ExtractQualityGate {

    /**
     * 检查草稿 Fact Blocks 是否满足基本质量要求
     */
    public DocumentImportQualityChecks check(KnowledgeDraftContent draftContent, String suggestedType) {
        DocumentImportQualityChecks qualityChecks = new DocumentImportQualityChecks();
        List<String> warnings = new ArrayList<String>();

        if (draftContent == null || draftContent.getFacts() == null || draftContent.getFacts().isEmpty()) {
            warnings.add("草稿中无有效 Fact Block，请补充内容或更换文档");
            qualityChecks.setWarnings(warnings);
            return qualityChecks;
        }

        boolean hasObservation = false;
        boolean hasDecision = false;
        boolean hasOutcomeOrEvidence = false;
        boolean hasRule = false;

        for (FactBlock factBlock : draftContent.getFacts()) {
            if (factBlock == null || StringUtils.isBlank(factBlock.getType())) {
                continue;
            }
            String factType = factBlock.getType();
            if ("observation".equals(factType)) {
                hasObservation = true;
            }
            if ("decision".equals(factType)) {
                hasDecision = true;
            }
            if ("outcome".equals(factType) || "evidence".equals(factType)) {
                hasOutcomeOrEvidence = true;
            }
            if ("rule".equals(factType) || "constraint".equals(factType)) {
                hasRule = true;
            }
        }

        qualityChecks.setHasObservation(hasObservation);
        qualityChecks.setHasDecision(hasDecision);
        qualityChecks.setHasOutcomeOrEvidence(hasOutcomeOrEvidence);

        if (MemoryConstants.KNOWLEDGE_TYPE_RULE.equals(suggestedType)) {
            if (!hasRule) {
                warnings.add("建议转为 Rule，但缺少 rule/constraint 类型 Fact");
            }
        } else if (MemoryConstants.KNOWLEDGE_TYPE_DECISION.equals(suggestedType)) {
            if (!hasDecision) {
                warnings.add("建议转为 Decision，但缺少 decision 类型 Fact");
            }
        } else {
            if (!hasObservation) {
                warnings.add("Experience 建议至少包含 1 条 observation");
            }
            if (!hasDecision) {
                warnings.add("Experience 建议包含 decision，说明方案取舍");
            }
            if (!hasOutcomeOrEvidence) {
                warnings.add("Experience 建议包含 outcome 或 evidence 验证结果");
            }
        }

        qualityChecks.setWarnings(warnings);
        return qualityChecks;
    }
}
