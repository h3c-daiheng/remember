package com.zhiyi.memory.prompt;

import com.zhiyi.memory.domain.FactBlock;
import com.zhiyi.memory.domain.KnowledgeAggregate;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * 治理合并优化 Prompt 模板
 */
public final class MergePromptTemplates {

    private MergePromptTemplates() {
    }

    /**
     * 构建系统 Prompt：要求输出结构化 JSON，Facts 须映射源记忆信息
     */
    public static String buildSystemPrompt() {
        return "你是智忆（团队记忆库）的治理助手。"
                + "任务：将多条零散、不完整的经验记忆合并为一条结构完整的 Experience。"
                + "输出 JSON，字段：title（string，≤80字）、facts（array，每项含 type 与 text）、mergeSummary（string，简述合并逻辑）。"
                + "facts.type 仅允许 observation/decision/constraint/evidence/action/outcome。"
                + "须保留各源记忆中的关键决策与约束，禁止编造未出现的内容。"
                + "若源记忆互相矛盾，在 mergeSummary 中说明并在 facts 中保留较新或 recall 更高的结论。";
    }

    /**
     * 构建用户 Prompt：拼接源记忆标题与 Facts
     */
    public static String buildUserPrompt(List<KnowledgeAggregate> sourceList) {
        StringBuilder builder = new StringBuilder();
        builder.append("请将以下 ").append(sourceList.size()).append(" 条同主题零散经验合并为一条完整 Experience：\n\n");
        int index = 1;
        for (KnowledgeAggregate aggregate : sourceList) {
            builder.append("--- 源记忆 #").append(index++).append(" (id=").append(aggregate.getId()).append(") ---\n");
            builder.append("标题：").append(StringUtils.defaultString(aggregate.getTitle())).append('\n');
            if (StringUtils.isNotBlank(aggregate.getModule())) {
                builder.append("模块：").append(aggregate.getModule()).append('\n');
            }
            if (aggregate.getFacts() != null) {
                for (FactBlock factBlock : aggregate.getFacts()) {
                    if (factBlock == null || StringUtils.isBlank(factBlock.getText())) {
                        continue;
                    }
                    builder.append("- [").append(StringUtils.defaultString(factBlock.getType())).append("] ")
                            .append(factBlock.getText()).append('\n');
                }
            }
            builder.append('\n');
        }
        return builder.toString();
    }
}
