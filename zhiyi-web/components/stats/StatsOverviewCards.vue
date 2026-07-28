<template>
    <div class="stats-overview">
        <div
            v-for="card in cardList"
            :key="card.key"
            class="stats-overview__card"
            :class="`stats-overview__card--${card.tone}`"
        >
            <div class="stats-overview__card-top">
                <div class="stats-overview__icon" :class="`stats-overview__icon--${card.tone}`">
                    <el-icon :size="18">
                        <component :is="card.icon" />
                    </el-icon>
                </div>
                <span v-if="card.badge" class="stats-overview__badge">{{ card.badge }}</span>
            </div>
            <p class="stats-overview__label">{{ card.label }}</p>
            <p class="stats-overview__value">
                {{ loading ? '—' : card.value }}
            </p>
            <p v-if="card.hint" class="stats-overview__hint">{{ card.hint }}</p>
        </div>
    </div>
</template>

<script setup>
import { DataLine, Finished, Histogram, Star } from '@element-plus/icons-vue'
import { formatRatePercent } from '~/composables/useWorkspaceStats'

const props = defineProps({
    loading: {
        type: Boolean,
        default: false,
    },
    overview: {
        type: Object,
        default: null,
    },
})

/** 顶部四张指标卡配置，含图标与主题色 */
const cardList = computed(() => {
    const data = props.overview || {}
    const periodDays = data.statsPeriodDays || 7
    return [
        {
            key: 'weeklyHelpfulRecalls',
            label: '本周有效召回',
            value: data.weeklyHelpfulRecalls ?? 0,
            hint: 'helpful / used 反馈次数',
            icon: Star,
            tone: 'primary',
            badge: '北极星',
        },
        {
            key: 'helpfulRate',
            label: 'helpful 占比',
            value: formatRatePercent(data.helpfulRate),
            hint: `近 ${periodDays} 天反馈质量`,
            icon: Finished,
            tone: 'emerald',
        },
        {
            key: 'draftToPublishedRate',
            label: '草稿采纳率',
            value: formatRatePercent(data.draftToPublishedRate),
            hint: '近 30 天 Capture 审阅',
            icon: Histogram,
            tone: 'amber',
        },
        {
            key: 'recallCount7d',
            label: `近 ${periodDays} 天 Recall`,
            value: data.recallCount7d ?? 0,
            hint: 'Recall + Search 成功次数',
            icon: DataLine,
            tone: 'violet',
        },
    ]
})
</script>

<style scoped>
.stats-overview {
    display: grid;
    grid-template-columns: 1fr;
    gap: 14px;
}

@media (min-width: 640px) {
    .stats-overview {
        grid-template-columns: repeat(2, 1fr);
    }
}

@media (min-width: 1024px) {
    .stats-overview {
        grid-template-columns: repeat(4, 1fr);
    }
}

.stats-overview__card {
    position: relative;
    overflow: hidden;
    padding: 18px 20px;
    border-radius: 14px;
    border: 1px solid #e8eaef;
    background: #fff;
    box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
    transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.stats-overview__card:hover {
    box-shadow: 0 4px 14px rgba(15, 23, 42, 0.07);
    transform: translateY(-1px);
}

.stats-overview__card--primary {
    border-color: #d8dcfe;
    background: linear-gradient(145deg, #fafbff 0%, #fff 55%);
}

.stats-overview__card-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 14px;
}

.stats-overview__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    border-radius: 10px;
}

.stats-overview__icon--primary {
    background: linear-gradient(135deg, #eff0fe 0%, #dfe0fe 100%);
    color: #5d65f9;
}

.stats-overview__icon--emerald {
    background: linear-gradient(135deg, #ecfdf5 0%, #d1fae5 100%);
    color: #059669;
}

.stats-overview__icon--amber {
    background: linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);
    color: #d97706;
}

.stats-overview__icon--violet {
    background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%);
    color: #7c3aed;
}

.stats-overview__badge {
    font-size: 11px;
    font-weight: 500;
    color: #5d65f9;
    background: #eff0fe;
    border: 1px solid #ced1fd;
    padding: 2px 8px;
    border-radius: 999px;
}

.stats-overview__label {
    margin: 0;
    font-size: 13px;
    color: #6b7280;
}

.stats-overview__value {
    margin: 6px 0 0;
    font-size: 28px;
    font-weight: 700;
    color: #111827;
    letter-spacing: -0.02em;
    font-variant-numeric: tabular-nums;
    line-height: 1.1;
}

.stats-overview__hint {
    margin: 8px 0 0;
    font-size: 11px;
    color: #9ca3af;
}
</style>
