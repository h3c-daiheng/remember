package com.zhiyi.memory.context;

import com.zhiyi.memory.domain.RecallContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据 RecallContext 构建检索 Query 文本。
 * 仅拼接任务意图字段，避免 project/module/repository 等上下文词面污染检索相似度。
 */
@Component
public class ContextQueryBuilder {

    /**
     * 拼接任务意图相关字段作为检索 Query；
     * project / module / repository / language / framework / modifiedFiles 只参与 Ranking，不进入 Query。
     */
    public String buildQueryText(RecallContext recallContext) {
        List<String> parts = new ArrayList<String>();
        appendIfPresent(parts, recallContext.getTask());
        appendIfPresent(parts, recallContext.getCurrentPrompt());
        appendIfPresent(parts, recallContext.getIssue());
        if (recallContext.getTags() != null) {
            parts.addAll(recallContext.getTags());
        }
        return StringUtils.join(parts, " ");
    }

    private void appendIfPresent(List<String> parts, String value) {
        if (StringUtils.isNotBlank(value)) {
            parts.add(value.trim());
        }
    }
}
