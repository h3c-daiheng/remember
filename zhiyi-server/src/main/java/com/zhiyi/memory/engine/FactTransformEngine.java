package com.zhiyi.memory.engine;

import com.zhiyi.common.BusinessException;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.domain.FactBlock;
import com.zhiyi.memory.domain.KnowledgeDraftContent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fact 转换引擎：Capture 草稿 Fact → Rule / Workflow / Decision 草稿 Fact
 */
@Component
public class FactTransformEngine {

    /**
     * 将 Capture 草稿内容转换为目标知识类型的 Fact 结构
     */
    public KnowledgeDraftContent transform(KnowledgeDraftContent sourceContent, String targetType) {
        if (sourceContent == null) {
            throw new BusinessException(400, "草稿内容为空");
        }
        if (MemoryConstants.KNOWLEDGE_TYPE_RULE.equals(targetType)) {
            return transformToRule(sourceContent);
        }
        if (MemoryConstants.KNOWLEDGE_TYPE_WORKFLOW.equals(targetType)) {
            return transformToWorkflow(sourceContent);
        }
        if (MemoryConstants.KNOWLEDGE_TYPE_DECISION.equals(targetType)) {
            return transformToDecision(sourceContent);
        }
        throw new BusinessException(400, "不支持的目标类型: " + targetType);
    }

    /**
     * 转为 Rule：保留 rule/constraint，action 可选保留为 evidence
     */
    private KnowledgeDraftContent transformToRule(KnowledgeDraftContent sourceContent) {
        KnowledgeDraftContent target = copyMeta(sourceContent);
        target.setTitle(buildRuleTitle(sourceContent));
        List<FactBlock> facts = new ArrayList<FactBlock>();
        if (sourceContent.getFacts() != null) {
            for (FactBlock factBlock : sourceContent.getFacts()) {
                if (factBlock == null || StringUtils.isBlank(factBlock.getText())) {
                    continue;
                }
                String type = StringUtils.defaultString(factBlock.getType());
                if ("rule".equals(type) || "constraint".equals(type)) {
                    facts.add(copyFact(factBlock));
                } else if ("action".equals(type)) {
                    FactBlock evidenceFact = copyFact(factBlock);
                    evidenceFact.setType("evidence");
                    facts.add(evidenceFact);
                }
            }
        }
        if (facts.isEmpty()) {
            throw new BusinessException(400, "草稿中无可用 rule/constraint 内容，无法转为 Rule");
        }
        target.setFacts(facts);
        return target;
    }

    /**
     * 转为 Workflow：rule 转为有序 action，保留 constraint 与 action
     */
    private KnowledgeDraftContent transformToWorkflow(KnowledgeDraftContent sourceContent) {
        KnowledgeDraftContent target = copyMeta(sourceContent);
        target.setTitle(buildWorkflowTitle(sourceContent));
        List<FactBlock> facts = new ArrayList<FactBlock>();
        if (sourceContent.getFacts() != null) {
            for (FactBlock factBlock : sourceContent.getFacts()) {
                if (factBlock == null || StringUtils.isBlank(factBlock.getText())) {
                    continue;
                }
                String type = StringUtils.defaultString(factBlock.getType());
                if ("rule".equals(type) || "action".equals(type)) {
                    FactBlock actionFact = copyFact(factBlock);
                    actionFact.setType("action");
                    facts.add(actionFact);
                } else if ("constraint".equals(type)) {
                    facts.add(copyFact(factBlock));
                }
            }
        }
        if (facts.isEmpty()) {
            throw new BusinessException(400, "草稿中无可用 action/constraint 内容，无法转为 Workflow");
        }
        target.setFacts(facts);
        return target;
    }

    /**
     * 转为 Decision：保留 decision/evidence/constraint，observation 转为 evidence
     */
    private KnowledgeDraftContent transformToDecision(KnowledgeDraftContent sourceContent) {
        KnowledgeDraftContent target = copyMeta(sourceContent);
        target.setTitle(buildDecisionTitle(sourceContent));
        List<FactBlock> facts = new ArrayList<FactBlock>();
        if (sourceContent.getFacts() != null) {
            for (FactBlock factBlock : sourceContent.getFacts()) {
                if (factBlock == null || StringUtils.isBlank(factBlock.getText())) {
                    continue;
                }
                String type = StringUtils.defaultString(factBlock.getType());
                if ("decision".equals(type) || "evidence".equals(type) || "constraint".equals(type)) {
                    facts.add(copyFact(factBlock));
                } else if ("observation".equals(type) || "action".equals(type)) {
                    FactBlock evidenceFact = copyFact(factBlock);
                    evidenceFact.setType("evidence");
                    facts.add(evidenceFact);
                } else if ("rule".equals(type)) {
                    FactBlock constraintFact = copyFact(factBlock);
                    constraintFact.setType("constraint");
                    facts.add(constraintFact);
                }
            }
        }
        if (facts.isEmpty()) {
            throw new BusinessException(400, "草稿中无可用 decision/evidence 内容，无法转为 Decision");
        }
        target.setFacts(facts);
        return target;
    }

    /**
     * 提取 Capture 草稿中可合并到 Rule 的 rule/constraint Fact
     */
    public List<FactBlock> extractRuleFacts(KnowledgeDraftContent sourceContent) {
        List<FactBlock> facts = new ArrayList<FactBlock>();
        if (sourceContent == null || sourceContent.getFacts() == null) {
            return facts;
        }
        for (FactBlock factBlock : sourceContent.getFacts()) {
            if (factBlock == null || StringUtils.isBlank(factBlock.getText())) {
                continue;
            }
            String type = StringUtils.defaultString(factBlock.getType());
            if ("rule".equals(type) || "constraint".equals(type)) {
                facts.add(copyFact(factBlock));
            }
        }
        return facts;
    }

    private KnowledgeDraftContent copyMeta(KnowledgeDraftContent sourceContent) {
        KnowledgeDraftContent target = new KnowledgeDraftContent();
        target.setProject(sourceContent.getProject());
        target.setModule(sourceContent.getModule());
        target.setRepository(sourceContent.getRepository());
        target.setLanguage(sourceContent.getLanguage());
        target.setFramework(sourceContent.getFramework());
        target.setTags(sourceContent.getTags());
        target.setArtifacts(sourceContent.getArtifacts());
        return target;
    }

    private FactBlock copyFact(FactBlock source) {
        FactBlock target = new FactBlock();
        target.setType(source.getType());
        target.setText(source.getText());
        return target;
    }

    /**
     * 生成 Rule 标题：[Rule] {module} {首条 rule 文本前 40 字}
     */
    private String buildRuleTitle(KnowledgeDraftContent sourceContent) {
        String module = StringUtils.defaultString(sourceContent.getModule(), "通用");
        String firstRuleText = findFirstFactText(sourceContent, "rule");
        if (StringUtils.isBlank(firstRuleText)) {
            firstRuleText = findFirstFactText(sourceContent, "constraint");
        }
        if (StringUtils.isBlank(firstRuleText)) {
            firstRuleText = StringUtils.defaultString(sourceContent.getTitle(), "未命名规范");
        }
        String snippet = firstRuleText.length() > 40 ? firstRuleText.substring(0, 40) : firstRuleText;
        return "[Rule] " + module + " " + snippet;
    }

    private String buildWorkflowTitle(KnowledgeDraftContent sourceContent) {
        String module = StringUtils.defaultString(sourceContent.getModule(), "通用");
        String title = StringUtils.defaultString(sourceContent.getTitle(), "操作流程");
        return "[Workflow] " + module + " " + title;
    }

    /**
     * 生成 Decision 标题：[Decision] {module} {首条 decision 文本前 40 字}
     */
    private String buildDecisionTitle(KnowledgeDraftContent sourceContent) {
        String module = StringUtils.defaultString(sourceContent.getModule(), "通用");
        String firstDecisionText = findFirstFactText(sourceContent, "decision");
        if (StringUtils.isBlank(firstDecisionText)) {
            firstDecisionText = findFirstFactText(sourceContent, "evidence");
        }
        if (StringUtils.isBlank(firstDecisionText)) {
            firstDecisionText = StringUtils.defaultString(sourceContent.getTitle(), "未命名决策");
        }
        String snippet = firstDecisionText.length() > 40 ? firstDecisionText.substring(0, 40) : firstDecisionText;
        return "[Decision] " + module + " " + snippet;
    }

    private String findFirstFactText(KnowledgeDraftContent content, String type) {
        if (content.getFacts() == null) {
            return null;
        }
        for (FactBlock factBlock : content.getFacts()) {
            if (factBlock != null && type.equals(factBlock.getType()) && StringUtils.isNotBlank(factBlock.getText())) {
                return factBlock.getText().trim();
            }
        }
        return null;
    }
}
