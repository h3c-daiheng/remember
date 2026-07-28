<template>
    <div class="max-w-layout mx-auto px-6 py-8">
        <div class="mb-6">
            <el-button text @click="router.push('/dashboard')">← 返回工作台</el-button>
        </div>

        <div class="bg-white rounded-xl border border-gray-200 p-6">
            <div class="mb-6">
                <div class="flex items-center gap-2 flex-wrap">
                    <h1 class="text-2xl font-semibold text-gray-900">
                        {{ allStepsDone ? QUICK_START_HEADER.completedTitle : QUICK_START_HEADER.title }}
                    </h1>
                    <el-tag v-if="allStepsDone" size="small" type="success" effect="light">全部完成</el-tag>
                    <el-tag v-else-if="currentStep" size="small" type="primary" effect="light">
                        第 {{ currentStepIndex + 1 }} 步
                    </el-tag>
                </div>
                <p class="text-sm text-gray-500 mt-1.5">
                    {{ allStepsDone ? QUICK_START_HEADER.completedSubtitle : headerSubtitle }}
                </p>
            </div>

            <!-- 进度条 -->
            <div class="mb-6">
                <el-progress
                    :percentage="progressPercent"
                    :stroke-width="6"
                    :show-text="false"
                    :color="allStepsDone ? '#67c23a' : '#5d65f9'"
                />
                <p v-if="!allStepsDone && currentStep" class="text-xs text-gray-400 mt-2">
                    {{ QUICK_START_HEADER.nextStepPrefix }}{{ currentStep.title }}
                </p>
            </div>

            <!-- 步骤列表（说明展开在对应步骤下方） -->
            <HomeQuickStartGuide
                :active-step-id="activeStepId"
                :published-count="publishedCount"
                :pending-draft-count="pendingDraftCount"
                :workspace-code="currentWorkspaceCode"
                @update:active-step-id="onActiveStepChange"
            />
        </div>
    </div>
</template>

<script setup>
import { fetchKnowledgeList } from '~/services/knowledge.service'

definePageMeta({
    layout: 'app',
})

useHead({ title: '快速开始' })

/** 页面浏览埋点 */
usePageTracker()

const router = useRouter()
const route = useRoute()
const { badgeValues } = useAppNavigation()
const { currentWorkspace: currentWorkspaceState } = useWorkspace()

const publishedCount = ref(0)
const statsLoading = ref(false)
const activeStepId = ref('')

/** 当前工作空间编码 */
const currentWorkspaceCode = computed(() => currentWorkspaceState.value?.workspaceCode || 'default')

/** 待确认草稿数量 */
const pendingDraftCount = computed(() => badgeValues.value.pendingDraftCount || 0)

const {
    QUICK_START_HEADER,
    currentStep,
    currentStepIndex,
    progressPercent,
    allStepsDone,
    headerSubtitle,
} = useQuickStart({
    publishedCount: () => publishedCount.value,
    pendingDraftCount: () => pendingDraftCount.value,
})

/** 展开/收起步骤并同步到 URL */
function onActiveStepChange(stepId) {
    activeStepId.value = stepId
    if (stepId) {
        router.replace({ query: { step: stepId } })
        return
    }
    router.replace({ query: {} })
}

/** 加载已发布经验总数（用于步骤完成判定） */
async function loadPublishedCount() {
    statsLoading.value = true
    try {
        const result = await fetchKnowledgeList(1, 1, '')
        publishedCount.value = result.total || 0
    } finally {
        statsLoading.value = false
    }
}

onMounted(() => {
    loadPublishedCount()

    const stepFromQuery = route.query.step
    if (typeof stepFromQuery === 'string' && stepFromQuery) {
        activeStepId.value = stepFromQuery
    }
})
</script>
