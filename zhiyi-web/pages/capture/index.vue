<template>
    <div class="capture-page max-w-layout mx-auto px-6 py-8">
        <!-- 页头：对齐经验中心布局 -->
        <div class="capture-page__header mb-8">
            <div class="flex flex-col lg:flex-row lg:items-start lg:justify-between gap-6">
                <div>
                        <h1 class="text-2xl font-semibold text-gray-900 tracking-tight">
                            {{ PAGE_LABELS.draftReview }}
                        </h1>
                        <p class="text-gray-500 mt-1.5 text-sm leading-relaxed">
                            提交后由 AI 审查自动审核；相似记忆命中时转人工确认
                        </p>
                        <p v-if="!loading && filteredDraftList.length > 0" class="text-xs text-gray-400 mt-2">
                            共 {{ filteredDraftList.length }} 条待确认草稿
                            <span v-if="keyword"> · 搜索「{{ keyword }}」</span>
                        </p>
                </div>

                <KnowledgeListToolbar
                    v-model:keyword="keyword"
                    class="shrink-0"
                    :show-create="false"
                    :can-edit="false"
                >
                    <template #actions-end>
                        <el-button @click="router.push('/memory')">
                            <el-icon class="mr-1"><Collection /></el-icon>
                            查看已发布记忆
                        </el-button>
                    </template>
                </KnowledgeListToolbar>
            </div>
            <el-alert
                v-if="!canEdit"
                class="mt-4"
                type="info"
                :closable="false"
                show-icon
                title="当前为查看者，仅可浏览待审草稿"
                description="采纳、拒绝、路由等审核操作需编辑者及以上角色"
            />
        </div>

        <!-- 加载骨架（静态，避免 animated 持续占用主线程） -->
        <div v-if="loading" class="space-y-3">
            <div
                v-for="index in 3"
                :key="index"
                class="bg-white rounded-xl border border-gray-200 p-5"
            >
                <el-skeleton :rows="3" />
            </div>
        </div>

        <!-- 空状态 -->
        <PageEmptyState
            v-else-if="draftList.length === 0"
            :flow-steps="memoryFlywheelSteps"
            title="等待 Agent 提交记忆草稿"
            subtitle="Agent 通过 memory_submit 提交后在此确认；默认按声明类型（经验/规则/流程/决策）审核发布"
            :tags="['经验 (Experience)', '规则 (Rule)', '流程 (Workflow)', '决策 (Decision)']"
            guide="配置 MCP 后，Agent 调用 memory_submit 提交草稿；确认无误后在此发布"
        >
            <el-button type="primary" plain @click="router.push('/dashboard/guide')">
                查看 MCP 接入指南
            </el-button>
        </PageEmptyState>

        <!-- 搜索无结果 -->
        <PageEmptyState
            v-else-if="filteredDraftList.length === 0"
            title="未找到匹配的草稿"
            subtitle="尝试更换关键词，或清除搜索查看全部待确认草稿"
            compact
        >
            <el-button @click="keyword = ''">清除搜索</el-button>
        </PageEmptyState>

        <!-- 草稿列表 -->
        <div v-else class="space-y-3">
            <CaptureDraftListItem
                v-for="draft in filteredDraftList"
                :key="draft.id"
                :draft="draft"
                @open="openDraftDrawer"
            />
        </div>

        <!-- 草稿详情抽屉 -->
        <CaptureDraftDetailDrawer
            :visible="drawerVisible"
            :draft="activeDraft"
            :can-review="canEdit"
            :approving="approvingId === activeDraft?.id"
            :rejecting="rejectingId === activeDraft?.id"
            :routing="routeBusy"
            @update:visible="drawerVisible = $event"
            @approve="handleApprove"
            @reject="openRejectDialog"
            @route="handleRoute"
            @merge-rule="openMergeRuleDialog"
            @ai-review-updated="handleAiReviewUpdated"
        />

        <CaptureMergeRuleDialog
            :visible="mergeDialogVisible"
            :loading="mergingId === activeDraft?.id"
            :similar-rule-list="mergeRuleCandidates"
            @update:visible="mergeDialogVisible = $event"
            @confirm="handleMergeRuleConfirm"
        />

        <CaptureRejectDialog
            :visible="rejectDialogVisible"
            :loading="rejectingId === activeDraft?.id"
            @update:visible="rejectDialogVisible = $event"
            @confirm="handleRejectConfirm"
        />
    </div>
</template>

<script setup>
import { Collection } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CAPTURE_TRACK_EVENTS } from '~/config/tracker'
import { MEMORY_FLYWHEEL_STEPS } from '~/constants/pageEmptyState'
import { PAGE_LABELS } from '~/constants/terminology'
import { KNOWLEDGE_TYPE_LABELS, KNOWLEDGE_TYPES } from '~/constants/knowledge'
import { fetchCaptureDraftDetail, fetchCaptureAiReview } from '~/services/capture.service'
import {
    AI_REVIEW_STATUS,
    CAPTURE_DRAFT_REVIEW_PENDING,
} from '~/utils/aiReview'

definePageMeta({
    layout: 'app',
})

useHead({ title: PAGE_LABELS.draftReview })

/** 页面浏览埋点 */
usePageTracker()

const router = useRouter()
const route = useRoute()
const memoryFlywheelSteps = MEMORY_FLYWHEEL_STEPS
const { reportEvent } = useTracker()
/** Capture Review 需编辑者及以上 */
const { canEdit } = useCanEditKnowledge()
const keyword = ref('')
const drawerVisible = ref(false)
const rejectDialogVisible = ref(false)
const mergeDialogVisible = ref(false)
const activeDraftId = ref(null)
/** 路由确认弹窗是否已打开，防止重复点击叠加多个 MessageBox 遮罩 */
const routeConfirming = ref(false)

/** 无审核权限时拦截写操作，避免查看者绕过 UI */
function assertCanReview() {
    if (!canEdit.value) {
        ElMessage.warning('当前角色无权限审核草稿')
        return false
    }
    return true
}

const {
    loading,
    draftList,
    approvingId,
    rejectingId,
    routingId,
    mergingId,
    loadDraftList,
    approveDraft,
    rejectDraft,
    routeDraft,
    mergeDraftIntoRule,
} = useCaptureReview()
const { syncPendingDraftCountFromList } = useAppNavigation()

/** 当前打开的草稿对象 */
const activeDraft = computed(() =>
    draftList.value.find((item) => item.id === activeDraftId.value) || null,
)

/** 路由操作进行中（含确认弹窗与 API 请求），用于禁用底部按钮 */
const routeBusy = computed(() =>
    routeConfirming.value || routingId.value === activeDraftId.value,
)

/** 按关键词过滤草稿列表（前端本地搜索） */
const filteredDraftList = computed(() => {
    const list = draftList.value
    const searchText = keyword.value.trim().toLowerCase()
    if (!searchText) {
        return list
    }
    return list.filter((draft) => {
        const content = draft.draftContent || {}
        const fields = [
            content.title,
            content.project,
            content.module,
            content.repository,
            ...(content.tags || []),
        ]
        return fields.some((field) => field && String(field).toLowerCase().includes(searchText))
    })
})

/** 打开草稿审核抽屉 */
function openDraftDrawer(draft) {
    reportEvent(CAPTURE_TRACK_EVENTS.VIEW_DETAIL, {
        draftId: draft?.id || '',
    })
    activeDraftId.value = draft.id
    drawerVisible.value = true
}

/**
 * 支持从 AI Review 页跳转：/capture?draftId=xxx 自动打开对应待确认草稿
 */
async function openDraftFromQuery() {
    const rawDraftId = route.query.draftId
    if (rawDraftId === undefined || rawDraftId === null || rawDraftId === '') {
        return
    }
    const draftId = Number(rawDraftId)
    if (!draftId) {
        return
    }
    let draft = draftList.value.find((item) => item.id === draftId)
    if (!draft) {
        try {
            draft = await fetchCaptureDraftDetail(draftId)
        } catch (error) {
            ElMessage.warning(error.message || '未找到对应草稿')
            router.replace({ path: '/capture', query: {} })
            return
        }
        if (draft?.reviewStatus !== CAPTURE_DRAFT_REVIEW_PENDING) {
            ElMessage.info('该草稿已不在待确认队列')
            router.replace({ path: '/capture', query: {} })
            return
        }
        // 详情拉取成功但不在当前列表时补入，保证抽屉能绑定 activeDraft
        draftList.value = [draft, ...draftList.value.filter((item) => item.id !== draft.id)]
    }
    openDraftDrawer(draft)
    router.replace({ path: '/capture', query: {} })
}

/** 采纳草稿并按声明类型直接发布 */
async function handleApprove(draft) {
    if (!assertCanReview()) {
        return
    }
    try {
        reportEvent(CAPTURE_TRACK_EVENTS.APPROVE, {
            draftId: draft?.id || '',
        })
        const result = await approveDraft(draft)
        const submittedType = draft?.draftContent?.submittedKnowledgeType || KNOWLEDGE_TYPES.EXPERIENCE
        const typeLabel = KNOWLEDGE_TYPE_LABELS[submittedType] || submittedType
        if (result?.similarWarnings?.length) {
            const topItem = result.similarWarnings[0]
            ElMessage.warning(
                `已发布${typeLabel}，但存在相似记忆 #${topItem.knowledgeId}「${topItem.title || '未命名'}」`,
            )
        } else {
            ElMessage.success(`已发布${typeLabel}，ID：${result.knowledgeId}`)
        }
        drawerVisible.value = false
        activeDraftId.value = null
        await loadDraftList()
        syncPendingDraftCountFromList(draftList.value)
    } catch (error) {
        ElMessage.error(error.message || '提交失败')
    }
}

/** AI Review 列表轮询定时器（重跑为异步，需持续刷新至终态） */
let aiReviewPollTimer = null

/** 停止 AI Review 列表轮询 */
function stopAiReviewPoll() {
    if (aiReviewPollTimer != null) {
        clearTimeout(aiReviewPollTimer)
        aiReviewPollTimer = null
    }
}

/**
 * 乐观更新当前草稿的 AI Review 字段，立刻刷新列表徽标
 */
function patchActiveDraftAiReview(patch) {
    const draftId = activeDraftId.value
    if (!draftId) {
        return
    }
    const draftIndex = draftList.value.findIndex((item) => item.id === draftId)
    if (draftIndex < 0) {
        return
    }
    draftList.value[draftIndex] = {
        ...draftList.value[draftIndex],
        ...patch,
    }
}

/**
 * AI Review 重跑后：先乐观标记排队，再静默轮询列表直到结束或草稿离开待确认队列
 */
async function handleAiReviewUpdated() {
    patchActiveDraftAiReview({
        aiReviewStatus: AI_REVIEW_STATUS.QUEUED,
        aiReviewDecision: null,
    })
    stopAiReviewPoll()
    await pollAiReviewListUntilSettled(0)
}

/**
 * 轮询刷新待确认列表，直到 AI Review 离开排队/进行中，或草稿已被自动处理
 */
async function pollAiReviewListUntilSettled(attempt) {
    try {
        await loadDraftList({ silent: true })
        syncPendingDraftCountFromList(draftList.value)
    } catch (error) {
        ElMessage.error(error.message || '刷新失败')
        return
    }

    const draftId = activeDraftId.value
    if (!draftId) {
        return
    }

    const draft = draftList.value.find((item) => item.id === draftId)
    // 草稿已不在待确认列表：AI 已自动采纳/拒绝/路由
    if (!draft) {
        if (drawerVisible.value) {
            drawerVisible.value = false
            activeDraftId.value = null
            ElMessage.success('草稿已由 AI 审查自动处理')
        }
        return
    }

    const stillRunning = draft.aiReviewStatus === AI_REVIEW_STATUS.QUEUED
        || draft.aiReviewStatus === AI_REVIEW_STATUS.RUNNING
    if (!stillRunning || attempt >= 20) {
        return
    }

    aiReviewPollTimer = setTimeout(() => {
        pollAiReviewListUntilSettled(attempt + 1)
    }, 3000)
}

/** 打开拒绝弹窗 */
function openRejectDialog() {
    if (!assertCanReview()) {
        return
    }
    rejectDialogVisible.value = true
}

/** 合并 Rule 候选：从当前草稿 AI Review 相似列表中筛选 Rule */
const mergeRuleCandidates = ref([])

/** 打开合并到 Rule 弹窗 */
async function openMergeRuleDialog() {
    if (!assertCanReview() || !activeDraft.value) {
        return
    }
    try {
        const aiReview = await fetchCaptureAiReview(activeDraft.value.id)
        mergeRuleCandidates.value = (aiReview?.similarKnowledge || [])
            .filter((item) => item?.knowledgeType === KNOWLEDGE_TYPES.RULE)
        if (!mergeRuleCandidates.value.length) {
            ElMessage.warning('未找到可合并的相似 Rule')
            return
        }
        mergeDialogVisible.value = true
    } catch (error) {
        ElMessage.error(error.message || '加载相似 Rule 失败')
    }
}

/** 确认合并到 Rule */
async function handleMergeRuleConfirm(mergeRequest) {
    if (!assertCanReview() || !activeDraft.value) {
        return
    }
    try {
        const result = await mergeDraftIntoRule(activeDraft.value, mergeRequest)
        ElMessage.success(`已合并到 Rule #${result.knowledgeId}`)
        mergeDialogVisible.value = false
        drawerVisible.value = false
        activeDraftId.value = null
        await loadDraftList()
        syncPendingDraftCountFromList(draftList.value)
    } catch (error) {
        ElMessage.error(error.message || '合并失败')
    }
}

/** 确认拒绝草稿 */
async function handleRejectConfirm(rejectRequest) {
    if (!assertCanReview() || !activeDraftId.value) {
        return
    }
    try {
        reportEvent(CAPTURE_TRACK_EVENTS.REJECT, {
            draftId: activeDraftId.value || '',
            rejectReason: rejectRequest?.rejectReason || '',
        })
        await rejectDraft(activeDraftId.value, rejectRequest)
        ElMessage.success('已拒绝')
        rejectDialogVisible.value = false
        drawerVisible.value = false
        activeDraftId.value = null
        await loadDraftList()
        syncPendingDraftCountFromList(draftList.value)
    } catch (error) {
        ElMessage.error(error.message || '操作失败')
    }
}

/** 路由草稿为 Rule / Workflow / Decision 并直接发布 */
async function handleRoute({ targetType }) {
    if (!assertCanReview()) {
        return
    }
    const draft = activeDraft.value
    if (!draft) {
        return
    }
    // 确认弹窗或请求进行中时忽略重复点击，避免 ElMessageBox 遮罩叠加
    if (routeConfirming.value || routingId.value === draft.id) {
        return
    }
    const typeLabels = {
        rule: '规则 (Rule)',
        workflow: '流程 (Workflow)',
        decision: '决策 (Decision)',
    }
    const centerLabels = {
        rule: '记忆中心',
        workflow: '记忆中心',
        decision: '记忆中心',
    }
    const typeLabel = typeLabels[targetType] || targetType
    const centerLabel = centerLabels[targetType] || '对应中心'
    routeConfirming.value = true
    drawerVisible.value = false
    try {
        await ElMessageBox.confirm(
            `确定将该草稿发布为${typeLabel}吗？发布后将写入${centerLabel}并立即可被 Recall 检索。`,
            `发布为${typeLabel}`,
            { type: 'info' },
        )
    } catch {
        drawerVisible.value = true
        return
    } finally {
        routeConfirming.value = false
    }
    const rejectReasonMap = {
        rule: 'REJECT_DOC_GAP',
        workflow: 'REJECT_NOT_EXPERIENCE',
        decision: 'REJECT_NOT_EXPERIENCE',
    }
    try {
        await routeDraft(
            draft,
            targetType,
            rejectReasonMap[targetType] || 'REJECT_NOT_EXPERIENCE',
            '',
        )
        ElMessage.success(`已发布为${typeLabel}`)
        activeDraftId.value = null
        await loadDraftList()
        syncPendingDraftCountFromList(draftList.value)
    } catch (error) {
        ElMessage.error(error.message || '路由失败')
    }
}

onMounted(async () => {
    try {
        await loadDraftList()
        syncPendingDraftCountFromList(draftList.value)
        await openDraftFromQuery()
    } catch (error) {
        ElMessage.error(error.message || '加载失败')
    }
})

/** 切换工作空间后静默刷新草稿列表 */
useWorkspaceChange(async () => {
    stopAiReviewPoll()
    drawerVisible.value = false
    rejectDialogVisible.value = false
    routeConfirming.value = false
    activeDraftId.value = null
    try {
        await loadDraftList({ silent: true })
    } catch (error) {
        ElMessage.error(error.message || '加载失败')
    }
})

onBeforeUnmount(() => {
    stopAiReviewPoll()
})
</script>

