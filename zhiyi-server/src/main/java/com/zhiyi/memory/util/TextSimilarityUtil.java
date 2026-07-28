package com.zhiyi.memory.util;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 文本相似度工具，MVP 阶段基于词频余弦相似度实现语义检索
 */
public final class TextSimilarityUtil {

    private TextSimilarityUtil() {
    }

    /**
     * 计算两段文本的余弦相似度，范围 0~1
     */
    public static double cosineSimilarity(String leftText, String rightText) {
        Map<String, Integer> leftVector = buildTermFrequency(leftText);
        Map<String, Integer> rightVector = buildTermFrequency(rightText);
        if (leftVector.isEmpty() || rightVector.isEmpty()) {
            return 0D;
        }

        Set<String> allTerms = new HashSet<String>();
        allTerms.addAll(leftVector.keySet());
        allTerms.addAll(rightVector.keySet());

        double dotProduct = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (String term : allTerms) {
            double leftValue = leftVector.containsKey(term) ? leftVector.get(term) : 0;
            double rightValue = rightVector.containsKey(term) ? rightVector.get(term) : 0;
            dotProduct += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        if (leftNorm == 0D || rightNorm == 0D) {
            return 0D;
        }
        return dotProduct / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    /**
     * 构建词频向量，支持中英文混合分词
     */
    private static Map<String, Integer> buildTermFrequency(String text) {
        Map<String, Integer> termFrequency = new HashMap<String, Integer>();
        if (StringUtils.isBlank(text)) {
            return termFrequency;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        String[] tokens = normalized.split("[\\s,;:.!?\\-_/\\\\()\\[\\]{}\"'`，。；：！？、]+");
        for (String token : tokens) {
            if (StringUtils.isBlank(token)) {
                continue;
            }
            appendToken(termFrequency, token);
            if (containsChinese(token)) {
                for (int index = 0; index < token.length() - 1; index++) {
                    appendToken(termFrequency, token.substring(index, index + 2));
                }
            }
        }
        return termFrequency;
    }

    private static void appendToken(Map<String, Integer> termFrequency, String token) {
        if (StringUtils.isBlank(token) || token.length() < 2) {
            return;
        }
        Integer count = termFrequency.get(token);
        termFrequency.put(token, count == null ? 1 : count + 1);
    }

    private static boolean containsChinese(String token) {
        for (int index = 0; index < token.length(); index++) {
            if (token.charAt(index) >= 0x4E00 && token.charAt(index) <= 0x9FFF) {
                return true;
            }
        }
        return false;
    }
}
