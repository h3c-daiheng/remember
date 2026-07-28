/**
 * 监听工作空间切换/创建，供各业务页在后台静默刷新数据，避免整页 skeleton 闪烁
 */
export function useWorkspaceChange(callback) {
    const { workspaceRevision } = useWorkspace()

    watch(workspaceRevision, (revision, previousRevision) => {
        if (previousRevision === undefined) {
            return
        }
        callback(revision)
    })
}
