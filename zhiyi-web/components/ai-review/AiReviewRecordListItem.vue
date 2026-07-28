<template>
    <!-- AI 审查记录列表项：点击打开详情抽屉 -->
    <div
        class="ai-review-record-card group"
        role="button"
        tabindex="0"
        @click="emit('open', record)"
        @keyup.enter="emit('open', record)"
    >
        <div class="ai-review-record-card__accent" :class="`ai-review-record-card__accent--${accentTone}`" />
        <div class="ai-review-record-card__body">
            <div class="flex items-start gap-3">
                <div class="flex-1 min-w-0">
                    <div class="flex items-start gap-3">
                        <h2 class="flex-1 min-w-0 text-base font-semibold text-gray-900 leading-snug group-hover:text-primary transition-colors line-clamp-2">
                            {{ record.draftTitle || '未命名草稿' }}
                        </h2>
                        <div class="shrink-0 flex items-center flex-wrap justify-end gap-1.5">
                            <span
                                v-if="recordBadge"
                                class="ai-review-record-card__badge"
                                :class="`ai-review-record-card__badge--${recordBadge.tone}`"
                            >
                                {{ recordBadge.label }}
                            </span>
                            <span class="ai-review-record-card__status">
                                {{ statusLabel }}
                            </span>
                        </div>
                    </div>

                    <div class="ai-review-record-card__meta">
                        <span>#{{ record.id }} · 草稿 #{{ record.draftId }}</span>
                        <span v-if="record.confidence != null">
                            置信度 {{ Math.round((record.confidence || 0) * 100) }}%
                        </span>
                        <span v-if="record.latencyMs != null">
                            耗时 {{ record.latencyMs }}ms
                        </span>
                        <span v-if="record.executed">已自动执行</span>
                        <span v-if="record.similarHit" class="text-amber-600">相似命中</span>
                    </div>

                    <p
                        v-if="record.errorMessage"
                        class="mt-2 text-xs text-gray-500 line-clamp-2"
                    >
                        {{ record.errorMessage }}
                    </p>
                </div>

                <div class="shrink-0 flex flex-col items-end justify-between self-stretch py-0.5">
                    <el-icon :size="14" class="ai-review-record-card__arrow">
                        <ArrowRight />
                    </el-icon>
                    <p v-if="createTimeText" class="text-xs text-gray-400 mt-auto whitespace-nowrap">
                        {{ createTimeText }}
                    </p>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ArrowRight } from '@element-plus/icons-vue'
import {
    AI_REVIEW_STATUS,
    formatAiReviewStatus,
    resolveAiReviewRecordBadge,
} from '~/utils/aiReview'
import { formatDateTime } from '~/utils/knowledge'

const props = defineProps({
    record: {
        type: Object,
        required: true,
    },
})

const emit = defineEmits(['open'])

/** 决策/结果徽标 */
const recordBadge = computed(() => resolveAiReviewRecordBadge(props.record))

/** 运行状态文案 */
const statusLabel = computed(() => formatAiReviewStatus(props.record?.status))

/** 创建时间 */
const createTimeText = computed(() => formatDateTime(props.record?.createTime) || '')

/** 左侧色条：按徽标语义区分 */
const accentTone = computed(() => {
    if (recordBadge.value?.tone) {
        return recordBadge.value.tone
    }
    const status = props.record?.status
    if (status === AI_REVIEW_STATUS.QUEUED || status === AI_REVIEW_STATUS.RUNNING) {
        return 'processing'
    }
    if (status === AI_REVIEW_STATUS.FAILED) {
        return 'danger'
    }
    return 'neutral'
})
</script>

<style scoped>
.ai-review-record-card {
    position: relative;
    display: flex;
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 12px;
    overflow: hidden;
    transition: border-color 0.2s, box-shadow 0.2s;
    cursor: pointer;
}

.ai-review-record-card:hover {
    border-color: #c7d2fe;
    box-shadow: 0 4px 14px rgba(15, 23, 42, 0.05);
}

.ai-review-record-card__accent {
    width: 3px;
    flex-shrink: 0;
    background: #e5e7eb;
}

.ai-review-record-card__accent--processing {
    background: #3b82f6;
}

.ai-review-record-card__accent--warning {
    background: #f59e0b;
}

.ai-review-record-card__accent--danger {
    background: #ef4444;
}

.ai-review-record-card__accent--success {
    background: #10b981;
}

.ai-review-record-card__accent--neutral {
    background: #e5e7eb;
}

.ai-review-record-card__body {
    flex: 1;
    min-width: 0;
    padding: 16px 18px 16px 16px;
}

.ai-review-record-card__meta {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px 14px;
    margin-top: 10px;
    font-size: 12px;
    color: #6b7280;
}

.ai-review-record-card__badge {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 12px;
    font-weight: 500;
    line-height: 1.4;
}

.ai-review-record-card__badge--processing {
    color: #1d4ed8;
    background: #eff6ff;
}

.ai-review-record-card__badge--warning {
    color: #b45309;
    background: #fffbeb;
}

.ai-review-record-card__badge--danger {
    color: #b91c1c;
    background: #fef2f2;
}

.ai-review-record-card__badge--success {
    color: #047857;
    background: #ecfdf5;
}

.ai-review-record-card__status {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 12px;
    color: #6b7280;
    background: #f3f4f6;
}

.ai-review-record-card__arrow {
    color: #9ca3af;
    transition: color 0.2s, transform 0.2s;
}

.group:hover .ai-review-record-card__arrow {
    color: var(--el-color-primary);
    transform: translateX(2px);
}
</style>
