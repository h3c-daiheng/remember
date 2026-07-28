<template>
    <div class="knowledge-timeline" :class="{ 'knowledge-timeline--compact': compact }">
        <header class="knowledge-timeline__head">
            <h3 class="knowledge-timeline__title">
                <span class="knowledge-timeline__icon">
                    <el-icon :size="13"><Clock /></el-icon>
                </span>
                <span class="knowledge-timeline__title-text">版本时间线</span>
                <span v-if="timelineList.length" class="knowledge-timeline__count">{{ timelineList.length }}</span>
            </h3>
        </header>

        <el-skeleton v-if="loading" :rows="compact ? 2 : 4" animated />

        <div v-else-if="!timelineList.length" class="related-panel__empty">
            <p class="related-panel__empty-text">暂无版本记录</p>
            <p class="related-panel__empty-hint">
                发布、失效、替代演进等治理操作将在此追溯
            </p>
        </div>

        <ol v-else class="knowledge-timeline__list">
            <li
                v-for="item in timelineList"
                :key="item.id"
                class="knowledge-timeline__item"
            >
                <div class="knowledge-timeline__marker" :class="markerClass(item.eventType)" />
                <div class="knowledge-timeline__content">
                    <div class="knowledge-timeline__row">
                        <span class="knowledge-timeline__event-type">{{ formatEventType(item.eventType) }}</span>
                        <time class="knowledge-timeline__time">{{ formatTime(item.createTime) }}</time>
                    </div>
                    <p class="knowledge-timeline__summary">{{ item.eventSummary }}</p>
                    <NuxtLink
                        v-if="item.relatedKnowledgeId"
                        :to="`/experience/${item.relatedKnowledgeId}`"
                        class="knowledge-timeline__related-link"
                    >
                        {{ item.relatedKnowledgeTitle || `经验 #${item.relatedKnowledgeId}` }}
                    </NuxtLink>
                </div>
            </li>
        </ol>
    </div>
</template>

<script setup>
import { Clock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchKnowledgeTimeline } from '~/services/knowledge.service'

const props = defineProps({
    /** 当前经验 ID */
    knowledgeId: {
        type: [String, Number],
        default: null,
    },
    /** 侧边栏紧凑模式 */
    compact: {
        type: Boolean,
        default: false,
    },
})

const loading = ref(false)
const timelineList = ref([])

/** 事件类型中文映射 */
const eventTypeLabelMap = {
    publish: '发布',
    deprecate: '失效',
    reactivate: '重新启用',
    supersede: '替代演进',
    cascade_review_hint: '级联 Review',
}

/** 加载版本时间线 */
async function loadTimeline(options = {}) {
    if (!props.knowledgeId) {
        timelineList.value = []
        return
    }
    if (!options.silent) {
        loading.value = true
    }
    try {
        timelineList.value = await fetchKnowledgeTimeline(props.knowledgeId) || []
    } catch (error) {
        if (!options.silent) {
            ElMessage.error(error.message || '加载版本时间线失败')
        }
    } finally {
        if (!options.silent) {
            loading.value = false
        }
    }
}

/** 格式化事件类型 */
function formatEventType(eventType) {
    return eventTypeLabelMap[eventType] || eventType || '事件'
}

/** 格式化时间 */
function formatTime(value) {
    if (!value) {
        return '—'
    }
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) {
        return value
    }
    return date.toLocaleString('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
    })
}

/** 事件类型对应标记样式 */
function markerClass(eventType) {
    if (eventType === 'publish' || eventType === 'reactivate') {
        return 'knowledge-timeline__marker--success'
    }
    if (eventType === 'deprecate') {
        return 'knowledge-timeline__marker--danger'
    }
    if (eventType === 'cascade_review_hint') {
        return 'knowledge-timeline__marker--warning'
    }
    return 'knowledge-timeline__marker--default'
}

watch(
    () => props.knowledgeId,
    () => {
        loadTimeline()
    },
    { immediate: true },
)
</script>

<style scoped>
.knowledge-timeline {
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 10px;
    padding: 12px 14px;
}

.knowledge-timeline--compact {
    padding: 10px 12px;
}

.knowledge-timeline__head {
    margin-bottom: 10px;
}

.knowledge-timeline__title {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0;
    font-size: 13px;
    font-weight: 600;
    color: #111827;
}

.knowledge-timeline__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    border-radius: 6px;
    color: #0891b2;
    background: rgba(8, 145, 178, 0.1);
    flex-shrink: 0;
}

.knowledge-timeline__title-text {
    flex: 1;
    min-width: 0;
}

.knowledge-timeline__count {
    flex-shrink: 0;
    min-width: 18px;
    padding: 0 6px;
    border-radius: 9999px;
    font-size: 11px;
    font-weight: 500;
    line-height: 18px;
    text-align: center;
    color: #0891b2;
    background: rgba(8, 145, 178, 0.08);
}

.knowledge-timeline__list {
    list-style: none;
    margin: 0;
    padding: 0 0 0 4px;
}

.knowledge-timeline--compact .knowledge-timeline__list {
    max-height: 260px;
    overflow-y: auto;
    padding-right: 2px;
    scrollbar-width: thin;
    scrollbar-color: #d1d5db transparent;
}

.knowledge-timeline__item {
    position: relative;
    display: flex;
    gap: 10px;
    padding-bottom: 14px;
}

.knowledge-timeline__item:not(:last-child)::before {
    content: '';
    position: absolute;
    left: 3px;
    top: 12px;
    bottom: 0;
    width: 1px;
    background: #e5e7eb;
}

.knowledge-timeline__item:last-child {
    padding-bottom: 0;
}

.knowledge-timeline__marker {
    position: relative;
    z-index: 1;
    flex-shrink: 0;
    width: 8px;
    height: 8px;
    margin-top: 5px;
    border-radius: 50%;
    background: #94a3b8;
    box-shadow: 0 0 0 2px #fff;
}

.knowledge-timeline__marker--success {
    background: #10b981;
}

.knowledge-timeline__marker--danger {
    background: #ef4444;
}

.knowledge-timeline__marker--warning {
    background: #f59e0b;
}

.knowledge-timeline__marker--default {
    background: #6366f1;
}

.knowledge-timeline__content {
    flex: 1;
    min-width: 0;
}

.knowledge-timeline__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
}

.knowledge-timeline__event-type {
    font-size: 12px;
    font-weight: 600;
    color: #374151;
}

.knowledge-timeline__time {
    font-size: 10px;
    color: #9ca3af;
    white-space: nowrap;
}

.knowledge-timeline__summary {
    margin: 3px 0 0;
    font-size: 11px;
    color: #6b7280;
    line-height: 1.5;
}

.knowledge-timeline__related-link {
    display: inline-block;
    margin-top: 4px;
    font-size: 11px;
    color: #5d65f9;
    text-decoration: none;
}

.knowledge-timeline__related-link:hover {
    text-decoration: underline;
}

.knowledge-timeline--compact .knowledge-timeline__summary {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}
</style>
