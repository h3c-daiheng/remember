package com.zhiyi.memory.controller;

import com.zhiyi.auth.LoginContext;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.Result;
import com.zhiyi.domain.vo.LoginUserVO;
import com.zhiyi.memory.domain.DocumentImportExtractRequest;
import com.zhiyi.memory.domain.DocumentImportExtractResponse;
import com.zhiyi.memory.domain.DocumentImportSubmitRequest;
import com.zhiyi.memory.knowledge.DocumentImportService;
import com.zhiyi.workspace.WorkspaceMemberRole;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 文档导入 API：粘贴/上传文档经 AI 抽取为 Capture 草稿
 */
@RestController
@RequestMapping("/import")
public class ImportController {

    private final DocumentImportService documentImportService;

    public ImportController(DocumentImportService documentImportService) {
        this.documentImportService = documentImportService;
    }

    /**
     * AI 抽取文档为结构化草稿预览，不直接入库
     */
    @PostMapping("/extract")
    public Result<DocumentImportExtractResponse> extract(@RequestBody DocumentImportExtractRequest extractRequest,
                                                         HttpServletRequest request) {
        LoginUserVO loginUser = requireKnowledgeEditor(request);
        return Result.success(documentImportService.extract(extractRequest, loginUser));
    }

    /**
     * 用户确认抽取结果后提交为 Capture 草稿，进入 Review 流程
     */
    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@RequestBody DocumentImportSubmitRequest submitRequest,
                                              HttpServletRequest request) {
        LoginUserVO loginUser = requireKnowledgeEditor(request);
        return Result.success(documentImportService.submit(submitRequest, loginUser));
    }

    /**
     * 校验当前用户具备内容协作权限（Owner / Admin / Editor 才可导入）
     */
    private LoginUserVO requireKnowledgeEditor(HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        if (!WorkspaceMemberRole.canEditKnowledge(loginUser.getMemberRole())) {
            throw new BusinessException(403, "当前角色无权限导入文档");
        }
        return loginUser;
    }
}
