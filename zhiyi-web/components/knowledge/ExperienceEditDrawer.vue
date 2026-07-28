<template>
    <!-- 已发布经验编辑抽屉：修改标题、元信息、标签与 Fact Block 后保存 -->
    <el-drawer
        :model-value="visible"
        direction="rtl"
        size="720px"
        destroy-on-close
        :close-on-click-modal="false"
        class="experience-edit-drawer"
        @update:model-value="handleVisibleChange"
    >
        <template #header>
            <div v-if="editForm" class="experience-edit-drawer__header">
                <div class="experience-edit-drawer__header-main">
                    <span class="experience-edit-drawer__badge">编辑经验</span>
                    <h2 class="experience-edit-drawer__headline">
                        {{ editForm.title || '未命名经验' }}
                    </h2>
                </div>
                <p class="experience-edit-drawer__hint">保存后将重新建立检索索引</p>
            </div>
        </template>

        <template v-if="editForm">
            <div class="experience-edit-drawer__body">
                <!-- 基础信息：垂直表单 + 上下文 inset 面板 -->
                <section class="experience-edit-drawer__meta">
                    <div class="experience-edit-drawer__field">
                        <label class="experience-edit-drawer__field-label">标题</label>
                        <el-input
                            v-model="editForm.title"
                            placeholder="概括本次经验的核心决策"
                            maxlength="256"
                            show-word-limit
                            class="experience-edit-drawer__title-input"
                        />
                    </div>

                    <div class="experience-edit-drawer__context-panel">
                        <div class="experience-edit-drawer__context-head">
                            <span class="experience-edit-drawer__field-label">召回上下文</span>
                            <span class="experience-edit-drawer__context-hint">Agent Recall 按项目、仓库、模块筛选</span>
                        </div>
                        <RecallContextFields
                            v-model:project="editForm.project"
                            v-model:module="editForm.module"
                            v-model:repository="editForm.repository"
                            layout="context-grid"
                            size="small"
                            project-placeholder="选择或输入项目"
                            module-placeholder="选择或输入模块"
                            repository-placeholder="选择或输入仓库"
                            class="experience-edit-drawer__context-fields"
                        />
                    </div>

                    <div class="experience-edit-drawer__field">
                        <label class="experience-edit-drawer__field-label">标签</label>
                        <el-select
                            v-model="editFormTags"
                            multiple
                            filterable
                            allow-create
                            default-first-option
                            collapse-tags
                            collapse-tags-tooltip
                            size="small"
                            :max-collapse-tags="4"
                            :loading="tagOptionsLoading"
                            placeholder="输入后回车添加，便于检索召回"
                            class="experience-edit-drawer__tags-select"
                        >
                            <el-option
                                v-for="tagItem in tagOptions"
                                :key="tagItem"
                                :label="tagItem"
                                :value="tagItem"
                            />
                        </el-select>
                    </div>
                </section>

                <!-- Fact Block 编辑：支持增删，与草稿编辑一致 -->
                <section class="experience-edit-drawer__card experience-edit-drawer__card--facts">
                    <KnowledgeDraftFactSection
                        :facts="editForm.facts"
                        knowledge-type="experience"
                        section-title="经验内容"
                        section-desc="支持 Markdown，至少保留一条有效内容"
                    />
                </section>
            </div>
        </template>

        <template #footer>
            <div class="experience-edit-drawer__footer">
                <span v-if="editForm" class="experience-edit-drawer__footer-meta">
                    {{ validFactCount }} 段有效内容
                </span>
                <div class="experience-edit-drawer__footer-actions">
                    <el-button @click="handleVisibleChange(false)">取消</el-button>
                    <el-button type="primary" :loading="saving" @click="handleSave">
                        保存修改
                    </el-button>
                </div>
            </div>
        </template>
    </el-drawer>
</template>

<script setup>
import { ElMessage } from 'element-plus'

const props = defineProps({
    /** 抽屉是否可见 */
    visible: {
        type: Boolean,
        default: false,
    },
    /** 待编辑的经验副本（由父组件 cloneKnowledgeForEdit 生成） */
    editForm: {
        type: Object,
        default: null,
    },
    /** 保存中状态 */
    saving: {
        type: Boolean,
        default: false,
    },
})

const emit = defineEmits(['update:visible', 'save'])

const { optionsLoading: tagOptionsLoading, tagOptions } = useWorkspaceContextOptions()

/** 标签字段：确保始终为数组，便于 el-select 双向绑定 */
const editFormTags = computed({
    get() {
        if (!props.editForm) {
            return []
        }
        if (!Array.isArray(props.editForm.tags)) {
            props.editForm.tags = []
        }
        return props.editForm.tags
    },
    set(value) {
        if (props.editForm) {
            props.editForm.tags = value
        }
    },
})

/** 有效 Fact 段数，用于底部状态提示 */
const validFactCount = computed(() =>
    (props.editForm?.facts || []).filter((fact) => fact?.text?.trim()).length,
)

/** 同步抽屉开关 */
function handleVisibleChange(value) {
    emit('update:visible', value)
}

/** 校验并提交保存 */
function handleSave() {
    if (!props.editForm?.title?.trim()) {
        ElMessage.warning('请填写经验标题')
        return
    }
    const validFacts = (props.editForm.facts || []).filter((fact) => fact?.text?.trim())
    if (validFacts.length === 0) {
        ElMessage.warning('请至少保留一条有效的 Fact Block 内容')
        return
    }
    emit('save', props.editForm)
}
</script>

<style>
.experience-edit-drawer .el-drawer__header {
    margin-bottom: 0;
    padding: 16px 20px 12px;
    border-bottom: 1px solid #f3f4f6;
}

.experience-edit-drawer .el-drawer__body {
    padding: 0;
    overflow-x: hidden;
}

.experience-edit-drawer .el-drawer__footer {
    padding: 12px 20px;
    border-top: 1px solid #f3f4f6;
    background: #fafafa;
}
</style>

<style scoped>
.experience-edit-drawer__header {
    padding-right: 20px;
    min-width: 0;
}

.experience-edit-drawer__header-main {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
}

.experience-edit-drawer__badge {
    flex-shrink: 0;
    padding: 2px 8px;
    border-radius: 9999px;
    font-size: 11px;
    font-weight: 500;
    color: #5d65f9;
    background: #eff0fe;
}

.experience-edit-drawer__headline {
    margin: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 15px;
    font-weight: 600;
    color: #111827;
}

.experience-edit-drawer__hint {
    margin: 6px 0 0;
    font-size: 12px;
    color: #9ca3af;
    line-height: 1.4;
}

.experience-edit-drawer__body {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 14px 18px 18px;
}

/* 基础信息卡片 */
.experience-edit-drawer__meta {
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 12px 14px;
    border: 1px solid #e5e7eb;
    border-radius: 12px;
    background: #fff;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.experience-edit-drawer__field {
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.experience-edit-drawer__field-label {
    font-size: 13px;
    font-weight: 600;
    color: #374151;
    letter-spacing: 0.01em;
}

/* 标题：突出层级，无边框干扰 */
.experience-edit-drawer__title-input :deep(.el-input__wrapper) {
    padding: 7px 12px;
    border-radius: 8px;
    background: #f9fafb;
    box-shadow: none !important;
    transition: background 0.15s, box-shadow 0.15s;
}

.experience-edit-drawer__title-input :deep(.el-input__wrapper:hover) {
    background: #f3f4f6;
}

.experience-edit-drawer__title-input :deep(.el-input__wrapper.is-focus) {
    background: #fff;
    box-shadow: 0 0 0 2px rgba(93, 101, 249, 0.15) !important;
}

.experience-edit-drawer__title-input :deep(.el-input__inner) {
    font-size: 15px;
    font-weight: 600;
    color: #111827;
    line-height: 1.45;
}

.experience-edit-drawer__title-input :deep(.el-input__count) {
    font-size: 11px;
    color: #9ca3af;
    background: transparent;
}

/* 召回上下文：inset 面板分组 */
.experience-edit-drawer__context-panel {
    padding: 8px 10px 10px;
    border-radius: 8px;
    background: linear-gradient(180deg, #f8fafc 0%, #f9fafb 100%);
    border: 1px solid #eef2f6;
}

.experience-edit-drawer__context-head {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    justify-content: space-between;
    gap: 4px 12px;
    margin-bottom: 8px;
}

.experience-edit-drawer__context-hint {
    font-size: 12px;
    color: #9ca3af;
    line-height: 1.4;
}

.experience-edit-drawer__context-fields.recall-context-fields--context-grid {
    gap: 6px 10px;
}

.experience-edit-drawer__context-fields :deep(.recall-context-fields__item) {
    margin-bottom: 0 !important;
}

.experience-edit-drawer__context-fields :deep(.el-select__wrapper) {
    border-radius: 6px;
    background: #fff;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.experience-edit-drawer__context-fields :deep(.el-select__wrapper.is-focused) {
    box-shadow: 0 0 0 2px rgba(93, 101, 249, 0.12);
}

/* 标签选择器 */
.experience-edit-drawer__tags-select {
    width: 100%;
}

.experience-edit-drawer__tags-select :deep(.el-select__wrapper) {
    min-height: 28px;
    padding: 1px 8px;
    border-radius: 6px;
    background: #f9fafb;
    box-shadow: none;
    font-size: 12px;
    transition: background 0.15s, box-shadow 0.15s;
}

.experience-edit-drawer__tags-select :deep(.el-select__wrapper:hover) {
    background: #f3f4f6;
}

.experience-edit-drawer__tags-select :deep(.el-select__wrapper.is-focused) {
    background: #fff;
    box-shadow: 0 0 0 2px rgba(93, 101, 249, 0.15);
}

.experience-edit-drawer__tags-select :deep(.el-tag) {
    border: none;
    border-radius: 4px;
    background: #eff0fe;
    color: #4338ca;
    font-size: 11px;
    height: 20px;
    padding: 0 6px;
}

.experience-edit-drawer__tags-select :deep(.el-tag .el-tag__close) {
    color: #6366f1;
}

.experience-edit-drawer__card {
    padding: 14px 18px;
    border: 1px solid #e5e7eb;
    border-radius: 12px;
    background: #fff;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.experience-edit-drawer__card--facts {
    padding: 14px 18px 12px;
}

/* 抽屉内 Fact 编辑区紧凑化 */
.experience-edit-drawer__card--facts :deep(.knowledge-draft-fact-section__head) {
    margin-bottom: 8px;
    padding-bottom: 8px;
}

.experience-edit-drawer__card--facts :deep(.knowledge-draft-fact-section__title) {
    font-size: 13px;
}

.experience-edit-drawer__card--facts :deep(.knowledge-draft-fact-section__desc) {
    margin-top: 2px;
    font-size: 11px;
}

.experience-edit-drawer__card--facts :deep(.knowledge-draft-fact-section__toolbar) {
    margin-top: 12px;
    padding-top: 12px;
}

.experience-edit-drawer__card--facts :deep(.fact-block-editor) {
    gap: 8px;
}

.experience-edit-drawer__card--facts :deep(.fact-block-editor__body) {
    padding: 10px 12px 8px;
}

.experience-edit-drawer__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    width: 100%;
}

.experience-edit-drawer__footer-meta {
    font-size: 12px;
    color: #9ca3af;
}

.experience-edit-drawer__footer-actions {
    display: flex;
    gap: 10px;
    margin-left: auto;
}
</style>
