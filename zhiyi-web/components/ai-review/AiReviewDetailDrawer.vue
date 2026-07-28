<template>
    <el-drawer
        :model-value="visible"
        size="520px"
        append-to-body
        destroy-on-close
        class="ai-review-detail-drawer"
        @update:model-value="emit('update:visible', $event)"
    >
        <template #header>
            <div class="ai-review-detail-drawer__header min-w-0 pr-4">
                <div class="flex items-center flex-wrap gap-2 mb-2">
                    <span
                        v-if="recordBadge"
                        class="ai-review-detail-drawer__badge"
                        :class="`ai-review-detail-drawer__badge--${recordBadge.tone}`"
                    >
                        {{ recordBadge.label }}
                    </span>
                    <span class="ai-review-detail-drawer__chip">
                        {{ formatAiReviewStatus(review.status) }}
                    </span>
                    <span v-if="review.executed" class="ai-review-detail-drawer__chip">
                        已自动执行
                    </span>
                    <span
                        v-if="review.similarHit"
                        class="ai-review-detail-drawer__chip ai-review-detail-drawer__chip--warn"
                    >
                        相似命中
                    </span>
                </div>
                <h2 class="ai-review-detail-drawer__title">
                    {{ detail?.draftTitle || 'AI 审查详情' }}
                </h2>
                <p class="ai-review-detail-drawer__meta-line">
                    记录 #{{ review.id || activeRecordId || recordId }}
                    <span v-if="review.draftId"> · 草稿 #{{ review.draftId }}</span>
                    <span v-if="review.createTime"> · {{ formatDateTime(review.createTime) }}</span>
                </p>
            </div>
        </template>

        <div v-if="loading" class="ai-review-detail-drawer__body">
            <el-skeleton :rows="6" />
        </div>
        <div v-else-if="!detail?.review" class="ai-review-detail-drawer__empty">
            记录不存在或已删除
        </div>
        <div v-else class="ai-review-detail-drawer__body space-y-5">
            <!-- 审查摘要 -->
            <section class="ai-review-detail-drawer__section">
                <div class="ai-review-detail-drawer__section-head">
                    <div>
                        <h3 class="ai-review-detail-drawer__section-title">审查摘要</h3>
                        <p class="ai-review-detail-drawer__section-desc">
                            决策、置信度与运行耗时
                        </p>
                    </div>
                    <el-button
                        v-if="showRetryButton"
                        size="small"
                        plain
                        :loading="retrying"
                        :disabled="!canRetryNow"
                        :title="retryDisabledReason"
                        @click="handleRetry"
                    >
                        重跑
                    </el-button>
                </div>
                <div class="ai-review-detail-drawer__stat-grid">
                    <div class="ai-review-detail-drawer__stat">
                        <span class="ai-review-detail-drawer__stat-label">决策</span>
                        <span class="ai-review-detail-drawer__stat-value">{{ decisionLabel }}</span>
                    </div>
                    <div class="ai-review-detail-drawer__stat">
                        <span class="ai-review-detail-drawer__stat-label">置信度</span>
                        <span class="ai-review-detail-drawer__stat-value">
                            {{ review.confidence != null ? `${Math.round((review.confidence || 0) * 100)}%` : '—' }}
                        </span>
                    </div>
                    <div class="ai-review-detail-drawer__stat">
                        <span class="ai-review-detail-drawer__stat-label">耗时</span>
                        <span class="ai-review-detail-drawer__stat-value">
                            {{ review.latencyMs != null ? `${review.latencyMs}ms` : '—' }}
                        </span>
                    </div>
                </div>
                <p v-if="review.traceId" class="mt-3 text-xs text-gray-400 break-all">
                    Trace：{{ review.traceId }}
                </p>
                <p
                    v-if="review.errorMessage"
                    class="mt-3 text-sm text-amber-800 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2"
                >
                    {{ review.errorMessage }}
                </p>
            </section>

            <!-- 推荐动作 -->
            <section
                v-if="review.recommendedAction || review.recommendedRejectReason"
                class="ai-review-detail-drawer__section"
            >
                <h3 class="ai-review-detail-drawer__section-title">推荐动作</h3>
                <p v-if="review.recommendedAction" class="text-sm text-gray-700 mt-2 leading-relaxed">
                    {{ review.recommendedAction }}
                </p>
                <p v-if="review.recommendedRejectReason" class="text-xs text-gray-500 mt-1.5">
                    拒绝码：{{ review.recommendedRejectReason }}
                </p>
            </section>

            <!-- 相似记忆 -->
            <section v-if="review.similarKnowledge?.length" class="ai-review-detail-drawer__section">
                <h3 class="ai-review-detail-drawer__section-title">相似记忆</h3>
                <div class="mt-3 space-y-2">
                    <div
                        v-for="item in review.similarKnowledge"
                        :key="item.knowledgeId"
                        class="ai-review-detail-drawer__similar-item"
                    >
                        <span class="ai-review-detail-drawer__similar-score">
                            {{ Math.round((item.similarityScore || 0) * 100) }}%
                        </span>
                        <span class="ai-review-detail-drawer__similar-type">
                            {{ item.knowledgeType || 'knowledge' }}
                        </span>
                        <span class="text-sm text-gray-800 truncate">
                            #{{ item.knowledgeId }} {{ item.title || '未命名' }}
                        </span>
                    </div>
                </div>
            </section>

            <!-- 检查清单 -->
            <section v-if="review.checklistHints?.length" class="ai-review-detail-drawer__section">
                <h3 class="ai-review-detail-drawer__section-title">检查清单</h3>
                <ul class="mt-3 space-y-2">
                    <li
                        v-for="(hint, index) in review.checklistHints"
                        :key="hint.id || index"
                        class="ai-review-detail-drawer__checklist-item"
                    >
                        <span class="font-medium text-gray-500">{{ hint.id || `#${index + 1}` }}</span>
                        <span class="mx-1.5 text-gray-300">·</span>
                        <span>{{ hint.status || '—' }}</span>
                        <span v-if="hint.evidence" class="text-gray-500">：{{ hint.evidence }}</span>
                    </li>
                </ul>
            </section>

            <!-- 提交人 -->
            <section
                v-if="detail.submitterNickname || detail.submitterId"
                class="ai-review-detail-drawer__section"
            >
                <h3 class="ai-review-detail-drawer__section-title">提交人</h3>
                <p class="text-sm text-gray-700 mt-2">
                    {{ detail.submitterNickname || `用户 #${detail.submitterId}` }}
                </p>
            </section>
        </div>

        <template #footer>
            <div class="flex items-center justify-between gap-2 w-full">
                <p class="text-xs text-gray-400 min-w-0 pr-2">
                    {{ footerHint }}
                </p>
                <div class="flex items-center gap-2 shrink-0">
                    <el-button
                        v-if="canGoCapture"
                        @click="handleGoCapture"
                    >
                        去确认
                    </el-button>
                    <el-button
                        v-if="showRetryButton"
                        type="primary"
                        :loading="retrying"
                        :disabled="!canRetryNow"
                        :title="retryDisabledReason"
                        @click="handleRetry"
                    >
                        重跑
                    </el-button>
                </div>
            </div>
        </template>
    </el-drawer>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import {
    fetchAiReviewById,
    fetchCaptureAiReview,
    retryCaptureAiReview,
} from '~/services/capture.service'
import {
    AI_REVIEW_STATUS,
    formatAiReviewDecision,
    formatAiReviewStatus,
    isDraftStillPending,
    resolveAiReviewRecordBadge,
} from '~/utils/aiReview'
import { formatDateTime } from '~/utils/knowledge'

const props = defineProps({
    visible: {
        type: Boolean,
        default: false,
    },
    /** 当前打开的记录 ID */
    recordId: {
        type: [Number, String],
        default: null,
    },
    /** 是否可重跑（编辑者及以上） */
    canReview: {
        type: Boolean,
        default: true,
    },
})

const emit = defineEmits(['update:visible', 'retried', 'go-capture', 'record-changed'])

const loading = ref(false)
const retrying = ref(false)
const detail = ref(null)
/** 抽屉内实际展示的记录 ID（重跑后会切到最新一条） */
const activeRecordId = ref(null)

/** 详情轮询定时器 */
let detailPollTimer = null

const review = computed(() => detail.value?.review || {})

const decisionLabel = computed(() => formatAiReviewDecision(review.value?.decision))

const recordBadge = computed(() => resolveAiReviewRecordBadge(review.value))

/** 关联草稿是否仍待确认 */
const draftStillPending = computed(() =>
    isDraftStillPending(detail.value?.draftReviewStatus),
)

/** 草稿仍待确认时可跳转 Capture */
const canGoCapture = computed(() =>
    draftStillPending.value && Boolean(review.value?.draftId),
)

/** 有编辑权限且存在草稿时展示重跑按钮（不可用时禁用并提示原因） */
const showRetryButton = computed(() =>
    props.canReview && Boolean(review.value?.draftId),
)

/** 当前审查是否排队/进行中 */
const isReviewRunning = computed(() => {
    const status = review.value?.status
    return status === AI_REVIEW_STATUS.QUEUED || status === AI_REVIEW_STATUS.RUNNING
})

/** 此刻是否允许发起重跑 */
const canRetryNow = computed(() =>
    showRetryButton.value && draftStillPending.value && !isReviewRunning.value,
)

/** 重跑禁用原因（用于 title / 底部提示） */
const retryDisabledReason = computed(() => {
    if (!showRetryButton.value) {
        return ''
    }
    if (isReviewRunning.value) {
        return 'AI 审查进行中，请稍后再试'
    }
    if (!draftStillPending.value) {
        return '仅待确认草稿可重跑'
    }
    return ''
})

/** 底部操作区提示文案 */
const footerHint = computed(() => {
    if (isReviewRunning.value) {
        return 'AI 审查进行中，完成后将自动刷新'
    }
    if (canRetryNow.value) {
        return '可重跑审查或前往草稿确认'
    }
    if (showRetryButton.value && !draftStillPending.value) {
        return '草稿已处理，无法重跑；可在草稿确认页查看待审项'
    }
    if (!props.canReview) {
        return '当前为查看者，仅可浏览审查记录'
    }
    return ''
})

/** 停止详情轮询 */
function stopDetailPoll() {
    if (detailPollTimer != null) {
        clearTimeout(detailPollTimer)
        detailPollTimer = null
    }
}

/**
 * 用完整详情视图刷新本地状态，并在记录 ID 变化时通知父组件
 */
function applyDetail(nextDetail) {
    detail.value = nextDetail
    const nextId = nextDetail?.review?.id
    if (nextId != null && nextId !== activeRecordId.value) {
        activeRecordId.value = nextId
        emit('record-changed', nextId)
    }
}

/**
 * 按记录主键加载详情
 */
async function loadDetailByRecordId(targetRecordId) {
    if (!targetRecordId) {
        detail.value = null
        return
    }
    const nextDetail = await fetchAiReviewById(targetRecordId)
    applyDetail(nextDetail)
}

/**
 * 按草稿加载最新审查，并补齐详情（含 draftReviewStatus / 提交人）
 */
async function loadLatestDetailByDraftId(draftId) {
    if (!draftId) {
        return
    }
    const latestReview = await fetchCaptureAiReview(draftId)
    if (!latestReview?.id) {
        return
    }
    await loadDetailByRecordId(latestReview.id)
}

/**
 * 加载详情（打开抽屉时使用 props.recordId）
 */
async function loadDetail() {
    if (!props.recordId || !props.visible) {
        detail.value = null
        activeRecordId.value = null
        return
    }
    loading.value = true
    try {
        activeRecordId.value = props.recordId
        await loadDetailByRecordId(props.recordId)
    } catch (error) {
        detail.value = null
        ElMessage.error(error.message || '加载详情失败')
    } finally {
        loading.value = false
    }
}

/**
 * 重跑后轮询最新审查，直到离开排队/进行中或达到上限。
 * previousRecordId：重跑前的记录，用于忽略「新记录尚未落库」时仍返回旧终态的响应。
 */
function pollLatestUntilSettled(draftId, attempt = 0, previousRecordId = null) {
    stopDetailPoll()
    detailPollTimer = setTimeout(async () => {
        if (!props.visible || !draftId) {
            return
        }
        try {
            const latestReview = await fetchCaptureAiReview(draftId)
            const latestStatus = latestReview?.status
            const isRunning = latestStatus === AI_REVIEW_STATUS.QUEUED
                || latestStatus === AI_REVIEW_STATUS.RUNNING
            const isStalePrevious = previousRecordId != null
                && latestReview?.id === previousRecordId
                && !isRunning
            // 仅在出现新记录或排队/进行中时刷新，避免旧终态覆盖乐观排队态
            if (latestReview?.id && !isStalePrevious) {
                await loadDetailByRecordId(latestReview.id)
            }
        } catch (error) {
            console.warn('AI 审查详情静默刷新失败', error)
        }
        const status = review.value?.status
        const stillRunning = status === AI_REVIEW_STATUS.QUEUED
            || status === AI_REVIEW_STATUS.RUNNING
        const waitingForNewRecord = previousRecordId != null
            && (review.value?.id == null || review.value?.id === previousRecordId)
            && !stillRunning
        if ((!stillRunning && !waitingForNewRecord) || attempt >= 20) {
            return
        }
        pollLatestUntilSettled(draftId, attempt + 1, previousRecordId)
    }, attempt === 0 ? 800 : 3000)
}

/** 跳转草稿确认页 */
function handleGoCapture() {
    if (!review.value?.draftId) {
        return
    }
    emit('go-capture', review.value.draftId)
}

/**
 * 重跑 AI 审查：触发后切到最新记录并轮询至终态
 */
async function handleRetry() {
    if (!review.value?.draftId || !canRetryNow.value) {
        if (retryDisabledReason.value) {
            ElMessage.warning(retryDisabledReason.value)
        }
        return
    }
    const draftId = review.value.draftId
    const previousRecordId = review.value.id
    retrying.value = true
    try {
        await retryCaptureAiReview(draftId)
        ElMessage.success('已触发 AI 审查重跑')
        emit('retried', { draftId })
        // 先乐观展示排队态，再轮询最新记录
        if (detail.value?.review) {
            detail.value = {
                ...detail.value,
                review: {
                    ...detail.value.review,
                    status: AI_REVIEW_STATUS.QUEUED,
                    decision: null,
                    executed: false,
                    errorMessage: null,
                },
            }
        }
        pollLatestUntilSettled(draftId, 0, previousRecordId)
    } catch (error) {
        ElMessage.error(error.message || '重跑失败')
    } finally {
        retrying.value = false
    }
}

watch(
    () => [props.visible, props.recordId],
    ([visible, recordId]) => {
        if (!visible) {
            stopDetailPoll()
            detail.value = null
            activeRecordId.value = null
            return
        }
        // 抽屉内重跑后会 emit record-changed 回写父组件；避免重复加载打断轮询
        if (
            recordId != null
            && String(recordId) === String(activeRecordId.value)
            && detail.value?.review
        ) {
            return
        }
        stopDetailPoll()
        loadDetail()
    },
    { immediate: true },
)

onBeforeUnmount(() => {
    stopDetailPoll()
})

/** 供父组件在列表轮询后刷新详情 */
defineExpose({
    reload: async () => {
        const draftId = review.value?.draftId
        if (draftId && isReviewRunning.value) {
            await loadLatestDetailByDraftId(draftId)
            return
        }
        const targetId = activeRecordId.value || props.recordId
        if (targetId) {
            await loadDetailByRecordId(targetId)
        }
    },
})
</script>

<style>
.ai-review-detail-drawer .el-drawer__header {
    margin-bottom: 0;
    padding: 20px 24px 16px;
    border-bottom: 1px solid #f3f4f6;
}

.ai-review-detail-drawer .el-drawer__body {
    padding: 0;
}

.ai-review-detail-drawer .el-drawer__footer {
    padding: 14px 24px;
    border-top: 1px solid #f3f4f6;
    background: #fafafa;
}
</style>

<style scoped>
.ai-review-detail-drawer__title {
    font-size: 18px;
    font-weight: 600;
    line-height: 1.45;
    color: #111827;
    letter-spacing: -0.01em;
}

.ai-review-detail-drawer__meta-line {
    margin-top: 6px;
    font-size: 12px;
    color: #9ca3af;
}

.ai-review-detail-drawer__body {
    padding: 20px 24px 24px;
}

.ai-review-detail-drawer__empty {
    padding: 48px 24px;
    text-align: center;
    font-size: 14px;
    color: #6b7280;
}

.ai-review-detail-drawer__section {
    padding: 16px;
    background: #f9fafb;
    border-radius: 12px;
    border: 1px solid #f3f4f6;
}

.ai-review-detail-drawer__section-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 14px;
}

.ai-review-detail-drawer__section-title {
    font-size: 13px;
    font-weight: 600;
    color: #374151;
}

.ai-review-detail-drawer__section-desc {
    margin-top: 2px;
    font-size: 12px;
    color: #9ca3af;
}

.ai-review-detail-drawer__stat-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 10px;
}

.ai-review-detail-drawer__stat {
    padding: 10px 12px;
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 10px;
}

.ai-review-detail-drawer__stat-label {
    display: block;
    font-size: 11px;
    color: #9ca3af;
    margin-bottom: 4px;
}

.ai-review-detail-drawer__stat-value {
    display: block;
    font-size: 13px;
    font-weight: 600;
    color: #111827;
}

.ai-review-detail-drawer__badge {
    display: inline-flex;
    padding: 2px 10px;
    border-radius: 999px;
    font-size: 12px;
    font-weight: 500;
}

.ai-review-detail-drawer__badge--processing {
    color: #1d4ed8;
    background: #dbeafe;
}

.ai-review-detail-drawer__badge--warning {
    color: #b45309;
    background: #fef3c7;
}

.ai-review-detail-drawer__badge--danger {
    color: #b91c1c;
    background: #fee2e2;
}

.ai-review-detail-drawer__badge--success {
    color: #047857;
    background: #d1fae5;
}

.ai-review-detail-drawer__chip {
    display: inline-flex;
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 12px;
    color: #6b7280;
    background: #f3f4f6;
}

.ai-review-detail-drawer__chip--warn {
    color: #b45309;
    background: #fffbeb;
}

.ai-review-detail-drawer__similar-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    background: #fff;
    border-radius: 10px;
    border: 1px solid #e5e7eb;
}

.ai-review-detail-drawer__similar-score {
    font-size: 12px;
    font-weight: 600;
    color: #1d4ed8;
    min-width: 36px;
}

.ai-review-detail-drawer__similar-type {
    padding: 0 6px;
    border-radius: 4px;
    font-size: 11px;
    color: #6b7280;
    background: #f3f4f6;
}

.ai-review-detail-drawer__checklist-item {
    font-size: 13px;
    color: #374151;
    line-height: 1.5;
}
</style>
