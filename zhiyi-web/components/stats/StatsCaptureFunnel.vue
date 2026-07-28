<template>
    <StatsPanel
        title="Capture 闭环漏斗"
        :subtitle="`近 ${funnel?.statsPeriodDays || 30} 天 ${STATS_FUNNEL_STEP_LABELS.remember} → 发布转化`"
    >
        <el-skeleton v-if="loading" :rows="3" animated />

        <template v-else-if="funnel">
            <div class="stats-funnel">
                <div
                    v-for="(step, index) in funnelSteps"
                    :key="step.key"
                    class="stats-funnel__step-wrap"
                >
                    <div class="stats-funnel__step" :class="`stats-funnel__step--${index}`">
                        <p class="stats-funnel__step-label">{{ step.label }}</p>
                        <p class="stats-funnel__step-value">{{ step.value }}</p>
                    </div>
                    <div
                        v-if="index < funnelSteps.length - 1"
                        class="stats-funnel__arrow"
                        aria-hidden="true"
                    >
                        <el-icon :size="14"><ArrowRight /></el-icon>
                    </div>
                </div>
            </div>

            <div class="stats-funnel__rates">
                <div class="stats-funnel__rate">
                    <span class="stats-funnel__rate-label">{{ STATS_FUNNEL_RATE_LABELS.rememberToDraft }}</span>
                    <span class="stats-funnel__rate-value">{{ formatRatePercent(funnel.draftFromRememberRate) }}</span>
                </div>
                <div class="stats-funnel__rate-divider" />
                <div class="stats-funnel__rate">
                    <span class="stats-funnel__rate-label">{{ STATS_FUNNEL_RATE_LABELS.reviewedToApproved }}</span>
                    <span class="stats-funnel__rate-value">{{ formatRatePercent(funnel.approvedFromReviewedRate) }}</span>
                </div>
            </div>
        </template>
    </StatsPanel>
</template>

<script setup>
import { ArrowRight } from '@element-plus/icons-vue'
import { formatRatePercent } from '~/composables/useWorkspaceStats'
import { STATS_FUNNEL_RATE_LABELS, STATS_FUNNEL_STEP_LABELS } from '~/constants/terminology'

const props = defineProps({
    loading: {
        type: Boolean,
        default: false,
    },
    funnel: {
        type: Object,
        default: null,
    },
})

/** 漏斗各阶段展示配置 */
const funnelSteps = computed(() => {
    const data = props.funnel || {}
    return [
        { key: 'remember', label: STATS_FUNNEL_STEP_LABELS.remember, value: data.rememberCount ?? 0 },
        { key: 'draft', label: STATS_FUNNEL_STEP_LABELS.draft, value: data.draftCount ?? 0 },
        { key: 'reviewed', label: STATS_FUNNEL_STEP_LABELS.reviewed, value: data.reviewedCount ?? 0 },
        { key: 'approved', label: STATS_FUNNEL_STEP_LABELS.approved, value: data.approvedCount ?? 0 },
        { key: 'published', label: STATS_FUNNEL_STEP_LABELS.published, value: data.publishedCount ?? 0 },
    ]
})
</script>

<style scoped>
.stats-funnel {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
    margin-bottom: 16px;
}

.stats-funnel__step-wrap {
    display: flex;
    align-items: center;
    gap: 6px;
}

.stats-funnel__step {
    min-width: 88px;
    padding: 12px 14px;
    border-radius: 10px;
    text-align: center;
    border: 1px solid #e8eaef;
    background: #f9fafb;
}

.stats-funnel__step--0 {
    background: linear-gradient(145deg, #eff0fe 0%, #fff 100%);
    border-color: #ced1fd;
}

.stats-funnel__step--1 {
    background: linear-gradient(145deg, #f0fdf4 0%, #fff 100%);
    border-color: #bbf7d0;
}

.stats-funnel__step--2 {
    background: linear-gradient(145deg, #fffbeb 0%, #fff 100%);
    border-color: #fde68a;
}

.stats-funnel__step--3 {
    background: linear-gradient(145deg, #fdf4ff 0%, #fff 100%);
    border-color: #e9d5ff;
}

.stats-funnel__step--4 {
    background: linear-gradient(145deg, #ecfdf5 0%, #fff 100%);
    border-color: #6ee7b7;
}

.stats-funnel__step-label {
    margin: 0;
    font-size: 11px;
    color: #6b7280;
}

.stats-funnel__step-value {
    margin: 4px 0 0;
    font-size: 22px;
    font-weight: 700;
    color: #111827;
    font-variant-numeric: tabular-nums;
}

.stats-funnel__arrow {
    display: none;
    color: #d1d5db;
}

@media (min-width: 640px) {
    .stats-funnel__arrow {
        display: flex;
        align-items: center;
    }
}

.stats-funnel__rates {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 12px;
    padding: 12px 14px;
    border-radius: 10px;
    background: #f9fafb;
    border: 1px solid #f3f4f6;
}

.stats-funnel__rate {
    display: flex;
    align-items: center;
    gap: 8px;
}

.stats-funnel__rate-label {
    font-size: 12px;
    color: #6b7280;
}

.stats-funnel__rate-value {
    font-size: 13px;
    font-weight: 600;
    color: #111827;
    font-variant-numeric: tabular-nums;
}

.stats-funnel__rate-divider {
    display: none;
    width: 1px;
    height: 14px;
    background: #e5e7eb;
}

@media (min-width: 640px) {
    .stats-funnel__rate-divider {
        display: block;
    }
}
</style>
