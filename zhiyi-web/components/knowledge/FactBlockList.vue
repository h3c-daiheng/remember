<template>
    <div class="fact-block-list">
        <div v-if="!factGroups.length" class="fact-block-list__empty">
            <p class="fact-block-list__empty-title">暂无 Fact Block 内容</p>
        </div>

        <div v-else class="fact-block-list__groups">
            <article
                v-for="(group, groupIndex) in factGroups"
                :key="group.type"
                class="fact-block-list__group"
            >
                <!-- 左侧色条 + 右侧内容，标题与正文同区 -->
                <div
                    class="fact-block-list__accent"
                    :class="getFactTypeTheme(group.type).accent"
                />
                <div class="fact-block-list__body">
                    <header class="fact-block-list__head">
                        <span
                            class="fact-block-list__type-badge"
                            :class="getFactTypeTheme(group.type).badge"
                        >
                            {{ FACT_TYPE_LABELS[group.type] || group.type }}
                        </span>
                        <span class="fact-block-list__index">
                            {{ groupIndex + 1 }}/{{ factGroups.length }}
                        </span>
                    </header>

                    <ul class="fact-block-list__items">
                        <li
                            v-for="(fact, index) in group.items"
                            :key="index"
                            class="fact-block-list__item"
                        >
                            <KnowledgeMarkdownContent :content="fact.text" />
                        </li>
                    </ul>
                </div>
            </article>
        </div>
    </div>
</template>

<script setup>
import { FACT_TYPE_LABELS, getFactTypeTheme } from '~/constants/knowledge'

defineProps({
    /** 已分组的 Fact 列表，或由 groupFactsByType 生成 */
    factGroups: {
        type: Array,
        default: () => [],
    },
})
</script>

<style scoped>
.fact-block-list__empty {
    padding: 20px 16px;
    background: #fff;
    border: 1px dashed #e5e7eb;
    border-radius: 10px;
    text-align: center;
}

.fact-block-list__empty-title {
    margin: 0;
    font-size: 13px;
    color: #9ca3af;
}

.fact-block-list__groups {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.fact-block-list__group {
    display: flex;
    gap: 0;
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 10px;
    overflow: hidden;
}

.fact-block-list__accent {
    flex-shrink: 0;
    width: 3px;
}

.fact-block-list__body {
    flex: 1;
    min-width: 0;
    padding: 10px 14px 12px;
}

.fact-block-list__head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 6px;
}

.fact-block-list__type-badge {
    display: inline-flex;
    align-items: center;
    padding: 1px 8px;
    border-radius: 6px;
    font-size: 12px;
    font-weight: 600;
    line-height: 20px;
}

.fact-block-list__index {
    font-size: 11px;
    color: #d1d5db;
    font-variant-numeric: tabular-nums;
}

.fact-block-list__items {
    list-style: none;
    margin: 0;
    padding: 0;
}

.fact-block-list__item + .fact-block-list__item {
    margin-top: 8px;
    padding-top: 8px;
    border-top: 1px solid #f3f4f6;
}
</style>
