<template>
    <!-- 治理对比卡片：展示单条记忆的 Facts 摘要 -->
    <div
        class="governance-compare-card"
        :class="[
            highlight ? 'governance-compare-card--highlight' : '',
            `governance-compare-card--${tone}`,
        ]"
    >
        <div v-if="highlight" class="governance-compare-card__ribbon">推荐保留</div>

        <div class="governance-compare-card__header">
            <div class="min-w-0">
                <NuxtLink
                    :to="detailPath"
                    class="governance-compare-card__title"
                    target="_blank"
                >
                    <span class="governance-compare-card__id">#{{ knowledge.id }}</span>
                    {{ knowledge.title }}
                </NuxtLink>
                <KnowledgeMeta
                    v-if="hasMeta"
                    :knowledge="knowledge"
                    variant="pills"
                    class="mt-2"
                />
            </div>
            <div class="governance-compare-card__stats">
                <span class="governance-compare-card__stat-label">召回</span>
                <strong class="governance-compare-card__stat-value">{{ knowledge.recallCount || 0 }}</strong>
            </div>
        </div>

        <div v-if="factList.length" class="governance-compare-card__facts">
            <div
                v-for="(fact, index) in factList"
                :key="index"
                class="governance-compare-card__fact"
            >
                <span class="governance-compare-card__fact-type">{{ fact.type || 'fact' }}</span>
                <span class="governance-compare-card__fact-text">{{ fact.text }}</span>
            </div>
            <p v-if="remainingFactCount > 0" class="governance-compare-card__more">
                还有 {{ remainingFactCount }} 条 Fact 未展示
            </p>
        </div>
        <p v-else class="governance-compare-card__empty">暂无 Fact</p>
    </div>
</template>

<script setup>
import { KNOWLEDGE_TYPES } from '~/constants/knowledge'

const props = defineProps({
    knowledge: {
        type: Object,
        required: true,
    },
    highlight: {
        type: Boolean,
        default: false,
    },
    tone: {
        type: String,
        default: 'neutral',
    },
})

/** 详情页路径 */
const detailPath = computed(() => {
    const knowledgeType = props.knowledge?.knowledgeType || KNOWLEDGE_TYPES.EXPERIENCE
    if (knowledgeType === KNOWLEDGE_TYPES.RULE || knowledgeType === KNOWLEDGE_TYPES.WORKFLOW) {
        return `/rule/${props.knowledge.id}`
    }
    if (knowledgeType === KNOWLEDGE_TYPES.DECISION) {
        return `/decision/${props.knowledge.id}`
    }
    return `/experience/${props.knowledge.id}`
})

/** 最多展示 4 条 Fact */
const factList = computed(() => (props.knowledge?.facts || []).slice(0, 4))

const remainingFactCount = computed(() => {
    const total = props.knowledge?.facts?.length || 0
    return Math.max(total - 4, 0)
})

const hasMeta = computed(() =>
    props.knowledge?.project
    || props.knowledge?.module
    || props.knowledge?.repository,
)
</script>

<style scoped>
.governance-compare-card {
    position: relative;
    overflow: hidden;
    padding: 16px 18px;
    border-radius: 14px;
    border: 1px solid #e8eaef;
    background: #fff;
    transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.governance-compare-card:hover {
    box-shadow: 0 4px 12px rgba(15, 23, 42, 0.05);
}

.governance-compare-card--highlight {
    border-color: #a7f3d0;
    background: linear-gradient(145deg, #ecfdf5 0%, #fff 60%);
}

.governance-compare-card__ribbon {
    position: absolute;
    top: 12px;
    right: -28px;
    width: 100px;
    padding: 3px 0;
    font-size: 10px;
    font-weight: 600;
    text-align: center;
    color: #fff;
    background: #10b981;
    transform: rotate(32deg);
    letter-spacing: 0.02em;
}

.governance-compare-card__header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 12px;
}

.governance-compare-card__title {
    display: block;
    font-size: 14px;
    font-weight: 600;
    color: #111827;
    line-height: 1.5;
    text-decoration: none;
}

.governance-compare-card__title:hover {
    color: #5d65f9;
}

.governance-compare-card__id {
    margin-right: 4px;
    color: #9ca3af;
    font-weight: 500;
}

.governance-compare-card__stats {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    flex-shrink: 0;
    padding: 6px 10px;
    border-radius: 8px;
    background: #f9fafb;
    border: 1px solid #eef0f3;
}

.governance-compare-card__stat-label {
    font-size: 10px;
    color: #9ca3af;
}

.governance-compare-card__stat-value {
    font-size: 16px;
    font-weight: 700;
    color: #111827;
    font-variant-numeric: tabular-nums;
    line-height: 1.2;
}

.governance-compare-card__facts {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.governance-compare-card__fact {
    display: flex;
    gap: 8px;
    align-items: flex-start;
    padding: 8px 10px;
    border-radius: 8px;
    background: #f9fafb;
}

.governance-compare-card__fact-type {
    flex-shrink: 0;
    padding: 1px 6px;
    border-radius: 4px;
    font-size: 10px;
    font-weight: 600;
    text-transform: lowercase;
    color: #5d65f9;
    background: #eff0fe;
}

.governance-compare-card__fact-text {
    font-size: 12px;
    line-height: 1.6;
    color: #4b5563;
}

.governance-compare-card__more {
    margin: 0;
    font-size: 11px;
    color: #9ca3af;
}

.governance-compare-card__empty {
    margin: 0;
    font-size: 12px;
    color: #9ca3af;
    font-style: italic;
}
</style>
