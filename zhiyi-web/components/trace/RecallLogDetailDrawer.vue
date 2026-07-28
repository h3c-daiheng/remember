<template>
    <!-- Recall 召回调试抽屉：展示单次召回的输入、Query、排序分解与 promptBlock 预览 -->
    <el-drawer
        :model-value="visible"
        direction="rtl"
        size="720px"
        destroy-on-close
        class="recall-log-detail-drawer"
        :show-close="true"
        @update:model-value="handleVisibleChange"
    >
        <template #header>
            <div class="recall-log-detail-drawer__header">
                <div class="flex items-center flex-wrap gap-2 mb-2">
                    <span class="recall-log-detail-drawer__badge">Recall 详情</span>
                    <span v-if="detail?.logId" class="recall-log-detail-drawer__meta">
                        日志 #{{ detail.logId }}
                    </span>
                    <span v-if="detail?.createTime" class="text-gray-300">·</span>
                    <span v-if="detail?.createTime" class="recall-log-detail-drawer__meta">
                        {{ formatDateTime(detail.createTime) }}
                    </span>
                </div>
                <h2 class="recall-log-detail-drawer__title">
                    {{ headerTitle }}
                </h2>
            </div>
        </template>

        <div v-loading="loading" class="recall-log-detail-drawer__body">
            <el-alert
                v-if="errorMessage"
                type="error"
                :closable="false"
                show-icon
                class="mb-4"
                :title="errorMessage"
            />

            <template v-if="detail">
                <!-- 执行摘要 -->
                <section class="recall-log-detail-drawer__section">
                    <h3 class="recall-log-detail-drawer__section-title">执行摘要</h3>
                    <dl class="recall-log-detail-drawer__meta-grid">
                        <div>
                            <dt>Session</dt>
                            <dd class="recall-log-detail-drawer__mono">{{ detail.recallSession || '—' }}</dd>
                        </div>
                        <div>
                            <dt>Trace ID</dt>
                            <dd class="recall-log-detail-drawer__mono">{{ detail.traceId || '—' }}</dd>
                        </div>
                        <div>
                            <dt>操作类型</dt>
                            <dd>{{ detail.operation || '—' }}</dd>
                        </div>
                        <div>
                            <dt>耗时</dt>
                            <dd>{{ detail.latencyMs != null ? `${detail.latencyMs} ms` : '—' }}</dd>
                        </div>
                        <div>
                            <dt>返回条数</dt>
                            <dd>{{ detail.itemCount ?? 0 }}</dd>
                        </div>
                        <div>
                            <dt>检索候选</dt>
                            <dd>{{ detail.retrievalCandidateCount ?? 0 }}</dd>
                        </div>
                        <div>
                            <dt>排序候选</dt>
                            <dd>{{ detail.rankedCandidateCount ?? 0 }}</dd>
                        </div>
                        <div>
                            <dt>Fallback</dt>
                            <dd>
                                <el-tag
                                    size="small"
                                    :type="detail.fallbackUsed ? 'warning' : 'success'"
                                >
                                    {{ detail.fallbackUsed ? '已降级' : '正常检索' }}
                                </el-tag>
                            </dd>
                        </div>
                    </dl>
                </section>

                <!-- 检索 Query -->
                <section class="recall-log-detail-drawer__section">
                    <h3 class="recall-log-detail-drawer__section-title">检索 Query</h3>
                    <pre class="recall-log-detail-drawer__code">{{ detail.queryText || '—' }}</pre>
                </section>

                <!-- RecallContext 输入 -->
                <section class="recall-log-detail-drawer__section">
                    <h3 class="recall-log-detail-drawer__section-title">Recall 输入（RecallContext）</h3>
                    <dl class="recall-log-detail-drawer__meta-grid">
                        <div class="recall-log-detail-drawer__meta-grid--wide">
                            <dt>任务描述</dt>
                            <dd>{{ requestContext.task || '—' }}</dd>
                        </div>
                        <div class="recall-log-detail-drawer__meta-grid--wide">
                            <dt>当前 Prompt</dt>
                            <dd>{{ requestContext.currentPrompt || '—' }}</dd>
                        </div>
                        <div>
                            <dt>仓库</dt>
                            <dd>{{ requestContext.repository || '—' }}</dd>
                        </div>
                        <div>
                            <dt>模块</dt>
                            <dd>{{ requestContext.module || '—' }}</dd>
                        </div>
                        <div>
                            <dt>当前文件</dt>
                            <dd>{{ requestContext.currentFile || '—' }}</dd>
                        </div>
                        <div>
                            <dt>语言 / 框架</dt>
                            <dd>{{ formatLanguageFramework(requestContext) }}</dd>
                        </div>
                        <div>
                            <dt>知识类型</dt>
                            <dd>{{ formatStringList(requestContext.knowledgeTypes) }}</dd>
                        </div>
                        <div>
                            <dt>Fact 类型</dt>
                            <dd>{{ formatStringList(requestContext.factTypes) }}</dd>
                        </div>
                        <div>
                            <dt>Limit</dt>
                            <dd>{{ requestContext.limit ?? '—' }}</dd>
                        </div>
                    </dl>
                </section>

                <!-- 召回结果与排序分解 -->
                <section class="recall-log-detail-drawer__section">
                    <h3 class="recall-log-detail-drawer__section-title">召回结果</h3>
                    <div v-if="detail.items?.length" class="space-y-3">
                        <div
                            v-for="recallItem in detail.items"
                            :key="`${recallItem.rank}-${recallItem.knowledgeId}`"
                            class="recall-log-detail-drawer__item-card"
                            :class="{ 'recall-log-detail-drawer__item-card--highlight': highlightKnowledgeId === recallItem.knowledgeId }"
                        >
                            <div class="flex items-start justify-between gap-3 mb-2">
                                <div class="min-w-0">
                                    <div class="flex flex-wrap items-center gap-2 mb-1">
                                        <span class="recall-log-detail-drawer__rank">#{{ recallItem.rank }}</span>
                                        <span class="font-medium text-gray-900">{{ recallItem.title || '未命名' }}</span>
                                    </div>
                                    <div class="flex flex-wrap items-center gap-2 text-xs text-gray-500">
                                        <span>#{{ recallItem.knowledgeId }}</span>
                                        <span v-if="recallItem.knowledgeType">· {{ recallItem.knowledgeType }}</span>
                                    </div>
                                </div>
                                <span class="text-sm text-gray-600 shrink-0">
                                    score {{ formatRecallScore(recallItem.score) }}
                                </span>
                            </div>
                            <div
                                v-if="formatScoreBreakdownEntries(recallItem.scoreBreakdown).length"
                                class="recall-log-detail-drawer__breakdown"
                            >
                                <span
                                    v-for="breakdownItem in formatScoreBreakdownEntries(recallItem.scoreBreakdown)"
                                    :key="breakdownItem.key"
                                    class="recall-log-detail-drawer__breakdown-chip"
                                >
                                    {{ breakdownItem.label }} {{ breakdownItem.value }}
                                </span>
                            </div>
                        </div>
                    </div>
                    <p v-else class="recall-log-detail-drawer__empty">本次 Recall 无返回条目</p>
                </section>

                <!-- promptBlock 预览 -->
                <section class="recall-log-detail-drawer__section">
                    <div class="flex items-center justify-between gap-3 mb-3">
                        <h3 class="recall-log-detail-drawer__section-title mb-0">promptBlock 预览</h3>
                        <span class="text-xs text-gray-400">
                            共 {{ detail.promptBlockLength || 0 }} 字符
                        </span>
                    </div>
                    <pre
                        v-if="detail.promptBlockPreview"
                        class="recall-log-detail-drawer__code recall-log-detail-drawer__code--prompt"
                    >{{ detail.promptBlockPreview }}</pre>
                    <p v-else class="recall-log-detail-drawer__empty">无 promptBlock 内容</p>
                </section>
            </template>
        </div>
    </el-drawer>
</template>

<script setup>
import { fetchRecallLogDetail } from '~/services/memory-trace.service'
import { formatDateTime } from '~/utils/knowledge'
import { formatRecallScore, formatScoreBreakdownEntries } from '~/utils/memoryTrace'

const props = defineProps({
    visible: {
        type: Boolean,
        default: false,
    },
    logId: {
        type: [Number, String],
        default: null,
    },
    /** 高亮展示的目标 knowledgeId，用于 Trace 页从命中卡片进入 */
    highlightKnowledgeId: {
        type: [Number, String],
        default: null,
    },
})

const emit = defineEmits(['update:visible'])

const loading = ref(false)
const errorMessage = ref('')
const detail = ref(null)

const requestContext = computed(() => detail.value?.requestContext || {})

const headerTitle = computed(() => {
    const taskText = requestContext.value.task
    if (taskText) {
        return taskText
    }
    return detail.value?.queryText || 'Recall 召回调试'
})

watch(
    () => [props.visible, props.logId],
    async ([visibleValue, logIdValue]) => {
        if (!visibleValue || !logIdValue) {
            detail.value = null
            errorMessage.value = ''
            return
        }
        await loadDetail(logIdValue)
    },
    { immediate: true },
)

/**
 * 加载 Recall 日志详情
 */
async function loadDetail(logIdValue) {
    loading.value = true
    errorMessage.value = ''
    try {
        detail.value = await fetchRecallLogDetail(logIdValue)
    } catch (error) {
        detail.value = null
        errorMessage.value = error?.message || '加载 Recall 详情失败'
    } finally {
        loading.value = false
    }
}

function handleVisibleChange(nextVisible) {
    emit('update:visible', nextVisible)
}

function formatStringList(valueList) {
    if (!valueList?.length) {
        return '—'
    }
    return valueList.join(', ')
}

function formatLanguageFramework(contextValue) {
    const parts = []
    if (contextValue.language) {
        parts.push(contextValue.language)
    }
    if (contextValue.framework) {
        parts.push(contextValue.framework)
    }
    return parts.length ? parts.join(' / ') : '—'
}
</script>

<style scoped>
.recall-log-detail-drawer__header {
    padding-right: 8px;
}

.recall-log-detail-drawer__badge {
    display: inline-flex;
    align-items: center;
    padding: 2px 10px;
    border-radius: 9999px;
    font-size: 12px;
    font-weight: 500;
    background: #ecfdf5;
    color: #047857;
}

.recall-log-detail-drawer__meta {
    font-size: 12px;
    color: #6b7280;
}

.recall-log-detail-drawer__title {
    font-size: 18px;
    font-weight: 600;
    color: #111827;
    line-height: 1.4;
}

.recall-log-detail-drawer__body {
    min-height: 240px;
}

.recall-log-detail-drawer__section {
    margin-bottom: 24px;
    padding-bottom: 24px;
    border-bottom: 1px solid #f3f4f6;
}

.recall-log-detail-drawer__section:last-child {
    margin-bottom: 0;
    padding-bottom: 0;
    border-bottom: none;
}

.recall-log-detail-drawer__section-title {
    font-size: 14px;
    font-weight: 600;
    color: #111827;
    margin-bottom: 12px;
}

.recall-log-detail-drawer__meta-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 12px 16px;
    margin: 0;
}

.recall-log-detail-drawer__meta-grid--wide {
    grid-column: 1 / -1;
}

.recall-log-detail-drawer__meta-grid dt {
    font-size: 11px;
    color: #9ca3af;
    margin-bottom: 2px;
}

.recall-log-detail-drawer__meta-grid dd {
    margin: 0;
    font-size: 13px;
    color: #374151;
    word-break: break-word;
}

.recall-log-detail-drawer__mono {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
}

.recall-log-detail-drawer__code {
    margin: 0;
    padding: 12px;
    border-radius: 10px;
    background: #f9fafb;
    border: 1px solid #e5e7eb;
    font-size: 12px;
    line-height: 1.6;
    color: #374151;
    white-space: pre-wrap;
    word-break: break-word;
}

.recall-log-detail-drawer__code--prompt {
    max-height: 320px;
    overflow: auto;
}

.recall-log-detail-drawer__item-card {
    padding: 14px;
    border-radius: 12px;
    border: 1px solid #e5e7eb;
    background: #fff;
}

.recall-log-detail-drawer__item-card--highlight {
    border-color: #86efac;
    background: linear-gradient(135deg, #f0fdf4 0%, #fff 100%);
}

.recall-log-detail-drawer__rank {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    border-radius: 6px;
    font-size: 12px;
    font-weight: 600;
    color: #047857;
    background: #d1fae5;
}

.recall-log-detail-drawer__breakdown {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
}

.recall-log-detail-drawer__breakdown-chip {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    border-radius: 6px;
    font-size: 11px;
    color: #4b5563;
    background: #f3f4f6;
}

.recall-log-detail-drawer__empty {
    font-size: 13px;
    color: #9ca3af;
}
</style>

<style>
.recall-log-detail-drawer .el-drawer__header {
    margin-bottom: 0;
    padding-bottom: 16px;
    border-bottom: 1px solid #f3f4f6;
}

.recall-log-detail-drawer .el-drawer__body {
    padding-top: 16px;
}
</style>
