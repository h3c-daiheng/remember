<template>
    <!-- 搜索结果卡片：展示排序、得分与 Fact 预览 -->
    <article class="search-result-card">
        <div class="search-result-card__header">
            <div
                class="search-result-card__rank"
                :class="{ 'search-result-card__rank--top': recallItem.rank <= 3 }"
            >
                #{{ recallItem.rank }}
            </div>
            <div class="search-result-card__main">
                <div class="search-result-card__title-row">
                    <NuxtLink
                        v-if="detailPath"
                        :to="detailPath"
                        class="search-result-card__title"
                    >
                        {{ recallItem.title || '未命名' }}
                    </NuxtLink>
                    <span v-else class="search-result-card__title search-result-card__title--static">
                        {{ recallItem.title || '未命名' }}
                    </span>
                    <span
                        v-if="recallItem.knowledgeType"
                        class="search-result-card__type"
                    >
                        {{ KNOWLEDGE_TYPE_LABELS[recallItem.knowledgeType] || recallItem.knowledgeType }}
                    </span>
                </div>
                <p class="search-result-card__meta">
                    知识 #{{ recallItem.knowledgeId }}
                </p>
            </div>
            <div class="search-result-card__score">
                <span class="search-result-card__score-label">综合分</span>
                <span class="search-result-card__score-value">{{ formatRecallScore(recallItem.score) }}</span>
            </div>
        </div>

        <div
            v-if="scoreBreakdownEntries.length"
            class="search-result-card__breakdown"
        >
            <span
                v-for="breakdownItem in scoreBreakdownEntries"
                :key="breakdownItem.key"
                class="search-result-card__breakdown-chip"
            >
                {{ breakdownItem.label }} {{ breakdownItem.value }}
            </span>
        </div>

        <div v-if="previewFacts.length" class="search-result-card__facts">
            <div
                v-for="(factItem, factIndex) in previewFacts"
                :key="`${factItem.type}-${factIndex}`"
                class="search-result-card__fact"
            >
                <span
                    class="search-result-card__fact-badge"
                    :class="getFactTypeTheme(factItem.type).badge"
                >
                    {{ FACT_TYPE_LABELS[factItem.type] || factItem.type }}
                </span>
                <p class="search-result-card__fact-text">{{ truncateFactText(factItem.text) }}</p>
            </div>
            <p v-if="hiddenFactCount > 0" class="search-result-card__fact-more">
                另有 {{ hiddenFactCount }} 条 Fact 未展示，点击查看详情
            </p>
        </div>
    </article>
</template>

<script setup>
import { FACT_TYPE_LABELS, KNOWLEDGE_TYPE_LABELS, getFactTypeTheme } from '~/constants/knowledge'
import { buildKnowledgeDetailPath, formatRecallScore, formatScoreBreakdownEntries } from '~/utils/memoryTrace'

const props = defineProps({
    /** Recall 返回的单条结果，含 rank */
    recallItem: {
        type: Object,
        required: true,
    },
    /** Fact 预览条数上限 */
    previewLimit: {
        type: Number,
        default: 3,
    },
    /** 单条 Fact 文本截断长度 */
    factTextLimit: {
        type: Number,
        default: 160,
    },
})

/** 详情页路径 */
const detailPath = computed(() => buildKnowledgeDetailPath({
    knowledgeId: props.recallItem.knowledgeId,
    knowledgeType: props.recallItem.knowledgeType,
}))

/** 排序分解展示项 */
const scoreBreakdownEntries = computed(() =>
    formatScoreBreakdownEntries(props.recallItem.scoreBreakdown),
)

/** 用于卡片预览的 Fact 列表 */
const previewFacts = computed(() => {
    const factList = props.recallItem.facts || []
    return factList.slice(0, props.previewLimit)
})

/** 未展示的 Fact 数量 */
const hiddenFactCount = computed(() => {
    const factList = props.recallItem.facts || []
    return Math.max(factList.length - props.previewLimit, 0)
})

/** 截断 Fact 文本，避免卡片过高 */
function truncateFactText(textValue) {
    const normalizedText = String(textValue || '').replace(/\s+/g, ' ').trim()
    if (normalizedText.length <= props.factTextLimit) {
        return normalizedText
    }
    return `${normalizedText.slice(0, props.factTextLimit)}…`
}
</script>

<style scoped>
.search-result-card {
    background: #fff;
    border: 1px solid #e8eaef;
    border-radius: 14px;
    padding: 20px 22px;
    box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
    transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.search-result-card:hover {
    border-color: #ced1fd;
    box-shadow: 0 4px 16px rgba(93, 101, 249, 0.08);
    transform: translateY(-1px);
}

.search-result-card__header {
    display: flex;
    align-items: flex-start;
    gap: 14px;
}

.search-result-card__rank {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 34px;
    border-radius: 10px;
    background: #f3f4f6;
    color: #9ca3af;
    font-size: 13px;
    font-weight: 700;
    flex-shrink: 0;
    font-variant-numeric: tabular-nums;
}

.search-result-card__rank--top {
    background: linear-gradient(135deg, #eff0fe 0%, #dfe0fe 100%);
    color: #5d65f9;
}

.search-result-card__main {
    min-width: 0;
    flex: 1;
}

.search-result-card__title-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    margin-bottom: 4px;
}

.search-result-card__title {
    font-size: 16px;
    font-weight: 600;
    color: #111827;
    text-decoration: none;
    line-height: 1.4;
    transition: color 0.15s ease;
}

.search-result-card__title:hover {
    color: #5d65f9;
}

.search-result-card__title--static {
    display: inline-block;
}

.search-result-card__type {
    display: inline-block;
    padding: 2px 8px;
    border-radius: 6px;
    font-size: 11px;
    font-weight: 500;
    color: #6b7280;
    background: #f3f4f6;
}

.search-result-card__meta {
    margin: 0;
    font-size: 12px;
    color: #9ca3af;
}

.search-result-card__score {
    flex-shrink: 0;
    text-align: right;
    padding: 6px 12px;
    border-radius: 10px;
    background: #f9fafb;
    border: 1px solid #f3f4f6;
}

.search-result-card__score-label {
    display: block;
    font-size: 10px;
    color: #9ca3af;
    margin-bottom: 2px;
    text-transform: uppercase;
    letter-spacing: 0.03em;
}

.search-result-card__score-value {
    font-size: 16px;
    font-weight: 700;
    color: #111827;
    font-variant-numeric: tabular-nums;
}

.search-result-card__breakdown {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 14px;
}

.search-result-card__breakdown-chip {
    display: inline-flex;
    align-items: center;
    padding: 3px 10px;
    border-radius: 999px;
    font-size: 11px;
    color: #6b7280;
    background: #f9fafb;
    border: 1px solid #f3f4f6;
}

.search-result-card__facts {
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid #f3f4f6;
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.search-result-card__fact {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    padding: 10px 12px;
    border-radius: 8px;
    background: #fafafa;
}

.search-result-card__fact-badge {
    flex-shrink: 0;
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 11px;
    font-weight: 500;
}

.search-result-card__fact-text {
    margin: 0;
    font-size: 13px;
    line-height: 1.55;
    color: #4b5563;
    white-space: pre-wrap;
    word-break: break-word;
}

.search-result-card__fact-more {
    margin: 0;
    font-size: 12px;
    color: #9ca3af;
    padding-left: 4px;
}
</style>
