package com.zhiyi.memory.util;

import com.zhiyi.memory.domain.ArtifactDto;
import com.zhiyi.memory.domain.FactBlock;
import com.zhiyi.memory.domain.KnowledgeAggregate;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeMarkdownSerializerTest {

    private KnowledgeAggregate sample() {
        KnowledgeAggregate agg = new KnowledgeAggregate();
        agg.setTitle("如何处理 504 超时");
        agg.setKnowledgeType("experience");
        agg.setProject("zhiyi-server");
        agg.setModule("memory");
        agg.setRepository("zhiyi-server");
        agg.setLanguage("java");
        agg.setFramework("spring-boot");
        agg.setTags(Arrays.asList("memory", "timeout"));
        agg.setLifecycleStatus(1);

        FactBlock obs = new FactBlock();
        obs.setType("observation");
        obs.setText("调用模型网关偶发 504");
        FactBlock act = new FactBlock();
        act.setType("action");
        act.setText("加重试与降级");
        agg.setFacts(Arrays.asList(obs, act));

        ArtifactDto art = new ArtifactDto();
        art.setArtifactType("commit");
        art.setArtifactRole("origin");
        art.setContentRef("588e6da");
        art.setArtifactUrl("https://example.com/commit/588e6da");
        agg.setArtifacts(Collections.singletonList(art));
        return agg;
    }

    @Test
    void serializeOne_should_render_frontmatter_facts_and_artifacts() {
        String md = KnowledgeMarkdownSerializer.serializeOne(sample());

        assertTrue(md.contains("title: \"如何处理 504 超时\""), md);
        assertTrue(md.contains("knowledgeType: experience"), md);
        assertTrue(md.contains("tags: [\"memory\",\"timeout\"]"), md);
        assertTrue(md.contains("### observation"), md);
        assertTrue(md.contains("调用模型网关偶发 504"), md);
        assertTrue(md.contains("### action"), md);
        assertTrue(md.contains("- [commit] origin: 588e6da (https://example.com/commit/588e6da)"), md);
    }

    @Test
    void serializeWorkspace_should_render_header_and_wrap_each_knowledge() {
        String md = KnowledgeMarkdownSerializer.serializeWorkspace(
                "默认工作空间", "2026-07-28 10:00", Collections.singletonList(sample()));

        assertTrue(md.contains("# 工作空间「默认工作空间」知识导出"), md);
        assertTrue(md.contains("共 1 条"), md);
        assertTrue(md.contains("<!-- KNOWLEDGE START -->"), md);
        assertTrue(md.contains("<!-- KNOWLEDGE END -->"), md);
    }
}
