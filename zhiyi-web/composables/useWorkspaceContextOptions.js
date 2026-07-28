/**
 * 工作空间 Recall 上下文字段下拉选项（Workspace 级共享，切换空间时刷新）
 */
import { fetchWorkspaceContextOptionsRequest } from '~/services/workspace-context.service'

export function useWorkspaceContextOptions() {
    const { currentWorkspace, workspaceRevision } = useWorkspace()

    const optionsLoading = useState('workspaceContextOptionsLoading', () => false)
    const repositoryOptions = useState('workspaceContextRepositoryOptions', () => [])
    const projectOptions = useState('workspaceContextProjectOptions', () => [])
    const moduleOptions = useState('workspaceContextModuleOptions', () => [])
    const tagOptions = useState('workspaceContextTagOptions', () => [])
    const loadSequence = useState('workspaceContextOptionsLoadSeq', () => 0)
    const watchRegistered = useState('workspaceContextOptionsWatchRegistered', () => false)

    const workspaceId = computed(() => currentWorkspace.value?.workspaceId || null)

    /** 拉取当前工作空间上下文字段选项 */
    async function loadContextOptions() {
        const targetWorkspaceId = workspaceId.value
        if (!targetWorkspaceId) {
            repositoryOptions.value = []
            projectOptions.value = []
            moduleOptions.value = []
            tagOptions.value = []
            return null
        }

        const currentSequence = ++loadSequence.value
        optionsLoading.value = true
        try {
            const optionsData = await fetchWorkspaceContextOptionsRequest(targetWorkspaceId)
            if (currentSequence !== loadSequence.value) {
                return null
            }
            repositoryOptions.value = optionsData?.repositories || []
            projectOptions.value = optionsData?.projects || []
            moduleOptions.value = optionsData?.modules || []
            tagOptions.value = optionsData?.tags || []
            return optionsData
        } finally {
            if (currentSequence === loadSequence.value) {
                optionsLoading.value = false
            }
        }
    }

    if (!watchRegistered.value) {
        watchRegistered.value = true
        watch([workspaceId, workspaceRevision], () => {
            loadContextOptions()
        }, { immediate: true })
    }

    return {
        optionsLoading,
        repositoryOptions,
        projectOptions,
        moduleOptions,
        tagOptions,
        loadContextOptions,
    }
}
