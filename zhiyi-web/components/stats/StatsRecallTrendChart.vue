<template>
    <StatsPanel title="近 30 天调用趋势" subtitle="Recall、Remember 与 helpful 反馈">
        <template #actions>
            <div class="stats-trend__legend">
                <span class="stats-trend__legend-item">
                    <span class="stats-trend__legend-dot stats-trend__legend-dot--recall" />
                    Recall
                </span>
                <span class="stats-trend__legend-item">
                    <span class="stats-trend__legend-dot stats-trend__legend-dot--remember" />
                    Remember
                </span>
                <span class="stats-trend__legend-item">
                    <span class="stats-trend__legend-dot stats-trend__legend-dot--helpful" />
                    helpful
                </span>
            </div>
        </template>

        <div v-if="loading" class="stats-trend__loading">
            <el-skeleton :rows="4" animated />
        </div>

        <PageEmptyState
            v-else-if="chartItems.length === 0"
            title="暂无趋势数据"
            subtitle="完成首次 Agent 调用后，将在此展示每日 Recall、Remember 与 helpful 变化"
            :tags="['Recall', 'Remember', 'helpful', '近 30 天']"
            compact
            :bordered="false"
        />

        <div v-else class="stats-trend__chart-wrap">
            <div class="stats-trend__chart">
                <div
                    v-for="item in chartItems"
                    :key="item.date"
                    class="stats-trend__group"
                    :title="`${item.date}\nRecall: ${item.recallCount}\nRemember: ${item.rememberCount}\nhelpful: ${item.helpfulCount}`"
                >
                    <div class="stats-trend__bars">
                        <div
                            class="stats-trend__bar stats-trend__bar--recall"
                            :style="{ height: `${barHeight(item.recallCount)}px` }"
                        />
                        <div
                            class="stats-trend__bar stats-trend__bar--remember"
                            :style="{ height: `${barHeight(item.rememberCount)}px` }"
                        />
                        <div
                            class="stats-trend__bar stats-trend__bar--helpful"
                            :style="{ height: `${barHeight(item.helpfulCount)}px` }"
                        />
                    </div>
                    <span
                        v-if="showDateLabel(item.date)"
                        class="stats-trend__label"
                    >
                        {{ formatShortDate(item.date) }}
                    </span>
                </div>
            </div>
        </div>
    </StatsPanel>
</template>

<script setup>
const props = defineProps({
    loading: {
        type: Boolean,
        default: false,
    },
    trendList: {
        type: Array,
        default: () => [],
    },
})

const MAX_BAR_HEIGHT = 120

/** 趋势数据，按日期升序 */
const chartItems = computed(() => {
    return [...(props.trendList || [])].sort((left, right) => {
        return String(left.date).localeCompare(String(right.date))
    })
})

/** 柱状图最大值，用于归一化高度 */
const maxValue = computed(() => {
    let peak = 1
    for (const item of chartItems.value) {
        peak = Math.max(
            peak,
            item.recallCount || 0,
            item.rememberCount || 0,
            item.helpfulCount || 0,
        )
    }
    return peak
})

/** 计算柱高像素值 */
function barHeight(count) {
    const value = count || 0
    if (value === 0) {
        return 2
    }
    return Math.max(4, Math.round((value / maxValue.value) * MAX_BAR_HEIGHT))
}

/** 稀疏展示 X 轴日期，避免 30 天标签拥挤 */
function showDateLabel(dateText) {
    const index = chartItems.value.findIndex((item) => item.date === dateText)
    if (index < 0) {
        return false
    }
    const total = chartItems.value.length
    if (total <= 7) {
        return true
    }
    return index === 0 || index === total - 1 || index % 5 === 0
}

/** 短日期格式：MM/dd */
function formatShortDate(dateText) {
    if (!dateText || dateText.length < 10) {
        return dateText
    }
    return dateText.slice(5).replace('-', '/')
}
</script>

<style scoped>
.stats-trend__legend {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px;
    font-size: 12px;
    color: #6b7280;
}

.stats-trend__legend-item {
    display: flex;
    align-items: center;
    gap: 6px;
}

.stats-trend__legend-dot {
    display: inline-block;
    width: 10px;
    height: 10px;
    border-radius: 3px;
}

.stats-trend__legend-dot--recall {
    background: linear-gradient(180deg, #8e93fb 0%, #5d65f9 100%);
}

.stats-trend__legend-dot--remember {
    background: linear-gradient(180deg, #6ee7b7 0%, #34d399 100%);
}

.stats-trend__legend-dot--helpful {
    background: linear-gradient(180deg, #fcd34d 0%, #f59e0b 100%);
}

.stats-trend__loading {
    height: 200px;
    display: flex;
    align-items: center;
}

.stats-trend__chart-wrap {
    padding: 12px 10px 4px;
    border-radius: 10px;
    background: linear-gradient(180deg, #f9fafb 0%, #fff 100%);
    border: 1px solid #f3f4f6;
    overflow-x: auto;
}

.stats-trend__chart {
    display: flex;
    align-items: flex-end;
    gap: 6px;
    min-width: max-content;
    height: 168px;
    padding: 0 4px;
    background-image:
        linear-gradient(to top, #f3f4f6 1px, transparent 1px);
    background-size: 100% 28px;
}

.stats-trend__group {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    width: 32px;
    flex-shrink: 0;
}

.stats-trend__bars {
    display: flex;
    align-items: flex-end;
    justify-content: center;
    gap: 2px;
    height: 128px;
    width: 100%;
}

.stats-trend__bar {
    width: 7px;
    border-radius: 4px 4px 0 0;
    transition: height 0.3s ease;
}

.stats-trend__bar--recall {
    background: linear-gradient(180deg, #8e93fb 0%, #5d65f9 100%);
}

.stats-trend__bar--remember {
    background: linear-gradient(180deg, #6ee7b7 0%, #34d399 100%);
}

.stats-trend__bar--helpful {
    background: linear-gradient(180deg, #fcd34d 0%, #f59e0b 100%);
}

.stats-trend__label {
    font-size: 10px;
    color: #9ca3af;
    white-space: nowrap;
    transform: rotate(-35deg);
    transform-origin: top center;
}
</style>
