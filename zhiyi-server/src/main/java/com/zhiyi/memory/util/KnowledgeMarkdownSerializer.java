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
        sb.append("tags: ").append(agg.getTags() == null ? "[]" : JSONUtil.toJsonStr(agg.getTags()).replace("\",\"", "\", \"")).append("\n");
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
