<template>
    <!-- 页面级空态：流程步骤 + 标题 + 标签 + 底部引导，对齐产品主路径空态视觉 -->
    <div
        class="page-empty-state"
        :class="{
            'page-empty-state--compact': compact,
            'page-empty-state--bordered': bordered,
        }"
    >
        <div class="page-empty-state__inner">
            <div v-if="flowSteps.length" class="page-empty-state__flow">
                <div
                    v-for="(stepItem, stepIndex) in flowSteps"
                    :key="stepIndex"
                    class="page-empty-state__flow-group"
                >
                    <span
                        class="page-empty-state__flow-step"
                        :class="`page-empty-state__flow-step--${resolveFlowTone(stepIndex, stepItem)}`"
                    >
                        {{ resolveFlowLabel(stepItem) }}
                    </span>
                    <span
                        v-if="stepIndex < flowSteps.length - 1"
                        class="page-empty-state__flow-line"
                        aria-hidden="true"
                    />
                </div>
            </div>

            <h2 class="page-empty-state__title">{{ title }}</h2>

            <p v-if="subtitle" class="page-empty-state__subtitle">
                {{ subtitle }}
            </p>

            <div v-if="tags.length" class="page-empty-state__tags">
                <span
                    v-for="tagItem in tags"
                    :key="tagItem"
                    class="page-empty-state__tag"
                >
                    {{ tagItem }}
                </span>
            </div>

            <div v-if="hasGuideArea" class="page-empty-state__footer">
                <div class="page-empty-state__divider" />

                <p v-if="guide && !$slots.guide" class="page-empty-state__guide">
                    <template v-if="guidePrefix">{{ guidePrefix }}</template>
                    <kbd v-if="guideKey" class="page-empty-state__kbd">{{ guideKey }}</kbd>
                    <template v-if="guideMiddle">{{ guideMiddle }}</template>
                    <strong v-if="guideHighlight" class="page-empty-state__guide-highlight">
                        {{ guideHighlight }}
                    </strong>
                    <template v-if="guideSuffix">{{ guideSuffix }}</template>
                </p>

                <slot name="guide" />
            </div>

            <div v-if="$slots.default" class="page-empty-state__actions">
                <slot />
            </div>
        </div>
    </div>
</template>

<script setup>
/**
 * 智忆通用空态：参考主路径空态布局，支持流程步骤、能力标签与底部操作引导。
 * 放在 components/ 根目录，确保 Nuxt 自动注册为 PageEmptyState（非 CommonPageEmptyState）。
 */
const props = defineProps({
    /** 顶部流程步骤，字符串或 { label, tone } */
    flowSteps: {
        type: Array,
        default: () => [],
    },
    title: {
        type: String,
        required: true,
    },
    subtitle: {
        type: String,
        default: '',
    },
    /** 能力要点标签，2～4 个短词组为宜 */
    tags: {
        type: Array,
        default: () => [],
    },
    /** 完整引导文案（与 guidePrefix 等互斥，简单场景直接用） */
    guide: {
        type: String,
        default: '',
    },
    /** 引导前缀，如「在左侧对话输入 」 */
    guidePrefix: {
        type: String,
        default: '',
    },
    /** 键盘快捷键展示，如 / */
    guideKey: {
        type: String,
        default: '',
    },
    /** 快捷键后的连接文案 */
    guideMiddle: {
        type: String,
        default: '',
    },
    /** 高亮操作名，如「生成用户旅程」 */
    guideHighlight: {
        type: String,
        default: '',
    },
    /** 引导后缀 */
    guideSuffix: {
        type: String,
        default: '',
    },
    /** 紧凑模式：用于面板、统计卡片内嵌 */
    compact: {
        type: Boolean,
        default: false,
    },
    /** 是否展示虚线边框容器（列表页空态默认开启） */
    bordered: {
        type: Boolean,
        default: true,
    },
})

const slots = useSlots()

/** 流程步骤默认色调循环：蓝 / 紫 / 绿 */
const FLOW_TONE_SEQUENCE = ['blue', 'purple', 'green']

const hasGuideArea = computed(() => {
    return Boolean(
        props.guide
            || props.guidePrefix
            || props.guideHighlight
            || slots.guide,
    )
})

/** 解析流程步骤展示文案 */
function resolveFlowLabel(stepItem) {
    if (typeof stepItem === 'string') {
        return stepItem
    }
    return stepItem?.label || ''
}

/** 解析流程步骤色调 */
function resolveFlowTone(stepIndex, stepItem) {
    if (stepItem && typeof stepItem === 'object' && stepItem.tone) {
        return stepItem.tone
    }
    return FLOW_TONE_SEQUENCE[stepIndex % FLOW_TONE_SEQUENCE.length]
}
</script>

<style scoped>
.page-empty-state {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
}

.page-empty-state--bordered {
    padding: 56px 24px;
    background: #fff;
    border-radius: 12px;
    border: 1px solid #eef0f3;
}

.page-empty-state--compact.page-empty-state--bordered {
    padding: 36px 20px;
}

.page-empty-state__inner {
    /* 按内容撑开宽度，避免固定 max-width 导致流程/引导文案被迫换行 */
    width: fit-content;
    max-width: 100%;
    margin: 0 auto;
    text-align: center;
    overflow-x: auto;
    overflow-y: visible;
}

.page-empty-state__flow {
    display: flex;
    flex-wrap: nowrap;
    align-items: center;
    justify-content: center;
    gap: 0;
    margin-bottom: 28px;
}

.page-empty-state__flow-group {
    display: contents;
}

.page-empty-state__flow-step {
    display: inline-flex;
    flex-shrink: 0;
    align-items: center;
    justify-content: center;
    min-width: 56px;
    padding: 6px 16px;
    border-radius: 999px;
    font-size: 13px;
    font-weight: 500;
    line-height: 1.4;
    white-space: nowrap;
}

.page-empty-state__flow-step--blue {
    color: #2563eb;
    background: #eff6ff;
}

.page-empty-state__flow-step--purple {
    color: #6d28d9;
    background: #f3f0ff;
}

.page-empty-state__flow-step--green {
    color: #0f766e;
    background: #ecfdf5;
}

.page-empty-state__flow-line {
    flex: 0 0 28px;
    flex-shrink: 0;
    height: 1px;
    background: #e5e7eb;
}

.page-empty-state__title {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
    line-height: 1.45;
    color: #111827;
    letter-spacing: -0.01em;
}

.page-empty-state--compact .page-empty-state__title {
    font-size: 16px;
}

.page-empty-state__subtitle {
    margin: 10px auto 0;
    font-size: 14px;
    line-height: 1.65;
    color: #6b7280;
}

.page-empty-state--compact .page-empty-state__subtitle {
    font-size: 13px;
}

.page-empty-state__tags {
    display: flex;
    flex-wrap: nowrap;
    align-items: center;
    justify-content: center;
    gap: 8px;
    margin-top: 20px;
}

.page-empty-state__tag {
    display: inline-flex;
    flex-shrink: 0;
    align-items: center;
    padding: 5px 12px;
    border-radius: 999px;
    font-size: 12px;
    line-height: 1.4;
    white-space: nowrap;
    color: #4b5563;
    background: #f3f4f6;
}

.page-empty-state__footer {
    margin-top: 28px;
}

.page-empty-state--compact .page-empty-state__footer {
    margin-top: 20px;
}

.page-empty-state__divider {
    height: 1px;
    margin-bottom: 18px;
    background: linear-gradient(
        90deg,
        rgba(229, 231, 235, 0) 0%,
        #e5e7eb 20%,
        #e5e7eb 80%,
        rgba(229, 231, 235, 0) 100%
    );
}

.page-empty-state__guide {
    margin: 0;
    font-size: 13px;
    line-height: 1.65;
    white-space: nowrap;
    color: #9ca3af;
}

.page-empty-state__kbd {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 22px;
    height: 22px;
    margin: 0 4px;
    padding: 0 6px;
    border: 1px solid #e5e7eb;
    border-radius: 6px;
    font-family: inherit;
    font-size: 12px;
    font-weight: 500;
    line-height: 1;
    color: #374151;
    background: #fff;
    box-shadow: 0 1px 0 rgba(15, 23, 42, 0.04);
}

.page-empty-state__guide-highlight {
    font-weight: 600;
    color: #4338ca;
}

.page-empty-state__actions {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: center;
    gap: 8px;
    margin-top: 18px;
}
</style>
