<template>
    <!-- 闭环追踪时间线：Remember → Capture → 发布 → Recall 命中 -->
    <div class="memory-trace-timeline">
        <!-- Step 1: Remember -->
        <section class="memory-trace-timeline__step">
            <div class="memory-trace-timeline__marker memory-trace-timeline__marker--done">
                <el-icon :size="14"><Promotion /></el-icon>
            </div>
            <div class="memory-trace-timeline__content">
                <div class="memory-trace-timeline__head">
                    <h3 class="memory-trace-timeline__title">Remember</h3>
                    <span class="memory-trace-timeline__badge memory-trace-timeline__badge--done">Agent 提交</span>
                </div>
                <div v-if="systemEvent" class="memory-trace-timeline__card">
                    <dl class="memory-trace-timeline__meta-grid">
                        <div>
                            <dt>Event ID</dt>
                            <dd>#{{ systemEvent.eventId }}</dd>
                        </div>
                        <div>
                            <dt>类型</dt>
                            <dd>{{ systemEvent.eventType || '—' }}</dd>
                        </div>
                        <div>
                            <dt>Actor</dt>
                            <dd>{{ systemEvent.actor || '—' }}</dd>
                        </div>
                        <div>
                            <dt>仓库 / 模块</dt>
                            <dd>{{ formatRepoModule(systemEvent.repository, systemEvent.module) }}</dd>
                        </div>
                        <div>
                            <dt>提交时间</dt>
                            <dd>{{ formatDateTime(systemEvent.createTime) || '—' }}</dd>
                        </div>
                    </dl>
                </div>
                <p v-else class="memory-trace-timeline__empty">未找到关联 Event</p>
            </div>
        </section>

        <!-- Step 2: Capture -->
        <section class="memory-trace-timeline__step">
            <div
                class="memory-trace-timeline__marker"
                :class="captureDraft ? 'memory-trace-timeline__marker--done' : 'memory-trace-timeline__marker--pending'"
            >
                <el-icon :size="14"><DocumentChecked /></el-icon>
            </div>
            <div class="memory-trace-timeline__content">
                <div class="memory-trace-timeline__head">
                    <h3 class="memory-trace-timeline__title">Capture & Review</h3>
                    <span
                        v-if="captureDraft"
                        class="memory-trace-timeline__badge"
                        :class="reviewBadgeClass"
                    >
                        {{ formatReviewStatus(captureDraft.reviewStatus) }}
                    </span>
                </div>
                <div v-if="captureDraft" class="memory-trace-timeline__card">
                    <p class="memory-trace-timeline__card-title">
                        {{ captureDraft.draftContent?.title || '未命名草稿' }}
                    </p>
                    <dl class="memory-trace-timeline__meta-grid">
                        <div>
                            <dt>草稿 ID</dt>
                            <dd>#{{ captureDraft.id }}</dd>
                        </div>
                        <div>
                            <dt>Review 动作</dt>
                            <dd>{{ formatReviewAction(captureDraft.reviewAction) }}</dd>
                        </div>
                        <div v-if="captureDraft.rejectReason">
                            <dt>拒绝码</dt>
                            <dd>{{ captureDraft.rejectReason }}</dd>
                        </div>
                        <div v-if="captureDraft.routedKnowledgeId">
                            <dt>路由产出</dt>
                            <dd>#{{ captureDraft.routedKnowledgeId }}</dd>
                        </div>
                        <div v-if="captureDraft.knowledgeId">
                            <dt>发布知识</dt>
                            <dd>#{{ captureDraft.knowledgeId }}</dd>
                        </div>
                    </dl>
                    <div class="memory-trace-timeline__actions">
                        <el-button size="small" @click="router.push('/capture')">
                            打开 Capture
                        </el-button>
                        <el-button
                            size="small"
                            type="primary"
                            plain
                            @click="router.push(`/trace?draftId=${captureDraft.id}`)"
                        >
                            刷新追踪
                        </el-button>
                    </div>
                </div>
                <p v-else class="memory-trace-timeline__empty">未找到 Capture 草稿</p>
            </div>
        </section>

        <!-- Step 3: Publish -->
        <section class="memory-trace-timeline__step">
            <div
                class="memory-trace-timeline__marker"
                :class="hasPublishedKnowledge ? 'memory-trace-timeline__marker--done' : 'memory-trace-timeline__marker--pending'"
            >
                <el-icon :size="14"><Upload /></el-icon>
            </div>
            <div class="memory-trace-timeline__content">
                <div class="memory-trace-timeline__head">
                    <h3 class="memory-trace-timeline__title">发布 / 路由产出</h3>
                    <span v-if="knowledgeList.length" class="memory-trace-timeline__badge memory-trace-timeline__badge--done">
                        {{ knowledgeList.length }} 条知识
                    </span>
                </div>
                <div v-if="knowledgeList.length" class="space-y-3">
                    <div
                        v-for="knowledgeItem in knowledgeList"
                        :key="knowledgeItem.knowledgeId"
                        class="memory-trace-timeline__card"
                    >
                        <div class="flex items-start justify-between gap-3">
                            <div class="min-w-0">
                                <p class="memory-trace-timeline__card-title">{{ knowledgeItem.title || '未命名' }}</p>
                                <div class="flex flex-wrap items-center gap-2 mt-2">
                                    <el-tag size="small" type="info">
                                        {{ KNOWLEDGE_TYPE_LABELS[knowledgeItem.knowledgeType] || knowledgeItem.knowledgeType }}
                                    </el-tag>
                                    <el-tag
                                        size="small"
                                        :type="lifecycleTagType(knowledgeItem.lifecycleStatus)"
                                    >
                                        {{ KNOWLEDGE_LIFECYCLE_LABELS[knowledgeItem.lifecycleStatus] || '未知' }}
                                    </el-tag>
                                    <span class="text-xs text-gray-400">
                                        Recall {{ knowledgeItem.recallCount || 0 }} 次
                                    </span>
                                </div>
                            </div>
                            <el-button
                                v-if="buildKnowledgeDetailPath(knowledgeItem)"
                                size="small"
                                type="primary"
                                link
                                @click="router.push(buildKnowledgeDetailPath(knowledgeItem))"
                            >
                                查看详情
                            </el-button>
                        </div>
                        <dl class="memory-trace-timeline__meta-grid mt-3">
                            <div>
                                <dt>Knowledge ID</dt>
                                <dd>#{{ knowledgeItem.knowledgeId }}</dd>
                            </div>
                            <div v-if="knowledgeItem.sourceCaptureDraftId">
                                <dt>来源草稿</dt>
                                <dd>#{{ knowledgeItem.sourceCaptureDraftId }}</dd>
                            </div>
                        </dl>
                    </div>
                </div>
                <p v-else class="memory-trace-timeline__empty">尚无关联知识（可能尚未 Review 或发布）</p>
            </div>
        </section>

        <!-- Step 4: Recall -->
        <section class="memory-trace-timeline__step memory-trace-timeline__step--last">
            <div
                class="memory-trace-timeline__marker"
                :class="recallHits.length ? 'memory-trace-timeline__marker--success' : 'memory-trace-timeline__marker--pending'"
            >
                <el-icon :size="14"><Search /></el-icon>
            </div>
            <div class="memory-trace-timeline__content">
                <div class="memory-trace-timeline__head">
                    <h3 class="memory-trace-timeline__title">Recall 命中</h3>
                    <span
                        v-if="recallHits.length"
                        class="memory-trace-timeline__badge"
                        :class="topOneHit ? 'memory-trace-timeline__badge--success' : 'memory-trace-timeline__badge--done'"
                    >
                        {{ topOneHit ? 'Top1 已命中' : `${recallHits.length} 次命中` }}
                    </span>
                    <el-button
                        v-if="recallHits.length > 1"
                        size="small"
                        link
                        type="primary"
                        class="memory-trace-timeline__toggle"
                        @click="showAllRecallHits = !showAllRecallHits"
                    >
                        {{ showAllRecallHits ? '仅看最近一次' : `查看全部 ${recallHits.length} 次` }}
                    </el-button>
                </div>
                <div v-if="recallHits.length" class="space-y-3">
                    <div
                        v-for="hitItem in displayedRecallHits"
                        :key="`${hitItem.recallSession}-${hitItem.logId}`"
                        class="memory-trace-timeline__card memory-trace-timeline__card--recall"
                    >
                        <div class="flex items-center justify-between gap-3 mb-2">
                            <span class="memory-trace-timeline__rank">
                                排名 #{{ hitItem.rank }}
                            </span>
                            <span v-if="hitItem.score != null" class="text-xs text-gray-500">
                                score {{ formatScore(hitItem.score) }}
                            </span>
                        </div>
                        <p v-if="hitItem.task" class="memory-trace-timeline__task">
                            {{ hitItem.task }}
                        </p>
                        <p v-if="hitItem.queryText" class="memory-trace-timeline__query">
                            Query：{{ truncateText(hitItem.queryText, 120) }}
                        </p>
                        <dl class="memory-trace-timeline__meta-grid">
                            <div>
                                <dt>Session</dt>
                                <dd class="memory-trace-timeline__mono">{{ hitItem.recallSession || '—' }}</dd>
                            </div>
                            <div>
                                <dt>模块 / 仓库</dt>
                                <dd>{{ formatRepoModule(hitItem.repository, hitItem.module) }}</dd>
                            </div>
                            <div>
                                <dt>Recall 时间</dt>
                                <dd>{{ formatDateTime(hitItem.createTime) || '—' }}</dd>
                            </div>
                            <div>
                                <dt>Trace ID</dt>
                                <dd class="memory-trace-timeline__mono">{{ hitItem.traceId || '—' }}</dd>
                            </div>
                            <div v-if="hitItem.fallbackUsed">
                                <dt>检索模式</dt>
                                <dd>
                                    <span class="memory-trace-timeline__fallback-tag">Fallback 降级</span>
                                </dd>
                            </div>
                            <div v-if="hitItem.itemCount != null">
                                <dt>返回条数</dt>
                                <dd>{{ hitItem.itemCount }}</dd>
                            </div>
                        </dl>
                        <div
                            v-if="formatScoreBreakdownEntries(hitItem.scoreBreakdown).length"
                            class="memory-trace-timeline__breakdown"
                        >
                            <span
                                v-for="breakdownItem in formatScoreBreakdownEntries(hitItem.scoreBreakdown)"
                                :key="`${hitItem.logId}-${breakdownItem.key}`"
                                class="memory-trace-timeline__breakdown-chip"
                            >
                                {{ breakdownItem.label }} {{ breakdownItem.value }}
                            </span>
                        </div>
                        <div class="memory-trace-timeline__actions">
                            <el-button
                                size="small"
                                type="primary"
                                plain
                                @click="openRecallDetail(hitItem)"
                            >
                                查看召回详情
                            </el-button>
                        </div>
                    </div>
                </div>
                <div v-else class="memory-trace-timeline__card memory-trace-timeline__card--hint">
                    <p class="memory-trace-timeline__empty-title">暂无 Recall 命中记录</p>
                    <p class="memory-trace-timeline__empty-desc">
                        请确认知识已发布并向量化，然后在 Cursor 中对同模块任务调用
                        <code>memory_recall</code>，再回到此页刷新查询。
                    </p>
                </div>
            </div>
        </section>

        <RecallLogDetailDrawer
            :visible="recallDetailVisible"
            :log-id="selectedRecallLogId"
            :highlight-knowledge-id="selectedRecallKnowledgeId"
            @update:visible="recallDetailVisible = $event"
        />
    </div>
</template>

<script setup>
import RecallLogDetailDrawer from '~/components/trace/RecallLogDetailDrawer.vue'
import {
    DocumentChecked,
    Promotion,
    Search,
    Upload,
} from '@element-plus/icons-vue'
import {
    KNOWLEDGE_LIFECYCLE,
    KNOWLEDGE_LIFECYCLE_LABELS,
    KNOWLEDGE_TYPE_LABELS,
} from '~/constants/knowledge'
import { formatDateTime } from '~/utils/knowledge'
import {
    buildKnowledgeDetailPath,
    formatRecallScore,
    formatReviewAction,
    formatReviewStatus,
    formatScoreBreakdownEntries,
    hasTopOneRecallHit,
    sortRecallHitsByTime,
} from '~/utils/memoryTrace'

const props = defineProps({
    traceResult: {
        type: Object,
        required: true,
    },
})

const router = useRouter()

const recallDetailVisible = ref(false)
const selectedRecallLogId = ref(null)
const selectedRecallKnowledgeId = ref(null)
/** 是否展开全部 Recall 命中，默认仅展示最近一次 */
const showAllRecallHits = ref(false)

const systemEvent = computed(() => props.traceResult?.systemEvent || null)
const captureDraft = computed(() => props.traceResult?.captureDraft || null)
const knowledgeList = computed(() => props.traceResult?.knowledgeList || [])
const recallHits = computed(() => sortRecallHitsByTime(props.traceResult?.recallHits || []))
const displayedRecallHits = computed(() => {
    if (showAllRecallHits.value) {
        return recallHits.value
    }
    return recallHits.value.length ? [recallHits.value[0]] : []
})

watch(
    () => props.traceResult?.entryId,
    () => {
        showAllRecallHits.value = false
    },
)

const hasPublishedKnowledge = computed(() => {
    return knowledgeList.value.some(
        (item) => item.lifecycleStatus === KNOWLEDGE_LIFECYCLE.PUBLISHED,
    )
})

const topOneHit = computed(() => hasTopOneRecallHit(recallHits.value))

const reviewBadgeClass = computed(() => {
    const status = captureDraft.value?.reviewStatus
    if (status === 1) {
        return 'memory-trace-timeline__badge--success'
    }
    if (status === 2) {
        return 'memory-trace-timeline__badge--warning'
    }
    return 'memory-trace-timeline__badge--pending'
})

function formatRepoModule(repository, moduleName) {
    const parts = []
    if (repository) {
        parts.push(repository)
    }
    if (moduleName) {
        parts.push(moduleName)
    }
    return parts.length ? parts.join(' · ') : '—'
}

function lifecycleTagType(lifecycleStatus) {
    if (lifecycleStatus === KNOWLEDGE_LIFECYCLE.PUBLISHED) {
        return 'success'
    }
    if (lifecycleStatus === KNOWLEDGE_LIFECYCLE.DRAFT) {
        return 'warning'
    }
    return 'info'
}

function formatScore(scoreValue) {
    return formatRecallScore(scoreValue)
}

/**
 * 打开 Recall 详情抽屉，便于分析单次召回输入输出
 */
function openRecallDetail(hitItem) {
    selectedRecallLogId.value = hitItem.logId
    selectedRecallKnowledgeId.value = hitItem.knowledgeId
    recallDetailVisible.value = true
}

function truncateText(textValue, maxLength) {
    if (!textValue) {
        return ''
    }
    if (textValue.length <= maxLength) {
        return textValue
    }
    return `${textValue.slice(0, maxLength)}...`
}
</script>

<style scoped>
.memory-trace-timeline {
    position: relative;
}

.memory-trace-timeline__step {
    position: relative;
    display: flex;
    gap: 16px;
    padding-bottom: 28px;
}

.memory-trace-timeline__step:not(.memory-trace-timeline__step--last)::before {
    content: '';
    position: absolute;
    left: 15px;
    top: 32px;
    bottom: 0;
    width: 2px;
    background: linear-gradient(180deg, #c7d2fe 0%, #e5e7eb 100%);
}

.memory-trace-timeline__marker {
    position: relative;
    z-index: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border-radius: 9999px;
    border: 2px solid #e5e7eb;
    background: #fff;
    color: #9ca3af;
    flex-shrink: 0;
}

.memory-trace-timeline__marker--done {
    border-color: #818cf8;
    background: #eef2ff;
    color: #4f46e5;
}

.memory-trace-timeline__marker--success {
    border-color: #34d399;
    background: #ecfdf5;
    color: #059669;
}

.memory-trace-timeline__marker--pending {
    border-color: #e5e7eb;
    background: #f9fafb;
    color: #9ca3af;
}

.memory-trace-timeline__content {
    flex: 1;
    min-width: 0;
}

.memory-trace-timeline__head {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 12px;
}

.memory-trace-timeline__toggle {
    margin-left: auto;
}

.memory-trace-timeline__title {
    font-size: 15px;
    font-weight: 600;
    color: #111827;
}

.memory-trace-timeline__badge {
    display: inline-flex;
    align-items: center;
    padding: 2px 10px;
    border-radius: 9999px;
    font-size: 12px;
    font-weight: 500;
    background: #f3f4f6;
    color: #6b7280;
}

.memory-trace-timeline__badge--done {
    background: #eef2ff;
    color: #4338ca;
}

.memory-trace-timeline__badge--success {
    background: #ecfdf5;
    color: #047857;
}

.memory-trace-timeline__badge--warning {
    background: #fffbeb;
    color: #b45309;
}

.memory-trace-timeline__badge--pending {
    background: #fef3c7;
    color: #b45309;
}

.memory-trace-timeline__card {
    padding: 16px;
    border-radius: 12px;
    border: 1px solid #e5e7eb;
    background: #fff;
}

.memory-trace-timeline__card--recall {
    border-color: #bbf7d0;
    background: linear-gradient(135deg, #f0fdf4 0%, #fff 100%);
}

.memory-trace-timeline__card--hint {
    border-style: dashed;
    background: #fafafa;
}

.memory-trace-timeline__card-title {
    font-size: 14px;
    font-weight: 600;
    color: #111827;
    line-height: 1.5;
}

.memory-trace-timeline__meta-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
    gap: 12px 16px;
    margin: 0;
}

.memory-trace-timeline__meta-grid dt {
    font-size: 11px;
    color: #9ca3af;
    margin-bottom: 2px;
}

.memory-trace-timeline__meta-grid dd {
    margin: 0;
    font-size: 13px;
    color: #374151;
    word-break: break-all;
}

.memory-trace-timeline__mono {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
}

.memory-trace-timeline__rank {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    border-radius: 6px;
    font-size: 12px;
    font-weight: 600;
    color: #047857;
    background: #d1fae5;
}

.memory-trace-timeline__task {
    font-size: 13px;
    color: #4b5563;
    line-height: 1.5;
    margin-bottom: 10px;
}

.memory-trace-timeline__query {
    font-size: 12px;
    color: #6b7280;
    line-height: 1.5;
    margin-bottom: 10px;
}

.memory-trace-timeline__fallback-tag {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    border-radius: 6px;
    font-size: 11px;
    font-weight: 500;
    color: #b45309;
    background: #fef3c7;
}

.memory-trace-timeline__breakdown {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 12px;
}

.memory-trace-timeline__breakdown-chip {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    border-radius: 6px;
    font-size: 11px;
    color: #4b5563;
    background: #f3f4f6;
}

.memory-trace-timeline__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px solid #f3f4f6;
}

.memory-trace-timeline__empty,
.memory-trace-timeline__empty-desc {
    font-size: 13px;
    color: #9ca3af;
    line-height: 1.6;
}

.memory-trace-timeline__empty-title {
    font-size: 14px;
    font-weight: 500;
    color: #6b7280;
    margin-bottom: 6px;
}

.memory-trace-timeline__empty-desc code {
    padding: 1px 6px;
    border-radius: 4px;
    background: #f3f4f6;
    font-size: 12px;
    color: #374151;
}
</style>
