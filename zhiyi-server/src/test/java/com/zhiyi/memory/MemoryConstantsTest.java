package com.zhiyi.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryConstantsTest {

    @Test
    void knowledgeTypeConstants_should_have_expected_values() {
        assertEquals("experience", MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE);
        assertEquals("rule", MemoryConstants.KNOWLEDGE_TYPE_RULE);
        assertEquals("workflow", MemoryConstants.KNOWLEDGE_TYPE_WORKFLOW);
        assertEquals("decision", MemoryConstants.KNOWLEDGE_TYPE_DECISION);
    }
}
