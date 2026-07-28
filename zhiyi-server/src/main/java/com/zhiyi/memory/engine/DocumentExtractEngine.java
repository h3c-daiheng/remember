package com.zhiyi.memory.engine;

import cn.hutool.json.JSONUtil;
import com.zhiyi.common.BusinessException;
import com.zhiyi.domain.vo.LoginUserVO;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.context.ContextNormalizer;
import com.zhiyi.memory.domain.ArtifactDto;
import com.zhiyi.memory.domain.DocumentExtractLlmResult;
import com.zhiyi.memory.domain.DocumentImportExtractRequest;
import com.zhiyi.memory.domain.DocumentImportExtractResponse;
import com.zhiyi.memory.domain.DocumentImportQualityChecks;
import com.zhiyi.memory.domain.KnowledgeDraftContent;
import com.zhiyi.memory.domain.SystemEventRequest;
import com.zhiyi.memory.prompt.ExtractPromptTemplates;
import com.zhiyi.memory.util.MemoryJsonUtil;
import com.zhiyi.modelgateway.ModelGateway;
import com.zhiyi.modelgateway.config.ModelGatewayProperties;
import com.zhiyi.modelgateway.domain.ChatCompletionRequest;
import com.zhiyi.modelgateway.domain.ChatCompletionResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档抽取引擎：调用 LLM 将原始文档转为 Capture 草稿 Fact Blocks
 */
@Component
public class DocumentExtractEngine {

    private final ModelGateway modelGateway;
    private final ModelGatewayProperties modelGatewayProperties;
    private final SummarizeEngine summarizeEngine;
    private final ExtractQualityGate extractQualityGate;
    private final ContextNormalizer contextNormalizer;

    public DocumentExtractEngine(ModelGateway modelGateway,
                                 ModelGatewayProperties modelGatewayProperties,
                                 SummarizeEngine summarizeEngine,
                                 ExtractQualityGate extractQualityGate,
                                 ContextNormalizer contextNormalizer) {
        this.modelGateway = modelGateway;
        this.modelGatewayProperties = modelGatewayProperties;
        this.summarizeEngine = summarizeEngine;
        this.extractQualityGate = extractQualityGate;
        this.contextNormalizer = contextNormalizer;
    }

    /**
     * 从用户文档抽取结构化草稿，仅预览不入库
     */
    public DocumentImportExtractResponse extract(DocumentImportExtractRequest extractRequest, LoginUserVO loginUser) {
        validateExtractRequest(extractRequest, loginUser);

        String systemPrompt = ExtractPromptTemplates.buildSystemPrompt();
        String userPrompt = ExtractPromptTemplates.buildUserPrompt(extractRequest, loginUser.getWorkspaceCode());

        ChatCompletionRequest chatRequest = ChatCompletionRequest.builder()
                .profile(modelGatewayProperties.getDefaultChatProfile())
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .temperature(0.2)
                .maxTokens(4096)
                .responseFormat("json_object")
                .tenantId(String.valueOf(loginUser.getWorkspaceId()))
                .build();

        ChatCompletionResponse chatResponse = modelGateway.chat(chatRequest);
        DocumentExtractLlmResult llmResult = parseLlmResult(chatResponse.getContent());

        DocumentImportExtractResponse response = new DocumentImportExtractResponse();
        response.setRequestId(chatResponse.getRequestId());
        response.setSubmit(Boolean.TRUE.equals(llmResult.getSubmit()));
        response.setSkipReason(llmResult.getSkipReason());
        response.setSuggestedType(llmResult.getRecommendedKnowledgeType());
        response.setConfidence(llmResult.getConfidence());
        response.setRouteHint(llmResult.getRouteHint());

        if (!response.isSubmit()) {
            return response;
        }

        SystemEventRequest eventRequest = buildEventRequest(llmResult, extractRequest, loginUser);
        KnowledgeDraftContent draftContent = summarizeEngine.toFactBlocks(eventRequest);
        mergeUserContext(draftContent, extractRequest, loginUser);
        appendDocumentArtifact(draftContent, extractRequest);
        contextNormalizer.normalizeDraftContent(draftContent);
        draftContent = MemoryJsonUtil.sanitizeDraftContent(draftContent);

        response.setDraft(draftContent);
        response.setQualityChecks(extractQualityGate.check(draftContent, response.getSuggestedType()));
        return response;
    }

    /**
     * 校验抽取请求参数
     */
    private void validateExtractRequest(DocumentImportExtractRequest extractRequest, LoginUserVO loginUser) {
        if (extractRequest == null || StringUtils.isBlank(extractRequest.getContent())) {
            throw new BusinessException(400, "文档内容不能为空");
        }
        if (extractRequest.getContent().length() > MemoryConstants.IMPORT_MAX_CONTENT_LENGTH) {
            throw new BusinessException(400, "文档内容超过最大长度限制（" + MemoryConstants.IMPORT_MAX_CONTENT_LENGTH + " 字符）");
        }
        if (StringUtils.isBlank(loginUser.getWorkspaceId())) {
            throw new BusinessException(400, "当前未选择工作空间");
        }
        if (StringUtils.isBlank(extractRequest.getSourceType())) {
            extractRequest.setSourceType(MemoryConstants.IMPORT_SOURCE_PASTE);
        }
        if (StringUtils.isBlank(extractRequest.getTargetType())) {
            extractRequest.setTargetType("auto");
        }
    }

    /**
     * 解析 LLM 返回的 JSON，兼容 markdown 代码块包裹
     */
    private DocumentExtractLlmResult parseLlmResult(String rawContent) {
        if (StringUtils.isBlank(rawContent)) {
            throw new BusinessException(502, "LLM 返回内容为空，请检查 ai-gateway 配置与 API Key");
        }
        String normalizedJson = unwrapJsonContent(rawContent.trim());
        try {
            return JSONUtil.toBean(normalizedJson, DocumentExtractLlmResult.class);
        } catch (Exception exception) {
            throw new BusinessException(502, "LLM 返回 JSON 解析失败，请重试");
        }
    }

    /**
     * 去除 LLM 可能输出的 markdown 代码块标记
     */
    private String unwrapJsonContent(String rawContent) {
        if (rawContent.startsWith("```")) {
            int firstLineBreak = rawContent.indexOf('\n');
            if (firstLineBreak > 0) {
                rawContent = rawContent.substring(firstLineBreak + 1);
            }
            if (rawContent.endsWith("```")) {
                rawContent = rawContent.substring(0, rawContent.length() - 3);
            }
        }
        return rawContent.trim();
    }

    /**
     * 将 LLM 输出的 memoryRememberRequest 转为 SystemEventRequest
     */
    @SuppressWarnings("unchecked")
    private SystemEventRequest buildEventRequest(DocumentExtractLlmResult llmResult,
                                                 DocumentImportExtractRequest extractRequest,
                                                 LoginUserVO loginUser) {
        Map<String, Object> rememberRequest = llmResult.getMemoryRememberRequest();
        if (rememberRequest == null || rememberRequest.isEmpty()) {
            throw new BusinessException(502, "LLM 未返回有效的 memoryRememberRequest");
        }

        SystemEventRequest eventRequest = JSONUtil.toBean(JSONUtil.toJsonStr(rememberRequest), SystemEventRequest.class);
        eventRequest.setType(MemoryConstants.EVENT_TYPE_DOCUMENT_IMPORT);
        eventRequest.setActor(MemoryConstants.IMPORT_ACTOR_WEB);
        eventRequest.setWorkspaceId(loginUser.getWorkspaceId());
        eventRequest.setWorkspace(loginUser.getWorkspaceCode());
        eventRequest.setTime(new Date());

        if (StringUtils.isNotBlank(extractRequest.getModule())) {
            eventRequest.setModule(extractRequest.getModule());
        }
        if (StringUtils.isNotBlank(extractRequest.getRepository())) {
            eventRequest.setRepository(extractRequest.getRepository());
        }
        if (StringUtils.isNotBlank(extractRequest.getProject())) {
            eventRequest.setProject(extractRequest.getProject());
        }

        Map<String, Object> metadata = eventRequest.getMetadata();
        if (metadata == null) {
            metadata = new HashMap<String, Object>();
            eventRequest.setMetadata(metadata);
        }
        metadata.put("importSource", extractRequest.getSourceType());
        if (StringUtils.isNotBlank(extractRequest.getFileName())) {
            metadata.put("fileName", extractRequest.getFileName());
        }
        if (StringUtils.isNotBlank(extractRequest.getTitle())) {
            metadata.put("documentTitle", extractRequest.getTitle());
        }

        Map<String, Object> payload = eventRequest.getPayload();
        if (payload == null) {
            payload = new HashMap<String, Object>();
            eventRequest.setPayload(payload);
        }
        if (extractRequest.getTags() != null && !extractRequest.getTags().isEmpty()) {
            List<String> mergedTags = new ArrayList<String>();
            mergedTags.addAll(MemoryJsonUtil.readStringList(payload, "tags"));
            mergedTags.addAll(extractRequest.getTags());
            payload.put("tags", MemoryJsonUtil.sanitizeTags(mergedTags));
        }
        return eventRequest;
    }

    /**
     * 用用户填写的召回上下文覆盖 LLM 推断结果
     */
    private void mergeUserContext(KnowledgeDraftContent draftContent,
                                  DocumentImportExtractRequest extractRequest,
                                  LoginUserVO loginUser) {
        if (StringUtils.isNotBlank(extractRequest.getModule())) {
            draftContent.setModule(extractRequest.getModule());
        }
        if (StringUtils.isNotBlank(extractRequest.getRepository())) {
            draftContent.setRepository(extractRequest.getRepository());
        }
        if (StringUtils.isNotBlank(extractRequest.getProject())) {
            draftContent.setProject(extractRequest.getProject());
        }
        contextNormalizer.clearProjectIfSameAsWorkspace(draftContent, loginUser.getWorkspaceCode());
        if (extractRequest.getTags() != null && !extractRequest.getTags().isEmpty()) {
            List<String> mergedTags = new ArrayList<String>();
            if (draftContent.getTags() != null) {
                mergedTags.addAll(draftContent.getTags());
            }
            mergedTags.addAll(extractRequest.getTags());
            draftContent.setTags(MemoryJsonUtil.sanitizeTags(mergedTags));
        }
    }

    /**
     * 追加原始文档 Artifact，便于 Review 时溯源
     */
    private void appendDocumentArtifact(KnowledgeDraftContent draftContent, DocumentImportExtractRequest extractRequest) {
        List<ArtifactDto> artifacts = draftContent.getArtifacts();
        if (artifacts == null) {
            artifacts = new ArrayList<ArtifactDto>();
            draftContent.setArtifacts(artifacts);
        }

        String contentRef = StringUtils.isNotBlank(extractRequest.getFileName())
                ? extractRequest.getFileName()
                : StringUtils.abbreviate(StringUtils.defaultIfBlank(extractRequest.getTitle(), "粘贴文档"), 120);

        boolean alreadyExists = false;
        for (ArtifactDto artifactDto : artifacts) {
            if (artifactDto != null && StringUtils.equals(artifactDto.getContentRef(), contentRef)) {
                alreadyExists = true;
                break;
            }
        }
        if (!alreadyExists) {
            ArtifactDto documentArtifact = new ArtifactDto();
            documentArtifact.setArtifactType("document");
            documentArtifact.setArtifactRole("origin");
            documentArtifact.setContentRef(contentRef);
            artifacts.add(0, documentArtifact);
        }
    }
}
