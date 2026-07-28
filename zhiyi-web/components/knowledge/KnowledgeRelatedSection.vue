<template>
    <section :class="compact ? 'related-section related-section--compact' : 'related-section'">
        <header v-if="!compact" class="related-section__head">
            <h2 class="related-section__title">相关内容</h2>
        </header>

        <div :class="compact ? 'related-section__stack' : 'related-section__grid'">
            <KnowledgeFeedbackSummary
                v-if="knowledgeId"
                :loading="feedbackLoading"
                :summary="feedbackSummary"
                :compact="compact"
                :class="compact ? '' : 'md:col-span-2'"
            />
            <KnowledgeRelatedExperience
                v-if="knowledgeId"
                :knowledge-id="knowledgeId"
                :compact="compact"
                :class="compact ? '' : 'md:col-span-2'"
            />
            <KnowledgeTimelineSection
                v-if="knowledgeId"
                :knowledge-id="knowledgeId"
                :compact="compact"
                :class="compact ? '' : 'md:col-span-2'"
            />
            <KnowledgeRelatedDecision
                v-if="knowledgeId"
                :knowledge-id="knowledgeId"
                :knowledge-type="knowledgeType"
                :compact="compact"
                :class="compact ? '' : 'md:col-span-2'"
            />
            <KnowledgeRelatedPlaceholder
                v-for="item in pendingPlaceholders"
                :key="item.key"
                :title="item.title"
                :description="item.description"
                :phase="item.phase"
                :compact="compact"
            />
        </div>
    </section>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { KNOWLEDGE_RELATED_PLACEHOLDERS } from '~/constants/placeholders'
import { fetchKnowledgeFeedbackSummary } from '~/services/knowledge.service'

const props = defineProps({
    /** 当前知识 ID，用于加载 Feedback 统计 */
    knowledgeId: {
        type: [String, Number],
        default: null,
    },
    /** 当前知识类型，供关联决策空状态提示使用 */
    knowledgeType: {
        type: String,
        default: 'experience',
    },
    /** 侧边栏紧凑模式 */
    compact: {
        type: Boolean,
        default: false,
    },
})

/** 尚未落地的关联内容占位（feedback、相关经验、时间线、关联决策已实现） */
const pendingPlaceholders = KNOWLEDGE_RELATED_PLACEHOLDERS.filter(
    (item) => item.key !== 'feedback'
        && item.key !== 'relatedExperience'
        && item.key !== 'timeline'
        && item.key !== 'relatedDecision',
)

const feedbackLoading = ref(false)
const feedbackSummary = ref(null)

/** 加载当前经验的 Feedback 统计 */
async function loadFeedbackSummary(options = {}) {
    if (!props.knowledgeId) {
        feedbackSummary.value = null
        return
    }
    if (!options.silent) {
        feedbackLoading.value = true
    }
    try {
        feedbackSummary.value = await fetchKnowledgeFeedbackSummary(props.knowledgeId)
    } catch (error) {
        if (!options.silent) {
            ElMessage.error(error.message || '加载反馈统计失败')
        }
    } finally {
        if (!options.silent) {
            feedbackLoading.value = false
        }
    }
}

watch(
    () => props.knowledgeId,
    () => {
        loadFeedbackSummary()
    },
    { immediate: true },
)
</script>

<style scoped>
.related-section__head {
    margin-bottom: 16px;
}

.related-section__title {
    font-size: 15px;
    font-weight: 600;
    color: #111827;
}

.related-section__grid {
    display: grid;
    grid-template-columns: 1fr;
    gap: 16px;
}

@media (min-width: 768px) {
    .related-section__grid {
        grid-template-columns: repeat(2, 1fr);
    }
}

.related-section__stack {
    display: flex;
    flex-direction: column;
    gap: 12px;
}
</style>

<style>
/* 侧边栏关联区块统一空状态样式 */
.related-panel__empty {
    padding: 14px 10px;
    border-radius: 8px;
    border: 1px dashed #e5e7eb;
    background: #f9fafb;
    text-align: center;
}

.related-panel__empty-text {
    margin: 0;
    font-size: 12px;
    font-weight: 500;
    color: #6b7280;
}

.related-panel__empty-hint {
    margin: 4px auto 0;
    max-width: 220px;
    font-size: 10px;
    color: #9ca3af;
    line-height: 1.5;
}
</style>
