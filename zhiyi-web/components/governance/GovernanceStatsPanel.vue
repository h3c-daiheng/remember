<template>
    <!-- 治理概览：紧凑指标 + 漏斗 + 配置 -->
    <section v-if="stats" class="governance-stats-panel">
        <div class="governance-stats-panel__metrics">
            <div
                v-for="card in metricCards"
                :key="card.key"
                class="governance-stats-panel__metric"
                :class="`governance-stats-panel__metric--${card.tone}`"
            >
                <el-icon :size="15" class="governance-stats-panel__metric-icon">
                    <component :is="card.icon" />
                </el-icon>
                <div class="governance-stats-panel__metric-body">
                    <span class="governance-stats-panel__metric-label">{{ card.label }}</span>
                    <strong class="governance-stats-panel__metric-value">{{ card.value }}</strong>
                </div>
            </div>
        </div>

        <div v-if="stats.funnel" class="governance-stats-panel__footer">
            <div class="governance-stats-panel__funnel">
                <span class="governance-stats-panel__funnel-prefix">闭环</span>
                <span
                    v-for="(step, index) in funnelSteps"
                    :key="step.key"
                    class="governance-stats-panel__funnel-step"
                >
                    <span class="governance-stats-panel__funnel-item">
                        {{ step.label }} <strong>{{ step.value }}</strong>
                    </span>
                    <span
                        v-if="index < funnelSteps.length - 1"
                        class="governance-stats-panel__funnel-sep"
                    >→</span>
                </span>
            </div>

            <div class="governance-stats-panel__breakdown">
                <span
                    v-for="chip in breakdownChips"
                    :key="chip.key"
                    class="governance-stats-panel__chip"
                    :class="chip.count ? 'governance-stats-panel__chip--active' : ''"
                >
                    {{ chip.label }} {{ chip.count }}
                </span>
                <span v-if="lastValidateText" class="governance-stats-panel__muted">{{ lastValidateText }}</span>
            </div>

            <div
                v-if="canManage && config"
                class="governance-stats-panel__config"
            >
                <span class="governance-stats-panel__config-label">自动处置</span>
                <el-switch
                    :model-value="config.autoResolveEnabled"
                    :disabled="configSaving"
                    size="small"
                    @change="handleAutoResolveToggle"
                />
                <span class="governance-stats-panel__config-hint">
                    ≥{{ Math.round((config.autoResolveSimilarityThreshold || 0.85) * 100) }}% 重复自动合并
                </span>
            </div>
        </div>
    </section>
</template>

<script setup>
import { CircleCheck, DataAnalysis, DocumentCopy, WarningFilled } from '@element-plus/icons-vue'
import { formatDateTime } from '~/utils/knowledge'

const props = defineProps({
    stats: {
        type: Object,
        default: null,
    },
    config: {
        type: Object,
        default: null,
    },
    canManage: {
        type: Boolean,
        default: false,
    },
    configSaving: {
        type: Boolean,
        default: false,
    },
})

const emit = defineEmits(['update-config'])

const totalOpenCount = computed(() => {
    if (!props.stats) {
        return 0
    }
    return (props.stats.openIssueCount || 0)
        + (props.stats.openFragmentIssueCount || 0)
        + (props.stats.openIncompleteIssueCount || 0)
        + (props.stats.openOutdatedIssueCount || 0)
        + (props.stats.openConflictIssueCount || 0)
})

const duplicateRatePercent = computed(() =>
    Math.round((props.stats?.duplicateIssueRate || 0) * 100),
)

const lastValidateText = computed(() => {
    if (props.stats?.lastValidateScanTime) {
        return formatDateTime(props.stats.lastValidateScanTime)
    }
    if (props.stats?.lastIncrementalScanTime) {
        return formatDateTime(props.stats.lastIncrementalScanTime)
    }
    return ''
})

/** 顶部指标配置 */
const metricCards = computed(() => [
    {
        key: 'published',
        label: '已发布',
        value: props.stats?.publishedKnowledgeCount || 0,
        icon: DocumentCopy,
        tone: 'primary',
    },
    {
        key: 'open',
        label: '待处理',
        value: totalOpenCount.value,
        icon: WarningFilled,
        tone: 'amber',
    },
    {
        key: 'autoResolved',
        label: '自动处置',
        value: props.stats?.autoResolvedIssueCount || 0,
        icon: CircleCheck,
        tone: 'emerald',
    },
    {
        key: 'duplicateRate',
        label: '重复占比',
        value: `${duplicateRatePercent.value}%`,
        icon: DataAnalysis,
        tone: 'violet',
    },
])

/** 漏斗步骤数据 */
const funnelSteps = computed(() => {
    const funnel = props.stats?.funnel || {}
    return [
        { key: 'scan', label: '扫描', value: funnel.totalScanBatchCount || 0 },
        { key: 'created', label: '创建', value: funnel.totalIssueCreatedCount || 0 },
        { key: 'resolved', label: '解决', value: funnel.totalIssueResolvedCount || 0 },
        { key: 'dismissed', label: '忽略', value: funnel.totalIssueDismissedCount || 0 },
    ]
})

/** 待处理分类 chips */
const breakdownChips = computed(() => [
    { key: 'incomplete', label: '不完整', count: props.stats?.openIncompleteIssueCount || 0 },
    { key: 'outdated', label: '过时', count: props.stats?.openOutdatedIssueCount || 0 },
    { key: 'conflict', label: '冲突', count: props.stats?.openConflictIssueCount || 0 },
])

function handleAutoResolveToggle(enabled) {
    emit('update-config', {
        autoResolveEnabled: enabled,
        autoResolveSimilarityThreshold: props.config?.autoResolveSimilarityThreshold,
    })
}
</script>

<style scoped>
.governance-stats-panel__metrics {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
}

@media (min-width: 768px) {
    .governance-stats-panel__metrics {
        grid-template-columns: repeat(4, minmax(0, 1fr));
    }
}

.governance-stats-panel__metric {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    border-radius: 10px;
    border: 1px solid #eef0f3;
    background: #fafafa;
}

.governance-stats-panel__metric--primary {
    background: #fafbff;
    border-color: #e0e4ff;
}

.governance-stats-panel__metric-icon {
    flex-shrink: 0;
    color: #6b7280;
}

.governance-stats-panel__metric--primary .governance-stats-panel__metric-icon {
    color: #5d65f9;
}

.governance-stats-panel__metric--amber .governance-stats-panel__metric-icon {
    color: #d97706;
}

.governance-stats-panel__metric--emerald .governance-stats-panel__metric-icon {
    color: #059669;
}

.governance-stats-panel__metric--violet .governance-stats-panel__metric-icon {
    color: #7c3aed;
}

.governance-stats-panel__metric-body {
    display: flex;
    flex-direction: column;
    gap: 1px;
    min-width: 0;
}

.governance-stats-panel__metric-label {
    font-size: 11px;
    color: #9ca3af;
    line-height: 1.2;
}

.governance-stats-panel__metric-value {
    font-size: 18px;
    font-weight: 700;
    color: #111827;
    font-variant-numeric: tabular-nums;
    line-height: 1.1;
}

.governance-stats-panel__footer {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 12px;
    margin-top: 10px;
    padding-top: 10px;
    border-top: 1px solid #f3f4f6;
}

.governance-stats-panel__funnel {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: #6b7280;
}

.governance-stats-panel__funnel-prefix {
    font-weight: 600;
    color: #9ca3af;
}

.governance-stats-panel__funnel-step {
    display: contents;
}

.governance-stats-panel__funnel-item strong {
    font-weight: 700;
    color: #374151;
}

.governance-stats-panel__funnel-sep {
    color: #d1d5db;
    margin: 0 1px;
}

.governance-stats-panel__breakdown {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
}

.governance-stats-panel__chip {
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 11px;
    color: #6b7280;
    background: #f3f4f6;
}

.governance-stats-panel__chip--active {
    color: #4338ca;
    background: #eff0fe;
}

.governance-stats-panel__muted {
    font-size: 11px;
    color: #9ca3af;
}

.governance-stats-panel__config {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    margin-left: auto;
}

.governance-stats-panel__config-label {
    font-size: 12px;
    color: #6b7280;
}

.governance-stats-panel__config-hint {
    font-size: 11px;
    color: #9ca3af;
}
</style>
