package com.zhiyi.memory.controller;

import com.zhiyi.auth.LoginContext;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.Result;
import com.zhiyi.domain.vo.GraphViewVO;
import com.zhiyi.domain.vo.LoginUserVO;
import com.zhiyi.memory.graph.GraphQueryService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 经验图谱 API：子图查询，供 Web 可视化与 Agent 探索
 */
@RestController
@RequestMapping("/graph")
public class GraphController {

    private final GraphQueryService graphQueryService;

    public GraphController(GraphQueryService graphQueryService) {
        this.graphQueryService = graphQueryService;
    }

    /**
     * 以 centerId 为中心查询子图
     */
    @GetMapping
    public Result<GraphViewVO> queryGraph(@RequestParam Long centerId,
                                          @RequestParam(defaultValue = "1") int depth,
                                          @RequestParam(defaultValue = "50") int limit,
                                          @RequestParam(required = false) String types,
                                          HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        return Result.success(graphQueryService.querySubGraph(
                centerId,
                requireWorkspaceId(loginUser),
                depth,
                limit,
                parseRelationTypes(types)));
    }

    private List<String> parseRelationTypes(String types) {
        if (StringUtils.isBlank(types)) {
            return Collections.emptyList();
        }
        List<String> relationTypeList = new ArrayList<String>();
        for (String relationType : types.split(",")) {
            if (StringUtils.isNotBlank(relationType)) {
                relationTypeList.add(relationType.trim());
            }
        }
        return relationTypeList;
    }

    private String requireWorkspaceId(LoginUserVO loginUser) {
        if (StringUtils.isBlank(loginUser.getWorkspaceId())) {
            throw new BusinessException(400, "当前未选择工作空间");
        }
        return loginUser.getWorkspaceId();
    }
}
