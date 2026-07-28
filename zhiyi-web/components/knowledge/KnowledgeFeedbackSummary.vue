<template>
    <div class="feedback-summary" :class="{ 'feedback-summary--compact': compact }">
        <header class="feedback-summary__head">
            <h3 class="feedback-summary__title">
                <span class="feedback-summary__icon">
                    <el-icon :size="13"><ChatDotRound /></el-icon>
                </span>
                <span class="feedback-summary__title-text">召回反馈</span>
                <span v-if="summary && summary.totalCount > 0" class="feedback-summary__count">{{ summary.totalCount }}</span>
            </h3>
        </header>

        <el-skeleton v-if="loading" :rows="compact ? 2 : 3" animated />

        <div v-else-if="!summary || summary.totalCount === 0" class="related-panel__empty">
            <p class="related-panel__empty-text">暂无 Feedback 记录</p>
            <p class="related-panel__empty-hint">
                Agent 在 Recall 后提交 helpful / outdated 等反馈后将在此展示
            </p>
        </div>

        <template v-else>
            <div class="feedback-summary__grid">
                <div
                    v-for="item in feedbackItems"
                    :key="item.key"
                    class="feedback-summary__item"
                    :class="item.highlight ? 'feedback-summary__item--highlight' : ''"
                >
                    <p class="feedback-summary__item-label">{{ item.label }}</p>
                    <p class="feedback-summary__item-count">{{ item.count }}</p>
                </div>
            </div>
            <p class="feedback-summary__footer">
                有效反馈占比：{{ formatRatePercent(summary.helpfulRate) }}
                · 共 {{ summary.totalCount }} 条
            </p>
        </template>
    </div>
</template>

<script setup>
import { ChatDotRound } from '@element-plus/icons-vue'
import { formatRatePercent } from '~/composables/useWorkspaceStats'

const props = defineProps({
    loading: {
        type: Boolean,
        default: false,
    },
    summary: {
        type: Object,
        default: null,
    },
    /** 侧边栏紧凑模式 */
    compact: {
        type: Boolean,
        default: false,
    },
})

/** 各 Feedback 类型计数展示 */
const feedbackItems = computed(() => {
    const data = props.summary || {}
    return [
        { key: 'helpful', label: 'helpful', count: data.helpfulCount ?? 0, highlight: true },
        { key: 'used', label: 'used', count: data.usedCount ?? 0, highlight: true },
        { key: 'notHelpful', label: 'not_helpful', count: data.notHelpfulCount ?? 0 },
        { key: 'outdated', label: 'outdated', count: data.outdatedCount ?? 0 },
        { key: 'wrong', label: 'wrong', count: data.wrongCount ?? 0 },
    ]
})
</script>

<style scoped>
.feedback-summary {
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 10px;
    padding: 12px 14px;
}

.feedback-summary--compact {
    padding: 10px 12px;
}

.feedback-summary__head {
    margin-bottom: 10px;
}

.feedback-summary__title {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0;
    font-size: 13px;
    font-weight: 600;
    color: #111827;
}

.feedback-summary__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    border-radius: 6px;
    color: #059669;
    background: rgba(5, 150, 105, 0.1);
    flex-shrink: 0;
}

.feedback-summary__title-text {
    flex: 1;
    min-width: 0;
}

.feedback-summary__count {
    flex-shrink: 0;
    min-width: 18px;
    padding: 0 6px;
    border-radius: 9999px;
    font-size: 11px;
    font-weight: 500;
    line-height: 18px;
    text-align: center;
    color: #059669;
    background: rgba(5, 150, 105, 0.08);
}

.feedback-summary__grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
}

.feedback-summary--compact .feedback-summary__grid {
    grid-template-columns: repeat(2, 1fr);
}

.feedback-summary__item {
    padding: 6px 8px;
    border-radius: 6px;
    border: 1px solid #f3f4f6;
    background: #fafafa;
}

.feedback-summary__item--highlight {
    border-color: rgba(93, 101, 249, 0.15);
    background: rgba(93, 101, 249, 0.04);
}

.feedback-summary__item-label {
    margin: 0;
    font-size: 11px;
    color: #9ca3af;
    text-transform: lowercase;
}

.feedback-summary__item-count {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: #111827;
    font-variant-numeric: tabular-nums;
}

.feedback-summary__item--highlight .feedback-summary__item-count {
    color: #5d65f9;
}

.feedback-summary__footer {
    margin: 8px 0 0;
    font-size: 12px;
    color: #9ca3af;
    line-height: 1.5;
}
</style>
