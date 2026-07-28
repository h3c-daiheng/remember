package com.zhiyi.workspace;

import com.zhiyi.common.BusinessException;
import com.zhiyi.domain.vo.LoginUserVO;
import org.apache.commons.lang3.StringUtils;

/**
 * 工作空间访问校验：路径上的 workspaceId 须与当前登录激活空间一致。
 * 业务页均按「当前空间」查询；跨空间请先切换再访问。
 */
public final class WorkspaceAccessGuard {

    private WorkspaceAccessGuard() {
    }

    /**
     * 校验用户已登录且请求的工作空间为当前激活空间
     *
     * @param loginUser   当前登录用户（含 workspaceId）
     * @param workspaceId 路径或入参中的工作空间 ID
     */
    public static void requireCurrentWorkspaceMember(LoginUserVO loginUser, String workspaceId) {
        if (StringUtils.isBlank(workspaceId)) {
            throw new BusinessException(400, "请先选择工作空间");
        }
        if (loginUser == null || StringUtils.isBlank(loginUser.getWorkspaceId())) {
            throw new BusinessException(400, "当前未选择工作空间");
        }
        if (!workspaceId.equals(loginUser.getWorkspaceId())) {
            throw new BusinessException(403, "无权访问该工作空间");
        }
    }
}
