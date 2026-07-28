package com.zhiyi.memory.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.zhiyi.memory.domain.ArtifactDto;
import com.zhiyi.memory.domain.FactBlock;
import com.zhiyi.memory.domain.KnowledgeDraftContent;
import com.zhiyi.memory.entity.KnowledgeArtifactEntity;
import com.zhiyi.memory.entity.KnowledgeFactEntity;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Memory 模块 JSON 与对象转换工具
 */
public final class MemoryJsonUtil {

    private MemoryJsonUtil() {
    }

    /**
     * 将对象序列化为 JSON 字符串
     */
    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        return JSONUtil.toJsonStr(value);
    }

    /**
     * 解析草稿 JSON
     */
    public static KnowledgeDraftContent parseDraftContent(String draftJson) {
        if (StrUtil.isBlank(draftJson)) {
            return new KnowledgeDraftContent();
        }
        KnowledgeDraftContent draftContent = JSONUtil.toBean(draftJson, KnowledgeDraftContent.class);
        return sanitizeDraftContent(draftContent);
    }

    /**
     * 清洗草稿内容：剔除空 Artifact、补全 Fact 类型，避免发布时违反非空约束
     */
    public static KnowledgeDraftContent sanitizeDraftContent(KnowledgeDraftContent draftContent) {
        if (draftContent == null) {
            return new KnowledgeDraftContent();
        }
        draftContent.setArtifacts(filterEmptyArtifacts(draftContent.getArtifacts()));
        draftContent.setFacts(sanitizeFacts(draftContent.getFacts()));
        draftContent.setTags(sanitizeTags(draftContent.getTags()));
        if (StringUtils.isBlank(draftContent.getTitle())) {
            draftContent.setTitle("未命名经验");
        }
        return draftContent;
    }

    /**
     * 标签去重并裁剪空白，避免 knowledge_tag 主键冲突
     */
    public static List<String> sanitizeTags(List<String> rawTags) {
        List<String> tags = new ArrayList<String>();
        if (rawTags == null) {
            return tags;
        }
        LinkedHashSet<String> seenTagNames = new LinkedHashSet<String>();
        for (String tagName : rawTags) {
            if (StringUtils.isBlank(tagName)) {
                continue;
            }
            String normalizedTagName = tagName.trim();
            if (seenTagNames.add(normalizedTagName)) {
                tags.add(normalizedTagName);
            }
        }
        return tags;
    }

    /**
     * 过滤无效 Fact Block，缺失 type 时默认 observation
     */
    private static List<FactBlock> sanitizeFacts(List<FactBlock> rawFacts) {
        List<FactBlock> facts = new ArrayList<FactBlock>();
        if (rawFacts == null) {
            return facts;
        }
        int sortOrder = 0;
        for (FactBlock factBlock : rawFacts) {
            if (factBlock == null || StringUtils.isBlank(factBlock.getText())) {
                continue;
            }
            if (StringUtils.isBlank(factBlock.getType())) {
                factBlock.setType("observation");
            }
            if (factBlock.getSortOrder() == null) {
                factBlock.setSortOrder(sortOrder);
            }
            facts.add(factBlock);
            sortOrder++;
        }
        return facts;
    }

    /**
     * 剔除无实质内容的 Artifact，避免 Review 页展示 [] · 占位行
     */
    public static List<ArtifactDto> filterEmptyArtifacts(List<ArtifactDto> rawArtifacts) {
        List<ArtifactDto> artifacts = new ArrayList<ArtifactDto>();
        if (rawArtifacts == null) {
            return artifacts;
        }
        for (ArtifactDto artifactDto : rawArtifacts) {
            if (artifactDto == null) {
                continue;
            }
            if (StringUtils.isAllBlank(artifactDto.getContentRef(), artifactDto.getArtifactUrl(), artifactDto.getArtifactType())) {
                continue;
            }
            artifacts.add(artifactDto);
        }
        return artifacts;
    }

    /**
     * FactBlock 转实体
     */
    public static KnowledgeFactEntity toFactEntity(Long knowledgeId, FactBlock factBlock, int sortOrder) {
        KnowledgeFactEntity entity = new KnowledgeFactEntity();
        entity.setKnowledgeId(knowledgeId);
        entity.setBlockType(factBlock.getType());
        entity.setBlockText(factBlock.getText());
        entity.setBlockMetadata(factBlock.getMetadata() == null ? null : toJson(factBlock.getMetadata()));
        entity.setSortOrder(factBlock.getSortOrder() == null ? sortOrder : factBlock.getSortOrder());
        return entity;
    }

    /**
     * 实体转 FactBlock
     */
    public static FactBlock toFactBlock(KnowledgeFactEntity entity) {
        FactBlock factBlock = new FactBlock();
        factBlock.setType(entity.getBlockType());
        factBlock.setText(entity.getBlockText());
        factBlock.setSortOrder(entity.getSortOrder());
        if (StrUtil.isNotBlank(entity.getBlockMetadata())) {
            factBlock.setMetadata(JSONUtil.toBean(entity.getBlockMetadata(), Map.class));
        }
        return factBlock;
    }

    /**
     * ArtifactDto 转实体
     */
    public static KnowledgeArtifactEntity toArtifactEntity(Long knowledgeId, ArtifactDto artifactDto) {
        KnowledgeArtifactEntity entity = new KnowledgeArtifactEntity();
        entity.setKnowledgeId(knowledgeId);
        entity.setArtifactType(artifactDto.getArtifactType());
        entity.setArtifactRole(artifactDto.getArtifactRole());
        entity.setArtifactUrl(artifactDto.getArtifactUrl());
        entity.setContentRef(artifactDto.getContentRef());
        entity.setAuthorId(artifactDto.getAuthorId());
        return entity;
    }

    /**
     * 实体转 ArtifactDto
     */
    public static ArtifactDto toArtifactDto(KnowledgeArtifactEntity entity) {
        ArtifactDto artifactDto = new ArtifactDto();
        artifactDto.setArtifactType(entity.getArtifactType());
        artifactDto.setArtifactRole(entity.getArtifactRole());
        artifactDto.setArtifactUrl(entity.getArtifactUrl());
        artifactDto.setContentRef(entity.getContentRef());
        artifactDto.setAuthorId(entity.getAuthorId());
        if (entity.getArtifactTime() != null) {
            artifactDto.setArtifactTime(String.valueOf(entity.getArtifactTime().getTime()));
        }
        return artifactDto;
    }

    /**
     * 从 Map 载荷中读取字符串字段
     */
    public static String readString(Map<String, Object> payload, String key) {
        if (payload == null || !payload.containsKey(key) || payload.get(key) == null) {
            return null;
        }
        return String.valueOf(payload.get(key));
    }

    /**
     * 从 Map 载荷中读取字符串列表
     */
    @SuppressWarnings("unchecked")
    public static List<String> readStringList(Map<String, Object> payload, String key) {
        if (payload == null || !payload.containsKey(key)) {
            return Collections.emptyList();
        }
        Object value = payload.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return Collections.emptyList();
    }

    /**
     * 构建空的 score breakdown
     */
    public static Map<String, Double> emptyScoreBreakdown() {
        Map<String, Double> breakdown = new HashMap<String, Double>();
        breakdown.put("taskMatch", 0D);
        breakdown.put("similarity", 0D);
        breakdown.put("trust", 0D);
        breakdown.put("freshness", 0D);
        breakdown.put("feedback", 0D);
        return breakdown;
    }
}
