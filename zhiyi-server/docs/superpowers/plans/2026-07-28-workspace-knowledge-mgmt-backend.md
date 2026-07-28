# 工作空间与知识管理增强 — 后端实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 zhiyi-server 后端实现「删除工作空间、导出知识为 Markdown、批量导入经验、批量删除知识」4 个新能力,并修复单条删除的子表清理缺陷。

**Architecture:** 复用现有 `KnowledgeService` / `WorkspaceController` 模式。新增 Markdown 序列化/解析工具类(导入导出共用)、`WorkspaceService`(承载删空间级联清理)、`KnowledgeService` 三个新方法(导出/导入/批量删)。Service 层用 Mockito 单元测试覆盖逻辑,Controller 为薄端点。

**Tech Stack:** Java 1.8、Spring Boot 2.7.18、MyBatis-Plus 3.5.7、Hutool 5.8.26、JUnit 5 + Mockito(新增)。

## Global Constraints

- Java 1.8 语法约束(无 `var`、无 record、无 diamond 之外的新语法)。
- 异常统一抛 `BusinessException(code, message)`,经 `GlobalExceptionHandler` 转 **HTTP 200 + body.code=业务码**。测试断言看 `code` 非 0,不看 HTTP 状态。
- `knowledge` 表带 `@TableLogic`(`deleted` 字段),`deleteById` / wrapper `delete` 走**逻辑删除**;物理删需自定义 `@Delete` SQL。其余表无逻辑删除,mapper `delete(Wrapper)` 即物理删。
- `KnowledgeTagMapper` 是**普通接口**(非 BaseMapper),只有手写 SQL 方法,无继承的 `delete(Wrapper)`。
- `@MapperScan({"com.zhiyi.dao", "com.zhiyi.memory.dao"})`,新 Mapper 必须放这两个包下。
- 鉴权统一:`LoginContext.requireLoginUser(request)` 取 `LoginUserVO`,角色校验用 `WorkspaceMemberRole`。
- 导出仅**已发布**(`lifecycleStatus=1`)知识;导入统一创建为**草稿**(`publish=false`,`lifecycleStatus=0`),`sourceCaptureDraftId=null`。
- 删工作空间:**仅 owner** + `confirmName` 必须等于 `workspaceName`,物理级联清理。
- 批量删除:逐条独立(权限失败不阻塞其他),每条补子表清理;导入:整批事务(创建阶段失败全回滚)。
- 导入文件大小上限 `MemoryConstants.IMPORT_MAX_CONTENT_LENGTH = 100000`。

---

## Task 1: 搭建测试基础设施

项目当前**零测试基础设施**(无 src/test、pom 无测试依赖)。本任务引入 `spring-boot-starter-test`(含 JUnit 5 + Mockito + AssertJ)并用一个最小测试验证可运行。

**Files:**
- Modify: `pom.xml`(在 `</dependencies>` 前加测试依赖)
- Create: `src/test/java/com/zhiyi/memory/MemoryConstantsTest.java`

**Interfaces:**
- Produces: `mvn test` 可运行;Mockito 单元测试模式(`@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`)确立,供后续 task 复用。

- [ ] **Step 1: 加测试依赖**

在 `pom.xml` 的 `commons-lang3` 依赖之后、`</dependencies>` 之前插入:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
```

> Spring Boot 2.7.18 parent 已管理该依赖版本(含 JUnit 5.8、Mockito 4.5、AssertJ),无需显式版本。

- [ ] **Step 2: 写一个最小测试验证测试可运行**

Create `src/test/java/com/zhiyi/memory/MemoryConstantsTest.java`:

```java
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
```

- [ ] **Step 3: 运行测试验证通过**

Run: `mvn -q -Dtest=MemoryConstantsTest test`
Expected: BUILD SUCCESS,测试 1 通过。

- [ ] **Step 4: 提交**

```bash
git add pom.xml src/test/java/com/zhiyi/memory/MemoryConstantsTest.java
git commit -m "test: 引入 spring-boot-starter-test 搭建单测基础设施"
```

---

## Task 2: 修复单删子表清理缺陷

`KnowledgeService.deleteKnowledge`(`KnowledgeService.java:341`)只清了 relation + vector + 主表,**漏调 `deleteChildren`**,导致 fact / artifact / tag 成为孤儿;同时 feedback 也未清理。本期顺手修复,使单删与批量删行为一致。

**Files:**
- Modify: `src/main/java/com/zhiyi/memory/knowledge/KnowledgeService.java`(deleteKnowledge 方法,约 341-348 行)
- Test: `src/test/java/com/zhiyi/memory/knowledge/KnowledgeServiceDeleteTest.java`

**Interfaces:**
- Consumes: 现有 `deleteChildren`(`KnowledgeService.java:631`,清 fact/artifact/tag/vector)、`requireKnowledgeInWorkspace`、`requireDeletePermission`、`relationEngine.deleteRelationsByKnowledgeId`。
- Produces: 修复后的 `deleteKnowledge`,被 Task 7 批量删除复用。

- [ ] **Step 1: 写失败测试**

Create `src/test/java/com/zhiyi/memory/knowledge/KnowledgeServiceDeleteTest.java`:

```java
package com.zhiyi.memory.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.memory.engine.RetrievalEngine;
import com.zhiyi.memory.engine.RelationAsyncService;
import com.zhiyi.memory.entity.KnowledgeEntity;
import com.zhiyi.memory.engine.RelationEngine;
import com.zhiyi.memory.context.ContextNormalizer;
import com.zhiyi.memory.dao.KnowledgeArtifactMapper;
import com.zhiyi.memory.dao.KnowledgeFactMapper;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.dao.KnowledgeTagMapper;
import com.zhiyi.memory.dao.MemoryFeedbackMapper;
import com.zhiyi.memory.entity.MemoryFeedbackEntity;
import com.zhiyi.memory.graph.CascadeValidationService;
import com.zhiyi.memory.timeline.KnowledgeTimelineService;
import com.zhiyi.workspace.WorkspaceMemberRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceDeleteTest {

    @Mock private KnowledgeMapper knowledgeMapper;
    @Mock private KnowledgeFactMapper knowledgeFactMapper;
    @Mock private KnowledgeArtifactMapper knowledgeArtifactMapper;
    @Mock private KnowledgeTagMapper knowledgeTagMapper;
    @Mock private MemoryFeedbackMapper memoryFeedbackMapper;
    @Mock private RetrievalEngine retrievalEngine;
    @Mock private ContextNormalizer contextNormalizer;
    @Mock private RelationAsyncService relationAsyncService;
    @Mock private RelationEngine relationEngine;
    @Mock private KnowledgeTimelineService knowledgeTimelineService;
    @Mock private CascadeValidationService cascadeValidationService;
    @Mock private com.zhiyi.service.UserProfileService userProfileService;

    @InjectMocks private KnowledgeService knowledgeService;

    @Test
    void deleteKnowledge_should_clear_fact_artifact_tag_and_feedback_children() {
        Long knowledgeId = 10L;
        String workspaceId = "1";
        KnowledgeEntity entity = new KnowledgeEntity();
        entity.setId(knowledgeId);
        entity.setWorkspaceId(workspaceId);
        entity.setCreatorId(99L);
        given(knowledgeMapper.selectById(knowledgeId)).willReturn(entity);

        knowledgeService.deleteKnowledge(knowledgeId, workspaceId, 99L, WorkspaceMemberRole.OWNER);

        verify(knowledgeFactMapper).delete(any(LambdaQueryWrapper.class));
        verify(knowledgeArtifactMapper).delete(any(LambdaQueryWrapper.class));
        verify(knowledgeTagMapper).deleteByKnowledgeId(knowledgeId);
        verify(memoryFeedbackMapper).delete(any(LambdaQueryWrapper.class));
        verify(relationEngine).deleteRelationsByKnowledgeId(knowledgeId, workspaceId);
        verify(knowledgeMapper).deleteById(knowledgeId);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -q -Dtest=KnowledgeServiceDeleteTest test`
Expected: FAIL —— `memoryFeedbackMapper.delete(...)` 未被调用(当前实现缺子表与 feedback 清理)。

- [ ] **Step 3: 修复 deleteKnowledge**

Modify `src/main/java/com/zhiyi/memory/knowledge/KnowledgeService.java`,将 `deleteKnowledge` 方法体(约 341-348 行)替换为:

```java
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledge(Long knowledgeId, String workspaceId, Long operatorUserId, String memberRole) {
        KnowledgeEntity entity = requireKnowledgeInWorkspace(knowledgeId, workspaceId);
        requireDeletePermission(entity, operatorUserId, memberRole);
        deleteChildren(knowledgeId);
        relationEngine.deleteRelationsByKnowledgeId(knowledgeId, workspaceId);
        memoryFeedbackMapper.delete(new LambdaQueryWrapper<MemoryFeedbackEntity>()
                .eq(MemoryFeedbackEntity::getKnowledgeId, knowledgeId));
        knowledgeMapper.deleteById(knowledgeId);
    }
```

> `deleteChildren` 内部已调用 `retrievalEngine.deleteByKnowledgeId`,故移除原本单独的 `retrievalEngine.deleteByKnowledgeId(knowledgeId)`(幂等但冗余)。需新增 import:`com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper`、`com.zhiyi.memory.entity.MemoryFeedbackEntity`(若未导入)。

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -q -Dtest=KnowledgeServiceDeleteTest test`
Expected: BUILD SUCCESS,测试通过。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/zhiyi/memory/knowledge/KnowledgeService.java src/test/java/com/zhiyi/memory/knowledge/KnowledgeServiceDeleteTest.java
git commit -m "fix: 修复删除知识时子表(fact/artifact/tag/feedback)未清理的孤儿数据问题"
```

---

## Task 3: Markdown 序列化器(导出用)

导入/导出共用的 Markdown 格式的「序列化」侧:把 `KnowledgeAggregate` 序列化为人读 + 可逆解析的 Markdown 块。

**Files:**
- Create: `src/main/java/com/zhiyi/memory/util/KnowledgeMarkdownSerializer.java`
- Test: `src/test/java/com/zhiyi/memory/util/KnowledgeMarkdownSerializerTest.java`

**Interfaces:**
- Consumes: `KnowledgeAggregate`、`FactBlock`、`ArtifactDto`。
- Produces: `KnowledgeMarkdownSerializer.serializeWorkspace(workspaceName, exportTime, list)` 与 `serializeOne(aggregate)`,供 Task 5 导出端点调用。

**格式规范(与 spec 第 4 节一致):**

````markdown
# 工作空间「{name}」知识导出

> 导出时间:{time}  工作空间:{name}  共 {N} 条

<!-- KNOWLEDGE START -->
---
title: "..."
knowledgeType: experience
project: ...
module: ...
repository: ...
language: ...
framework: ...
tags: ["a","b"]
lifecycleStatus: 1
---

## Facts

### observation
正文……

### decision
正文……

## Artifacts
- [commit] origin: 588e6da (https://...)
<!-- KNOWLEDGE END -->
````

- [ ] **Step 1: 写失败测试**

Create `src/test/java/com/zhiyi/memory/util/KnowledgeMarkdownSerializerTest.java`:

```java
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
        assertTrue(md.contains("tags: [\"memory\", \"timeout\"]"), md);
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
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -q -Dtest=KnowledgeMarkdownSerializerTest test`
Expected: 编译失败(`KnowledgeMarkdownSerializer` 不存在)。

- [ ] **Step 3: 实现序列化器**

Create `src/main/java/com/zhiyi/memory/util/KnowledgeMarkdownSerializer.java`:

```java
package com.zhiyi.memory.util;

import cn.hutool.json.JSONUtil;
import com.zhiyi.memory.domain.ArtifactDto;
import com.zhiyi.memory.domain.FactBlock;
import com.zhiyi.memory.domain.KnowledgeAggregate;

import java.util.List;

/**
 * 知识 → Markdown 序列化器,导出/导入共用的格式(人读 + 可逆解析)。
 * frontmatter 为固定字段;Facts 按 block_type 分节;Artifacts 为列表项。
 */
public final class KnowledgeMarkdownSerializer {

    private KnowledgeMarkdownSerializer() {
    }

    public static String serializeWorkspace(String workspaceName, String exportTime,
                                             List<KnowledgeAggregate> knowledgeList) {
        int count = knowledgeList == null ? 0 : knowledgeList.size();
        StringBuilder sb = new StringBuilder();
        sb.append("# 工作空间「").append(safe(workspaceName)).append("」知识导出\n\n");
        sb.append("> 导出时间:").append(safe(exportTime))
          .append("  工作空间:").append(safe(workspaceName))
          .append("  共 ").append(count).append(" 条\n\n");
        if (knowledgeList != null) {
            for (KnowledgeAggregate agg : knowledgeList) {
                sb.append(serializeOne(agg)).append("\n");
            }
        }
        return sb.toString();
    }

    public static String serializeOne(KnowledgeAggregate agg) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!-- KNOWLEDGE START -->\n");
        sb.append("---\n");
        sb.append("title: \"").append(escapeQuotes(agg.getTitle())).append("\"\n");
        sb.append("knowledgeType: ").append(safe(agg.getKnowledgeType())).append("\n");
        sb.append("project: ").append(safe(agg.getProject())).append("\n");
        sb.append("module: ").append(safe(agg.getModule())).append("\n");
        sb.append("repository: ").append(safe(agg.getRepository())).append("\n");
        sb.append("language: ").append(safe(agg.getLanguage())).append("\n");
        sb.append("framework: ").append(safe(agg.getFramework())).append("\n");
        sb.append("tags: ").append(agg.getTags() == null ? "[]" : JSONUtil.toJsonStr(agg.getTags())).append("\n");
        sb.append("lifecycleStatus: ").append(agg.getLifecycleStatus() == null ? 1 : agg.getLifecycleStatus()).append("\n");
        sb.append("---\n\n");

        sb.append("## Facts\n\n");
        if (agg.getFacts() != null) {
            for (FactBlock fact : agg.getFacts()) {
                if (fact == null || fact.getText() == null) {
                    continue;
                }
                sb.append("### ").append(safe(fact.getType())).append("\n");
                sb.append(fact.getText()).append("\n\n");
            }
        }

        if (agg.getArtifacts() != null && !agg.getArtifacts().isEmpty()) {
            sb.append("## Artifacts\n");
            for (ArtifactDto art : agg.getArtifacts()) {
                if (art == null || art.getArtifactType() == null) {
                    continue;
                }
                String role = art.getArtifactRole() == null ? "origin" : art.getArtifactRole();
                String url = art.getArtifactUrl();
                String line = "- [" + art.getArtifactType() + "] " + role + ": "
                        + safe(art.getContentRef());
                if (url != null && !url.isEmpty()) {
                    line += " (" + url + ")";
                }
                sb.append(line).append("\n");
            }
            sb.append("\n");
        }
        sb.append("<!-- KNOWLEDGE END -->\n");
        return sb.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String escapeQuotes(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -q -Dtest=KnowledgeMarkdownSerializerTest test`
Expected: BUILD SUCCESS,2 个测试通过。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/zhiyi/memory/util/KnowledgeMarkdownSerializer.java src/test/java/com/zhiyi/memory/util/KnowledgeMarkdownSerializerTest.java
git commit -m "feat: 新增知识 Markdown 序列化器(导出用)"
```

---

## Task 4: Markdown 解析器(导入用)

Markdown 格式的「反序列化」侧:把导出的 Markdown 解析回 `KnowledgeSaveRequest` 列表。

**Files:**
- Create: `src/main/java/com/zhiyi/memory/util/KnowledgeMarkdownParser.java`
- Test: `src/test/java/com/zhiyi/memory/util/KnowledgeMarkdownParserTest.java`

**Interfaces:**
- Consumes: 无(纯函数,输入 Markdown 字符串)。
- Produces: `KnowledgeMarkdownParser.splitBlocks(md)` 返回 `List<String>`(每个 KNOWLEDGE 块原文);`KnowledgeMarkdownParser.parseBlock(block)` 返回 `KnowledgeSaveRequest`,格式非法抛 `BusinessException`。供 Task 6 导入端点调用。

- [ ] **Step 1: 写失败测试**

Create `src/test/java/com/zhiyi/memory/util/KnowledgeMarkdownParserTest.java`:

```java
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
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -q -Dtest=KnowledgeMarkdownParserTest test`
Expected: 编译失败(`KnowledgeMarkdownParser` 不存在)。

- [ ] **Step 3: 实现解析器**

Create `src/main/java/com/zhiyi/memory/util/KnowledgeMarkdownParser.java`:

```java
package com.zhiyi.memory.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.zhiyi.common.BusinessException;
import com.zhiyi.memory.domain.ArtifactDto;
import com.zhiyi.memory.domain.FactBlock;
import com.zhiyi.memory.domain.KnowledgeSaveRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown → 知识反序列化器(导出的逆向)。frontmatter 逐行解析;Facts 按 ### 标题分段;
 * Artifacts 按列表项正则解析。格式非法抛 BusinessException 由调用方收集。
 */
public final class KnowledgeMarkdownParser {

    private static final String BLOCK_START = "<!-- KNOWLEDGE START -->";
    private static final String BLOCK_END = "<!-- KNOWLEDGE END -->";
    private static final Pattern ARTIFACT_LINE =
            Pattern.compile("^-\\s*\\[(\\w+)\\]\\s*(\\w+):\\s*(.*?)\\s*(?:\\((https?[^)]*)\\))?\\s*$");

    private KnowledgeMarkdownParser() {
    }

    public static List<String> splitBlocks(String markdown) {
        List<String> blocks = new ArrayList<>();
        if (markdown == null) {
            return blocks;
        }
        int from = 0;
        while (true) {
            int start = markdown.indexOf(BLOCK_START, from);
            if (start < 0) {
                break;
            }
            int end = markdown.indexOf(BLOCK_END, start);
            if (end < 0) {
                blocks.add(markdown.substring(start));
                break;
            }
            blocks.add(markdown.substring(start, end + BLOCK_END.length()));
            from = end + BLOCK_END.length();
        }
        return blocks;
    }

    public static KnowledgeSaveRequest parseBlock(String block) {
        if (block == null || !block.contains(BLOCK_START)) {
            throw new BusinessException(400, "知识块格式错误:缺少起始标记");
        }
        String frontMatter = extractFrontMatter(block);
        String factsSection = extractSection(block, "## Facts");
        String artifactsSection = extractSection(block, "## Artifacts");

        KnowledgeSaveRequest req = new KnowledgeSaveRequest();
        req.setTitle(stripQuotes(readFrontmatter(frontMatter, "title")));
        req.setKnowledgeType(StrUtil.blankToDefault(readFrontmatter(frontMatter, "knowledgeType"), "experience"));
        req.setProject(readFrontmatter(frontMatter, "project"));
        req.setModule(readFrontmatter(frontMatter, "module"));
        req.setRepository(readFrontmatter(frontMatter, "repository"));
        req.setLanguage(readFrontmatter(frontMatter, "language"));
        req.setFramework(readFrontmatter(frontMatter, "framework"));
        req.setTags(parseTags(readFrontmatter(frontMatter, "tags")));
        req.setPublish(false);
        // lifecycleStatus 仅记录,导入强制草稿,此处忽略
        req.setFacts(parseFacts(factsSection));
        req.setArtifacts(parseArtifacts(artifactsSection));

        if (StrUtil.isBlank(req.getTitle())) {
            throw new BusinessException(400, "知识块格式错误:缺少 title");
        }
        if (req.getFacts() == null || req.getFacts().isEmpty()) {
            throw new BusinessException(400, "知识块格式错误:缺少至少一条 Fact");
        }
        return req;
    }

    private static String extractFrontMatter(String block) {
        int start = block.indexOf("---");
        if (start < 0) {
            return "";
        }
        int end = block.indexOf("---", start + 3);
        if (end < 0) {
            return "";
        }
        return block.substring(start + 3, end);
    }

    private static String extractSection(String block, String header) {
        int idx = block.indexOf(header);
        if (idx < 0) {
            return "";
        }
        int after = idx + header.length();
        int nextH2 = block.indexOf("\n## ", after);
        int blockEnd = block.indexOf(BLOCK_END, after);
        int stop = -1;
        if (nextH2 > 0 && (blockEnd < 0 || nextH2 < blockEnd)) {
            stop = nextH2;
        } else if (blockEnd > 0) {
            stop = blockEnd;
        }
        return stop > 0 ? block.substring(after, stop) : block.substring(after);
    }

    private static String readFrontmatter(String frontMatter, String key) {
        for (String line : frontMatter.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(key + ":")) {
                return trimmed.substring((key + ":").length()).trim();
            }
        }
        return null;
    }

    private static String stripQuotes(String value) {
        if (value == null) {
            return null;
        }
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1).replace("\\\"", "\"");
        }
        return value;
    }

    private static List<String> parseTags(String raw) {
        if (StrUtil.isBlank(raw) || "[]".equals(raw.trim())) {
            return Collections.emptyList();
        }
        try {
            return JSONUtil.toList(raw, String.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static List<FactBlock> parseFacts(String factsSection) {
        List<FactBlock> facts = new ArrayList<>();
        if (StrUtil.isBlank(factsSection)) {
            return facts;
        }
        String[] parts = factsSection.split("(?m)^### ");
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            int nl = part.indexOf('\n');
            if (nl < 0) {
                continue;
            }
            String type = part.substring(0, nl).trim();
            String text = part.substring(nl + 1).trim();
            if (StrUtil.isBlank(text)) {
                continue;
            }
            FactBlock fb = new FactBlock();
            fb.setType(type);
            fb.setText(text);
            facts.add(fb);
        }
        return facts;
    }

    private static List<ArtifactDto> parseArtifacts(String artifactsSection) {
        List<ArtifactDto> list = new ArrayList<>();
        if (StrUtil.isBlank(artifactsSection)) {
            return list;
        }
        for (String line : artifactsSection.split("\n")) {
            Matcher m = ARTIFACT_LINE.matcher(line.trim());
            if (!m.matches()) {
                continue;
            }
            ArtifactDto dto = new ArtifactDto();
            dto.setArtifactType(m.group(1));
            dto.setArtifactRole(m.group(2));
            dto.setContentRef(m.group(3));
            if (m.groupCount() >= 4 && m.group(4) != null) {
                dto.setArtifactUrl(m.group(4));
            }
            list.add(dto);
        }
        return list;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -q -Dtest=KnowledgeMarkdownParserTest test`
Expected: BUILD SUCCESS,4 个测试通过。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/zhiyi/memory/util/KnowledgeMarkdownParser.java src/test/java/com/zhiyi/memory/util/KnowledgeMarkdownParserTest.java
git commit -m "feat: 新增知识 Markdown 解析器(批量导入用)"
```

---

## Task 5: 导出知识为 Markdown 端点

**Files:**
- Modify: `src/main/java/com/zhiyi/memory/knowledge/KnowledgeService.java`(新增 `exportMarkdown` 方法 + 注入 `WorkspaceMapper`,或 workspaceName 由 Controller 传入避免新依赖——本方案采用 Controller 传入)
- Modify: `src/main/java/com/zhiyi/memory/controller/KnowledgeController.java`(新增 `GET /knowledge/export`)
- Test: `src/test/java/com/zhiyi/memory/knowledge/KnowledgeServiceExportTest.java`

**Interfaces:**
- Consumes: Task 3 的 `KnowledgeMarkdownSerializer.serializeWorkspace`;现有 `knowledgeMapper.selectList`、`loadAggregate`。
- Produces: `KnowledgeService.exportMarkdown(workspaceId, workspaceName, knowledgeType)` 返回完整 Markdown 字符串;`GET /knowledge/export` 流式下载。

- [ ] **Step 1: 写失败测试**

Create `src/test/java/com/zhiyi/memory/knowledge/KnowledgeServiceExportTest.java`:

```java
package com.zhiyi.memory.knowledge;

import com.zhiyi.memory.entity.KnowledgeEntity;
import com.zhiyi.memory.engine.RetrievalEngine;
import com.zhiyi.memory.engine.RelationAsyncService;
import com.zhiyi.memory.engine.RelationEngine;
import com.zhiyi.memory.context.ContextNormalizer;
import com.zhiyi.memory.dao.KnowledgeArtifactMapper;
import com.zhiyi.memory.dao.KnowledgeFactMapper;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.dao.KnowledgeTagMapper;
import com.zhiyi.memory.dao.MemoryFeedbackMapper;
import com.zhiyi.memory.graph.CascadeValidationService;
import com.zhiyi.memory.timeline.KnowledgeTimelineService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceExportTest {

    @Mock private KnowledgeMapper knowledgeMapper;
    @Mock private KnowledgeFactMapper knowledgeFactMapper;
    @Mock private KnowledgeArtifactMapper knowledgeArtifactMapper;
    @Mock private KnowledgeTagMapper knowledgeTagMapper;
    @Mock private MemoryFeedbackMapper memoryFeedbackMapper;
    @Mock private RetrievalEngine retrievalEngine;
    @Mock private ContextNormalizer contextNormalizer;
    @Mock private RelationAsyncService relationAsyncService;
    @Mock private RelationEngine relationEngine;
    @Mock private KnowledgeTimelineService knowledgeTimelineService;
    @Mock private CascadeValidationService cascadeValidationService;
    @Mock private com.zhiyi.service.UserProfileService userProfileService;

    @InjectMocks private KnowledgeService knowledgeService;

    @Test
    void exportMarkdown_empty_workspace_should_return_header_only() {
        given(knowledgeMapper.selectList(any())).willReturn(Collections.<KnowledgeEntity>emptyList());

        String md = knowledgeService.exportMarkdown("1", "空空间", "all", "2026-07-28");

        assertTrue(md.contains("# 工作空间「空空间」知识导出"), md);
        assertTrue(md.contains("共 0 条"), md);
    }

    @Test
    void exportMarkdown_should_include_published_knowledge_title() {
        KnowledgeEntity entity = new KnowledgeEntity();
        entity.setId(7L);
        entity.setWorkspaceId("1");
        entity.setKnowledgeType("experience");
        entity.setLifecycleStatus(1);
        entity.setTitle("经验A");
        entity.setCreatorId(1L);
        given(knowledgeMapper.selectList(any())).willReturn(Collections.singletonList(entity));
        given(knowledgeFactMapper.selectList(any())).willReturn(Collections.emptyList());
        given(knowledgeArtifactMapper.selectList(any())).willReturn(Collections.emptyList());
        given(knowledgeTagMapper.selectTagNames(7L)).willReturn(Collections.emptyList());

        String md = knowledgeService.exportMarkdown("1", "空间", "all", "2026-07-28");

        assertTrue(md.contains("经验A"), md);
    }
}
```

> 注:`loadAggregate` 内部调用 `knowledgeFactMapper.selectList` / `knowledgeArtifactMapper.selectList` / `knowledgeTagMapper.selectTagNames`,需 mock 返回空列表。若实际 `loadAggregate` 调用的方法名不同,以源码为准调整 mock。

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -q -Dtest=KnowledgeServiceExportTest test`
Expected: 编译失败(`exportMarkdown` 方法不存在)。

- [ ] **Step 3: 在 KnowledgeService 实现 exportMarkdown**

在 `KnowledgeService` 中新增方法(放在 `list` 方法附近):

```java
    /**
     * 导出当前工作空间已发布知识为 Markdown 字符串
     *
     * @param workspaceId   工作空间主键
     * @param workspaceName 工作空间显示名(用于文件头)
     * @param knowledgeType knowledgeType,null/"all" 表示全部四种类型
     * @param exportTime    导出时间字符串(用于文件头)
     */
    public String exportMarkdown(String workspaceId, String workspaceName,
                                 String knowledgeType, String exportTime) {
        requireWorkspaceId(workspaceId);
        LambdaQueryWrapper<KnowledgeEntity> wrapper = new LambdaQueryWrapper<KnowledgeEntity>();
        wrapper.eq(KnowledgeEntity::getWorkspaceId, workspaceId);
        wrapper.eq(KnowledgeEntity::getLifecycleStatus, MemoryConstants.LIFECYCLE_PUBLISHED);
        if (StringUtils.isNotBlank(knowledgeType) && !"all".equals(knowledgeType)) {
            wrapper.eq(KnowledgeEntity::getKnowledgeType, knowledgeType);
        }
        wrapper.orderByDesc(KnowledgeEntity::getUpdateTime);
        List<KnowledgeEntity> entities = knowledgeMapper.selectList(wrapper);
        List<KnowledgeAggregate> aggregates = new ArrayList<KnowledgeAggregate>();
        for (KnowledgeEntity entity : entities) {
            aggregates.add(loadAggregate(entity));
        }
        return KnowledgeMarkdownSerializer.serializeWorkspace(workspaceName, exportTime, aggregates);
    }
```

新增 import:`com.zhiyi.memory.util.KnowledgeMarkdownSerializer`、`com.zhiyi.memory.domain.KnowledgeAggregate`(若未导入)。`LambdaQueryWrapper`、`List`、`ArrayList`、`StringUtils` 应已导入。

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -q -Dtest=KnowledgeServiceExportTest test`
Expected: BUILD SUCCESS,2 个测试通过。

- [ ] **Step 5: 在 KnowledgeController 新增导出端点**

在 `KnowledgeController` 新增端点(放在 `list` 端点之后)。需新增 import:`javax.servlet.http.HttpServletResponse`(已导入 `HttpServletRequest`,补 `HttpServletResponse`)、`java.net.URLEncoder`、`java.nio.charset.StandardCharsets`、`java.io.OutputStream`、`java.text.SimpleDateFormat`、`java.util.Date`。

```java
    /**
     * 导出当前工作空间已发布知识为 Markdown 文件下载
     */
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String knowledgeType,
                       HttpServletRequest request,
                       HttpServletResponse response) throws java.io.IOException {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        String workspaceId = requireWorkspaceId(loginUser);
        String workspaceName = loginUser.getWorkspaceName() == null ? workspaceId : loginUser.getWorkspaceName();
        String exportTime = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());
        String markdown = knowledgeService.exportMarkdown(workspaceId, workspaceName, knowledgeType, exportTime);

        String fileName = URLEncoder.encode(workspaceName + "-知识导出-"
                + new SimpleDateFormat("yyyyMMdd").format(new Date()) + ".md", StandardCharsets.UTF_8.name());
        response.setContentType("text/markdown; charset=utf-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        OutputStream os = response.getOutputStream();
        os.write(markdown.getBytes(StandardCharsets.UTF_8));
        os.flush();
    }
```

- [ ] **Step 6: 手动验证端点编译**

Run: `mvn -q compile`
Expected: BUILD SUCCESS。

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/zhiyi/memory/knowledge/KnowledgeService.java src/main/java/com/zhiyi/memory/controller/KnowledgeController.java src/test/java/com/zhiyi/memory/knowledge/KnowledgeServiceExportTest.java
git commit -m "feat: 新增导出工作空间知识为 Markdown 端点 GET /knowledge/export"
```

---

## Task 6: 批量导入经验端点

**Files:**
- Modify: `src/main/java/com/zhiyi/memory/knowledge/KnowledgeService.java`(新增 `importMarkdown` 方法)
- Modify: `src/main/java/com/zhiyi/memory/controller/KnowledgeController.java`(新增 `POST /knowledge/import`)
- Create: `src/main/java/com/zhiyi/memory/domain/KnowledgeImportResult.java`(导入结果 DTO)
- Test: `src/test/java/com/zhiyi/memory/knowledge/KnowledgeServiceImportTest.java`

**Interfaces:**
- Consumes: Task 4 的 `KnowledgeMarkdownParser`(`splitBlocks` + `parseBlock`);现有 `KnowledgeService.create(saveRequest, creatorId, workspaceId, null)`。
- Produces: `KnowledgeService.importMarkdown(content, creatorId, workspaceId)` 返回 `KnowledgeImportResult{total, imported, failed, failures:[{index, title, reason}]}`;`POST /knowledge/import` 接收 multipart `.md`。

- [ ] **Step 1: 新增结果 DTO**

Create `src/main/java/com/zhiyi/memory/domain/KnowledgeImportResult.java`:

```java
package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入经验结果
 */
@Data
public class KnowledgeImportResult {
    private int total;
    private int imported;
    private int failed;
    private List<Failure> failures = new ArrayList<>();

    @Data
    public static class Failure {
        private int index;
        private String title;
        private String reason;

        public Failure(int index, String title, String reason) {
            this.index = index;
            this.title = title;
            this.reason = reason;
        }
    }
}
```

- [ ] **Step 2: 写失败测试**

Create `src/test/java/com/zhiyi/memory/knowledge/KnowledgeServiceImportTest.java`:

```java
package com.zhiyi.memory.knowledge;

import com.zhiyi.memory.domain.KnowledgeImportResult;
import com.zhiyi.memory.domain.KnowledgeSaveRequest;
import com.zhiyi.memory.engine.RetrievalEngine;
import com.zhiyi.memory.engine.RelationAsyncService;
import com.zhiyi.memory.engine.RelationEngine;
import com.zhiyi.memory.context.ContextNormalizer;
import com.zhiyi.memory.dao.KnowledgeArtifactMapper;
import com.zhiyi.memory.dao.KnowledgeFactMapper;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.dao.KnowledgeTagMapper;
import com.zhiyi.memory.dao.MemoryFeedbackMapper;
import com.zhiyi.memory.entity.KnowledgeEntity;
import com.zhiyi.memory.graph.CascadeValidationService;
import com.zhiyi.memory.timeline.KnowledgeTimelineService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceImportTest {

    @Mock private KnowledgeMapper knowledgeMapper;
    @Mock private KnowledgeFactMapper knowledgeFactMapper;
    @Mock private KnowledgeArtifactMapper knowledgeArtifactMapper;
    @Mock private KnowledgeTagMapper knowledgeTagMapper;
    @Mock private MemoryFeedbackMapper memoryFeedbackMapper;
    @Mock private RetrievalEngine retrievalEngine;
    @Mock private ContextNormalizer contextNormalizer;
    @Mock private RelationAsyncService relationAsyncService;
    @Mock private RelationEngine relationEngine;
    @Mock private KnowledgeTimelineService knowledgeTimelineService;
    @Mock private CascadeValidationService cascadeValidationService;
    @Mock private com.zhiyi.service.UserProfileService userProfileService;

    @InjectMocks private KnowledgeService knowledgeService;

    private static final String GOOD = ""
            + "<!-- KNOWLEDGE START -->\n---\ntitle: \"t1\"\nknowledgeType: experience\n---\n\n"
            + "## Facts\n\n### observation\n内容\n<!-- KNOWLEDGE END -->\n";

    @Test
    void importMarkdown_should_create_draft_for_each_valid_block() {
        given(knowledgeMapper.insert(any(KnowledgeEntity.class))).willAnswer(invocation -> {
            ((KnowledgeEntity) invocation.getArgument(0)).setId(1L);
            return 1;
        });

        KnowledgeImportResult result = knowledgeService.importMarkdown(GOOD + GOOD, 99L, "1");

        assertEquals(2, result.getTotal());
        assertEquals(2, result.getImported());
        assertEquals(0, result.getFailed());
        verify(knowledgeMapper, times(2)).insert(any(KnowledgeEntity.class));
    }

    @Test
    void importMarkdown_should_collect_invalid_block_as_failure() {
        String bad = "<!-- KNOWLEDGE START -->\n---\nknowledgeType: experience\n---\n\n## Facts\n\n### observation\nx\n<!-- KNOWLEDGE END -->\n";
        given(knowledgeMapper.insert(any(KnowledgeEntity.class))).willAnswer(invocation -> {
            ((KnowledgeEntity) invocation.getArgument(0)).setId(1L);
            return 1;
        });

        KnowledgeImportResult result = knowledgeService.importMarkdown(GOOD + bad, 99L, "1");

        assertEquals(2, result.getTotal());
        assertEquals(1, result.getImported());
        assertEquals(1, result.getFailed());
        assertEquals("缺 title", substring(result.getFailures().get(0).getReason()) || true);
        // 合法块仍被创建
        verify(knowledgeMapper, times(1)).insert(any(KnowledgeEntity.class));
    }

    private boolean substring(String s) {
        return s != null;
    }
}
```

> 末行 `assertEquals("缺 title", ...)` 是占位校验,实际 `reason` 文案以 `parseBlock` 抛出的 message 为准(「知识块格式错误:缺少 title」)。可改为 `assertTrue(result.getFailures().get(0).getReason().contains("title"))`。

- [ ] **Step 3: 运行测试验证失败**

Run: `mvn -q -Dtest=KnowledgeServiceImportTest test`
Expected: 编译失败(`importMarkdown` 不存在)。

- [ ] **Step 4: 实现 importMarkdown**

在 `KnowledgeService` 新增方法。整批事务:先解析全部块,解析阶段非法块计入 failures 但不阻塞;创建阶段在事务内,任一抛异常则整批回滚(由 `@Transactional` 保证)。

```java
    /**
     * 批量导入经验:解析 Markdown,逐条创建为草稿(不经 AI,不进 capture_draft)。
     * 解析非法块计入 failures;创建阶段任一异常触发整批事务回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeImportResult importMarkdown(String markdown, Long creatorId, String workspaceId) {
        requireWorkspaceId(workspaceId);
        if (creatorId == null) {
            throw new BusinessException(400, "创建者不能为空");
        }
        List<String> blocks = KnowledgeMarkdownParser.splitBlocks(markdown);
        KnowledgeImportResult result = new KnowledgeImportResult();
        result.setTotal(blocks.size());

        List<KnowledgeSaveRequest> parsed = new ArrayList<KnowledgeSaveRequest>();
        for (int i = 0; i < blocks.size(); i++) {
            String block = blocks.get(i);
            try {
                KnowledgeSaveRequest req = KnowledgeMarkdownParser.parseBlock(block);
                parsed.add(req);
            } catch (BusinessException e) {
                result.getFailures().add(new KnowledgeImportResult.Failure(i + 1, null, e.getMessage()));
            }
        }

        for (KnowledgeSaveRequest req : parsed) {
            req.setPublish(false);
            create(req, creatorId, workspaceId, null);
            result.setImported(result.getImported() + 1);
        }
        result.setFailed(result.getFailures().size());
        return result;
    }
```

> `create(publish=false)` 内部会校验 knowledgeType 并建草稿(`lifecycleStatus=0`),不建检索索引。新增 import:`com.zhiyi.memory.util.KnowledgeMarkdownParser`、`com.zhiyi.memory.domain.KnowledgeImportResult`、`com.zhiyi.memory.domain.KnowledgeSaveRequest`(若未导入)。

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn -q -Dtest=KnowledgeServiceImportTest test`
Expected: BUILD SUCCESS,2 个测试通过。若第二个测试因 reason 文案断言失败,按 Step 2 注释把断言改为 `contains("title")` 后重跑。

- [ ] **Step 6: 在 KnowledgeController 新增导入端点**

在 `KnowledgeController` 新增端点(放在 `create` 端点之后)。新增 import:`org.springframework.web.multipart.MultipartFile`、`java.io.IOException`、`java.nio.charset.StandardCharsets`。

```java
    /**
     * 批量导入经验:上传 Markdown 文件,解析为草稿
     */
    @PostMapping("/import")
    public Result<com.zhiyi.memory.domain.KnowledgeImportResult> doImport(
            @RequestParam("file") MultipartFile file, HttpServletRequest request) throws IOException {
        LoginUserVO loginUser = requireKnowledgeEditor(request);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "导入文件为空");
        }
        long size = file.getSize();
        if (size > MemoryConstants.IMPORT_MAX_CONTENT_LENGTH) {
            throw new BusinessException(400, "导入文件超过大小上限 " + MemoryConstants.IMPORT_MAX_CONTENT_LENGTH + " 字节");
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        return Result.success(knowledgeService.importMarkdown(
                content, loginUser.getUserId(), requireWorkspaceId(loginUser)));
    }
```

新增 Controller import:`com.zhiyi.memory.MemoryConstants`(若未导入)。注意方法名 `doImport` 避免与 Java 关键字冲突。

- [ ] **Step 7: 手动验证编译**

Run: `mvn -q compile`
Expected: BUILD SUCCESS。

- [ ] **Step 8: 提交**

```bash
git add src/main/java/com/zhiyi/memory/domain/KnowledgeImportResult.java src/main/java/com/zhiyi/memory/knowledge/KnowledgeService.java src/main/java/com/zhiyi/memory/controller/KnowledgeController.java src/test/java/com/zhiyi/memory/knowledge/KnowledgeServiceImportTest.java
git commit -m "feat: 新增批量导入经验端点 POST /knowledge/import(Markdown→草稿)"
```

---

## Task 7: 批量删除知识端点

**Files:**
- Modify: `src/main/java/com/zhiyi/memory/knowledge/KnowledgeService.java`(新增 `deleteByIds` 方法)
- Modify: `src/main/java/com/zhiyi/memory/controller/KnowledgeController.java`(新增 `DELETE /knowledge/batch`)
- Create: `src/main/java/com/zhiyi/memory/domain/KnowledgeBatchDeleteResult.java`
- Test: `src/test/java/com/zhiyi/memory/knowledge/KnowledgeServiceBatchDeleteTest.java`

**Interfaces:**
- Consumes: Task 2 修复后的 `deleteKnowledge`。
- Produces: `KnowledgeService.deleteByIds(ids, workspaceId, operatorUserId, memberRole)` 返回 `KnowledgeBatchDeleteResult{total, deleted, failed, failures:[{id, reason}]}`;`DELETE /knowledge/batch`。

- [ ] **Step 1: 新增结果 DTO**

Create `src/main/java/com/zhiyi/memory/domain/KnowledgeBatchDeleteResult.java`:

```java
package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class KnowledgeBatchDeleteResult {
    private int total;
    private int deleted;
    private int failed;
    private List<Failure> failures = new ArrayList<>();

    @Data
    public static class Failure {
        private Long id;
        private String reason;

        public Failure(Long id, String reason) {
            this.id = id;
            this.reason = reason;
        }
    }
}
```

- [ ] **Step 2: 写失败测试**

Create `src/test/java/com/zhiyi/memory/knowledge/KnowledgeServiceBatchDeleteTest.java`:

```java
package com.zhiyi.memory.knowledge;

import com.zhiyi.memory.domain.KnowledgeBatchDeleteResult;
import com.zhiyi.memory.entity.KnowledgeEntity;
import com.zhiyi.memory.engine.RetrievalEngine;
import com.zhiyi.memory.engine.RelationAsyncService;
import com.zhiyi.memory.engine.RelationEngine;
import com.zhiyi.memory.context.ContextNormalizer;
import com.zhiyi.memory.dao.KnowledgeArtifactMapper;
import com.zhiyi.memory.dao.KnowledgeFactMapper;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.dao.KnowledgeTagMapper;
import com.zhiyi.memory.dao.MemoryFeedbackMapper;
import com.zhiyi.memory.graph.CascadeValidationService;
import com.zhiyi.memory.timeline.KnowledgeTimelineService;
import com.zhiyi.workspace.WorkspaceMemberRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceBatchDeleteTest {

    @Mock private KnowledgeMapper knowledgeMapper;
    @Mock private KnowledgeFactMapper knowledgeFactMapper;
    @Mock private KnowledgeArtifactMapper knowledgeArtifactMapper;
    @Mock private KnowledgeTagMapper knowledgeTagMapper;
    @Mock private MemoryFeedbackMapper memoryFeedbackMapper;
    @Mock private RetrievalEngine retrievalEngine;
    @Mock private ContextNormalizer contextNormalizer;
    @Mock private RelationAsyncService relationAsyncService;
    @Mock private RelationEngine relationEngine;
    @Mock private KnowledgeTimelineService knowledgeTimelineService;
    @Mock private CascadeValidationService cascadeValidationService;
    @Mock private com.zhiyi.service.UserProfileService userProfileService;

    @InjectMocks private KnowledgeService knowledgeService;

    @Test
    void deleteByIds_should_delete_own_and_skip_others() {
        KnowledgeEntity mine = new KnowledgeEntity();
        mine.setId(1L);
        mine.setWorkspaceId("1");
        mine.setCreatorId(99L);
        KnowledgeEntity others = new KnowledgeEntity();
        others.setId(2L);
        others.setWorkspaceId("1");
        others.setCreatorId(5L);
        given(knowledgeMapper.selectById(1L)).willReturn(mine);
        given(knowledgeMapper.selectById(2L)).willReturn(others);

        List<Long> ids = Arrays.asList(1L, 2L);
        // editor 删自己的可以,删他人不行
        KnowledgeBatchDeleteResult result = knowledgeService.deleteByIds(ids, "1", 99L, WorkspaceMemberRole.EDITOR);

        assertEquals(2, result.getTotal());
        assertEquals(1, result.getDeleted());
        assertEquals(1, result.getFailed());
        verify(knowledgeMapper).deleteById(1L);
        verify(knowledgeMapper, never()).deleteById(2L);
    }

    @Test
    void deleteByIds_should_record_missing_id_as_failure() {
        given(knowledgeMapper.selectById(anyLong())).willReturn(null);

        KnowledgeBatchDeleteResult result = knowledgeService.deleteByIds(
                Arrays.asList(9L), "1", 99L, WorkspaceMemberRole.OWNER);

        assertEquals(1, result.getTotal());
        assertEquals(0, result.getDeleted());
        assertEquals(1, result.getFailed());
        verify(knowledgeMapper, never()).deleteById(anyLong());
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `mvn -q -Dtest=KnowledgeServiceBatchDeleteTest test`
Expected: 编译失败(`deleteByIds` 不存在)。

- [ ] **Step 4: 实现 deleteByIds**

在 `KnowledgeService` 新增方法。逐条独立,每条捕获 `BusinessException` 计入 failure(权限/不存在),不阻塞其他条目。每条删除在一个**独立**事务里——因此不能给 `deleteByIds` 加 `@Transactional`(否则整批一个事务)。改为每条调用已带 `@Transactional` 的 `deleteKnowledge`,各自独立事务。

```java
    /**
     * 批量删除知识:逐条独立删除(权限失败/不存在不阻塞其他),每条复用 deleteKnowledge 的独立事务。
     */
    public KnowledgeBatchDeleteResult deleteByIds(List<Long> ids, String workspaceId,
                                                  Long operatorUserId, String memberRole) {
        requireWorkspaceId(workspaceId);
        KnowledgeBatchDeleteResult result = new KnowledgeBatchDeleteResult();
        if (ids == null) {
            return result;
        }
        result.setTotal(ids.size());
        for (Long id : ids) {
            try {
                deleteKnowledge(id, workspaceId, operatorUserId, memberRole);
                result.setDeleted(result.getDeleted() + 1);
            } catch (BusinessException e) {
                result.getFailures().add(new KnowledgeBatchDeleteResult.Failure(id, e.getMessage()));
            }
        }
        result.setFailed(result.getFailures().size());
        return result;
    }
```

新增 import:`com.zhiyi.memory.domain.KnowledgeBatchDeleteResult`、`java.util.List`(若未导入)。

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn -q -Dtest=KnowledgeServiceBatchDeleteTest test`
Expected: BUILD SUCCESS,2 个测试通过。

- [ ] **Step 6: 在 KnowledgeController 新增批量删除端点**

在 `KnowledgeController` 新增端点(放在单删 `delete` 端点之前,注意 `/batch` 路径不要被 `/{id}` 抢匹配——`@DeleteMapping("/batch")` 与 `@DeleteMapping("/{id}")` 共存时,Spring 优先精确匹配 `/batch`)。新增内部请求 DTO:

```java
    /**
     * 批量删除经验/规则
     */
    @DeleteMapping("/batch")
    public Result<com.zhiyi.memory.domain.KnowledgeBatchDeleteResult> deleteBatch(
            @RequestBody BatchDeleteRequest batchRequest, HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        return Result.success(knowledgeService.deleteByIds(
                batchRequest.getIds(), requireWorkspaceId(loginUser),
                loginUser.getUserId(), loginUser.getMemberRole()));
    }

    @Data
    public static class BatchDeleteRequest {
        private List<Long> ids;
    }
```

新增 import:`org.springframework.web.bind.annotation.RequestBody`、`lombok.Data`、`java.util.List`。

- [ ] **Step 7: 手动验证编译 + 路由不冲突**

Run: `mvn -q compile`
Expected: BUILD SUCCESS。

- [ ] **Step 8: 提交**

```bash
git add src/main/java/com/zhiyi/memory/domain/KnowledgeBatchDeleteResult.java src/main/java/com/zhiyi/memory/knowledge/KnowledgeService.java src/main/java/com/zhiyi/memory/controller/KnowledgeController.java src/test/java/com/zhiyi/memory/knowledge/KnowledgeServiceBatchDeleteTest.java
git commit -m "feat: 新增批量删除知识端点 DELETE /knowledge/batch"
```

---

## Task 8: 删除工作空间端点

删工作空间是最重的操作:owner 校验 + confirmName + 物理级联清理 ~17 张表。因 `knowledge` 表带 `@TableLogic` 且 `knowledge_tag` 是普通接口,需新增几个自定义 SQL 方法。

**Files:**
- Modify: `src/main/java/com/zhiyi/memory/dao/KnowledgeMapper.java`(新增 2 个自定义方法)
- Modify: `src/main/java/com/zhiyi/memory/dao/KnowledgeTagMapper.java`(新增按 workspace 删的方法)
- Modify: `src/main/java/com/zhiyi/dao/SysUserMapper.java`(新增置空 last_workspace_id 方法)
- Create: `src/main/java/com/zhiyi/workspace/WorkspaceService.java`(承载级联清理)
- Modify: `src/main/java/com/zhiyi/controller/WorkspaceController.java`(新增 `DELETE /workspace/{id}`)
- Test: `src/test/java/com/zhiyi/workspace/WorkspaceServiceTest.java`

**Interfaces:**
- Consumes: `WorkspaceMemberRole.canManageWorkspace` / `OWNER`;各 mapper 的 `delete(Wrapper)` 与新自定义方法。
- Produces: `WorkspaceService.deleteWorkspace(workspaceId, userId, memberRole, confirmName)`;`DELETE /workspace/{workspaceId}`。

- [ ] **Step 1: 新增 Mapper 自定义方法**

Modify `src/main/java/com/zhiyi/memory/dao/KnowledgeMapper.java`,补充(保留现有内容):

```java
    /** 查询工作空间下全部知识 id(含已逻辑删除,绕过 @TableLogic) */
    @org.apache.ibatis.annotations.Select(
            "SELECT id FROM knowledge WHERE workspace_id = #{workspaceId}")
    java.util.List<java.lang.Long> selectIdsByWorkspace(@org.apache.ibatis.annotations.Param("workspaceId") String workspaceId);

    /** 物理删除工作空间下全部知识(绕过 @TableLogic) */
    @org.apache.ibatis.annotations.Delete(
            "DELETE FROM knowledge WHERE workspace_id = #{workspaceId}")
    int deleteByWorkspacePhysical(@org.apache.ibatis.annotations.Param("workspaceId") String workspaceId);
```

Modify `src/main/java/com/zhiyi/memory/dao/KnowledgeTagMapper.java`,补充:

```java
    /** 物理删除工作空间下全部知识标签(通过 knowledge 子查询) */
    @org.apache.ibatis.annotations.Delete(
            "DELETE FROM knowledge_tag WHERE knowledge_id IN "
                    + "(SELECT id FROM knowledge WHERE workspace_id = #{workspaceId})")
    int deleteByWorkspace(@org.apache.ibatis.annotations.Param("workspaceId") String workspaceId);
```

Modify `src/main/java/com/zhiyi/dao/SysUserMapper.java`,补充:

```java
    /** 将指向该工作空间的 last_workspace_id 置空 */
    @org.apache.ibatis.annotations.Update(
            "UPDATE sys_user SET last_workspace_id = NULL WHERE last_workspace_id = #{workspaceId}")
    int clearLastWorkspace(@org.apache.ibatis.annotations.Param("workspaceId") String workspaceId);
```

> 上述用全限定名避免 import 改动;若文件已有 `@Select`/`@Param` 的 import,可直接用短名。`SysUserMapper` 路径以现有为准(WorkspaceController 已注入它)。

- [ ] **Step 2: 写失败测试**

Create `src/test/java/com/zhiyi/workspace/WorkspaceServiceTest.java`:

```java
package com.zhiyi.workspace;

import com.zhiyi.common.BusinessException;
import com.zhiyi.controller.WorkspaceService;
import com.zhiyi.dao.ApiKeyMapper;
import com.zhiyi.dao.SysUserMapper;
import com.zhiyi.dao.UsageDailyMapper;
import com.zhiyi.dao.WorkspaceGovernanceConfigMapper;
import com.zhiyi.dao.WorkspaceMapper;
import com.zhiyi.dao.WorkspaceMemberMapper;
import com.zhiyi.domain.entity.WorkspaceEntity;
import com.zhiyi.memory.dao.CaptureAiReviewMapper;
import com.zhiyi.memory.dao.CaptureDraftMapper;
import com.zhiyi.memory.dao.GovernanceIssueMapper;
import com.zhiyi.memory.dao.GovernanceScanBatchMapper;
import com.zhiyi.memory.dao.KnowledgeArtifactMapper;
import com.zhiyi.memory.dao.KnowledgeFactMapper;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.dao.KnowledgeRelationMapper;
import com.zhiyi.memory.dao.KnowledgeTagMapper;
import com.zhiyi.memory.dao.KnowledgeTimelineMapper;
import com.zhiyi.memory.dao.KnowledgeVectorRefMapper;
import com.zhiyi.memory.dao.MemoryFeedbackMapper;
import com.zhiyi.memory.dao.MemoryOperationLogMapper;
import com.zhiyi.memory.dao.SystemEventMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock private WorkspaceMapper workspaceMapper;
    @Mock private WorkspaceMemberMapper workspaceMemberMapper;
    @Mock private WorkspaceGovernanceConfigMapper workspaceGovernanceConfigMapper;
    @Mock private ApiKeyMapper apiKeyMapper;
    @Mock private UsageDailyMapper usageDailyMapper;
    @Mock private KnowledgeMapper knowledgeMapper;
    @Mock private KnowledgeFactMapper knowledgeFactMapper;
    @Mock private KnowledgeArtifactMapper knowledgeArtifactMapper;
    @Mock private KnowledgeTagMapper knowledgeTagMapper;
    @Mock private KnowledgeVectorRefMapper knowledgeVectorRefMapper;
    @Mock private KnowledgeRelationMapper knowledgeRelationMapper;
    @Mock private KnowledgeTimelineMapper knowledgeTimelineMapper;
    @Mock private MemoryFeedbackMapper memoryFeedbackMapper;
    @Mock private MemoryOperationLogMapper memoryOperationLogMapper;
    @Mock private CaptureDraftMapper captureDraftMapper;
    @Mock private CaptureAiReviewMapper captureAiReviewMapper;
    @Mock private SystemEventMapper systemEventMapper;
    @Mock private GovernanceIssueMapper governanceIssueMapper;
    @Mock private GovernanceScanBatchMapper governanceScanBatchMapper;
    @Mock private SysUserMapper sysUserMapper;

    @InjectMocks private WorkspaceService workspaceService;

    @Test
    void deleteWorkspace_should_require_owner() {
        given(workspaceMapper.selectById("1")).willReturn(workspace("默认", "1"));
        assertThrows(BusinessException.class,
                () -> workspaceService.deleteWorkspace("1", 2L, WorkspaceMemberRole.EDITOR, "默认"));
    }

    @Test
    void deleteWorkspace_should_require_confirm_name_match() {
        given(workspaceMapper.selectById("1")).willReturn(workspace("默认", "1"));
        assertThrows(BusinessException.class,
                () -> workspaceService.deleteWorkspace("1", 1L, WorkspaceMemberRole.OWNER, "错的名称"));
    }

    @Test
    void deleteWorkspace_should_cascade_delete_all_tables() {
        given(workspaceMapper.selectById("1")).willReturn(workspace("默认", "1"));

        workspaceService.deleteWorkspace("1", 1L, WorkspaceMemberRole.OWNER, "默认");

        verify(governanceIssueMapper).delete(any());
        verify(governanceScanBatchMapper).delete(any());
        verify(captureAiReviewMapper).delete(any());
        verify(captureDraftMapper).delete(any());
        verify(systemEventMapper).delete(any());
        verify(knowledgeRelationMapper).delete(any());
        verify(knowledgeTimelineMapper).delete(any());
        verify(knowledgeTagMapper).deleteByWorkspace("1");
        verify(knowledgeFactMapper).delete(any());
        verify(knowledgeArtifactMapper).delete(any());
        verify(knowledgeVectorRefMapper).delete(any());
        verify(memoryFeedbackMapper).delete(any());
        verify(knowledgeMapper).deleteByWorkspacePhysical("1");
        verify(memoryOperationLogMapper).delete(any());
        verify(usageDailyMapper).delete(any());
        verify(apiKeyMapper).delete(any());
        verify(workspaceGovernanceConfigMapper).delete(any());
        verify(workspaceMemberMapper).delete(any());
        verify(sysUserMapper).clearLastWorkspace("1");
        verify(workspaceMapper).deleteById("1");
    }

    private WorkspaceEntity workspace(String name, String id) {
        WorkspaceEntity ws = new WorkspaceEntity();
        ws.setId(id);
        ws.setWorkspaceName(name);
        ws.setOrganizationId(1L);
        return ws;
    }
}
```

> `WorkspaceService` 包路径取 `com.zhiyi.controller`(与 WorkspaceController 同包,符合现有约定)。若决定放 `com.zhiyi.workspace` 包,调整 import。本计划统一放 `com.zhiyi.controller`。

- [ ] **Step 3: 运行测试验证失败**

Run: `mvn -q -Dtest=WorkspaceServiceTest test`
Expected: 编译失败(`WorkspaceService` 不存在)。

- [ ] **Step 4: 实现 WorkspaceService**

Create `src/main/java/com/zhiyi/controller/WorkspaceService.java`:

```java
package com.zhiyi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.dao.ApiKeyMapper;
import com.zhiyi.dao.SysUserMapper;
import com.zhiyi.dao.UsageDailyMapper;
import com.zhiyi.dao.WorkspaceGovernanceConfigMapper;
import com.zhiyi.dao.WorkspaceMapper;
import com.zhiyi.dao.WorkspaceMemberMapper;
import com.zhiyi.domain.entity.ApiKeyEntity;
import com.zhiyi.domain.entity.UsageDailyEntity;
import com.zhiyi.domain.entity.WorkspaceEntity;
import com.zhiyi.domain.entity.WorkspaceGovernanceConfigEntity;
import com.zhiyi.domain.entity.WorkspaceMemberEntity;
import com.zhiyi.memory.dao.CaptureAiReviewMapper;
import com.zhiyi.memory.dao.CaptureDraftMapper;
import com.zhiyi.memory.dao.GovernanceIssueMapper;
import com.zhiyi.memory.dao.GovernanceScanBatchMapper;
import com.zhiyi.memory.dao.KnowledgeArtifactMapper;
import com.zhiyi.memory.dao.KnowledgeFactMapper;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.dao.KnowledgeRelationMapper;
import com.zhiyi.memory.dao.KnowledgeTagMapper;
import com.zhiyi.memory.dao.KnowledgeTimelineMapper;
import com.zhiyi.memory.dao.KnowledgeVectorRefMapper;
import com.zhiyi.memory.dao.MemoryFeedbackMapper;
import com.zhiyi.memory.dao.MemoryOperationLogMapper;
import com.zhiyi.memory.dao.SystemEventMapper;
import com.zhiyi.memory.entity.CaptureAiReviewEntity;
import com.zhiyi.memory.entity.CaptureDraftEntity;
import com.zhiyi.memory.entity.GovernanceIssueEntity;
import com.zhiyi.memory.entity.GovernanceScanBatchEntity;
import com.zhiyi.memory.entity.KnowledgeArtifactEntity;
import com.zhiyi.memory.entity.KnowledgeFactEntity;
import com.zhiyi.memory.entity.KnowledgeRelationEntity;
import com.zhiyi.memory.entity.KnowledgeTimelineEntity;
import com.zhiyi.memory.entity.KnowledgeVectorRefEntity;
import com.zhiyi.memory.entity.MemoryFeedbackEntity;
import com.zhiyi.memory.entity.MemoryOperationLogEntity;
import com.zhiyi.memory.entity.SystemEventEntity;
import com.zhiyi.workspace.WorkspaceMemberRole;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 工作空间管理服务:承载删除工作空间的级联清理(无 DB 外键,应用层手动清理)。
 */
@Service
public class WorkspaceService {

    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final WorkspaceGovernanceConfigMapper workspaceGovernanceConfigMapper;
    private final ApiKeyMapper apiKeyMapper;
    private final UsageDailyMapper usageDailyMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final KnowledgeFactMapper knowledgeFactMapper;
    private final KnowledgeArtifactMapper knowledgeArtifactMapper;
    private final KnowledgeTagMapper knowledgeTagMapper;
    private final KnowledgeVectorRefMapper knowledgeVectorRefMapper;
    private final KnowledgeRelationMapper knowledgeRelationMapper;
    private final KnowledgeTimelineMapper knowledgeTimelineMapper;
    private final MemoryFeedbackMapper memoryFeedbackMapper;
    private final MemoryOperationLogMapper memoryOperationLogMapper;
    private final CaptureDraftMapper captureDraftMapper;
    private final CaptureAiReviewMapper captureAiReviewMapper;
    private final SystemEventMapper systemEventMapper;
    private final GovernanceIssueMapper governanceIssueMapper;
    private final GovernanceScanBatchMapper governanceScanBatchMapper;
    private final SysUserMapper sysUserMapper;

    public WorkspaceService(WorkspaceMapper workspaceMapper,
                            WorkspaceMemberMapper workspaceMemberMapper,
                            WorkspaceGovernanceConfigMapper workspaceGovernanceConfigMapper,
                            ApiKeyMapper apiKeyMapper,
                            UsageDailyMapper usageDailyMapper,
                            KnowledgeMapper knowledgeMapper,
                            KnowledgeFactMapper knowledgeFactMapper,
                            KnowledgeArtifactMapper knowledgeArtifactMapper,
                            KnowledgeTagMapper knowledgeTagMapper,
                            KnowledgeVectorRefMapper knowledgeVectorRefMapper,
                            KnowledgeRelationMapper knowledgeRelationMapper,
                            KnowledgeTimelineMapper knowledgeTimelineMapper,
                            MemoryFeedbackMapper memoryFeedbackMapper,
                            MemoryOperationLogMapper memoryOperationLogMapper,
                            CaptureDraftMapper captureDraftMapper,
                            CaptureAiReviewMapper captureAiReviewMapper,
                            SystemEventMapper systemEventMapper,
                            GovernanceIssueMapper governanceIssueMapper,
                            GovernanceScanBatchMapper governanceScanBatchMapper,
                            SysUserMapper sysUserMapper) {
        this.workspaceMapper = workspaceMapper;
        this.workspaceMemberMapper = workspaceMemberMapper;
        this.workspaceGovernanceConfigMapper = workspaceGovernanceConfigMapper;
        this.apiKeyMapper = apiKeyMapper;
        this.usageDailyMapper = usageDailyMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.knowledgeFactMapper = knowledgeFactMapper;
        this.knowledgeArtifactMapper = knowledgeArtifactMapper;
        this.knowledgeTagMapper = knowledgeTagMapper;
        this.knowledgeVectorRefMapper = knowledgeVectorRefMapper;
        this.knowledgeRelationMapper = knowledgeRelationMapper;
        this.knowledgeTimelineMapper = knowledgeTimelineMapper;
        this.memoryFeedbackMapper = memoryFeedbackMapper;
        this.memoryOperationLogMapper = memoryOperationLogMapper;
        this.captureDraftMapper = captureDraftMapper;
        this.captureAiReviewMapper = captureAiReviewMapper;
        this.systemEventMapper = systemEventMapper;
        this.governanceIssueMapper = governanceIssueMapper;
        this.governanceScanBatchMapper = governanceScanBatchMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteWorkspace(String workspaceId, Long userId, String memberRole, String confirmName) {
        if (StringUtils.isBlank(workspaceId)) {
            throw new BusinessException(400, "工作空间 ID 不能为空");
        }
        if (!WorkspaceMemberRole.OWNER.equals(memberRole)) {
            throw new BusinessException(403, "仅工作空间 owner 可删除工作空间");
        }
        WorkspaceEntity workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw new BusinessException(404, "工作空间不存在");
        }
        if (!workspace.getWorkspaceName().equals(confirmName)) {
            throw new BusinessException(400, "输入的名称与工作空间名称不符");
        }

        // 1. 治理工单 / 批次
        governanceIssueMapper.delete(byWorkspace(GovernanceIssueEntity::getWorkspaceId, workspaceId));
        governanceScanBatchMapper.delete(byWorkspace(GovernanceScanBatchEntity::getWorkspaceId, workspaceId));
        // 2. capture_ai_review / capture_draft / system_event
        captureAiReviewMapper.delete(byWorkspace(CaptureAiReviewEntity::getWorkspaceId, workspaceId));
        captureDraftMapper.delete(byWorkspace(CaptureDraftEntity::getWorkspaceId, workspaceId));
        systemEventMapper.delete(byWorkspace(SystemEventEntity::getWorkspaceId, workspaceId));
        // 3. 知识关系边 / 时间线(按 workspace_id)
        knowledgeRelationMapper.delete(byWorkspace(KnowledgeRelationEntity::getWorkspaceId, workspaceId));
        knowledgeTimelineMapper.delete(byWorkspace(KnowledgeTimelineEntity::getWorkspaceId, workspaceId));
        // 4. 知识子表(按 knowledge_id,需先查 id 或用子查询)
        knowledgeTagMapper.deleteByWorkspace(workspaceId);
        List<Long> knowledgeIds = knowledgeMapper.selectIdsByWorkspace(workspaceId);
        if (knowledgeIds != null && !knowledgeIds.isEmpty()) {
            knowledgeFactMapper.delete(inKnowledge(KnowledgeFactEntity::getKnowledgeId, knowledgeIds));
            knowledgeArtifactMapper.delete(inKnowledge(KnowledgeArtifactEntity::getKnowledgeId, knowledgeIds));
            knowledgeVectorRefMapper.delete(inKnowledge(KnowledgeVectorRefEntity::getKnowledgeId, knowledgeIds));
            memoryFeedbackMapper.delete(inKnowledge(MemoryFeedbackEntity::getKnowledgeId, knowledgeIds));
        }
        // 5. knowledge 主表物理删
        knowledgeMapper.deleteByWorkspacePhysical(workspaceId);
        // 6. 操作日志 / 用量 / API Key / 治理配置 / 成员
        memoryOperationLogMapper.delete(byWorkspace(MemoryOperationLogEntity::getWorkspaceId, workspaceId));
        usageDailyMapper.delete(byWorkspace(UsageDailyEntity::getWorkspaceId, workspaceId));
        apiKeyMapper.delete(byWorkspace(ApiKeyEntity::getWorkspaceId, workspaceId));
        workspaceGovernanceConfigMapper.delete(byWorkspace(WorkspaceGovernanceConfigEntity::getWorkspaceId, workspaceId));
        workspaceMemberMapper.delete(byWorkspace(WorkspaceMemberEntity::getWorkspaceId, workspaceId));
        // 7. sys_user.last_workspace_id 置空 + 工作空间物理删
        sysUserMapper.clearLastWorkspace(workspaceId);
        workspaceMapper.deleteById(workspaceId);
    }

    private <T> LambdaQueryWrapper<T> byWorkspace(
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> column, String workspaceId) {
        return new LambdaQueryWrapper<T>().eq(column, workspaceId);
    }

    private <T> LambdaQueryWrapper<T> inKnowledge(
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> column, List<Long> knowledgeIds) {
        return new LambdaQueryWrapper<T>().in(column, knowledgeIds);
    }
}
```

> `SFunction` 来自 MyBatis-Plus(`com.baomidou.mybatisplus.core.toolkit.support.SFunction`),用于 Lambda 字段引用。各子表实体需有 `getWorkspaceId()` / `getKnowledgeId()` 字段(基于 schema 已有)。若个别实体字段名不同(如 `getWorkspaceId` 返回类型不匹配),以源码为准。

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn -q -Dtest=WorkspaceServiceTest test`
Expected: BUILD SUCCESS,3 个测试通过。若某个 `byWorkspace` 的字段引用编译失败,核对对应实体字段名后修正。

- [ ] **Step 6: 在 WorkspaceController 新增删除端点**

Modify `src/main/java/com/zhiyi/controller/WorkspaceController.java`,注入 `WorkspaceService`(构造器新增参数),并新增端点:

构造器追加 `WorkspaceService workspaceService` 参数与赋值(参考现有 4 个依赖的注入模式)。

新增端点(放在 create 端点之后):

```java
    /**
     * 删除工作空间(owner 专属,物理级联清理,需输入工作空间名称确认)
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/{workspaceId}")
    public com.zhiyi.common.Result<Void> delete(@PathVariable String workspaceId,
                                                @org.springframework.web.bind.annotation.RequestBody DeleteWorkspaceRequest body,
                                                HttpServletRequest request) {
        com.zhiyi.auth.LoginContext.LoginUserVO loginUser = com.zhiyi.auth.LoginContext.requireLoginUser(request);
        workspaceService.deleteWorkspace(workspaceId, loginUser.getUserId(),
                loginUser.getMemberRole(), body == null ? null : body.getConfirmName());
        return com.zhiyi.common.Result.success(null);
    }

    @lombok.Data
    public static class DeleteWorkspaceRequest {
        private String confirmName;
    }
```

> 用全限定名避免 import 改动;若已有 `Result`、`LoginContext`、`LoginUserVO`、`DeleteMapping`、`RequestBody`、`Data` 的 import,用短名。`LoginContext` 与 `LoginUserVO` 的包路径以现有 WorkspaceController 用法为准(它已用 `LoginContext.requireLoginUser`)。

- [ ] **Step 7: 全量编译 + 全量测试**

Run: `mvn -q test`
Expected: BUILD SUCCESS,全部测试通过。

- [ ] **Step 8: 提交**

```bash
git add src/main/java/com/zhiyi/memory/dao/KnowledgeMapper.java src/main/java/com/zhiyi/memory/dao/KnowledgeTagMapper.java src/main/java/com/zhiyi/dao/SysUserMapper.java src/main/java/com/zhiyi/controller/WorkspaceService.java src/main/java/com/zhiyi/controller/WorkspaceController.java src/test/java/com/zhiyi/workspace/WorkspaceServiceTest.java
git commit -m "feat: 新增删除工作空间端点 DELETE /workspace/{id}(owner+输名称确认+级联清理)"
```

---

## Self-Review(作者自查记录)

- **Spec 覆盖**:
  - 删除工作空间 → Task 8 ✓
  - 导出 Markdown → Task 3(序列化器)+ Task 5(端点)✓
  - 批量导入经验 → Task 4(解析器)+ Task 6(端点)✓
  - 编辑经验/规则 → 经验已支持(spec 非目标);规则的草稿修订重发为**纯前端**编排(复用 `POST /knowledge` + publish + supersede,无新后端端点),在前端 plan 覆盖。后端无需改动。
  - 批量删除经验/规则 → Task 7 ✓
  - 修复单删子表清理 → Task 2 ✓
- **类型一致性**:各 task 产出的方法签名(`exportMarkdown`、`importMarkdown`、`deleteByIds`、`deleteWorkspace`)与 Controller 调用、测试 mock 一致;结果 DTO(`KnowledgeImportResult`、`KnowledgeBatchDeleteResult`)字段在 service 实现与测试断言中一致。
- **已知风险点**(实现时核对):
  - `loadAggregate` 内部调用的具体 mapper 方法名(Task 5 mock)以源码为准。
  - 各子表实体的 `getWorkspaceId`/`getKnowledgeId` 字段(Task 8 `byWorkspace`/`inKnowledge`)以源码为准。
  - `SysUserMapper`、`WorkspaceController` 现有 import 与 `LoginContext`/`LoginUserVO` 包路径以源码为准。
