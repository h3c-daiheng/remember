<template>
    <!-- 草稿列表项：左侧内容区 + 右侧居中箭头，点击后在抽屉中打开详情审核 -->
    <div
        class="capture-draft-card group"
        role="button"
        tabindex="0"
        @click="emit('open', draft)"
        @keyup.enter="emit('open', draft)"
    >
        <div class="capture-draft-card__body flex items-stretch gap-3">
            <div class="capture-draft-card__main flex-1 min-w-0">
                <!-- 标题行：主标题 + 右上角类型/状态 -->
                <div class="capture-draft-card__title-row flex items-start gap-3">
                    <h2 class="flex-1 min-w-0 text-base font-semibold text-gray-900 leading-snug group-hover:text-primary transition-colors line-clamp-2">
                        {{ draft.draftContent?.title || '未命名草稿' }}
                    </h2>
                    <div class="capture-draft-card__badges shrink-0 flex items-center flex-wrap justify-end gap-1.5">
                        <span
                            v-if="submittedTypeLabel"
                            class="capture-draft-card__type-badge"
                            :class="typeBadgeClass"
                        >
                            {{ submittedTypeLabel }}
                        </span>
                        <span
                            v-if="aiReviewBadge"
                            class="capture-draft-card__ai-badge"
                            :class="`capture-draft-card__ai-badge--${aiReviewBadge.tone}`"
                        >
                            {{ aiReviewBadge.label }}
                        </span>
                        <span class="capture-draft-card__status-badge">
                            <el-icon :size="12"><Clock /></el-icon>
                            待确认
                        </span>
                    </div>
                </div>

                <!-- 元信息：项目/仓库、内容段数 -->
                <div
                    v-if="hasMeta || contentSectionCount > 0"
                    class="flex items-center flex-wrap gap-x-3 gap-y-2 mt-2.5"
                >
                    <KnowledgeMeta
                        v-if="hasMeta"
                        :knowledge="draft.draftContent"
                        variant="pills"
                        class="min-w-0"
                    />

                    <span
                        v-if="contentSectionCount > 0"
                        class="capture-draft-card__section-chip shrink-0"
                    >
                        {{ contentSectionCount }} 段内容
                    </span>
                </div>

                <!-- 底栏：标签靠左，头像/昵称/时间靠右，与记忆卡片一致 -->
                <div class="capture-draft-card__footer">
                    <div
                        v-if="draft.draftContent?.tags?.length"
                        class="capture-draft-card__tags"
                    >
                        <span
                            v-for="tag in draft.draftContent.tags"
                            :key="tag"
                            class="capture-draft-card__tag"
                        >
                            {{ tag }}
                        </span>
                    </div>
                    <KnowledgeSubmitter
                        :knowledge="submitterKnowledge"
                        size="compact"
                        :time-text="createTimeText"
                    />
                </div>
            </div>

            <!-- 进入箭头：卡片右侧垂直居中 -->
            <div class="capture-draft-card__arrow-wrap shrink-0 flex items-center">
                <el-icon
                    :size="14"
                    class="capture-draft-card__arrow"
                >
                    <ArrowRight />
                </el-icon>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ArrowRight, Clock } from '@element-plus/icons-vue'
import { KNOWLEDGE_TYPE_LABELS, KNOWLEDGE_TYPES } from '~/constants/knowledge'
import { resolveAiReviewBadge } from '~/utils/aiReview'
import { buildCaptureDraftSubmitterKnowledge, formatDateTime } from '~/utils/knowledge'

const props = defineProps({
    draft: {
        type: Object,
        required: true,
    },
})

const emit = defineEmits(['open'])

/** 提交人展示对象，复用 KnowledgeSubmitter 组件 */
const submitterKnowledge = computed(() => buildCaptureDraftSubmitterKnowledge(props.draft))

/** Agent 提交时声明的知识类型 */
const submittedKnowledgeType = computed(() =>
    props.draft?.draftContent?.submittedKnowledgeType || KNOWLEDGE_TYPES.EXPERIENCE,
)

/** Agent 提交时声明的知识类型标签 */
const submittedTypeLabel = computed(() =>
    KNOWLEDGE_TYPE_LABELS[submittedKnowledgeType.value] || submittedKnowledgeType.value,
)

/** 按声明类型区分类型徽章配色，便于列表快速扫读 */
const typeBadgeClass = computed(() => {
    const typeClassMap = {
        [KNOWLEDGE_TYPES.EXPERIENCE]: 'capture-draft-card__type-badge--experience',
        [KNOWLEDGE_TYPES.RULE]: 'capture-draft-card__type-badge--rule',
        [KNOWLEDGE_TYPES.WORKFLOW]: 'capture-draft-card__type-badge--workflow',
        [KNOWLEDGE_TYPES.DECISION]: 'capture-draft-card__type-badge--decision',
    }
    return typeClassMap[submittedKnowledgeType.value] || typeClassMap[KNOWLEDGE_TYPES.EXPERIENCE]
})

/** 是否存在项目/模块/仓库等元信息 */
const hasMeta = computed(() => {
    const content = props.draft?.draftContent
    return Boolean(content?.project || content?.module || content?.repository)
})

/** 草稿内容段数（Fact Block 数量），用于列表快速预览 */
const contentSectionCount = computed(() => props.draft?.draftContent?.facts?.length || 0)

/** 底栏归属展示的创建时间 */
const createTimeText = computed(() => formatDateTime(props.draft?.createTime) || '')

/** AI Review 状态徽标 */
const aiReviewBadge = computed(() => resolveAiReviewBadge(props.draft))
</script>

<style scoped>
.capture-draft-card {
    position: relative;
    overflow: hidden;
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 12px;
    padding: 16px 20px;
    transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
    cursor: pointer;
}

.capture-draft-card:hover {
    border-color: rgba(93, 101, 249, 0.35);
    box-shadow: 0 4px 16px rgba(93, 101, 249, 0.08);
    transform: translateY(-1px);
}

.capture-draft-card::before {
    content: '';
    position: absolute;
    left: 0;
    top: 12px;
    bottom: 12px;
    width: 3px;
    border-radius: 0 3px 3px 0;
    background: transparent;
    transition: background 0.2s;
}

.capture-draft-card:hover::before {
    background: #f59e0b;
}

.capture-draft-card__type-badge {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    border-radius: 9999px;
    font-size: 11px;
    font-weight: 500;
    line-height: 18px;
}

.capture-draft-card__type-badge--experience {
    color: #2563eb;
    background: #eff6ff;
}

.capture-draft-card__type-badge--rule {
    color: #7c3aed;
    background: #f5f3ff;
}

.capture-draft-card__type-badge--workflow {
    color: #4338ca;
    background: #eef2ff;
}

.capture-draft-card__type-badge--decision {
    color: #0d9488;
    background: #f0fdfa;
}

.capture-draft-card__ai-badge {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    border-radius: 9999px;
    font-size: 11px;
    font-weight: 500;
    line-height: 18px;
}

.capture-draft-card__ai-badge--processing {
    color: #1d4ed8;
    background: #dbeafe;
}

.capture-draft-card__ai-badge--warning {
    color: #b45309;
    background: #fef3c7;
}

.capture-draft-card__ai-badge--danger {
    color: #b91c1c;
    background: #fee2e2;
}

.capture-draft-card__ai-badge--success {
    color: #047857;
    background: #d1fae5;
}

.capture-draft-card__status-badge {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 2px 8px;
    border-radius: 9999px;
    font-size: 11px;
    font-weight: 500;
    line-height: 18px;
    color: #d97706;
    background: #fffbeb;
}

.capture-draft-card__badges {
    max-width: 140px;
}

/* 底栏：标签与归属信息同一行，标签可换行、归属贴右 */
.capture-draft-card__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-top: 12px;
    min-width: 0;
}

.capture-draft-card__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    min-width: 0;
    flex: 1;
}

.capture-draft-card__footer :deep(.knowledge-submitter) {
    margin-top: 0;
    margin-left: auto;
    flex-shrink: 0;
}

.capture-draft-card__arrow {
    color: #d1d5db;
    transition: color 0.2s, transform 0.2s;
}

.capture-draft-card:hover .capture-draft-card__arrow {
    color: #5d65f9;
    transform: translateX(2px);
}

.capture-draft-card__section-chip {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    border-radius: 6px;
    font-size: 12px;
    font-weight: 500;
    line-height: 18px;
    color: #4b5563;
    background: #f3f4f6;
}

.capture-draft-card:hover .capture-draft-card__section-chip {
    color: #4338ca;
    background: #eef2ff;
}

.capture-draft-card__tag {
    display: inline-block;
    padding: 2px 10px;
    border-radius: 9999px;
    font-size: 12px;
    line-height: 20px;
    color: #6b7280;
    background: #f3f4f6;
    transition: background 0.15s, color 0.15s;
}

.capture-draft-card:hover .capture-draft-card__tag {
    background: #fffbeb;
    color: #d97706;
}
</style>
