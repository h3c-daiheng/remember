<template>
    <!-- Agent 召回请求列表项：点击打开详情抽屉 -->
    <div
        class="recall-log-card group"
        role="button"
        tabindex="0"
        @click="emit('open', record)"
        @keyup.enter="emit('open', record)"
    >
        <div class="recall-log-card__accent" :class="`recall-log-card__accent--${accentTone}`" />
        <div class="recall-log-card__body">
            <div class="flex items-start gap-3">
                <div class="flex-1 min-w-0">
                    <div class="flex items-start gap-3">
                        <h2 class="flex-1 min-w-0 text-base font-semibold text-gray-900 leading-snug group-hover:text-primary transition-colors line-clamp-2">
                            {{ record.task || '（无任务描述）' }}
                        </h2>
                        <div class="shrink-0 flex items-center flex-wrap justify-end gap-1.5">
                            <span
                                class="recall-log-card__badge"
                                :class="`recall-log-card__badge--${operationBadgeTone}`"
                            >
                                {{ operationLabel }}
                            </span>
                            <span
                                class="recall-log-card__status"
                                :class="`recall-log-card__status--${successTone}`"
                            >
                                {{ successLabel }}
                            </span>
                        </div>
                    </div>

                    <div class="recall-log-card__meta">
                        <span>#{{ record.logId }}</span>
                        <span v-if="record.latencyMs != null">耗时 {{ record.latencyMs }}ms</span>
                        <span v-if="record.itemCount != null">返回 {{ record.itemCount }} 条</span>
                        <span v-if="record.fallbackUsed" class="text-amber-600">Fallback</span>
                        <span v-if="authLabel">{{ authLabel }}</span>
                    </div>

                    <p
                        v-if="record.topTitle"
                        class="mt-2 text-xs text-gray-500 line-clamp-1"
                    >
                        Top1：{{ record.topTitle }}
                    </p>
                    <p
                        v-else-if="record.errorMessage"
                        class="mt-2 text-xs text-red-500 line-clamp-2"
                    >
                        {{ record.errorMessage }}
                    </p>
                    <p
                        v-if="record.recallSession"
                        class="mt-1.5 text-xs text-gray-400 font-mono truncate"
                    >
                        {{ record.recallSession }}
                    </p>
                </div>

                <div class="shrink-0 flex flex-col items-end justify-between self-stretch py-0.5">
                    <el-icon :size="14" class="recall-log-card__arrow">
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
import { formatDateTime } from '~/utils/knowledge'

const props = defineProps({
    record: {
        type: Object,
        required: true,
    },
})

const emit = defineEmits(['open'])

/** 操作类型文案 */
const operationLabel = computed(() => {
    if (props.record?.operation === 'search') {
        return '搜索'
    }
    return '召回'
})

const operationBadgeTone = computed(() =>
    props.record?.operation === 'search' ? 'neutral' : 'processing',
)

/** 成功态 */
const isSuccess = computed(() => props.record?.success === 1)

const successLabel = computed(() => (isSuccess.value ? '成功' : '失败'))

const successTone = computed(() => (isSuccess.value ? 'success' : 'danger'))

const accentTone = computed(() => (isSuccess.value ? 'success' : 'danger'))

/** 鉴权方式简要展示 */
const authLabel = computed(() => {
    const authType = props.record?.authType
    if (authType === 'api_key') {
        const keyId = props.record?.apiKeyId
        return keyId != null ? `API Key #${keyId}` : 'API Key'
    }
    if (authType === 'jwt') {
        return 'JWT'
    }
    return ''
})

const createTimeText = computed(() => formatDateTime(props.record?.createTime) || '')
</script>

<style scoped>
.recall-log-card {
    position: relative;
    display: flex;
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 12px;
    overflow: hidden;
    transition: border-color 0.2s, box-shadow 0.2s;
    cursor: pointer;
}

.recall-log-card:hover {
    border-color: #c7d2fe;
    box-shadow: 0 4px 14px rgba(15, 23, 42, 0.05);
}

.recall-log-card__accent {
    width: 3px;
    flex-shrink: 0;
    background: #e5e7eb;
}

.recall-log-card__accent--success {
    background: #10b981;
}

.recall-log-card__accent--danger {
    background: #ef4444;
}

.recall-log-card__body {
    flex: 1;
    min-width: 0;
    padding: 16px 18px 16px 16px;
}

.recall-log-card__meta {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px 14px;
    margin-top: 10px;
    font-size: 12px;
    color: #6b7280;
}

.recall-log-card__badge {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 12px;
    font-weight: 500;
    line-height: 1.4;
}

.recall-log-card__badge--processing {
    color: #1d4ed8;
    background: #eff6ff;
}

.recall-log-card__badge--neutral {
    color: #4b5563;
    background: #f3f4f6;
}

.recall-log-card__status {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 12px;
    line-height: 1.4;
}

.recall-log-card__status--success {
    color: #047857;
    background: #ecfdf5;
}

.recall-log-card__status--danger {
    color: #b91c1c;
    background: #fef2f2;
}

.recall-log-card__arrow {
    color: #9ca3af;
    transition: color 0.2s, transform 0.2s;
}

.group:hover .recall-log-card__arrow {
    color: var(--el-color-primary);
    transform: translateX(2px);
}
</style>
