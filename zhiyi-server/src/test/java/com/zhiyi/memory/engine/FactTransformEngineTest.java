package com.zhiyi.memory.engine;

import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.domain.FactBlock;
import com.zhiyi.memory.domain.KnowledgeDraftContent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FactTransformEngine 标题生成单测：Rule/Decision 应优先使用提交的 title，
 * 仅在 title 缺失时回退到首条 Fact 文本，避免用 fact.text[0] 覆盖用户提交的标题。
 */
class FactTransformEngineTest {

    private final FactTransformEngine engine = new FactTransformEngine();

    private FactBlock fact(String type, String text) {
        FactBlock factBlock = new FactBlock();
        factBlock.setType(type);
        factBlock.setText(text);
        return factBlock;
    }

    private KnowledgeDraftContent ruleDraft(String title) {
        KnowledgeDraftContent draft = new KnowledgeDraftContent();
        draft.setTitle(title);
        draft.setModule("支付");
        List<FactBlock> facts = new ArrayList<FactBlock>();
        facts.add(fact("rule", "所有金额必须以分为单位存储，禁止使用浮点数。"));
        facts.add(fact("constraint", "不允许在订单表直接存储元为单位的小数。"));
        draft.setFacts(facts);
        return draft;
    }

    @Test
    void transformToRule_shouldUseSubmittedTitleInsteadOfFirstFact() {
        KnowledgeDraftContent draft = ruleDraft("金额存储规范");

        KnowledgeDraftContent result = engine.transform(draft, MemoryConstants.KNOWLEDGE_TYPE_RULE);

        assertEquals("[Rule] 支付 金额存储规范", result.getTitle());
        assertFalse(result.getTitle().contains("所有金额必须以分为单位"),
                "标题不应回退为首条 rule 文本");
    }

    @Test
    void transformToRule_shouldFallbackToFirstRuleTextWhenTitleMissing() {
        KnowledgeDraftContent draft = ruleDraft(null);

        KnowledgeDraftContent result = engine.transform(draft, MemoryConstants.KNOWLEDGE_TYPE_RULE);

        assertTrue(result.getTitle().startsWith("[Rule] 支付 "));
        assertTrue(result.getTitle().contains("所有金额必须以分为单位"),
                "title 缺失时应回退到首条 rule 文本");
    }

    @Test
    void transformToRule_shouldTruncateLongTitle() {
        StringBuilder longTitleBuilder = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longTitleBuilder.append("长");
        }
        String longTitle = longTitleBuilder.toString();

        KnowledgeDraftContent result = engine.transform(ruleDraft(longTitle), MemoryConstants.KNOWLEDGE_TYPE_RULE);

        assertTrue(result.getTitle().startsWith("[Rule] 支付 "));
        assertTrue(result.getTitle().length() <= "[Rule] 支付 ".length() + 80);
    }

    @Test
    void transformToDecision_shouldUseSubmittedTitleInsteadOfFirstFact() {
        KnowledgeDraftContent draft = new KnowledgeDraftContent();
        draft.setTitle("采用消息队列异步落库");
        draft.setModule("订单");
        List<FactBlock> facts = new ArrayList<FactBlock>();
        facts.add(fact("decision", "高峰期订单写入走 MQ 异步，牺牲即时一致换吞吐。"));
        draft.setFacts(facts);

        KnowledgeDraftContent result = engine.transform(draft, MemoryConstants.KNOWLEDGE_TYPE_DECISION);

        assertEquals("[Decision] 订单 采用消息队列异步落库", result.getTitle());
        assertFalse(result.getTitle().contains("高峰期订单写入走 MQ"),
                "标题不应回退为首条 decision 文本");
    }

    @Test
    void transformToWorkflow_shouldKeepTitleBasedTitle() {
        KnowledgeDraftContent draft = new KnowledgeDraftContent();
        draft.setTitle("发布回滚流程");
        draft.setModule("运维");
        List<FactBlock> facts = new ArrayList<FactBlock>();
        facts.add(fact("action", "先摘流再回滚数据库。"));
        draft.setFacts(facts);

        KnowledgeDraftContent result = engine.transform(draft, MemoryConstants.KNOWLEDGE_TYPE_WORKFLOW);

        assertEquals("[Workflow] 运维 发布回滚流程", result.getTitle());
    }
}
