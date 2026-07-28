package com.zhiyi.memory.knowledge;

import com.zhiyi.common.BusinessException;
import com.zhiyi.domain.vo.LoginUserVO;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.context.ContextNormalizer;
import com.zhiyi.memory.domain.ArtifactDto;
import com.zhiyi.memory.domain.DocumentImportExtractRequest;
import com.zhiyi.memory.domain.DocumentImportExtractResponse;
import com.zhiyi.memory.domain.DocumentImportSubmitRequest;
import com.zhiyi.memory.domain.KnowledgeDraftContent;
import com.zhiyi.memory.domain.SystemEventRequest;
import com.zhiyi.memory.engine.CaptureEngine;
import com.zhiyi.memory.engine.DocumentExtractEngine;
import com.zhiyi.memory.util.MemoryJsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档导入服务：AI 抽取预览与提交 Capture 草稿
 */
@Service
public class DocumentImportService {

    private final DocumentExtractEngine documentExtractEngine;
    private final CaptureEngine captureEngine;
    private final ContextNormalizer contextNormalizer;

    public DocumentImportService(DocumentExtractEngine documentExtractEngine,
                                 CaptureEngine captureEngine,
                                 ContextNormalizer contextNormalizer) {
        this.documentExtractEngine = documentExtractEngine;
        this.captureEngine = captureEngine;
        this.contextNormalizer = contextNormalizer;
    }

    /**
     * AI 抽取文档为结构化草稿预览
     */
    public DocumentImportExtractResponse extract(DocumentImportExtractRequest extractRequest, LoginUserVO loginUser) {
        return documentExtractEngine.extract(extractRequest, loginUser);
    }

    /**
     * 用户确认后提交为 Capture 草稿，走 Review 闭环
     */
    public Map<String, Object> submit(DocumentImportSubmitRequest submitRequest, LoginUserVO loginUser) {
        if (submitRequest == null || submitRequest.getDraftContent() == null) {
            throw new BusinessException(400, "草稿内容不能为空");
        }
        if (StringUtils.isBlank(loginUser.getWorkspaceId())) {
            throw new BusinessException(400, "当前未选择工作空间");
        }

        KnowledgeDraftContent draftContent = MemoryJsonUtil.sanitizeDraftContent(submitRequest.getDraftContent());
        contextNormalizer.normalizeDraftContent(draftContent);
        if (draftContent.getFacts() == null || draftContent.getFacts().isEmpty()) {
            throw new BusinessException(400, "草稿至少包含一条 Fact Block");
        }

        SystemEventRequest eventRequest = buildSubmitEventRequest(submitRequest, draftContent, loginUser);
        return captureEngine.submit(eventRequest, loginUser.getUserId());
    }

    /**
     * 将用户确认的草稿包装为统一 Event，复用 CaptureEngine 入库
     */
    private SystemEventRequest buildSubmitEventRequest(DocumentImportSubmitRequest submitRequest,
                                                       KnowledgeDraftContent draftContent,
                                                       LoginUserVO loginUser) {
        SystemEventRequest eventRequest = new SystemEventRequest();
        eventRequest.setType(MemoryConstants.EVENT_TYPE_DOCUMENT_IMPORT);
        eventRequest.setActor(MemoryConstants.IMPORT_ACTOR_WEB);
        eventRequest.setWorkspace(loginUser.getWorkspaceCode());
        eventRequest.setWorkspaceId(loginUser.getWorkspaceId());
        eventRequest.setRepository(draftContent.getRepository());
        eventRequest.setModule(draftContent.getModule());
        eventRequest.setTime(new Date());

        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put("importSource", StringUtils.defaultIfBlank(submitRequest.getSourceType(), MemoryConstants.IMPORT_SOURCE_PASTE));
        if (StringUtils.isNotBlank(submitRequest.getFileName())) {
            metadata.put("fileName", submitRequest.getFileName());
        }
        if (StringUtils.isNotBlank(submitRequest.getTitle())) {
            metadata.put("documentTitle", submitRequest.getTitle());
        }
        if (StringUtils.isNotBlank(submitRequest.getSuggestedType())) {
            metadata.put("suggestedType", submitRequest.getSuggestedType());
        }
        if (StringUtils.isNotBlank(submitRequest.getRouteHint())) {
            metadata.put("routeHint", submitRequest.getRouteHint());
        }
        eventRequest.setMetadata(metadata);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("task", draftContent.getTitle());
        payload.put("facts", draftContent.getFacts());
        payload.put("tags", draftContent.getTags());
        eventRequest.setPayload(payload);

        List<ArtifactDto> artifacts = draftContent.getArtifacts();
        if (artifacts == null || artifacts.isEmpty()) {
            artifacts = new ArrayList<ArtifactDto>();
            ArtifactDto documentArtifact = new ArtifactDto();
            documentArtifact.setArtifactType("document");
            documentArtifact.setArtifactRole("origin");
            documentArtifact.setContentRef(StringUtils.defaultIfBlank(
                    submitRequest.getFileName(),
                    StringUtils.abbreviate(StringUtils.defaultIfBlank(submitRequest.getTitle(), "粘贴文档"), 120)));
            artifacts.add(documentArtifact);
        }
        eventRequest.setArtifacts(artifacts);
        return eventRequest;
    }
}
