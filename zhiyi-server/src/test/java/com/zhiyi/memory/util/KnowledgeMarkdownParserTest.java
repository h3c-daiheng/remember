package com.zhiyi.memory.util;

import com.zhiyi.common.BusinessException;
import com.zhiyi.memory.domain.KnowledgeSaveRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeMarkdownParserTest {

    private static final String BLOCK = ""
            + "<!-- KNOWLEDGE START -->\n"
            + "---\n"
            + "title: \"如何处理 504 超时\"\n"
            + "knowledgeType: rule\n"
            + "project: zhiyi-server\n"
            + "module: memory\n"
            + "tags: [\"memory\", \"timeout\"]\n"
            + "lifecycleStatus: 1\n"
            + "---\n\n"
            + "## Facts\n\n"
            + "### observation\n"
            + "调用网关偶发 504\n\n"
            + "### action\n"
            + "加重试\n"
            + "## Artifacts\n"
            + "- [commit] origin: 588e6da (https://example.com/c/588e6da)\n"
            + "<!-- KNOWLEDGE END -->\n";

    @Test
    void splitBlocks_should_extract_knowledge_blocks_ignoring_header() {
        String md = "# 工作空间「x」知识导出\n\n> 导出时间:t\n\n" + BLOCK + BLOCK;
        List<String> blocks = KnowledgeMarkdownParser.splitBlocks(md);
        assertEquals(2, blocks.size());
    }

    @Test
    void parseBlock_should_map_frontmatter_facts_artifacts() {
        KnowledgeSaveRequest req = KnowledgeMarkdownParser.parseBlock(BLOCK);

        assertEquals("如何处理 504 超时", req.getTitle());
        assertEquals("rule", req.getKnowledgeType());
        assertEquals("zhiyi-server", req.getProject());
        assertEquals(2, req.getTags().size());
        assertTrue(req.getTags().contains("timeout"));
        assertEquals(2, req.getFacts().size());
        assertEquals("observation", req.getFacts().get(0).getType());
        assertEquals("调用网关偶发 504", req.getFacts().get(0).getText().trim());
        assertEquals("action", req.getFacts().get(1).getType());
        assertEquals(1, req.getArtifacts().size());
        assertEquals("commit", req.getArtifacts().get(0).getArtifactType());
        assertEquals("588e6da", req.getArtifacts().get(0).getContentRef());
        assertEquals("https://example.com/c/588e6da", req.getArtifacts().get(0).getArtifactUrl());
    }

    @Test
    void parseBlock_should_throw_when_title_missing() {
        String bad = "<!-- KNOWLEDGE START -->\n---\nknowledgeType: experience\n---\n\n## Facts\n\n### observation\nx\n<!-- KNOWLEDGE END -->\n";
        assertThrows(BusinessException.class, () -> KnowledgeMarkdownParser.parseBlock(bad));
    }

    @Test
    void parseBlock_should_throw_when_no_fact() {
        String bad = "<!-- KNOWLEDGE START -->\n---\ntitle: \"t\"\nknowledgeType: experience\n---\n\n<!-- KNOWLEDGE END -->\n";
        assertThrows(BusinessException.class, () -> KnowledgeMarkdownParser.parseBlock(bad));
    }
}
