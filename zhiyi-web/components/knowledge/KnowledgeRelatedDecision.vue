<template>
    <div class="related-decision" :class="{ 'related-decision--compact': compact }">
        <header class="related-decision__head">
            <h3 class="related-decision__title">
                <span class="related-decision__icon">
                    <el-icon :size="13"><Document /></el-icon>
                </span>
                <span class="related-decision__title-text">关联决策</span>
                <span v-if="decisionList.length" class="related-decision__count">{{ decisionList.length }}</span>
            </h3>
        </header>

        <el-skeleton v-if="loading" :rows="compact ? 2 : 3" animated />

        <div v-else-if="!decisionList.length" class="related-panel__empty">
            <p class="related-panel__empty-text">暂无关联决策</p>
            <p class="related-panel__empty-hint">
                {{ emptyHint }}
            </p>
        </div>

        <ul v-else class="related-decision__list">
            <li
                v-for="item in decisionList"
                :key="item.id"
                class="related-decision__item"
            >
                <NuxtLink :to="resolveDetailPath(item)" class="related-decision__link">
                    <div class="related-decision__row">
                        <p class="related-decision__item-title">{{ item.partnerTitle }}</p>
                        <span class="related-decision__type-badge">{{ formatKnowledgeType(item.partnerKnowledgeType) }}</span>
                    </div>
                    <p v-if="item.partnerModule" class="related-decision__module">
                        模块：{{ item.partnerModule }}
                    </p>
                    <div class="related-decision__meta">
                        <span class="related-decision__source-badge" :class="sourceBadgeClass(item.relationSource)">
                            {{ formatRelationSource(item.relationSource) }}
                        </span>
                        <span
                            v-if="item.confidence != null && item.relationSource === 'auto'"
                            class="related-decision__confidence"
                        >
                            置信度 {{ formatConfidence(item.confidence) }}
                        </span>
                    </div>
                </NuxtLink>
            </li>
        </ul>
    </div>
</template>

<script setup>
import { Document } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { KNOWLEDGE_TYPE_LABELS } from '~/constants/knowledge'
import { resolveKnowledgeDetailPath } from '~/constants/graph'
import { fetchKnowledgeRelatedDecisions } from '~/services/knowledge.service'

const props = defineProps({
    /** 当前知识 ID */
    knowledgeId: {
        type: [String, Number],
        default: null,
    },
    /** 当前知识类型，用于空状态提示文案 */
    knowledgeType: {
        type: String,
        default: 'experience',
    },
    /** 侧边栏紧凑模式 */
    compact: {
        type: Boolean,
        default: false,
    },
})

const loading = ref(false)
const decisionList = ref([])

/** 空状态说明：按当前页面知识类型区分提示 */
const emptyHint = computed(() => {
    if (props.knowledgeType === 'decision') {
        return '同模块下发布的经验将自动建立关联决策边，也可由编辑者手工关联'
    }
    return '同模块下发布的 Decision 将自动关联；也可由编辑者手工建立关联决策边'
})

/** 加载关联决策列表，按人工边优先、置信度降序排列 */
async function loadDecisionList(options = {}) {
    if (!props.knowledgeId) {
        decisionList.value = []
        return
    }
    if (!options.silent) {
        loading.value = true
    }
    try {
        const relationList = await fetchKnowledgeRelatedDecisions(props.knowledgeId) || []
        decisionList.value = sortDecisionList(relationList)
    } catch (error) {
        decisionList.value = []
        if (!options.silent) {
            ElMessage.error(error.message || '加载关联决策失败')
        }
    } finally {
        if (!options.silent) {
            loading.value = false
        }
    }
}

/** 排序：人工边优先，其次按置信度降序 */
function sortDecisionList(relationList) {
    return [...relationList].sort((left, right) => {
        const leftManualScore = left.relationSource === 'manual' ? 1 : 0
        const rightManualScore = right.relationSource === 'manual' ? 1 : 0
        if (rightManualScore !== leftManualScore) {
            return rightManualScore - leftManualScore
        }
        const leftConfidence = left.confidence ?? 0
        const rightConfidence = right.confidence ?? 0
        return rightConfidence - leftConfidence
    })
}

/** 根据对端知识类型生成详情页路径 */
function resolveDetailPath(item) {
    const detailPath = resolveKnowledgeDetailPath(item.partnerKnowledgeType)
    return `${detailPath}/${item.partnerId}`
}

/** 知识类型中文标签 */
function formatKnowledgeType(knowledgeType) {
    return KNOWLEDGE_TYPE_LABELS[knowledgeType] || knowledgeType || '知识'
}

/** 建边来源中文标签 */
function formatRelationSource(relationSource) {
    if (relationSource === 'manual') {
        return '人工关联'
    }
    if (relationSource === 'merge') {
        return '合并导入'
    }
    return '自动关联'
}

/** 建边来源对应样式 */
function sourceBadgeClass(relationSource) {
    if (relationSource === 'manual') {
        return 'related-decision__source-badge--manual'
    }
    return 'related-decision__source-badge--auto'
}

/** 格式化置信度为百分比 */
function formatConfidence(confidence) {
    return `${Math.round(confidence * 100)}%`
}

watch(
    () => props.knowledgeId,
    () => {
        loadDecisionList()
    },
    { immediate: true },
)
</script>

<style scoped>
.related-decision {
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 10px;
    padding: 12px 14px;
}

.related-decision--compact {
    padding: 10px 12px;
}

.related-decision__head {
    margin-bottom: 10px;
}

.related-decision__title {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0;
    font-size: 13px;
    font-weight: 600;
    color: #111827;
}

.related-decision__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    border-radius: 6px;
    color: #9333ea;
    background: rgba(147, 51, 234, 0.1);
    flex-shrink: 0;
}

.related-decision__title-text {
    flex: 1;
    min-width: 0;
}

.related-decision__count {
    flex-shrink: 0;
    min-width: 18px;
    padding: 0 6px;
    border-radius: 9999px;
    font-size: 11px;
    font-weight: 500;
    line-height: 18px;
    text-align: center;
    color: #9333ea;
    background: rgba(147, 51, 234, 0.08);
}

.related-decision__list {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.related-decision--compact .related-decision__list {
    max-height: 240px;
    overflow-y: auto;
    padding-right: 2px;
    scrollbar-width: thin;
    scrollbar-color: #d1d5db transparent;
}

.related-decision__item {
    border: 1px solid #f3e8ff;
    border-radius: 8px;
    background: #faf5ff;
    transition: border-color 0.15s, background 0.15s, box-shadow 0.15s;
}

.related-decision__item:hover {
    border-color: rgba(147, 51, 234, 0.28);
    background: rgba(147, 51, 234, 0.04);
    box-shadow: 0 1px 2px rgba(147, 51, 234, 0.06);
}

.related-decision__link {
    display: block;
    padding: 8px 10px;
    text-decoration: none;
    color: inherit;
}

.related-decision__row {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 8px;
}

.related-decision__item-title {
    margin: 0;
    flex: 1;
    min-width: 0;
    font-size: 12px;
    font-weight: 600;
    color: #111827;
    line-height: 1.45;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.related-decision__type-badge {
    flex-shrink: 0;
    padding: 0 6px;
    border-radius: 9999px;
    font-size: 10px;
    line-height: 16px;
    color: #7e22ce;
    background: rgba(147, 51, 234, 0.12);
}

.related-decision__module {
    margin: 4px 0 0;
    font-size: 10px;
    color: #6b7280;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.related-decision__meta {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
    margin-top: 5px;
}

.related-decision__source-badge {
    display: inline-block;
    padding: 0 6px;
    border-radius: 9999px;
    font-size: 10px;
    line-height: 16px;
}

.related-decision__source-badge--auto {
    color: #92400e;
    background: #fef3c7;
}

.related-decision__source-badge--manual {
    color: #1d4ed8;
    background: #dbeafe;
}

.related-decision__confidence {
    font-size: 10px;
    color: #9ca3af;
}
</style>
