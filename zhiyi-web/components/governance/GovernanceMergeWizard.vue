<template>
    <!-- 合并优化向导：选择记忆 → 预览 → 确认发布 -->
    <section class="governance-merge-wizard">
        <div class="governance-merge-wizard__top">
            <div class="governance-merge-wizard__header">
                <div class="governance-merge-wizard__icon">
                    <el-icon :size="18"><Connection /></el-icon>
                </div>
                <div class="governance-merge-wizard__header-text">
                    <h2 class="governance-merge-wizard__title">合并优化向导</h2>
                    <p class="governance-merge-wizard__subtitle">
                        将多条零散、不完整的已发布记忆合并为一条结构完整的 Experience，源记忆将被新版替代并下架
                    </p>
                </div>
            </div>

            <el-steps
                :active="currentStep"
                finish-status="success"
                simple
                class="governance-merge-wizard__steps"
            >
                <el-step
                    v-for="(stepLabel, index) in GOVERNANCE_MERGE_FLOW_STEPS"
                    :key="index"
                    :title="stepLabel"
                />
            </el-steps>
        </div>

        <!-- Step 1：选择记忆 -->
        <div v-if="currentStep === 0" class="governance-merge-wizard__panel">
            <div v-if="presetIssueId" class="governance-merge-wizard__preset">
                来自工单 <strong>#{{ presetIssueId }}</strong>，可直接进入预览
                <el-button
                    type="primary"
                    :loading="mergePreviewLoading"
                    :disabled="!canEdit"
                    @click="handleGeneratePreview"
                >
                    生成合并预览
                </el-button>
            </div>
            <div v-else class="governance-merge-wizard__form">
                <div class="governance-merge-wizard__form-label">
                    <label class="governance-merge-wizard__label">记忆 ID（逗号分隔）</label>
                    <span class="governance-merge-wizard__hint">
                        <el-icon :size="14"><InfoFilled /></el-icon>
                        须选择至少 2 条同类型的已发布记忆
                    </span>
                </div>
                <div class="governance-merge-wizard__form-row">
                    <el-input
                        v-model="knowledgeIdInput"
                        placeholder="例如：12, 34, 56"
                        :disabled="!canEdit"
                    />
                    <el-button
                        type="primary"
                        :loading="mergePreviewLoading"
                        :disabled="!canEdit"
                        @click="handleGeneratePreview"
                    >
                        生成合并预览
                    </el-button>
                </div>
            </div>
        </div>

        <!-- Step 2：预览编辑 -->
        <div v-else-if="currentStep === 1" class="governance-merge-wizard__panel">
            <div v-if="mergePreviewLoading" class="py-8">
                <el-skeleton :rows="6" animated />
            </div>
            <template v-else-if="editablePreview">
                <div class="governance-merge-wizard__preview-meta">
                    <span class="governance-merge-wizard__preview-badge">{{ previewSourceLabel }}</span>
                    <span v-if="editablePreview.mergeSummary">{{ editablePreview.mergeSummary }}</span>
                </div>

                <div class="governance-merge-wizard__field">
                    <label class="governance-merge-wizard__label">合并标题</label>
                    <el-input v-model="editablePreview.title" maxlength="256" show-word-limit />
                </div>

                <div class="governance-merge-wizard__field">
                    <label class="governance-merge-wizard__label">合并 Facts</label>
                    <div class="governance-merge-wizard__editor">
                        <KnowledgeFactBlockEditor v-model="editablePreview.facts" removable />
                    </div>
                </div>

                <section class="governance-merge-wizard__sources">
                    <h3 class="governance-merge-wizard__label">
                        源记忆（{{ editablePreview.sourceKnowledgeIds?.length || 0 }} 条）
                    </h3>
                    <div class="governance-merge-wizard__source-ids">
                        {{ (editablePreview.sourceKnowledgeIds || []).join(', ') }}
                    </div>
                </section>

                <el-input
                    v-model="mergeComment"
                    type="textarea"
                    :rows="2"
                    placeholder="合并备注（可选）"
                />
            </template>
        </div>

        <!-- Step 3：完成 -->
        <div v-else class="governance-merge-wizard__panel governance-merge-wizard__panel--center">
            <el-result icon="success" title="合并优化完成">
                <template #sub-title>
                    新版经验 #{{ publishedKnowledgeId }} 已发布，源记忆已建立替代关系并下架
                </template>
                <template #extra>
                    <el-button type="primary" @click="handleViewExperience">
                        查看经验
                    </el-button>
                    <el-button @click="handleReset">
                        继续合并
                    </el-button>
                </template>
            </el-result>
        </div>

        <div v-if="currentStep === 1" class="governance-merge-wizard__footer">
            <el-button @click="currentStep = 0">上一步</el-button>
            <el-button
                type="primary"
                :loading="mergeConfirmLoading"
                :disabled="!canEdit || !editablePreview?.title"
                @click="handleConfirmMerge"
            >
                确认发布新版
            </el-button>
        </div>
    </section>
</template>

<script setup>
import { Connection, InfoFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GOVERNANCE_MERGE_FLOW_STEPS } from '~/constants/governance'

const props = defineProps({
    canEdit: {
        type: Boolean,
        default: true,
    },
    presetIssueId: {
        type: [Number, String],
        default: null,
    },
    mergePreviewLoading: {
        type: Boolean,
        default: false,
    },
    mergeConfirmLoading: {
        type: Boolean,
        default: false,
    },
})

const emit = defineEmits(['preview', 'confirm', 'completed'])

const router = useRouter()

const currentStep = ref(0)
const knowledgeIdInput = ref('')
const editablePreview = ref(null)
const mergeComment = ref('')
const publishedKnowledgeId = ref(null)

const previewSourceLabel = computed(() => {
    const source = editablePreview.value?.previewSource
    if (source === 'llm') {
        return 'AI 增强预览'
    }
    if (source === 'hybrid') {
        return '规则 + AI 预览'
    }
    return '规则合并预览'
})

/** 解析用户输入的记忆 ID */
function parseKnowledgeIds() {
    if (props.presetIssueId) {
        return null
    }
    const idList = knowledgeIdInput.value
        .split(/[,，\s]+/)
        .map((item) => Number(item.trim()))
        .filter((item) => Number.isFinite(item) && item > 0)
    if (idList.length < 2) {
        throw new Error('请至少输入 2 个有效的记忆 ID')
    }
    return idList
}

/** 生成合并预览 */
async function handleGeneratePreview() {
    if (!props.canEdit) {
        return
    }
    try {
        if (props.presetIssueId) {
            emit('preview', { issueId: props.presetIssueId })
        } else {
            emit('preview', { knowledgeIds: parseKnowledgeIds() })
        }
    } catch (error) {
        ElMessage.error(error.message || '请输入有效的记忆 ID')
    }
}

/** 外部设置预览结果并进入 Step 2 */
function applyPreview(previewResult) {
    editablePreview.value = JSON.parse(JSON.stringify(previewResult || {}))
    currentStep.value = 1
}

/** 确认合并发布 */
async function handleConfirmMerge() {
    if (!editablePreview.value) {
        return
    }
    try {
        await ElMessageBox.confirm(
            '将发布合并后的新版经验，并对全部源记忆建立替代关系并下架，Recall 将不再召回源记忆。',
            '确认合并优化',
            { type: 'warning' },
        )
    } catch {
        return
    }

    emit('confirm', {
        issueId: props.presetIssueId || undefined,
        sourceKnowledgeIds: editablePreview.value.sourceKnowledgeIds,
        title: editablePreview.value.title,
        facts: editablePreview.value.facts,
        tags: editablePreview.value.tags,
        artifacts: editablePreview.value.artifacts,
        project: editablePreview.value.project,
        module: editablePreview.value.module,
        repository: editablePreview.value.repository,
        knowledgeType: editablePreview.value.knowledgeType,
        comment: mergeComment.value,
    })
}

/** 合并成功后进入完成步 */
function markCompleted(newKnowledgeId) {
    publishedKnowledgeId.value = newKnowledgeId
    currentStep.value = 2
    emit('completed', newKnowledgeId)
}

function handleViewExperience() {
    if (publishedKnowledgeId.value) {
        router.push(`/experience/${publishedKnowledgeId.value}`)
    }
}

function handleReset() {
    currentStep.value = 0
    editablePreview.value = null
    mergeComment.value = ''
    publishedKnowledgeId.value = null
    knowledgeIdInput.value = ''
}

defineExpose({
    applyPreview,
    markCompleted,
    handleReset,
})
</script>

<style scoped>
.governance-merge-wizard {
    padding: 18px 20px 20px;
    border-radius: 16px;
    border: 1px solid #e8eaef;
    background: #fff;
    box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.governance-merge-wizard__top {
    display: flex;
    flex-direction: column;
    gap: 14px;
    margin-bottom: 16px;
    padding-bottom: 14px;
    border-bottom: 1px solid #f3f4f6;
}

.governance-merge-wizard__header {
    display: flex;
    align-items: flex-start;
    gap: 12px;
}

.governance-merge-wizard__header-text {
    min-width: 0;
}

.governance-merge-wizard__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    width: 36px;
    height: 36px;
    border-radius: 10px;
    color: #5d65f9;
    background: linear-gradient(135deg, #eff0fe 0%, #dfe0fe 100%);
}

.governance-merge-wizard__title {
    margin: 0;
    font-size: 18px;
    font-weight: 700;
    color: #111827;
    letter-spacing: -0.01em;
}

.governance-merge-wizard__subtitle {
    margin: 4px 0 0;
    font-size: 13px;
    line-height: 1.55;
    color: #6b7280;
}

.governance-merge-wizard__steps {
    max-width: 520px;
}

.governance-merge-wizard__steps :deep(.el-step__title) {
    font-size: 12px;
    line-height: 1.4;
}

.governance-merge-wizard__steps :deep(.el-step__arrow) {
    margin: 0 8px;
}

.governance-merge-wizard__panel {
    padding: 0;
}

.governance-merge-wizard__panel--center {
    padding: 8px 0 0;
}

.governance-merge-wizard__panel--center :deep(.el-result) {
    padding: 12px 0 0;
}

.governance-merge-wizard__form {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.governance-merge-wizard__form-label {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 8px 12px;
}

.governance-merge-wizard__form-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 10px;
    max-width: 640px;
}

.governance-merge-wizard__form-row :deep(.el-input) {
    flex: 1 1 240px;
    min-width: 0;
}

.governance-merge-wizard__hint {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: #6366f1;
}

.governance-merge-wizard__preset {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 10px 16px;
    padding: 10px 12px;
    border-radius: 10px;
    font-size: 13px;
    color: #4b5563;
    background: #f9fafb;
    border: 1px solid #eef0f3;
}

.governance-merge-wizard__label {
    display: block;
    margin: 0;
    font-size: 13px;
    font-weight: 600;
    color: #374151;
}

.governance-merge-wizard__preview-meta {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    margin-bottom: 14px;
    font-size: 12px;
    color: #6b7280;
}

.governance-merge-wizard__preview-badge {
    padding: 3px 10px;
    border-radius: 999px;
    font-weight: 600;
    color: #5d65f9;
    background: #eff0fe;
    border: 1px solid #ced1fd;
}

.governance-merge-wizard__field {
    margin-bottom: 14px;
}

.governance-merge-wizard__field .governance-merge-wizard__label {
    margin-bottom: 6px;
}

.governance-merge-wizard__editor {
    padding: 12px;
    border-radius: 12px;
    border: 1px solid #e8eaef;
    background: #fafafa;
}

.governance-merge-wizard__sources {
    margin-bottom: 14px;
}

.governance-merge-wizard__sources .governance-merge-wizard__label {
    margin-bottom: 6px;
}

.governance-merge-wizard__source-ids {
    padding: 8px 10px;
    border-radius: 8px;
    font-size: 12px;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    color: #6b7280;
    background: #f9fafb;
    border: 1px solid #eef0f3;
}

.governance-merge-wizard__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 16px;
    padding-top: 14px;
    border-top: 1px solid #f3f4f6;
}
</style>
