<template>
    <!-- 草稿审核抽屉：编辑 Fact、查看产物并确认发布或拒绝 -->
    <el-drawer
        :model-value="visible"
        direction="rtl"
        size="680px"
        destroy-on-close
        class="capture-draft-drawer"
        :show-close="true"
        @update:model-value="handleVisibleChange"
    >
        <template #header>
            <div v-if="draft" class="capture-draft-drawer__header">
                <div class="flex items-center flex-wrap gap-2 mb-2.5">
                    <span class="capture-draft-drawer__status">
                        <el-icon :size="12"><Clock /></el-icon>
                        待确认
                    </span>
                    <span
                        v-if="aiReviewBadge"
                        class="capture-draft-drawer__ai-badge"
                        :class="`capture-draft-drawer__ai-badge--${aiReviewBadge.tone}`"
                    >
                        {{ aiReviewBadge.label }}
                    </span>
                    <span class="capture-draft-drawer__meta-id">
                        草稿 #{{ draft.id }}
                    </span>
                    <span class="text-gray-300">·</span>
                    <span class="capture-draft-drawer__meta-id">
                        事件 #{{ draft.eventId }}
                    </span>
                    <span v-if="draft.createTime" class="text-gray-300">·</span>
                    <span v-if="draft.createTime" class="capture-draft-drawer__meta-time">
                        <el-icon :size="12"><Calendar /></el-icon>
                        {{ formatDateTime(draft.createTime) }}
                    </span>
                    <span class="text-gray-300">·</span>
                    <MemoryTraceLink
                        :draft-id="draft.id"
                        link-style
                        :show-icon="false"
                    />
                </div>
                <h2 class="capture-draft-drawer__title">
                    {{ drawerTitle }}
                </h2>
                <KnowledgeSubmitter
                    :knowledge="submitterKnowledge"
                    size="default"
                />
            </div>
        </template>

        <template v-if="draft">
            <div class="capture-draft-drawer__body">
                <!-- 相似记忆命中：提示人工合并或拒绝 -->
                <el-alert
                    v-if="showSimilarAlert"
                    class="mb-4"
                    type="warning"
                    :closable="false"
                    show-icon
                    title="存在相似记忆，请合并或拒绝"
                    :description="similarAlertDescription"
                />
                <div
                    v-if="showSimilarAlert && canReview && similarRuleList.length"
                    class="mb-4 -mt-2"
                >
                    <el-button
                        size="small"
                        type="warning"
                        plain
                        :disabled="submitBusy || rejecting"
                        @click="emit('merge-rule')"
                    >
                        合并到已有 Rule
                    </el-button>
                </div>

                <!-- Agent 提交类型 -->
                <section
                    v-if="submittedKnowledgeTypeLabel"
                    class="capture-draft-drawer__section capture-draft-drawer__section--type"
                >
                    <span class="capture-draft-drawer__type-badge">
                        Agent 提交类型：{{ submittedKnowledgeTypeLabel }}
                    </span>
                </section>

                <!-- AI Review 记录 -->
                <section class="capture-draft-drawer__section">
                    <div class="capture-draft-drawer__section-head">
                        <div class="flex items-center gap-2.5">
                            <div class="capture-draft-drawer__section-icon">
                                <el-icon :size="16"><Cpu /></el-icon>
                            </div>
                            <div>
                                <h3 class="capture-draft-drawer__section-title">AI 审查</h3>
                                <p class="capture-draft-drawer__section-desc">
                                    提交后自动审查记录；相似记忆命中时转人工处理
                                </p>
                            </div>
                        </div>
                        <el-button
                            v-if="canReview"
                            size="small"
                            plain
                            :loading="retryingAiReview"
                            :disabled="aiReviewLoading || isAiReviewRunning"
                            @click="handleRetryAiReview"
                        >
                            重跑
                        </el-button>
                    </div>

                    <div v-if="aiReviewLoading" class="capture-draft-drawer__empty">
                        <p class="text-sm text-gray-500">正在加载 AI 审查…</p>
                    </div>
                    <div v-else-if="!aiReview" class="capture-draft-drawer__empty">
                        <p class="text-sm text-gray-500">暂无 AI 审查记录</p>
                        <p class="text-xs text-gray-400 mt-1">提交后将自动触发；也可点击重跑</p>
                    </div>
                    <div v-else class="capture-draft-drawer__ai-review">
                        <div class="capture-draft-drawer__ai-meta">
                            <span>决策：{{ decisionLabel }}</span>
                            <span v-if="aiReview.confidence != null">
                                置信度：{{ Math.round((aiReview.confidence || 0) * 100) }}%
                            </span>
                            <span v-if="aiReview.latencyMs != null">
                                耗时：{{ aiReview.latencyMs }}ms
                            </span>
                            <span v-if="aiReview.executed">已自动执行</span>
                        </div>
                        <p
                            v-if="aiReview.errorMessage"
                            class="capture-draft-drawer__ai-reason"
                        >
                            {{ aiReview.errorMessage }}
                        </p>
                        <div
                            v-if="aiReview.similarKnowledge?.length"
                            class="capture-draft-drawer__similar-list"
                        >
                            <p class="capture-draft-drawer__similar-title">相似记忆</p>
                            <div
                                v-for="item in aiReview.similarKnowledge"
                                :key="item.knowledgeId"
                                class="capture-draft-drawer__similar-item"
                            >
                                <span class="capture-draft-drawer__similar-score">
                                    {{ Math.round((item.similarityScore || 0) * 100) }}%
                                </span>
                                <span class="capture-draft-drawer__similar-type">
                                    {{ item.knowledgeType || 'knowledge' }}
                                </span>
                                <span class="capture-draft-drawer__similar-name">
                                    #{{ item.knowledgeId }} {{ item.title || '未命名' }}
                                </span>
                            </div>
                        </div>
                    </div>
                </section>

                <!-- 元信息与标签 -->
                <section v-if="hasContextMeta" class="capture-draft-drawer__section">
                    <KnowledgeMeta
                        :knowledge="draft.draftContent"
                        variant="pills"
                    />
                    <div
                        v-if="draft.draftContent?.tags?.length"
                        class="flex flex-wrap gap-1.5 mt-3"
                    >
                        <span
                            v-for="tag in draft.draftContent.tags"
                            :key="tag"
                            class="capture-draft-drawer__tag"
                        >
                            {{ tag }}
                        </span>
                    </div>
                </section>

                <!-- 经验内容编辑区；查看者只读 -->
                <section
                    class="capture-draft-drawer__section"
                    :class="{ 'capture-draft-drawer__section--readonly': !canReview }"
                >
                    <div class="capture-draft-drawer__section-head">
                        <div class="flex items-center gap-2.5">
                            <div class="capture-draft-drawer__section-icon">
                                <el-icon :size="16"><EditPen /></el-icon>
                            </div>
                            <div>
                                <h3 class="capture-draft-drawer__section-title">经验内容</h3>
                                <p class="capture-draft-drawer__section-desc">
                                    {{ canReview ? '支持 Markdown，提交前可编辑调整' : '当前角色仅可浏览草稿内容' }}
                                </p>
                            </div>
                        </div>
                        <span
                            v-if="factCount > 0"
                            class="capture-draft-drawer__section-count"
                        >
                            {{ factCount }} 项
                        </span>
                    </div>

                    <KnowledgeFactBlockEditor
                        v-if="draft.draftContent?.facts?.length"
                        :facts="draft.draftContent.facts"
                    />
                    <div v-else class="capture-draft-drawer__empty">
                        <el-icon :size="28" class="text-gray-300 mb-2"><DocumentDelete /></el-icon>
                        <p class="text-sm text-gray-500">暂无 Fact Block 内容</p>
                        <p class="text-xs text-gray-400 mt-1">请拒绝后让 Agent 重新提交</p>
                    </div>
                </section>

                <!-- 关联产物 -->
                <section v-if="visibleArtifacts.length" class="capture-draft-drawer__section">
                    <div class="capture-draft-drawer__section-head">
                        <div class="flex items-center gap-2.5">
                            <div class="capture-draft-drawer__section-icon capture-draft-drawer__section-icon--artifact">
                                <el-icon :size="16"><Link /></el-icon>
                            </div>
                            <div>
                                <h3 class="capture-draft-drawer__section-title">关联产物</h3>
                                <p class="capture-draft-drawer__section-desc">
                                    Agent 提交时的代码、对话等来源引用
                                </p>
                            </div>
                        </div>
                        <span class="capture-draft-drawer__section-count">
                            {{ visibleArtifacts.length }} 项
                        </span>
                    </div>

                    <div class="capture-draft-drawer__artifact-list">
                        <div
                            v-for="(artifact, index) in visibleArtifacts"
                            :key="index"
                            class="capture-draft-drawer__artifact"
                        >
                            <el-tag
                                size="small"
                                type="warning"
                                effect="light"
                                class="shrink-0"
                            >
                                {{ ARTIFACT_ROLE_LABELS[artifact.artifactRole] || artifact.artifactRole || '来源' }}
                            </el-tag>
                            <span class="capture-draft-drawer__artifact-type">
                                {{ artifact.artifactType || 'code' }}
                            </span>
                            <span class="capture-draft-drawer__artifact-ref">
                                {{ artifact.contentRef || artifact.artifactUrl }}
                            </span>
                        </div>
                    </div>
                </section>
            </div>
        </template>

        <template #footer>
            <div class="capture-draft-drawer__footer">
                <!-- 查看者只读：可看闭环追踪，不可 Review -->
                <template v-if="!canReview">
                    <p class="capture-draft-drawer__footer-hint">
                        当前为查看者，仅可浏览草稿；审核与发布需编辑者及以上角色
                    </p>
                    <div class="capture-draft-drawer__footer-actions">
                        <MemoryTraceLink
                            v-if="draft?.id"
                            :draft-id="draft.id"
                            link-style
                        />
                    </div>
                </template>
                <template v-else>
                    <div class="capture-draft-drawer__footer-actions">
                        <MemoryTraceLink
                            v-if="draft?.id"
                            :draft-id="draft.id"
                            link-style
                        />
                        <el-button
                            :loading="rejecting"
                            :disabled="submitBusy"
                            @click="emit('reject')"
                        >
                            拒绝
                        </el-button>
                        <!-- Split Button：主按钮按 Agent 声明类型提交，下拉可选其他目标类型 -->
                        <el-dropdown
                            split-button
                            type="primary"
                            trigger="click"
                            :loading="submitBusy"
                            :disabled="rejecting"
                            @click="handleApproveClick"
                            @command="handleSubmitCommand"
                        >
                            {{ confirmButtonLabel }}
                            <template #dropdown>
                                <el-dropdown-menu class="capture-draft-drawer__submit-menu">
                                    <el-dropdown-item
                                        v-for="option in submitTargetOptions"
                                        :key="option.targetType"
                                        :command="option.targetType"
                                        :disabled="submitBusy"
                                    >
                                        <div class="capture-draft-drawer__submit-option">
                                            <div class="capture-draft-drawer__submit-option-head">
                                                <span class="capture-draft-drawer__submit-option-label">
                                                    {{ option.label }}
                                                </span>
                                                <span
                                                    v-if="option.isRecommended"
                                                    class="capture-draft-drawer__submit-option-badge"
                                                >
                                                    Agent 推荐
                                                </span>
                                            </div>
                                            <p class="capture-draft-drawer__submit-option-desc">
                                                {{ option.description }}
                                            </p>
                                        </div>
                                    </el-dropdown-item>
                                </el-dropdown-menu>
                            </template>
                        </el-dropdown>
                    </div>
                </template>
            </div>
        </template>
    </el-drawer>
</template>

<script setup>
import { Calendar, Clock, Cpu, DocumentDelete, EditPen, Link } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
    ARTIFACT_ROLE_LABELS,
    KNOWLEDGE_TYPE_LABELS,
    KNOWLEDGE_TYPES,
} from '~/constants/knowledge'
import {
    fetchCaptureAiReview,
    retryCaptureAiReview,
} from '~/services/capture.service'
import {
    AI_REVIEW_STATUS,
    formatAiReviewDecision,
    resolveAiReviewBadge,
} from '~/utils/aiReview'
import { formatDateTime, buildCaptureDraftSubmitterKnowledge } from '~/utils/knowledge'

const props = defineProps({
    /** 抽屉是否可见 */
    visible: {
        type: Boolean,
        default: false,
    },
    /** 当前审核的草稿 */
    draft: {
        type: Object,
        default: null,
    },
    approving: {
        type: Boolean,
        default: false,
    },
    rejecting: {
        type: Boolean,
        default: false,
    },
    routing: {
        type: Boolean,
        default: false,
    },
    /** 是否可 Review（owner/admin/editor）；查看者只读 */
    canReview: {
        type: Boolean,
        default: true,
    },
})

const emit = defineEmits(['update:visible', 'approve', 'reject', 'route', 'merge-rule', 'ai-review-updated'])

/** AI Review 详情 */
const aiReview = ref(null)
const aiReviewLoading = ref(false)
const retryingAiReview = ref(false)

/** 各知识类型在下拉中的补充说明，帮助审核员选择改路由目标 */
const SUBMIT_TARGET_DESCRIPTIONS = {
    [KNOWLEDGE_TYPES.EXPERIENCE]: '可复用的做事方法与验证结论',
    [KNOWLEDGE_TYPES.RULE]: '编码规范、检查清单等长期约定',
    [KNOWLEDGE_TYPES.WORKFLOW]: '流程步骤与操作指引',
    [KNOWLEDGE_TYPES.DECISION]: '关键取舍与决策记录',
}

/** 提交或改路由进行中，用于互斥 footer 操作 */
const submitBusy = computed(() => props.approving || props.routing)

/** 下拉展示顺序：默认经验在前，其余按规范层到决策层排列 */
const SUBMIT_TARGET_ORDER = [
    KNOWLEDGE_TYPES.EXPERIENCE,
    KNOWLEDGE_TYPES.RULE,
    KNOWLEDGE_TYPES.WORKFLOW,
    KNOWLEDGE_TYPES.DECISION,
]

/** Agent 提交时声明的知识类型，缺省为 experience */
const submittedKnowledgeType = computed(() =>
    props.draft?.draftContent?.submittedKnowledgeType || KNOWLEDGE_TYPES.EXPERIENCE,
)

/** Agent 提交类型中文标签 */
const submittedKnowledgeTypeLabel = computed(() =>
    KNOWLEDGE_TYPE_LABELS[submittedKnowledgeType.value] || '',
)

/** 下拉可选的发布目标：四类知识类型均列出，Agent 声明项标记推荐 */
const submitTargetOptions = computed(() =>
    SUBMIT_TARGET_ORDER.map((targetType) => ({
        targetType,
        label: KNOWLEDGE_TYPE_LABELS[targetType] || targetType,
        description: SUBMIT_TARGET_DESCRIPTIONS[targetType] || '',
        isRecommended: targetType === submittedKnowledgeType.value,
    })),
)

/** 主按钮点击：按 Agent 声明类型直接提交 */
function handleApproveClick() {
    if (submitBusy.value || props.rejecting || !props.draft) {
        return
    }
    emit('approve', props.draft)
}

/**
 * 下拉选择发布目标：与 Agent 声明一致则走采纳，否则通知父组件改路由
 */
function handleSubmitCommand(targetType) {
    if (submitBusy.value || props.rejecting || !props.draft) {
        return
    }
    if (targetType === submittedKnowledgeType.value) {
        emit('approve', props.draft)
        return
    }
    emit('route', { targetType })
}

/** Split Button 主区域文案：明确默认发布的目标类型 */
const confirmButtonLabel = computed(() => {
    const typeLabel = submittedKnowledgeTypeLabel.value || '经验'
    return `发布为${typeLabel}`
})

/** 抽屉标题：优先展示草稿标题 */
const drawerTitle = computed(() => props.draft?.draftContent?.title || '未命名草稿')

/** 提交人展示对象，复用 KnowledgeSubmitter 组件 */
const submitterKnowledge = computed(() => buildCaptureDraftSubmitterKnowledge(props.draft))

/** AI Review 状态徽标 */
const aiReviewBadge = computed(() => resolveAiReviewBadge(props.draft))

/** AI Review 是否进行中 */
const isAiReviewRunning = computed(() => {
    const status = props.draft?.aiReviewStatus
    return status === AI_REVIEW_STATUS.QUEUED || status === AI_REVIEW_STATUS.RUNNING
})

/** 决策中文 */
const decisionLabel = computed(() => formatAiReviewDecision(aiReview.value?.decision))

/** 是否展示相似记忆告警 */
const showSimilarAlert = computed(() => Boolean(aiReview.value?.similarHit))

/** 相似记忆告警描述 */
const similarAlertDescription = computed(() => {
    const list = aiReview.value?.similarKnowledge || []
    if (!list.length) {
        return 'AI 审查命中相似记忆门禁，请核对后合并到已有知识或拒绝本草稿。'
    }
    const topItem = list[0]
    const scoreText = Math.round((topItem.similarityScore || 0) * 100)
    return `最高相似：#${topItem.knowledgeId}「${topItem.title || '未命名'}」${scoreText}%，请合并或拒绝。`
})

/** 相似 Rule 列表，供合并弹窗选择 */
const similarRuleList = computed(() => {
    const list = aiReview.value?.similarKnowledge || []
    return list.filter((item) => item?.knowledgeType === KNOWLEDGE_TYPES.RULE)
})

/** Fact Block 数量 */
const factCount = computed(() => props.draft?.draftContent?.facts?.length || 0)

/**
 * 加载当前草稿的 AI Review 记录
 */
async function loadAiReview() {
    if (!props.draft?.id || !props.visible) {
        aiReview.value = null
        return
    }
    aiReviewLoading.value = true
    try {
        aiReview.value = await fetchCaptureAiReview(props.draft.id)
    } catch (error) {
        aiReview.value = null
        console.warn('加载 AI Review 失败', error)
    } finally {
        aiReviewLoading.value = false
    }
}

/** 抽屉内 AI Review 详情轮询定时器 */
let aiReviewDetailPollTimer = null

/** 停止抽屉内 AI Review 详情轮询 */
function stopAiReviewDetailPoll() {
    if (aiReviewDetailPollTimer != null) {
        clearTimeout(aiReviewDetailPollTimer)
        aiReviewDetailPollTimer = null
    }
}

/**
 * 轮询加载 AI Review 详情，直到草稿离开排队/进行中或抽屉关闭
 */
function pollAiReviewDetailUntilSettled(attempt = 0) {
    stopAiReviewDetailPoll()
    aiReviewDetailPollTimer = setTimeout(async () => {
        if (!props.visible || !props.draft?.id) {
            return
        }
        await loadAiReview()
        const status = props.draft?.aiReviewStatus
        const stillRunning = status === AI_REVIEW_STATUS.QUEUED
            || status === AI_REVIEW_STATUS.RUNNING
        if (!stillRunning || attempt >= 20) {
            return
        }
        pollAiReviewDetailUntilSettled(attempt + 1)
    }, attempt === 0 ? 800 : 3000)
}

/**
 * 重跑 AI Review
 */
async function handleRetryAiReview() {
    if (!props.draft?.id || !props.canReview) {
        return
    }
    retryingAiReview.value = true
    try {
        await retryCaptureAiReview(props.draft.id)
        ElMessage.success('已触发 AI 审查重跑')
        // 通知父页刷新列表徽标，并轮询详情至终态
        emit('ai-review-updated')
        pollAiReviewDetailUntilSettled(0)
    } catch (error) {
        ElMessage.error(error.message || '重跑失败')
    } finally {
        retryingAiReview.value = false
    }
}

watch(
    () => [props.visible, props.draft?.id],
    ([visible]) => {
        stopAiReviewDetailPoll()
        if (visible) {
            loadAiReview()
        } else {
            aiReview.value = null
        }
    },
    { immediate: true },
)

/** 列表侧 AI Review 状态变化时同步刷新抽屉详情 */
watch(
    () => [props.draft?.aiReviewStatus, props.draft?.aiReviewDecision],
    () => {
        if (props.visible && props.draft?.id) {
            loadAiReview()
        }
    },
)

onBeforeUnmount(() => {
    stopAiReviewDetailPoll()
})

/** 是否存在项目/模块/仓库等上下文元信息 */
const hasContextMeta = computed(() => {
    const content = props.draft?.draftContent
    return Boolean(
        content?.project
        || content?.module
        || content?.repository
        || content?.language
        || content?.framework
        || content?.tags?.length,
    )
})

/** 过滤无实质内容的 Artifact */
const visibleArtifacts = computed(() => {
    const artifacts = props.draft?.draftContent?.artifacts
    if (!Array.isArray(artifacts)) {
        return []
    }
    return artifacts.filter((artifact) => {
        const contentRef = artifact?.contentRef || artifact?.artifactUrl || artifact?.artifactType
        return Boolean(contentRef && String(contentRef).trim())
    })
})

/** 同步抽屉开关状态到父组件 */
function handleVisibleChange(value) {
    emit('update:visible', value)
}
</script>

<style>
.capture-draft-drawer .el-drawer__header {
    margin-bottom: 0;
    padding: 20px 24px 16px;
    border-bottom: 1px solid #f3f4f6;
}

.capture-draft-drawer .el-drawer__body {
    padding: 0;
}

.capture-draft-drawer .el-drawer__footer {
    padding: 14px 24px;
    border-top: 1px solid #f3f4f6;
    background: #fafafa;
}
</style>

<style scoped>
.capture-draft-drawer__header {
    padding-right: 24px;
}

.capture-draft-drawer__status {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 2px 10px;
    border-radius: 9999px;
    font-size: 12px;
    font-weight: 500;
    color: #d97706;
    background: #fffbeb;
}

.capture-draft-drawer__ai-badge {
    display: inline-flex;
    align-items: center;
    padding: 2px 10px;
    border-radius: 9999px;
    font-size: 12px;
    font-weight: 500;
}

.capture-draft-drawer__ai-badge--processing {
    color: #1d4ed8;
    background: #dbeafe;
}

.capture-draft-drawer__ai-badge--warning {
    color: #b45309;
    background: #fef3c7;
}

.capture-draft-drawer__ai-badge--danger {
    color: #b91c1c;
    background: #fee2e2;
}

.capture-draft-drawer__ai-badge--success {
    color: #047857;
    background: #d1fae5;
}

.capture-draft-drawer__ai-review {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.capture-draft-drawer__ai-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    font-size: 12px;
    color: #6b7280;
}

.capture-draft-drawer__ai-reason {
    margin: 0;
    font-size: 13px;
    line-height: 1.5;
    color: #92400e;
}

.capture-draft-drawer__similar-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 12px;
    border-radius: 10px;
    background: #fffbeb;
    border: 1px solid #fde68a;
}

.capture-draft-drawer__similar-title {
    margin: 0;
    font-size: 12px;
    font-weight: 600;
    color: #92400e;
}

.capture-draft-drawer__similar-item {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    font-size: 12px;
    color: #374151;
}

.capture-draft-drawer__similar-score {
    font-weight: 600;
    color: #b45309;
}

.capture-draft-drawer__similar-type {
    padding: 0 6px;
    border-radius: 4px;
    background: #fff;
    color: #6b7280;
}

.capture-draft-drawer__similar-name {
    flex: 1;
    min-width: 0;
    word-break: break-all;
}

.capture-draft-drawer__meta-id,
.capture-draft-drawer__meta-time {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: #9ca3af;
}

.capture-draft-drawer__title {
    font-size: 18px;
    font-weight: 600;
    line-height: 1.45;
    color: #111827;
    letter-spacing: -0.01em;
}

.capture-draft-drawer__body {
    padding: 20px 24px 24px;
}

.capture-draft-drawer__section--type {
    margin-bottom: 12px;
}

.capture-draft-drawer__type-badge {
    display: inline-flex;
    align-items: center;
    padding: 4px 12px;
    border-radius: 9999px;
    font-size: 12px;
    font-weight: 500;
    color: #4338ca;
    background: #eef2ff;
}

.capture-draft-drawer__section + .capture-draft-drawer__section {
    margin-top: 24px;
    padding-top: 24px;
    border-top: 1px solid #f3f4f6;
}

.capture-draft-drawer__section--readonly {
    opacity: 0.85;
    pointer-events: none;
}

.capture-draft-drawer__section-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 14px;
}

.capture-draft-drawer__section-head--toggle {
    width: 100%;
    margin-bottom: 0;
    padding: 0;
    border: none;
    background: transparent;
    cursor: pointer;
}

.capture-draft-drawer__section-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border-radius: 8px;
    background: linear-gradient(135deg, #eef2ff 0%, #e0e7ff 100%);
    color: #6366f1;
    flex-shrink: 0;
}

.capture-draft-drawer__section-icon--artifact {
    background: linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);
    color: #d97706;
}

.capture-draft-drawer__section-title {
    font-size: 14px;
    font-weight: 600;
    color: #111827;
    line-height: 1.3;
}

.capture-draft-drawer__section-desc {
    margin-top: 2px;
    font-size: 12px;
    color: #9ca3af;
    line-height: 1.4;
}

.capture-draft-drawer__section-count {
    flex-shrink: 0;
    padding: 2px 8px;
    border-radius: 6px;
    font-size: 12px;
    color: #6b7280;
    background: #f3f4f6;
}

.capture-draft-drawer__tag {
    display: inline-block;
    padding: 2px 10px;
    border-radius: 9999px;
    font-size: 12px;
    line-height: 20px;
    color: #6b7280;
    background: #f3f4f6;
}

.capture-draft-drawer__empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 32px 16px;
    border: 1px dashed #e5e7eb;
    border-radius: 12px;
    background: #fafafa;
    text-align: center;
}

.capture-draft-drawer__artifact-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.capture-draft-drawer__artifact {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    padding: 10px 14px;
    border-radius: 10px;
    background: #f9fafb;
    border: 1px solid #f3f4f6;
    font-size: 13px;
}

.capture-draft-drawer__artifact-type {
    color: #6b7280;
    font-weight: 500;
}

.capture-draft-drawer__artifact-ref {
    flex: 1;
    min-width: 0;
    color: #374151;
    word-break: break-all;
}

.capture-draft-drawer__footer {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
    width: 100%;
}

.capture-draft-drawer__footer-hint {
    margin: 0;
    font-size: 12px;
    color: #9ca3af;
    line-height: 1.5;
}

.capture-draft-drawer__footer-actions {
    display: flex;
    flex-wrap: nowrap;
    align-items: center;
    justify-content: flex-end;
    gap: 8px;
    flex-shrink: 0;
    overflow-x: auto;
}

.capture-draft-drawer__footer-actions .el-dropdown {
    flex-shrink: 0;
}

.capture-draft-drawer__submit-menu {
    min-width: 240px;
}

.capture-draft-drawer__submit-option {
    padding: 2px 0;
    line-height: 1.4;
}

.capture-draft-drawer__submit-option-head {
    display: flex;
    align-items: center;
    gap: 8px;
}

.capture-draft-drawer__submit-option-label {
    font-size: 14px;
    font-weight: 500;
    color: #111827;
}

.capture-draft-drawer__submit-option-badge {
    padding: 0 6px;
    border-radius: 9999px;
    font-size: 11px;
    font-weight: 500;
    color: #4338ca;
    background: #eef2ff;
}

.capture-draft-drawer__submit-option-desc {
    margin: 4px 0 0;
    font-size: 12px;
    color: #9ca3af;
    line-height: 1.45;
}

.capture-draft-drawer__footer:has(.capture-draft-drawer__footer-actions:only-child) {
    flex-direction: row;
    justify-content: flex-end;
}
</style>
