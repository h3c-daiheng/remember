<template>
    <!-- 治理扫描操作区：单行紧凑布局 -->
    <section class="governance-scan-panel">
        <div class="governance-scan-panel__row">
            <el-select
                :model-value="scanTask"
                class="governance-scan-panel__select governance-scan-panel__select--wide"
                :disabled="disabled || scanning"
                @update:model-value="emit('update:scanTask', $event)"
            >
                <el-option
                    v-for="option in GOVERNANCE_SCAN_TASK_OPTIONS"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                />
            </el-select>

            <el-select
                :model-value="knowledgeType"
                placeholder="全部类型"
                class="governance-scan-panel__select"
                :disabled="disabled || scanning"
                @update:model-value="emit('update:knowledgeType', $event)"
            >
                <el-option
                    v-for="option in knowledgeTypeOptions"
                    :key="option.value || 'all'"
                    :label="option.label"
                    :value="option.value"
                />
            </el-select>

            <el-button
                type="primary"
                class="governance-scan-panel__action"
                :loading="scanning"
                :disabled="disabled"
                @click="emit('scan')"
            >
                <el-icon v-if="!scanning" class="mr-1"><Search /></el-icon>
                {{ scanning ? '扫描中' : '开始扫描' }}
            </el-button>

            <span v-if="scanning" class="governance-scan-panel__status">
                <el-icon class="is-loading" :size="14"><Loading /></el-icon>
                分析中…
            </span>
            <span v-else class="governance-scan-panel__status governance-scan-panel__status--muted">
                {{ lastScanText }}
            </span>
        </div>
    </section>
</template>

<script setup>
import { Loading, Search } from '@element-plus/icons-vue'
import {
    GOVERNANCE_KNOWLEDGE_TYPE_OPTIONS,
    GOVERNANCE_SCAN_MODE_FRAGMENT,
    GOVERNANCE_SCAN_MODE_VALIDATE,
    GOVERNANCE_SCAN_TASK_DUPLICATE_FULL,
    GOVERNANCE_SCAN_TASK_DUPLICATE_INCREMENTAL,
    GOVERNANCE_SCAN_TASK_FRAGMENT,
    GOVERNANCE_SCAN_TASK_OPTIONS,
    GOVERNANCE_SCAN_TASK_VALIDATE,
} from '~/constants/governance'
import { formatDateTime } from '~/utils/knowledge'

const props = defineProps({
    scanTask: {
        type: String,
        default: GOVERNANCE_SCAN_TASK_DUPLICATE_FULL,
    },
    knowledgeType: {
        type: String,
        default: '',
    },
    scanning: {
        type: Boolean,
        default: false,
    },
    disabled: {
        type: Boolean,
        default: false,
    },
    stats: {
        type: Object,
        default: null,
    },
})

const emit = defineEmits(['update:scanTask', 'update:knowledgeType', 'scan'])

const activeScanOption = computed(() =>
    GOVERNANCE_SCAN_TASK_OPTIONS.find((option) => option.value === props.scanTask)
    || GOVERNANCE_SCAN_TASK_OPTIONS[0],
)

const knowledgeTypeOptions = computed(() => {
    if (activeScanOption.value.scanMode === GOVERNANCE_SCAN_MODE_FRAGMENT) {
        return GOVERNANCE_KNOWLEDGE_TYPE_OPTIONS.filter((option) =>
            !option.value || option.value === 'experience',
        )
    }
    if (activeScanOption.value.scanMode === GOVERNANCE_SCAN_MODE_VALIDATE) {
        return GOVERNANCE_KNOWLEDGE_TYPE_OPTIONS
    }
    return GOVERNANCE_KNOWLEDGE_TYPE_OPTIONS
})

const lastScanText = computed(() => {
    if (props.scanTask === GOVERNANCE_SCAN_TASK_VALIDATE && props.stats?.lastValidateScanTime) {
        return `上次校验 ${formatDateTime(props.stats.lastValidateScanTime)}`
    }
    if (props.scanTask === GOVERNANCE_SCAN_TASK_DUPLICATE_INCREMENTAL && props.stats?.lastIncrementalScanTime) {
        return `上次增量 ${formatDateTime(props.stats.lastIncrementalScanTime)}`
    }
    if (props.scanTask === GOVERNANCE_SCAN_TASK_FRAGMENT && props.stats?.lastFragmentScanTime) {
        return `上次碎片 ${formatDateTime(props.stats.lastFragmentScanTime)}`
    }
    if (props.stats?.lastScanTime) {
        return `上次全量 ${formatDateTime(props.stats.lastScanTime)}`
    }
    return '尚未扫描'
})
</script>

<style scoped>
.governance-scan-panel__row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
}

.governance-scan-panel__select {
    width: 140px;
}

.governance-scan-panel__select--wide {
    width: 168px;
}

.governance-scan-panel__action {
    flex-shrink: 0;
}

.governance-scan-panel__status {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: #5d65f9;
}

.governance-scan-panel__status--muted {
    color: #9ca3af;
}
</style>
