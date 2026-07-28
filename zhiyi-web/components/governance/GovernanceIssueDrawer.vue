<template>
    <!-- 治理工单详情抽屉：对比主版本与重复记忆，一键处置 -->
    <el-drawer
        :model-value="visible"
        direction="rtl"
        size="720px"
        destroy-on-close
        class="governance-issue-drawer"
        @update:model-value="handleVisibleChange"
    >
        <template #header>
            <div v-if="issue" class="governance-issue-drawer__header">
                <div class="flex items-center gap-2 mb-2">
                    <span
                        class="governance-issue-drawer__badge"
                        :class="`governance-issue-drawer__badge--${issueTone}`"
                    >
                        {{ issueTypeLabel }} · {{ Math.round((issue.similarityScore || 0) * 100) }}%
                    </span>
                    <span class="text-xs text-gray-400">工单 #{{ issue.id }}</span>
                    <span
                        v-if="detailLoading"
                        class="governance-issue-drawer__loading-tag"
                    >
                        加载详情中
                    </span>
                </div>
                <h2
                    class="text-lg font-semibold text-gray-900"
                    :class="{ 'governance-issue-drawer__title--loading': detailLoading && !issueDetail }"
                >
                    {{ primaryTitle }}
                </h2>
                <p v-if="!detailLoading || issueDetail" class="text-sm text-gray-500 mt-1">
                    建议：{{ suggestedActionLabel }}
                </p>
            </div>
        </template>

        <template v-if="issue">
            <!-- 详情加载骨架 -->
            <div v-if="detailLoading" class="governance-issue-drawer__body">
                <section class="governance-issue-drawer__section">
                    <el-skeleton animated>
                        <template #template>
                            <div class="flex items-center gap-2 mb-3">
                                <el-skeleton-item variant="text" style="width: 120px; height: 18px;" />
                                <el-skeleton-item variant="text" style="width: 48px; height: 20px;" />
                            </div>
                            <div class="rounded-xl border border-gray-100 p-4 space-y-3">
                                <el-skeleton-item variant="h3" style="width: 72%;" />
                                <el-skeleton-item variant="text" style="width: 40%;" />
                                <el-skeleton-item variant="text" style="width: 100%;" />
                                <el-skeleton-item variant="text" style="width: 92%;" />
                                <el-skeleton-item variant="text" style="width: 85%;" />
                            </div>
                        </template>
                    </el-skeleton>
                </section>

                <section class="governance-issue-drawer__section">
                    <el-skeleton animated>
                        <template #template>
                            <el-skeleton-item variant="text" style="width: 140px; height: 18px; margin-bottom: 12px;" />
                            <div
                                v-for="index in duplicateSkeletonCount"
                                :key="index"
                                class="rounded-xl border border-gray-100 p-4 space-y-3 mb-3"
                            >
                                <el-skeleton-item variant="h3" style="width: 68%;" />
                                <el-skeleton-item variant="text" style="width: 100%;" />
                                <el-skeleton-item variant="text" style="width: 88%;" />
                            </div>
                        </template>
                    </el-skeleton>
                </section>
            </div>

            <!-- 详情内容 -->
            <div v-else class="governance-issue-drawer__body space-y-6">
                <section class="governance-issue-drawer__section">
                    <h3 class="governance-issue-drawer__section-title">
                        推荐主版本
                        <el-tag size="small" type="success" effect="light" class="ml-2">保留</el-tag>
                    </h3>
                    <GovernanceKnowledgeCompareCard
                        v-if="issueDetail?.primaryKnowledge"
                        :knowledge="issueDetail.primaryKnowledge"
                        highlight
                    />
                    <div
                        v-else
                        class="governance-issue-drawer__empty-card"
                    >
                        主版本内容暂不可用（可能已下架）
                    </div>
                </section>

                <section class="governance-issue-drawer__section">
                    <h3 class="governance-issue-drawer__section-title">
                        {{ relatedSectionTitle }}（{{ duplicateList.length }} 条）
                    </h3>
                    <div v-if="duplicateList.length" class="space-y-3">
                        <GovernanceKnowledgeCompareCard
                            v-for="item in duplicateList"
                            :key="item.id"
                            :knowledge="item"
                        />
                    </div>
                    <div v-else class="governance-issue-drawer__empty-card">
                        暂无重复项详情
                    </div>
                </section>

                <section
                    v-if="issueMetadataSummary"
                    class="governance-issue-drawer__section"
                >
                    <h3 class="governance-issue-drawer__section-title">校验详情</h3>
                    <div class="governance-issue-drawer__metadata">
                        <p class="governance-issue-drawer__metadata-text">{{ issueMetadataSummary }}</p>
                    </div>
                </section>

                <section v-if="canEdit && issue.status === GOVERNANCE_ISSUE_STATUS_OPEN">
                    <el-input
                        v-model="resolveComment"
                        type="textarea"
                        :rows="2"
                        placeholder="处置备注（可选）"
                    />
                </section>

                <el-alert
                    v-if="issue.status !== GOVERNANCE_ISSUE_STATUS_OPEN"
                    type="info"
                    :closable="false"
                    show-icon
                    :title="GOVERNANCE_ISSUE_STATUS_LABELS[issue.status] || '已处理'"
                    :description="issue.resolveComment || issue.resolvedAction || ''"
                />
            </div>
        </template>

        <template #footer>
            <div
                v-if="issue && canEdit && issue.status === GOVERNANCE_ISSUE_STATUS_OPEN"
                class="governance-issue-drawer__footer"
            >
                <p v-if="detailLoading" class="governance-issue-drawer__footer-hint">
                    详情加载完成后可进行处置
                </p>
                <div class="flex flex-wrap gap-2 justify-end">
                    <el-button
                        :loading="resolving"
                        :disabled="detailLoading"
                        @click="handleDismiss"
                    >
                        标记误报
                    </el-button>
                    <el-button
                        v-if="isOutdatedIssue"
                        type="danger"
                        :loading="resolving"
                        :disabled="detailLoading"
                        @click="handleDeprecate"
                    >
                        下架过时记忆
                    </el-button>
                    <template v-else-if="isFragmentCluster || (isIncompleteIssue && hasRelatedMembers)">
                        <el-button
                            type="primary"
                            :loading="resolving"
                            :disabled="detailLoading"
                            @click="handleOpenMergeWizard"
                        >
                            进入合并向导
                        </el-button>
                    </template>
                    <template v-else-if="!isConflictIssue">
                        <el-button
                            v-if="canMergeFacts"
                            type="warning"
                            :loading="resolving"
                            :disabled="detailLoading"
                            @click="handleMergeFacts"
                        >
                            合并 Fact
                        </el-button>
                        <el-button
                            type="primary"
                            :loading="resolving"
                            :disabled="detailLoading"
                            @click="handleKeepPrimary"
                        >
                            保留主版本，下架其余
                        </el-button>
                    </template>
                </div>
            </div>
        </template>
    </el-drawer>
</template>

<script setup>
import { ElMessageBox } from 'element-plus'
import {
    GOVERNANCE_ACTION_DEPRECATE,
    GOVERNANCE_ACTION_DISMISS,
    GOVERNANCE_ACTION_KEEP_PRIMARY,
    GOVERNANCE_ACTION_MERGE_FACTS,
    GOVERNANCE_ACTION_LABELS,
    GOVERNANCE_ISSUE_STATUS_OPEN,
    GOVERNANCE_ISSUE_STATUS_LABELS,
    GOVERNANCE_ISSUE_TYPE_CONFLICT,
    GOVERNANCE_ISSUE_TYPE_FRAGMENT_CLUSTER,
    GOVERNANCE_ISSUE_TYPE_INCOMPLETE,
    GOVERNANCE_ISSUE_TYPE_OUTDATED,
    GOVERNANCE_ISSUE_TYPE_LABELS,
} from '~/constants/governance'
import { KNOWLEDGE_TYPES } from '~/constants/knowledge'

const props = defineProps({
    visible: {
        type: Boolean,
        default: false,
    },
    issue: {
        type: Object,
        default: null,
    },
    issueDetail: {
        type: Object,
        default: null,
    },
    detailLoading: {
        type: Boolean,
        default: false,
    },
    resolving: {
        type: Boolean,
        default: false,
    },
    canEdit: {
        type: Boolean,
        default: true,
    },
})

const emit = defineEmits(['update:visible', 'resolve', 'open-merge-wizard'])

const resolveComment = ref('')

/** 主版本标题：加载中优先用列表摘要，避免标题闪烁 */
const primaryTitle = computed(() => {
    if (props.detailLoading && !props.issueDetail?.primaryKnowledge) {
        return props.issue?.primaryKnowledge?.title
            || `记忆 #${props.issue?.primaryKnowledgeId || ''}`
    }
    return props.issueDetail?.primaryKnowledge?.title
        || props.issue?.primaryKnowledge?.title
        || `记忆 #${props.issue?.primaryKnowledgeId || ''}`
})

/** 建议动作文案 */
const suggestedActionLabel = computed(() =>
    GOVERNANCE_ACTION_LABELS[props.issue?.suggestedAction] || props.issue?.suggestedAction || '人工确认',
)

/** 重复记忆列表 */
const duplicateList = computed(() => props.issueDetail?.relatedKnowledgeList || [])

/** 骨架屏重复项数量：按列表已知条数展示，至少 1 条 */
const duplicateSkeletonCount = computed(() => {
    const knownCount = props.issue?.relatedKnowledgeIds?.length || 0
    return Math.max(knownCount, 1)
})

/** 是否为碎片聚类工单 */
const isFragmentCluster = computed(() =>
    props.issue?.issueType === GOVERNANCE_ISSUE_TYPE_FRAGMENT_CLUSTER,
)

const isIncompleteIssue = computed(() =>
    props.issue?.issueType === GOVERNANCE_ISSUE_TYPE_INCOMPLETE,
)

const isOutdatedIssue = computed(() =>
    props.issue?.issueType === GOVERNANCE_ISSUE_TYPE_OUTDATED,
)

const isConflictIssue = computed(() =>
    props.issue?.issueType === GOVERNANCE_ISSUE_TYPE_CONFLICT,
)

/** 不完整工单是否有关联记忆可参与合并（单条仅能标记误报或人工补全） */
const hasRelatedMembers = computed(() =>
    (props.issue?.relatedKnowledgeIds?.length || 0) > 0,
)

const relatedSectionTitle = computed(() => {
    if (isFragmentCluster.value || isIncompleteIssue.value) {
        return '关联记忆'
    }
    if (isConflictIssue.value) {
        return '冲突 Rule'
    }
    if (isOutdatedIssue.value) {
        return '关联记忆'
    }
    return '重复记忆'
})

/** 校验详情摘要 */
const issueMetadataSummary = computed(() => {
    const metadataJson = props.issueDetail?.issueMetadataJson || props.issue?.issueMetadataJson
    if (!metadataJson) {
        return ''
    }
    try {
        const metadata = JSON.parse(metadataJson)
        if (metadata.missingFactTypes?.length) {
            return `缺失字段：${metadata.missingFactTypes.join('、')}`
        }
        if (metadata.outdatedFeedbackCount) {
            return `累计 outdated/wrong 反馈 ${metadata.outdatedFeedbackCount} 次`
        }
        if (metadata.leftConstraintSnippet && metadata.rightConstraintSnippet) {
            return `约束 A：${metadata.leftConstraintSnippet}\n约束 B：${metadata.rightConstraintSnippet}`
        }
        return JSON.stringify(metadata)
    } catch {
        return metadataJson
    }
})

/** 工单类型文案 */
const issueTypeLabel = computed(() =>
    GOVERNANCE_ISSUE_TYPE_LABELS[props.issue?.issueType] || '治理工单',
)

/** 工单类型主题色 */
const issueTone = computed(() => {
    const toneMap = {
        [GOVERNANCE_ISSUE_TYPE_FRAGMENT_CLUSTER]: 'indigo',
        [GOVERNANCE_ISSUE_TYPE_INCOMPLETE]: 'rose',
        [GOVERNANCE_ISSUE_TYPE_OUTDATED]: 'slate',
        [GOVERNANCE_ISSUE_TYPE_CONFLICT]: 'red',
    }
    return toneMap[props.issue?.issueType] || 'amber'
})

/** Rule 类型才展示合并 Fact 按钮 */
const canMergeFacts = computed(() =>
    props.issue?.knowledgeType === KNOWLEDGE_TYPES.RULE
    || props.issue?.suggestedAction === GOVERNANCE_ACTION_MERGE_FACTS,
)

watch(
    () => props.visible,
    (value) => {
        if (value) {
            resolveComment.value = ''
        }
    },
)

function handleVisibleChange(value) {
    emit('update:visible', value)
}

/**
 * 弹出确认框前先收起抽屉，避免 MessageBox 与 Drawer 遮罩层叠
 * 用户取消时恢复抽屉展示
 */
async function runConfirmAfterCloseDrawer(confirmTask) {
    const savedComment = resolveComment.value
    emit('update:visible', false)
    await nextTick()
    try {
        await confirmTask()
    } catch {
        emit('update:visible', true)
        resolveComment.value = savedComment
        return false
    }
    return true
}

/** 保留主版本并下架重复项 */
async function handleKeepPrimary() {
    const confirmed = await runConfirmAfterCloseDrawer(() =>
        ElMessageBox.confirm(
            '将保留推荐主版本，对其余重复记忆建立替代关系并下架，Recall 将不再召回它们。',
            '确认处置',
            { type: 'warning' },
        ),
    )
    if (!confirmed) {
        return
    }
    emit('resolve', {
        action: GOVERNANCE_ACTION_KEEP_PRIMARY,
        comment: resolveComment.value,
    })
}

/** 合并 Fact 到主版本 Rule */
async function handleMergeFacts() {
    const confirmed = await runConfirmAfterCloseDrawer(() =>
        ElMessageBox.confirm(
            '将把重复 Rule 的 Fact 追加到主版本（文本去重），然后下架重复项。',
            '确认合并 Fact',
            { type: 'warning' },
        ),
    )
    if (!confirmed) {
        return
    }
    emit('resolve', {
        action: GOVERNANCE_ACTION_MERGE_FACTS,
        comment: resolveComment.value,
    })
}

/** 下架过时记忆 */
async function handleDeprecate() {
    const confirmed = await runConfirmAfterCloseDrawer(() =>
        ElMessageBox.confirm(
            '将下架该过时记忆，Recall 不再召回。若仍有效请标记误报。',
            '确认下架',
            { type: 'warning' },
        ),
    )
    if (!confirmed) {
        return
    }
    emit('resolve', {
        action: GOVERNANCE_ACTION_DEPRECATE,
        comment: resolveComment.value,
    })
}

/** 进入合并向导 */
function handleOpenMergeWizard() {
    emit('update:visible', false)
    emit('open-merge-wizard', props.issue)
}

/** 标记为误报（无二次确认，直接收起抽屉后提交） */
async function handleDismiss() {
    emit('update:visible', false)
    await nextTick()
    emit('resolve', {
        action: GOVERNANCE_ACTION_DISMISS,
        comment: resolveComment.value,
    })
}
</script>

<style scoped>
.governance-issue-drawer :deep(.el-drawer__header) {
    margin-bottom: 0;
    padding-bottom: 16px;
    border-bottom: 1px solid #f3f4f6;
}

.governance-issue-drawer :deep(.el-drawer__footer) {
    padding-top: 16px;
    border-top: 1px solid #f3f4f6;
    background: #fafafa;
}

.governance-issue-drawer__badge {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    border-radius: 9999px;
    font-size: 12px;
    font-weight: 600;
    color: #b45309;
    background: #fffbeb;
}

.governance-issue-drawer__badge--indigo {
    color: #4338ca;
    background: #eef2ff;
}

.governance-issue-drawer__badge--rose {
    color: #be123c;
    background: #fff1f2;
}

.governance-issue-drawer__badge--slate {
    color: #334155;
    background: #f8fafc;
}

.governance-issue-drawer__badge--red {
    color: #b91c1c;
    background: #fef2f2;
}

.governance-issue-drawer__loading-tag {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    border-radius: 9999px;
    font-size: 11px;
    color: #4338ca;
    background: #eef2ff;
}

.governance-issue-drawer__title--loading {
    color: #6b7280;
}

.governance-issue-drawer__section-title {
    display: flex;
    align-items: center;
    font-size: 14px;
    font-weight: 600;
    color: #111827;
    margin-bottom: 12px;
}

.governance-issue-drawer__metadata {
    padding: 14px 16px;
    border-radius: 12px;
    border: 1px solid #e8eaef;
    background: linear-gradient(145deg, #fafbff 0%, #fff 100%);
}

.governance-issue-drawer__metadata-text {
    margin: 0;
    font-size: 13px;
    line-height: 1.65;
    color: #4b5563;
    white-space: pre-wrap;
}

.governance-issue-drawer__empty-card {
    padding: 16px;
    border-radius: 12px;
    border: 1px dashed #e5e7eb;
    font-size: 13px;
    color: #9ca3af;
    text-align: center;
    background: #fafafa;
}

.governance-issue-drawer__footer {
    width: 100%;
}

.governance-issue-drawer__footer-hint {
    margin: 0 0 10px;
    font-size: 12px;
    color: #9ca3af;
    text-align: right;
}
</style>
