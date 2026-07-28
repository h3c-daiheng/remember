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
