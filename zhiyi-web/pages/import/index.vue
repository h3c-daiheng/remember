<template>
    <div class="import-page max-w-layout mx-auto px-6 py-8">
        <!-- 页头 -->
        <div class="import-page__header mb-8">
            <div class="flex flex-col lg:flex-row lg:items-start lg:justify-between gap-6">
                <div>
                        <h1 class="text-2xl font-semibold text-gray-900 tracking-tight">
                            文档导入
                        </h1>
                        <p class="text-gray-500 mt-1.5 text-sm leading-relaxed">
                            粘贴或上传文档，经 AI 抽取为结构化 Fact Blocks，确认后进入 Capture 草稿审核
                        </p>
                </div>
                <el-button @click="router.push('/capture')">
                    <el-icon class="mr-1"><DocumentChecked /></el-icon>
                    查看待审草稿
                </el-button>
            </div>
            <el-alert
                v-if="!canEdit"
                class="mt-4"
                type="info"
                :closable="false"
                show-icon
                title="当前为查看者，无法导入文档"
                description="文档导入需编辑者及以上角色；您仍可查看 Capture 待审草稿"
            />
        </div>

        <div class="import-page__grid">
            <!-- 左侧：输入区 -->
            <section class="import-page__panel" :class="{ 'import-page__panel--readonly': !canEdit }">
                <h2 class="import-page__panel-title">1. 输入文档</h2>

                <el-tabs v-model="inputMode" class="import-page__tabs" @tab-change="resetPreview">
                    <el-tab-pane label="粘贴文本" name="paste" />
                    <el-tab-pane label="上传文件" name="upload" />
                </el-tabs>

                <div v-if="inputMode === 'upload'" class="mb-4">
                    <el-upload
                        drag
                        :auto-upload="false"
                        :show-file-list="false"
                        :disabled="!canEdit"
                        accept=".md,.txt,.markdown"
                        @change="handleFileChange"
                    >
                        <el-icon class="el-icon--upload" :size="40"><UploadFilled /></el-icon>
                        <div class="el-upload__text">
                            拖拽文件到此处，或 <em>点击上传</em>
                        </div>
                        <template #tip>
                            <div class="el-upload__tip text-center">
                                支持 .md / .txt，最大 2MB
                            </div>
                        </template>
                    </el-upload>
                    <p v-if="form.fileName" class="text-xs text-gray-500 mt-2">
                        已选择：{{ form.fileName }}
                    </p>
                </div>

                <el-form label-position="top" class="import-page__form">
                    <el-form-item label="文档标题（可选）">
                        <el-input
                            v-model="form.title"
                            placeholder="用于辅助 AI 生成草稿标题"
                            maxlength="200"
                            show-word-limit
                            @input="resetPreview"
                        />
                    </el-form-item>

                    <el-form-item label="文档内容">
                        <el-input
                            v-model="form.content"
                            type="textarea"
                            :rows="14"
                            placeholder="粘贴 PR 描述、复盘记录、ADR、故障报告等原文…"
                            @input="resetPreview"
                        />
                        <p class="text-xs text-gray-400 mt-1.5">
                            {{ contentLength }} / {{ MAX_CONTENT_LENGTH }} 字符
                        </p>
                    </el-form-item>

                    <el-form-item label="转化目标">
                        <el-select v-model="form.targetType" class="w-full" @change="resetPreview">
                            <el-option
                                v-for="option in TARGET_TYPE_OPTIONS"
                                :key="option.value"
                                :label="option.label"
                                :value="option.value"
                            />
                        </el-select>
                    </el-form-item>

                    <div class="import-page__context-grid">
                        <el-form-item label="模块">
                            <el-input v-model="form.module" placeholder="如 memory、payment" @input="resetPreview" />
                        </el-form-item>
                        <el-form-item label="仓库">
                            <el-input v-model="form.repository" placeholder="如 zhiyi" @input="resetPreview" />
                        </el-form-item>
                    </div>

                    <el-form-item label="标签">
                        <div class="flex flex-wrap gap-2 mb-2">
                            <el-tag
                                v-for="tag in form.tags"
                                :key="tag"
                                closable
                                @close="removeTag(tag)"
                            >
                                {{ tag }}
                            </el-tag>
                        </div>
                        <div class="flex gap-2">
                            <el-input
                                v-model="tagInput"
                                placeholder="输入标签后回车"
                                @keyup.enter="addTag"
                            />
                            <el-button @click="addTag">添加</el-button>
                        </div>
                    </el-form-item>
                </el-form>

                <el-button
                    type="primary"
                    class="w-full"
                    :loading="extracting"
                    :disabled="!canEdit || !canExtract"
                    @click="runExtract"
                >
                    <el-icon class="mr-1"><MagicStick /></el-icon>
                    AI 抽取预览
                </el-button>
            </section>

            <!-- 右侧：预览区 -->
            <section class="import-page__panel">
                <h2 class="import-page__panel-title">2. 抽取预览</h2>

                <PageEmptyState
                    v-if="!extractResult && !extracting"
                    :flow-steps="['输入', '抽取', '确认']"
                    title="粘贴文档，预览结构化抽取"
                    subtitle="将 PR 描述、复盘记录、ADR 等非结构化文档转为 Fact Blocks，经人工确认后进入 Capture Review"
                    :tags="['Fact Blocks', '转化目标', '模块标签', '人工确认']"
                    guide="输入文档内容后，点击左侧「AI 抽取预览」查看结构化结果"
                    compact
                />

                <div v-else-if="extracting" class="import-page__loading">
                    <el-skeleton :rows="8" animated />
                </div>

                <div v-else-if="extractResult && !extractResult.submit" class="import-page__skip">
                    <el-alert
                        type="warning"
                        :closable="false"
                        show-icon
                        title="不建议沉淀为经验"
                    >
                        <template #default>
                            <p class="text-sm leading-relaxed">
                                {{ extractResult.skipReason || '文档内容不足以形成结构化经验' }}
                            </p>
                            <p v-if="extractResult.routeHint" class="text-xs text-gray-500 mt-2">
                                路由建议：{{ extractResult.routeHint }}
                            </p>
                        </template>
                    </el-alert>
                    <el-button class="mt-4" @click="resetPreview">重新抽取</el-button>
                </div>

                <div v-else-if="hasPreview" class="import-page__preview">
                    <!-- 元信息 -->
                    <div class="import-page__meta">
                        <div class="flex flex-wrap gap-2 mb-3">
                            <el-tag type="success" effect="plain">
                                建议类型：{{ suggestedTypeLabel }}
                            </el-tag>
                            <el-tag v-if="extractResult.routeHint" effect="plain">
                                {{ extractResult.routeHint }}
                            </el-tag>
                            <el-tag v-if="extractResult.confidence" type="info" effect="plain">
                                置信度 {{ Math.round(extractResult.confidence * 100) }}%
                            </el-tag>
                        </div>

                        <el-input
                            v-model="editableDraft.title"
                            placeholder="草稿标题"
                            maxlength="256"
                            show-word-limit
                            class="mb-3"
                        />

                        <RecallContextFields
                            v-model:project="editableDraft.project"
                            v-model:module="editableDraft.module"
                            v-model:repository="editableDraft.repository"
                            layout="context-grid"
                        />
                    </div>

                    <!-- 质量检查 -->
                    <div v-if="extractResult.qualityChecks" class="import-page__quality">
                        <h3 class="import-page__section-title">质量检查</h3>
                        <div class="flex flex-wrap gap-2 mb-2">
                            <el-tag :type="extractResult.qualityChecks.hasObservation ? 'success' : 'info'" size="small">
                                observation {{ extractResult.qualityChecks.hasObservation ? '✓' : '✗' }}
                            </el-tag>
                            <el-tag :type="extractResult.qualityChecks.hasDecision ? 'success' : 'info'" size="small">
                                decision {{ extractResult.qualityChecks.hasDecision ? '✓' : '✗' }}
                            </el-tag>
                            <el-tag :type="extractResult.qualityChecks.hasOutcomeOrEvidence ? 'success' : 'info'" size="small">
                                outcome/evidence {{ extractResult.qualityChecks.hasOutcomeOrEvidence ? '✓' : '✗' }}
                            </el-tag>
                        </div>
                        <ul v-if="extractResult.qualityChecks.warnings?.length" class="import-page__warnings">
                            <li
                                v-for="(warning, index) in extractResult.qualityChecks.warnings"
                                :key="index"
                            >
                                {{ warning }}
                            </li>
                        </ul>
                    </div>

                    <!-- Fact 编辑 -->
                    <KnowledgeDraftFactSection
                        v-if="editableDraft?.facts"
                        section-title="Fact Blocks"
                        section-desc="可编辑 AI 抽取结果，确认后提交为 Capture 草稿"
                        :facts="editableDraft.facts"
                        :knowledge-type="extractResult.suggestedType || 'experience'"
                    />

                    <div class="import-page__actions">
                        <el-button @click="resetPreview">重新抽取</el-button>
                        <el-button
                            type="primary"
                            :loading="submitting"
                            :disabled="!canEdit"
                            @click="runSubmit"
                        >
                            提交为 Capture 草稿
                        </el-button>
                    </div>
                </div>
            </section>
        </div>
    </div>
</template>

<script setup>
import { DocumentChecked, MagicStick, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { IMPORT_TRACK_EVENTS } from '~/config/tracker'
import { KNOWLEDGE_TYPE_LABELS } from '~/constants/knowledge'

definePageMeta({
    layout: 'app',
    ssr: false,
})

/** 页面浏览埋点 */
usePageTracker()

const router = useRouter()
const { reportEvent } = useTracker()
/** 文档导入需编辑者及以上 */
const { canEdit } = useCanEditKnowledge()

const {
    TARGET_TYPE_OPTIONS,
    MAX_CONTENT_LENGTH,
    inputMode,
    form,
    tagInput,
    extracting,
    submitting,
    extractResult,
    editableDraft,
    contentLength,
    canExtract,
    hasPreview,
    handleFileChange: onFileChange,
    addTag,
    removeTag,
    runExtract: onRunExtract,
    runSubmit: onRunSubmit,
    resetPreview,
} = useDocumentImport()

/**
 * 上传导入文件时上报埋点
 * @param {...any} args
 */
function handleFileChange(...args) {
    if (!canEdit.value) {
        ElMessage.warning('当前角色无权限导入文档')
        return
    }
    reportEvent(IMPORT_TRACK_EVENTS.UPLOAD_FILE, {
        from: 'import',
    })
    return onFileChange(...args)
}

/**
 * 触发 AI 抽取前上报埋点
 * @param {...any} args
 */
function runExtract(...args) {
    if (!canEdit.value) {
        ElMessage.warning('当前角色无权限导入文档')
        return
    }
    reportEvent(IMPORT_TRACK_EVENTS.SUBMIT, {
        stage: 'extract',
        inputMode: inputMode.value || '',
        contentLength: contentLength.value || 0,
    })
    return onRunExtract(...args)
}

/**
 * 确认提交导入结果前上报埋点
 * @param {...any} args
 */
function runSubmit(...args) {
    if (!canEdit.value) {
        ElMessage.warning('当前角色无权限导入文档')
        return
    }
    reportEvent(IMPORT_TRACK_EVENTS.SUBMIT, {
        stage: 'confirm',
        suggestedType: extractResult.value?.suggestedType || '',
    })
    return onRunSubmit(...args)
}

/** 建议知识类型中文标签 */
const suggestedTypeLabel = computed(() => {
    const suggestedType = extractResult.value?.suggestedType
    return KNOWLEDGE_TYPE_LABELS[suggestedType] || suggestedType || '经验'
})
</script>

<style scoped>
.import-page__grid {
    display: grid;
    grid-template-columns: 1fr;
    gap: 24px;
}

@media (min-width: 1024px) {
    .import-page__grid {
        grid-template-columns: 1fr 1fr;
        align-items: start;
    }
}

.import-page__panel {
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 16px;
    padding: 24px;
}

.import-page__panel--readonly {
    opacity: 0.72;
    pointer-events: none;
}

.import-page__panel-title {
    margin: 0 0 16px;
    font-size: 15px;
    font-weight: 600;
    color: #111827;
}

.import-page__context-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
}

.import-page__loading,
.import-page__skip {
    padding: 24px 0;
}

.import-page__section-title {
    margin: 0 0 8px;
    font-size: 13px;
    font-weight: 600;
    color: #374151;
}

.import-page__quality {
    margin: 16px 0;
    padding: 12px;
    background: #f9fafb;
    border-radius: 10px;
    border: 1px solid #f3f4f6;
}

.import-page__warnings {
    margin: 8px 0 0;
    padding-left: 18px;
    font-size: 12px;
    color: #d97706;
    line-height: 1.6;
}

.import-page__meta {
    margin-bottom: 16px;
}

.import-page__actions {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    margin-top: 20px;
    padding-top: 16px;
    border-top: 1px solid #f3f4f6;
}
</style>
