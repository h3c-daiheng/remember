<template>
    <StatsPanel
        title="本月用量"
        :subtitle="`${usage?.month || currentMonthLabel} · 来自 usage_daily 日聚合`"
    >
        <template #actions>
            <el-date-picker
                v-model="selectedMonth"
                type="month"
                value-format="YYYY-MM"
                placeholder="选择月份"
                size="small"
                class="stats-usage-month-picker"
                @change="handleMonthChange"
            />
        </template>

        <el-skeleton v-if="loading" :rows="3" animated />

        <template v-else-if="usage">
            <!-- Recall 配额进度 -->
            <div class="stats-usage__quota">
                <div class="stats-usage__quota-head">
                    <span class="stats-usage__quota-label">月 Recall 用量</span>
                    <span class="stats-usage__quota-value">
                        {{ usage.recallUsed ?? 0 }}
                        <span class="stats-usage__quota-total">/ {{ usage.recallQuota ?? 0 }}</span>
                    </span>
                </div>
                <el-progress
                    :percentage="recallQuotaPercent"
                    :stroke-width="12"
                    :color="recallQuotaColor"
                    :show-text="false"
                />
                <p class="stats-usage__quota-hint">
                    当前为用量展示模式，配额硬限流未启用
                </p>
            </div>

            <!-- Remember / Feedback 累计 -->
            <div class="stats-usage__metrics">
                <div class="stats-usage__metric stats-usage__metric--remember">
                    <p class="stats-usage__metric-label">Remember</p>
                    <p class="stats-usage__metric-value">{{ usage.rememberUsed ?? 0 }}</p>
                </div>
                <div class="stats-usage__metric stats-usage__metric--feedback">
                    <p class="stats-usage__metric-label">Feedback</p>
                    <p class="stats-usage__metric-value">{{ usage.feedbackUsed ?? 0 }}</p>
                </div>
            </div>

            <!-- 当月日用量迷你趋势 -->
            <div v-if="dailyChartItems.length > 0" class="stats-usage__chart">
                <div class="stats-usage__chart-grid">
                    <div class="stats-usage__chart-bars">
                        <div
                            v-for="item in dailyChartItems"
                            :key="item.date"
                            class="stats-usage__chart-bar-wrap"
                            :title="`${item.date}: Recall ${item.recallCount}`"
                        >
                            <div
                                class="stats-usage__chart-bar"
                                :style="{ height: `${dailyBarHeight(item.recallCount)}px` }"
                            />
                        </div>
                    </div>
                </div>
                <p class="stats-usage__chart-caption">当月每日 Recall 次数</p>
            </div>

            <PageEmptyState
                v-else
                title="本月暂无 Recall 调用记录"
                subtitle="Agent 开始 Recall 后，将在此展示当月每日用量迷你趋势"
                compact
                :bordered="false"
            />
        </template>
    </StatsPanel>
</template>

<script setup>
const props = defineProps({
    loading: {
        type: Boolean,
        default: false,
    },
    usage: {
        type: Object,
        default: null,
    },
})

const emit = defineEmits(['month-change'])

/** 月份选择器绑定值 */
const selectedMonth = ref('')

/** 当前月文案 */
const currentMonthLabel = computed(() => {
    const now = new Date()
    const month = String(now.getMonth() + 1).padStart(2, '0')
    return `${now.getFullYear()}-${month}`
})

watch(
    () => props.usage?.month,
    (month) => {
        if (month) {
            selectedMonth.value = month
        }
    },
    { immediate: true },
)

/** Recall 配额使用百分比 */
const recallQuotaPercent = computed(() => {
    const quota = props.usage?.recallQuota || 0
    const used = props.usage?.recallUsed || 0
    if (quota <= 0) {
        return 0
    }
    return Math.min(100, Math.round((used / quota) * 100))
})

/** 配额进度条颜色：超 80% 预警 */
const recallQuotaColor = computed(() => {
    if (recallQuotaPercent.value >= 90) {
        return '#ef4444'
    }
    if (recallQuotaPercent.value >= 80) {
        return '#f59e0b'
    }
    return '#5d65f9'
})

/** 当月日用量序列 */
const dailyChartItems = computed(() => props.usage?.dailyList || [])

/** 日柱最大值 */
const dailyMaxRecall = computed(() => {
    let peak = 1
    for (const item of dailyChartItems.value) {
        peak = Math.max(peak, item.recallCount || 0)
    }
    return peak
})

/** 日柱高度 */
function dailyBarHeight(count) {
    const value = count || 0
    if (value === 0) {
        return 2
    }
    return Math.max(4, Math.round((value / dailyMaxRecall.value) * 72))
}

/** 切换统计月份 */
function handleMonthChange(monthValue) {
    emit('month-change', monthValue || currentMonthLabel.value)
}
</script>

<style scoped>
.stats-usage-month-picker {
    width: 140px;
}

.stats-usage__quota {
    margin-bottom: 18px;
}

.stats-usage__quota-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;
}

.stats-usage__quota-label {
    font-size: 13px;
    color: #6b7280;
}

.stats-usage__quota-value {
    font-size: 14px;
    font-weight: 600;
    color: #111827;
    font-variant-numeric: tabular-nums;
}

.stats-usage__quota-total {
    font-weight: 400;
    color: #9ca3af;
}

.stats-usage__quota-hint {
    margin: 10px 0 0;
    font-size: 11px;
    color: #9ca3af;
}

.stats-usage__metrics {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
    margin-bottom: 18px;
}

.stats-usage__metric {
    padding: 14px 16px;
    border-radius: 10px;
    border: 1px solid #e8eaef;
}

.stats-usage__metric--remember {
    background: linear-gradient(145deg, #f0fdf4 0%, #fff 100%);
    border-color: #bbf7d0;
}

.stats-usage__metric--feedback {
    background: linear-gradient(145deg, #fffbeb 0%, #fff 100%);
    border-color: #fde68a;
}

.stats-usage__metric-label {
    margin: 0;
    font-size: 12px;
    color: #6b7280;
}

.stats-usage__metric-value {
    margin: 4px 0 0;
    font-size: 22px;
    font-weight: 700;
    color: #111827;
    font-variant-numeric: tabular-nums;
}

.stats-usage__chart {
    padding-top: 4px;
}

.stats-usage__chart-grid {
    padding: 12px 10px 8px;
    border-radius: 10px;
    background: linear-gradient(180deg, #f9fafb 0%, #fff 100%);
    border: 1px solid #f3f4f6;
}

.stats-usage__chart-bars {
    display: flex;
    align-items: flex-end;
    gap: 3px;
    min-width: max-content;
    height: 88px;
    overflow-x: auto;
    padding-bottom: 2px;
}

.stats-usage__chart-bar-wrap {
    display: flex;
    flex-direction: column;
    align-items: center;
    width: 10px;
    flex-shrink: 0;
}

.stats-usage__chart-bar {
    width: 8px;
    border-radius: 4px 4px 0 0;
    background: linear-gradient(180deg, #8e93fb 0%, #5d65f9 100%);
    transition: height 0.3s ease;
}

.stats-usage__chart-caption {
    margin: 10px 0 0;
    font-size: 11px;
    color: #9ca3af;
}

</style>
