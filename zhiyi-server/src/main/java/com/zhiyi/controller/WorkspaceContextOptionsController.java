package com.zhiyi.controller;

import com.zhiyi.auth.LoginContext;
import com.zhiyi.common.Result;
import com.zhiyi.domain.vo.LoginUserVO;
import com.zhiyi.domain.vo.WorkspaceContextOptionsVO;
import com.zhiyi.service.WorkspaceContextOptionsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 工作空间 Recall 上下文字段可选值接口
 */
@RestController
@RequestMapping("/workspace/{workspaceId}/context-options")
public class WorkspaceContextOptionsController {

    private final WorkspaceContextOptionsService workspaceContextOptionsService;

    public WorkspaceContextOptionsController(WorkspaceContextOptionsService workspaceContextOptionsService) {
        this.workspaceContextOptionsService = workspaceContextOptionsService;
    }

    /**
     * 获取仓库 / 项目 / 模块 / 标签下拉选项（Workspace 内历史数据去重聚合）
     */
    @GetMapping
    public Result<WorkspaceContextOptionsVO> getOptions(HttpServletRequest request,
                                                        @PathVariable("workspaceId") String workspaceId) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        WorkspaceContextOptionsVO optionsView = workspaceContextOptionsService.getOptions(
                loginUser, workspaceId);
        return Result.success(optionsView);
    }
}
