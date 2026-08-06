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
                    <label class="governance-merge-wizard__label">选择待合并记忆</label>
                    <span class="governance-merge-wizard__hint">
                        <el-icon :size="14"><InfoFilled /></el-icon>
                        须至少 2 条同类型的已发布记忆，可搜索选择或直接输入 ID
                    </span>
                </div>
                <div class="governance-merge-wizard__form-row">
                    <el-input
                        v-model="knowledgeIdInput"
                        placeholder="例如：12, 34, 56"
                        :disabled="!canEdit"
                    />
                    <el-button
                        :disabled="!canEdit"
                        @click="openPicker"
                    >
                        <el-icon class="mr-1"><Search /></el-icon>选择记忆
                    </el-button>
                    <el-button
                        type="primary"
                        :loading="mergePreviewLoading"
                        :disabled="!canEdit"
                        @click="handleGeneratePreview"
                    >
                        生成合并预览
                    </el-button>
                </div>
                <div v-if="selectedSourceItems.length" class="governance-merge-wizard__selected">
                    <span class="governance-merge-wizard__selected-label">
                        已选 {{ selectedSourceItems.length }} 条
                    </span>
                    <div class="governance-merge-wizard__selected-chips">
                        <el-tag
                            v-for="item in selectedSourceItems"
                            :key="item.id"
                            closable
                            class="governance-merge-wizard__selected-chip"
                            @close="removeSelectedSource(item.id)"
                        >
                            #{{ item.id }} {{ item.title }}
                        </el-tag>
                    </div>
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

        <!-- 选择记忆对话框：跨页多选已发布记忆，锁定同类型 -->
        <el-dialog
            v-model="pickerVisible"
            title="选择待合并记忆"
            width="720px"
            :close-on-click-modal="false"
            append-to-body
            class="governance-merge-wizard__picker-dialog"
        >
            <div class="governance-merge-wizard__picker-toolbar">
                <el-input
                    v-model="pickerKeyword"
                    placeholder="搜索标题 / 标签"
                    clearable
                    class="governance-merge-wizard__picker-search"
                    @keyup.enter="handlePickerSearch"
                    @clear="handlePickerSearch"
                >
                    <template #prefix><el-icon><Search /></el-icon></template>
                </el-input>
                <el-select
                    v-model="pickerTypeFilter"
                    class="governance-merge-wizard__picker-type"
                    @change="handlePickerTypeChange"
                >
                    <el-option
                        v-for="option in MEMORY_TYPE_FILTER_OPTIONS"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value"
                    />
                </el-select>
                <el-button @click="handlePickerSearch">搜索</el-button>
            </div>

            <div v-if="pickerSelectedType" class="governance-merge-wizard__picker-hint">
                <el-icon :size="14"><InfoFilled /></el-icon>
                已锁定类型：<strong>{{ KNOWLEDGE_TYPE_LABELS[pickerSelectedType] }}</strong>
                ，合并须为同一类型，其它类型不可选
            </div>

            <div v-loading="pickerLoading" class="governance-merge-wizard__picker-list">
                <div
                    v-if="!pickerLoading && pickerList.length === 0"
                    class="governance-merge-wizard__picker-empty"
                >
                    暂无已发布记忆
                </div>
                <div
                    v-for="item in pickerList"
                    :key="item.id"
                    class="governance-merge-wizard__picker-item"
                    :class="{
                        'governance-merge-wizard__picker-item--selected': isPickerItemSelected(item),
                        'governance-merge-wizard__picker-item--disabled': isPickerItemDisabled(item),
                    }"
                    @click="onPickerItemClick(item)"
                >
                    <el-checkbox
                        :model-value="isPickerItemSelected(item)"
                        :disabled="isPickerItemDisabled(item)"
                    />
                    <div class="governance-merge-wizard__picker-item-body">
                        <div class="governance-merge-wizard__picker-item-top">
                            <span class="governance-merge-wizard__picker-item-type">
                                {{ KNOWLEDGE_TYPE_LABELS[item.knowledgeType] || item.knowledgeType }}
                            </span>
                            <span class="governance-merge-wizard__picker-item-title">{{ item.title }}</span>
                        </div>
                        <div class="governance-merge-wizard__picker-item-meta">
                            <span>#{{ item.id }}</span>
                            <span v-if="item.module">· {{ item.module }}</span>
                            <span>· 召回 {{ item.recallCount || 0 }} 次</span>
                        </div>
                    </div>
                </div>
            </div>

            <div v-if="pickerTotal > pickerPageSize" class="governance-merge-wizard__picker-pagination">
                <el-pagination
                    background
                    layout="prev, pager, next"
                    :total="pickerTotal"
                    :page-size="pickerPageSize"
                    :current-page="pickerPageNum"
                    @current-change="handlePickerPageChange"
                />
            </div>

            <template #footer>
                <span class="governance-merge-wizard__picker-count">已选 {{ pickerSelectedCount }} 条</span>
                <el-button @click="pickerVisible = false">取消</el-button>
                <el-button
                    type="primary"
                    :disabled="pickerSelectedCount < 2"
                    @click="confirmPicker"
                >
                    确认选择
                </el-button>
            </template>
        </el-dialog>
    </section>
</template>

<script setup>
import { Connection, InfoFilled, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GOVERNANCE_MERGE_FLOW_STEPS } from '~/constants/governance'
import {
    KNOWLEDGE_LIFECYCLE,
    KNOWLEDGE_TYPE_LABELS,
    MEMORY_TYPE_FILTER_ALL,
    MEMORY_TYPE_FILTER_OPTIONS,
} from '~/constants/knowledge'
import { fetchKnowledgeList } from '~/services/knowledge.service'

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

/** 选择器确认后的源记忆摘要，用于回显已选标题（输入框为唯一真相源，chips 仅作展示） */
const selectedSourceItems = ref([])
/** 回填输入框时跳过手动编辑监听，避免误清空 chips */
const isSyncingInput = ref(false)

/** 选择记忆对话框状态 */
const pickerVisible = ref(false)
const pickerLoading = ref(false)
const pickerKeyword = ref('')
const pickerTypeFilter = ref(MEMORY_TYPE_FILTER_ALL)
const pickerList = ref([])
const pickerTotal = ref(0)
const pickerPageNum = ref(1)
const pickerPageSize = 10
/** 跨页多选：id -> 记忆摘要，确认时整体回填 */
const pickerSelectedMap = ref(new Map())

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

/** 已选第一条的类型：选中后锁定，禁止跨类型多选（后端要求同类型合并） */
const pickerSelectedType = computed(() => {
    for (const item of pickerSelectedMap.value.values()) {
        return item.knowledgeType
    }
    return null
})

const pickerSelectedCount = computed(() => pickerSelectedMap.value.size)

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

/** 手动编辑输入框时清空 chips：输入框是唯一真相源，chips 仅在选择器确认后展示 */
watch(knowledgeIdInput, (value) => {
    if (isSyncingInput.value) {
        isSyncingInput.value = false
        return
    }
    if (selectedSourceItems.value.length === 0) {
        return
    }
    const derived = selectedSourceItems.value.map((item) => item.id).join(', ')
    if (value !== derived) {
        selectedSourceItems.value = []
    }
})

/** 打开选择器：用当前已选初始化，并按已选类型预筛 */
function openPicker() {
    if (!props.canEdit) {
        return
    }
    pickerSelectedMap.value = new Map(
        selectedSourceItems.value.map((item) => [item.id, item]),
    )
    pickerKeyword.value = ''
    pickerTypeFilter.value = pickerSelectedType.value || MEMORY_TYPE_FILTER_ALL
    pickerPageNum.value = 1
    pickerVisible.value = true
    loadPickerList()
}

async function loadPickerList() {
    pickerLoading.value = true
    try {
        const result = await fetchKnowledgeList(
            pickerPageNum.value,
            pickerPageSize,
            pickerKeyword.value,
            {
                knowledgeType: pickerTypeFilter.value === MEMORY_TYPE_FILTER_ALL
                    ? undefined
                    : pickerTypeFilter.value,
                lifecycleStatus: KNOWLEDGE_LIFECYCLE.PUBLISHED,
            },
        )
        pickerList.value = result.list || []
        pickerTotal.value = result.total || 0
    } catch (error) {
        ElMessage.error(error.message || '加载记忆列表失败')
    } finally {
        pickerLoading.value = false
    }
}

function handlePickerSearch() {
    pickerPageNum.value = 1
    loadPickerList()
}

function handlePickerTypeChange() {
    pickerPageNum.value = 1
    loadPickerList()
}

function handlePickerPageChange(page) {
    pickerPageNum.value = page
    loadPickerList()
}

function isPickerItemSelected(item) {
    return pickerSelectedMap.value.has(item.id)
}

function isPickerItemDisabled(item) {
    return pickerSelectedType.value !== null
        && item.knowledgeType !== pickerSelectedType.value
}

function onPickerItemClick(item) {
    if (isPickerItemDisabled(item)) {
        return
    }
    const next = new Map(pickerSelectedMap.value)
    if (next.has(item.id)) {
        next.delete(item.id)
    } else {
        next.set(item.id, {
            id: item.id,
            title: item.title,
            knowledgeType: item.knowledgeType,
            module: item.module,
        })
    }
    pickerSelectedMap.value = next
}

/** 确认选择：回填输入框并同步 chips 摘要 */
function confirmPicker() {
    if (pickerSelectedMap.value.size < 2) {
        ElMessage.warning('请至少选择 2 条记忆')
        return
    }
    applyPickerSelection(Array.from(pickerSelectedMap.value.values()))
    pickerVisible.value = false
}

function applyPickerSelection(items) {
    selectedSourceItems.value = items
    isSyncingInput.value = true
    knowledgeIdInput.value = items.map((item) => item.id).join(', ')
}

function removeSelectedSource(id) {
    const remaining = selectedSourceItems.value.filter((item) => item.id !== id)
    selectedSourceItems.value = remaining
    isSyncingInput.value = true
    knowledgeIdInput.value = remaining.map((item) => item.id).join(', ')
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
    selectedSourceItems.value = []
    isSyncingInput.value = true
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

.governance-merge-wizard__selected {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;
    gap: 8px;
    margin-top: 4px;
    padding: 10px 12px;
    border-radius: 10px;
    background: #f9fafb;
    border: 1px solid #eef0f3;
}

.governance-merge-wizard__selected-label {
    flex-shrink: 0;
    font-size: 12px;
    font-weight: 600;
    color: #5d65f9;
    line-height: 24px;
}

.governance-merge-wizard__selected-chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    min-width: 0;
}

.governance-merge-wizard__selected-chip {
    max-width: 100%;
}

.governance-merge-wizard__selected-chip :deep(.el-tag__text) {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.governance-merge-wizard__picker-toolbar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 10px;
    margin-bottom: 12px;
}

.governance-merge-wizard__picker-search {
    flex: 1 1 240px;
    min-width: 0;
}

.governance-merge-wizard__picker-type {
    width: 150px;
}

.governance-merge-wizard__picker-hint {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 10px;
    padding: 8px 12px;
    border-radius: 8px;
    font-size: 12px;
    color: #4338ca;
    background: #eef2ff;
    border: 1px solid #c7d2fe;
}

.governance-merge-wizard__picker-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
    max-height: 380px;
    overflow-y: auto;
    padding: 2px;
}

.governance-merge-wizard__picker-empty {
    padding: 32px 0;
    text-align: center;
    font-size: 13px;
    color: #9ca3af;
}

.governance-merge-wizard__picker-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 12px;
    border-radius: 10px;
    border: 1px solid #e8eaef;
    background: #fff;
    cursor: pointer;
    transition: border-color 0.15s, background 0.15s;
}

.governance-merge-wizard__picker-item:hover {
    border-color: #c7d2fe;
    background: #fafbff;
}

.governance-merge-wizard__picker-item--selected {
    border-color: #5d65f9;
    background: #eff0fe;
}

.governance-merge-wizard__picker-item--disabled {
    cursor: not-allowed;
    opacity: 0.5;
}

.governance-merge-wizard__picker-item--disabled:hover {
    border-color: #e8eaef;
    background: #fff;
}

/* checkbox 纯展示，点击由整行处理，避免冒泡双触发 */
.governance-merge-wizard__picker-item :deep(.el-checkbox) {
    pointer-events: none;
}

.governance-merge-wizard__picker-item-body {
    flex: 1;
    min-width: 0;
}

.governance-merge-wizard__picker-item-top {
    display: flex;
    align-items: center;
    gap: 8px;
}

.governance-merge-wizard__picker-item-type {
    flex-shrink: 0;
    padding: 2px 8px;
    border-radius: 9999px;
    font-size: 11px;
    font-weight: 500;
    color: #4338ca;
    background: #eef2ff;
}

.governance-merge-wizard__picker-item-title {
    flex: 1;
    min-width: 0;
    font-size: 14px;
    font-weight: 600;
    color: #111827;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.governance-merge-wizard__picker-item-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 4px;
    font-size: 12px;
    color: #9ca3af;
}

.governance-merge-wizard__picker-pagination {
    display: flex;
    justify-content: center;
    margin-top: 14px;
}

.governance-merge-wizard__picker-count {
    margin-right: auto;
    font-size: 13px;
    color: #5d65f9;
}
</style>
