package com.zhiyi.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhiyi.auth.LoginContext;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.Result;
import com.zhiyi.dao.SysUserMapper;
import com.zhiyi.dao.WorkspaceMapper;
import com.zhiyi.dao.WorkspaceMemberMapper;
import com.zhiyi.service.AuthService;
import com.zhiyi.workspace.WorkspaceMemberRole;
import com.zhiyi.domain.entity.SysUser;
import com.zhiyi.domain.entity.WorkspaceEntity;
import com.zhiyi.domain.entity.WorkspaceMemberEntity;
import com.zhiyi.domain.vo.LoginUserVO;
import com.zhiyi.domain.vo.WorkspaceListItemVO;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作空间本地接口：列表与切换（不依赖 gonline 主站）。
 * 与本地账号登录同走 /api 通道，Result.code=0；对齐前端 /api/workspace/* 调用。
 */
@RestController
@RequestMapping("/workspace")
public class WorkspaceController {

    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final SysUserMapper sysUserMapper;
    private final AuthService authService;
    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceMapper workspaceMapper,
                               WorkspaceMemberMapper workspaceMemberMapper,
                               SysUserMapper sysUserMapper,
                               AuthService authService,
                               WorkspaceService workspaceService) {
        this.workspaceMapper = workspaceMapper;
        this.workspaceMemberMapper = workspaceMemberMapper;
        this.sysUserMapper = sysUserMapper;
        this.authService = authService;
        this.workspaceService = workspaceService;
    }

    /**
     * 当前用户可访问的工作空间列表，标记当前会话所在空间
     */
    @GetMapping("/list")
    public Result<List<WorkspaceListItemVO>> list(HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        Long userId = loginUser.getUserId();
        // current 以本地 sys_user.last_workspace_id 为准：switch 已更新此字段，
        // 避免沿用 JWT 旧 workspaceId 导致切换后 current 不变、前端"切了又切回"
        SysUser sysUser = sysUserMapper.selectById(userId);
        String currentWorkspaceId = sysUser != null ? sysUser.getLastWorkspaceId() : loginUser.getWorkspaceId();

        List<WorkspaceMemberEntity> memberships = workspaceMemberMapper.selectList(
                new QueryWrapper<WorkspaceMemberEntity>().eq("user_id", userId));
        if (memberships.isEmpty()) {
            return Result.success(new ArrayList<>());
        }

        List<String> workspaceIds = memberships.stream()
                .map(WorkspaceMemberEntity::getWorkspaceId)
                .collect(Collectors.toList());
        List<WorkspaceEntity> workspaces = workspaceMapper.selectList(
                new QueryWrapper<WorkspaceEntity>()
                        .in("id", workspaceIds)
                        .eq("status", 1));
        Map<String, WorkspaceMemberEntity> memberByWorkspaceId = memberships.stream()
                .collect(Collectors.toMap(WorkspaceMemberEntity::getWorkspaceId, m -> m, (a, b) -> a));

        List<WorkspaceListItemVO> list = new ArrayList<>();
        for (WorkspaceEntity workspace : workspaces) {
            WorkspaceMemberEntity member = memberByWorkspaceId.get(workspace.getId());
            if (member == null) {
                continue;
            }
            WorkspaceListItemVO vo = new WorkspaceListItemVO();
            vo.setId(workspace.getId());
            vo.setWorkspaceCode(workspace.getWorkspaceCode());
            vo.setWorkspaceName(workspace.getWorkspaceName());
            vo.setWorkspaceType(null);
            vo.setMemberRole(member.getMemberRole());
            vo.setMemberCount(countMembers(workspace.getId()));
            vo.setCurrent(workspace.getId().equals(currentWorkspaceId));
            vo.setCreateTime(workspace.getCreateTime());
            list.add(vo);
        }
        return Result.success(list);
    }

    /**
     * 切换工作空间：校验成员资格后更新 sys_user.last_workspace_id，返回新空间上下文
     */
    @PostMapping("/switch")
    public Result<Map<String, Object>> switchWorkspace(HttpServletRequest request,
                                                        @RequestBody SwitchRequest req) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        Long userId = loginUser.getUserId();
        String workspaceId = req == null ? null : req.getWorkspaceId();
        if (StringUtils.isBlank(workspaceId)) {
            throw new BusinessException(400, "workspaceId 不能为空");
        }

        WorkspaceMemberEntity member = workspaceMemberMapper.selectOne(
                new QueryWrapper<WorkspaceMemberEntity>()
                        .eq("workspace_id", workspaceId)
                        .eq("user_id", userId));
        if (member == null) {
            throw new BusinessException(403, "无权访问该工作空间");
        }

        WorkspaceEntity workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null || (workspace.getStatus() != null && workspace.getStatus() != 1)) {
            throw new BusinessException(404, "工作空间不存在或已停用");
        }

        // 更新本地用户最近空间并重签 JWT（workspace 上下文已变更），
        // 否则后续按 JWT 旧 workspaceId 查询会跨空间错乱
        SysUser sysUser = sysUserMapper.selectById(userId);
        if (sysUser != null) {
            sysUser.setLastWorkspaceId(workspaceId);
            sysUserMapper.updateById(sysUser);
        }
        String newToken = authService.reissueToken(userId);

        Map<String, Object> data = new HashMap<>();
        data.put("token", newToken);
        data.put("id", workspace.getId());
        data.put("workspaceCode", workspace.getWorkspaceCode());
        data.put("workspaceName", workspace.getWorkspaceName());
        data.put("workspaceType", null);
        data.put("memberRole", member.getMemberRole());
        return Result.success(data);
    }

    /**
     * 新建工作空间：本地创建（organization_id=1）+ 创建者加为 owner + 重签 JWT 切到新空间。
     * 本地无 gonline 主站，空间创建在智忆本地完成。
     */
    @PostMapping
    public Result<Map<String, Object>> create(HttpServletRequest request,
                                                @RequestBody CreateRequest req) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        Long userId = loginUser.getUserId();
        String workspaceName = req == null ? null : StringUtils.trimToNull(req.getWorkspaceName());
        if (StringUtils.isBlank(workspaceName)) {
            throw new BusinessException(400, "工作空间名称不能为空");
        }
        String workspaceCode = req == null ? null : StringUtils.trimToNull(req.getWorkspaceCode());
        if (StringUtils.isBlank(workspaceCode)) {
            workspaceCode = "ws_" + System.currentTimeMillis();
        }

        WorkspaceEntity existing = workspaceMapper.selectOne(new QueryWrapper<WorkspaceEntity>()
                .eq("organization_id", 1)
                .eq("workspace_code", workspaceCode));
        if (existing != null) {
            throw new BusinessException(409, "工作空间标识已存在，请更换");
        }

        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setOrganizationId(1L);
        workspace.setWorkspaceCode(workspaceCode);
        workspace.setWorkspaceName(workspaceName);
        workspace.setStatus(1);
        workspaceMapper.insert(workspace);

        // @TableId(INPUT) 不回填自增 id，按唯一 (org, code) 回查
        WorkspaceEntity created = workspaceMapper.selectOne(new QueryWrapper<WorkspaceEntity>()
                .eq("organization_id", 1)
                .eq("workspace_code", workspaceCode));
        String workspaceId = created.getId();

        // 创建者加为 owner
        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.setWorkspaceId(workspaceId);
        member.setUserId(userId);
        member.setMemberRole(WorkspaceMemberRole.OWNER);
        workspaceMemberMapper.insert(member);

        // 切到新空间并重签 JWT
        SysUser sysUser = sysUserMapper.selectById(userId);
        if (sysUser != null) {
            sysUser.setLastWorkspaceId(workspaceId);
            sysUserMapper.updateById(sysUser);
        }
        String newToken = authService.reissueToken(userId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", newToken);
        data.put("id", workspaceId);
        data.put("workspaceCode", workspaceCode);
        data.put("workspaceName", workspaceName);
        data.put("workspaceType", null);
        data.put("memberRole", WorkspaceMemberRole.OWNER);
        return Result.success(data);
    }

    /**
     * 删除工作空间(owner 专属,物理级联清理,需输入工作空间名称确认)
     */
    @DeleteMapping("/{workspaceId}")
    public Result<Void> delete(@PathVariable String workspaceId,
                               @RequestBody DeleteWorkspaceRequest body,
                               HttpServletRequest request) {
        LoginUserVO loginUser = LoginContext.requireLoginUser(request);
        workspaceService.deleteWorkspace(workspaceId, loginUser.getUserId(),
                loginUser.getMemberRole(), body == null ? null : body.getConfirmName());
        return Result.success(null);
    }

    /** 统计空间成员数 */
    private Integer countMembers(String workspaceId) {
        Long count = workspaceMemberMapper.selectCount(
                new QueryWrapper<WorkspaceMemberEntity>().eq("workspace_id", workspaceId));
        return count == null ? 0 : count.intValue();
    }

    /** 切换工作空间入参 */
    @Data
    public static class SwitchRequest {
        private String workspaceId;
    }

    /** 新建工作空间入参 */
    @Data
    public static class CreateRequest {
        private String workspaceName;
        private String workspaceCode;
    }

    /** 删除工作空间入参(需输入名称确认) */
    @Data
    public static class DeleteWorkspaceRequest {
        private String confirmName;
    }
}
