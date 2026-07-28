/**
 * 单条知识的修改 / 删除权限（详情页、草稿编辑页共用）
 * - 修改：编辑角色可改任意；创建者可改本人
 * - 删除：管理者可删任意；其余角色仅可删本人
 */
import { canDeleteKnowledge, canModifyKnowledge } from '~/constants/workspace'

/**
 * @param {import('vue').Ref|import('vue').ComputedRef} detailRef 含 creatorId 的知识详情
 */
export function useKnowledgeActionPermission(detailRef) {
    const { currentWorkspace } = useWorkspace()
    const { currentUser } = useAuth()
    // 确保 memberRole 就绪（详情页可能早于列表进入）
    useWorkspacePermission()

    const canModify = computed(() =>
        canModifyKnowledge(
            currentWorkspace.value?.memberRole,
            detailRef.value?.creatorId,
            currentUser.value?.userId,
        ),
    )

    const canDelete = computed(() =>
        canDeleteKnowledge(
            currentWorkspace.value?.memberRole,
            detailRef.value?.creatorId,
            currentUser.value?.userId,
        ),
    )

    return {
        canModify,
        canDelete,
    }
}
