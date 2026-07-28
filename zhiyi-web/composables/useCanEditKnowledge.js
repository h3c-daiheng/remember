/**
 * 判断当前用户是否可编辑/发布知识（新建、Review、导入等）
 * 内部委托 useWorkspacePermission，保持旧调用方兼容
 */
export function useCanEditKnowledge() {
    const { canEdit, syncing } = useWorkspacePermission()
    return {
        canEdit,
        syncing,
    }
}
