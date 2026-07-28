<template>
    <!-- 知识草稿编辑页通用布局：顶栏、元信息卡与内容区 -->
    <div class="knowledge-draft-editor">
        <header class="knowledge-draft-editor__header">
            <button type="button"
                class="knowledge-draft-editor__back"
                @click="router.push(backPath)">
                <el-icon :size="16">
                    <ArrowLeft />
                </el-icon>
                <span>{{ backLabel }}</span>
            </button>

            <div class="knowledge-draft-editor__actions">
                <MemoryTraceLink :knowledge-id="knowledgeId"
                    link-style
                    label="闭环追踪" />
                <div v-if="showModify || showDelete"
                    class="knowledge-draft-editor__action-divider" />
                <el-button v-if="showModify"
                    :loading="saving"
                    @click="emit('save')">
                    保存草稿
                </el-button>
                <el-button v-if="showModify"
                    type="primary"
                    :loading="publishing"
                    @click="emit('publish')">
                    发布
                </el-button>
                <el-button v-if="showDelete"
                    type="danger"
                    plain
                    :loading="deleting"
                    @click="emit('delete')">
                    删除
                </el-button>
            </div>
        </header>

        <el-alert v-if="!showModify"
            class="mb-1"
            type="info"
            :closable="false"
            show-icon
            title="当前为只读模式"
            description="您没有编辑该草稿的权限，仅可浏览内容" />

        <section class="knowledge-draft-editor__meta"
            :class="{ 'knowledge-draft-editor__meta--readonly': !showModify }">
            <div class="knowledge-draft-editor__meta-head">
                <div class="knowledge-draft-editor__badges">
                    <span class="knowledge-draft-editor__badge knowledge-draft-editor__badge--draft">
                        草稿
                    </span>
                    <span class="knowledge-draft-editor__badge knowledge-draft-editor__badge--type">
                        {{ typeLabel }}
                    </span>
                </div>
                <p v-if="metaHint"
                    class="knowledge-draft-editor__meta-hint">
                    {{ metaHint }}
                </p>
            </div>

            <el-input v-model="editForm.title"
                :placeholder="titlePlaceholder"
                :readonly="!showModify"
                maxlength="256"
                show-word-limit
                class="knowledge-draft-editor__title" />

            <div class="knowledge-draft-editor__context">
                <div class="knowledge-draft-editor__context-head">
                    <span class="knowledge-draft-editor__context-label">召回上下文</span>
                    <span class="knowledge-draft-editor__context-desc">
                        发布后 Agent Recall 将按项目、仓库、模块筛选注入
                    </span>
                </div>
                <RecallContextFields v-model:project="editForm.project"
                    v-model:module="editForm.module"
                    v-model:repository="editForm.repository"
                    layout="context-grid"
                    project-placeholder="选择或输入项目"
                    module-placeholder="选择或输入模块"
                    repository-placeholder="选择或输入仓库" />
            </div>
        </section>

        <section class="knowledge-draft-editor__content"
            :class="{ 'knowledge-draft-editor__content--readonly': !showModify }">
            <slot />
        </section>
    </div>
</template>

<script setup>
import { ArrowLeft } from '@element-plus/icons-vue';

defineProps({
    /** 编辑表单对象，含 title / project / module / repository 等字段 */
    editForm: {
        type: Object,
        required: true,
    },
    /** 返回列表页路径 */
    backPath: {
        type: String,
        required: true,
    },
    /** 返回按钮文案 */
    backLabel: {
        type: String,
        required: true,
    },
    /** 知识类型中文标签 */
    typeLabel: {
        type: String,
        required: true,
    },
    /** 标题输入占位 */
    titlePlaceholder: {
        type: String,
        default: '请输入标题',
    },
    /** 元信息区右侧提示文案 */
    metaHint: {
        type: String,
        default: '',
    },
    /** 当前草稿 Knowledge ID，供闭环追踪入口使用 */
    knowledgeId: {
        type: [String, Number],
        required: true,
    },
    /** 保存草稿 loading */
    saving: {
        type: Boolean,
        default: false,
    },
    /** 是否展示保存/发布按钮（发布者或编辑角色） */
    showModify: {
        type: Boolean,
        default: true,
    },
    /** 发布 loading */
    publishing: {
        type: Boolean,
        default: false,
    },
    /** 是否展示删除按钮（发布者或编辑角色） */
    showDelete: {
        type: Boolean,
        default: false,
    },
    /** 删除 loading */
    deleting: {
        type: Boolean,
        default: false,
    },
})

const emit = defineEmits(['save', 'publish', 'delete'])

const router = useRouter()
</script>

<style scoped>
.knowledge-draft-editor {
    display: flex;
    flex-direction: column;
    gap: 20px;
}

.knowledge-draft-editor__header {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 12px 16px;
    padding-bottom: 4px;
}

.knowledge-draft-editor__back {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 2px;
    border: none;
    background: transparent;
    font-size: 14px;
    font-weight: 500;
    color: #6b7280;
    cursor: pointer;
    transition: color 0.15s;
}

.knowledge-draft-editor__back:hover {
    color: #4338ca;
}

.knowledge-draft-editor__actions {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 10px;
}

.knowledge-draft-editor__action-divider {
    width: 1px;
    height: 20px;
    background: #e5e7eb;
}

.knowledge-draft-editor__meta,
.knowledge-draft-editor__content {
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 16px;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.knowledge-draft-editor__meta {
    padding: 24px 28px;
}

.knowledge-draft-editor__meta--readonly,
.knowledge-draft-editor__content--readonly {
    opacity: 0.85;
    pointer-events: none;
}

.knowledge-draft-editor__meta-head {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 8px 16px;
    margin-bottom: 16px;
}

.knowledge-draft-editor__badges {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
}

.knowledge-draft-editor__badge {
    display: inline-flex;
    align-items: center;
    padding: 4px 10px;
    border-radius: 9999px;
    font-size: 12px;
    font-weight: 500;
    line-height: 1.4;
}

.knowledge-draft-editor__badge--draft {
    color: #b45309;
    background: #fff7ed;
    border: 1px solid #ffedd5;
}

.knowledge-draft-editor__badge--type {
    color: #4338ca;
    background: #eef2ff;
    border: 1px solid #e0e7ff;
}

.knowledge-draft-editor__meta-hint {
    margin: 0;
    font-size: 12px;
    color: #9ca3af;
}

.knowledge-draft-editor__title :deep(.el-input__wrapper) {
    padding: 0;
    border: none;
    background: transparent;
    box-shadow: none !important;
}

.knowledge-draft-editor__title :deep(.el-input__inner) {
    height: auto;
    padding: 0;
    font-size: 26px;
    font-weight: 600;
    line-height: 1.35;
    color: #111827;
}

.knowledge-draft-editor__title :deep(.el-input__inner::placeholder) {
    color: #d1d5db;
    font-weight: 500;
}

.knowledge-draft-editor__title :deep(.el-input__count) {
    font-size: 12px;
    color: #9ca3af;
}

.knowledge-draft-editor__context {
    margin-top: 6px;
    padding-top: 20px;
    border-top: 1px solid #f3f4f6;
}

.knowledge-draft-editor__context-head {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 6px 12px;
    margin-bottom: 14px;
}

.knowledge-draft-editor__context-label {
    font-size: 13px;
    font-weight: 600;
    color: #374151;
}

.knowledge-draft-editor__context-desc {
    font-size: 12px;
    color: #9ca3af;
}

.knowledge-draft-editor__content {
    padding: 24px 28px 28px;
}

@media (max-width: 640px) {

    .knowledge-draft-editor__meta,
    .knowledge-draft-editor__content {
        padding-left: 18px;
        padding-right: 18px;
    }

    .knowledge-draft-editor__title :deep(.el-input__inner) {
        font-size: 22px;
    }

    .knowledge-draft-editor__action-divider {
        display: none;
    }
}
</style>
