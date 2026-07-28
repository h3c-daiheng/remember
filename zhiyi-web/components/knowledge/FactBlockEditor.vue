<template>
    <!-- Capture 草稿审核：按类型着色的可编辑 Fact Block 列表 -->
    <div class="fact-block-editor">
        <div
            v-for="(fact, index) in facts"
            :key="index"
            class="fact-block-editor__item"
            :class="[
                getFactTypeTheme(fact.type).card,
                { 'fact-block-editor__item--has-evidence': hasHighlights(index) },
            ]"
        >
            <div
                class="fact-block-editor__accent"
                :class="getFactTypeTheme(fact.type).accent"
            />

            <div class="fact-block-editor__body">
                <div class="fact-block-editor__header">
                    <span
                        class="fact-block-editor__badge"
                        :class="getFactTypeTheme(fact.type).badge"
                    >
                        <el-icon :size="13">
                            <component :is="factTypeIconMap[fact.type] || Document" />
                        </el-icon>
                        {{ FACT_TYPE_LABELS[fact.type] || fact.type }}
                    </span>
                    <el-button
                        v-if="removable"
                        text
                        type="danger"
                        size="small"
                        class="fact-block-editor__remove"
                        @click="handleRemove(index)"
                    >
                        删除
                    </el-button>
                </div>

                <el-input
                    v-model="fact.text"
                    type="textarea"
                    :autosize="{ minRows: 3, maxRows: 14 }"
                    placeholder="支持 Markdown：编号列表、行内代码 `path/to/file`、加粗等"
                    class="fact-block-editor__textarea"
                    :class="{ 'fact-block-editor__textarea--highlighted': hasHighlights(index) }"
                />

                <!-- Review 证据高亮：标出 AI 预审定位的重复或问题片段 -->
                <div
                    v-if="getHighlights(index).length"
                    class="fact-block-editor__evidence-list"
                >
                    <div
                        v-for="(highlight, highlightIndex) in getHighlights(index)"
                        :key="`${index}-${highlightIndex}`"
                        class="fact-block-editor__evidence-item"
                    >
                        <span class="fact-block-editor__evidence-tag">
                            {{ highlight.checklistId }}
                        </span>
                        <mark class="fact-block-editor__evidence-snippet">
                            {{ highlight.snippet }}
                        </mark>
                        <span
                            v-if="highlight.evidence"
                            class="fact-block-editor__evidence-reason"
                        >
                            {{ highlight.evidence }}
                        </span>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
import {
    CircleCheck,
    Connection,
    Document,
    Lightning,
    Lock,
    SetUp,
    View,
} from '@element-plus/icons-vue'
import { FACT_TYPE_LABELS, getFactTypeTheme } from '~/constants/knowledge'

/** 可编辑的 Fact Block 列表，草稿编辑时使用 */
const props = defineProps({
    facts: {
        type: Array,
        default: () => [],
    },
    /** 是否展示删除按钮 */
    removable: {
        type: Boolean,
        default: false,
    },
    /** Fact 下标 → 证据高亮列表，来自 Review checklistHints */
    evidenceHighlights: {
        type: Object,
        default: () => ({}),
    },
})

const emit = defineEmits(['remove'])

/** 获取指定 Fact 的证据高亮条目 */
function getHighlights(factIndex) {
    return props.evidenceHighlights[factIndex] || []
}

/** 指定 Fact 是否存在证据高亮 */
function hasHighlights(factIndex) {
    return getHighlights(factIndex).length > 0
}

/** 删除指定下标的 Fact Block */
function handleRemove(index) {
    emit('remove', index)
}

/** Fact 类型与图标映射，便于审核时快速识别段落性质 */
const factTypeIconMap = {
    observation: View,
    decision: SetUp,
    constraint: Lock,
    rule: Document,
    evidence: Connection,
    action: Lightning,
    outcome: CircleCheck,
}
</script>

<style scoped>
.fact-block-editor {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.fact-block-editor__item {
    position: relative;
    display: flex;
    overflow: hidden;
    border-width: 1px;
    border-style: solid;
    border-radius: 12px;
    transition: box-shadow 0.2s;
}

.fact-block-editor__item:focus-within {
    box-shadow: 0 0 0 3px rgba(93, 101, 249, 0.08);
}

.fact-block-editor__item--has-evidence {
    box-shadow: inset 0 0 0 1px rgba(239, 68, 68, 0.25);
}

.fact-block-editor__accent {
    flex-shrink: 0;
    width: 3px;
}

.fact-block-editor__body {
    flex: 1;
    min-width: 0;
    padding: 14px 16px 12px;
}

.fact-block-editor__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 10px;
}

.fact-block-editor__badge {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 3px 10px;
    border-radius: 9999px;
    font-size: 12px;
    font-weight: 500;
    line-height: 1.4;
}

.fact-block-editor__textarea :deep(.el-textarea__inner) {
    padding: 0;
    border: none;
    background: transparent;
    box-shadow: none;
    resize: none;
    font-size: 14px;
    line-height: 1.65;
    color: #374151;
}

.fact-block-editor__textarea :deep(.el-textarea__inner:focus) {
    box-shadow: none;
}

.fact-block-editor__textarea :deep(.el-textarea__inner::placeholder) {
    color: #9ca3af;
    font-size: 13px;
}

.fact-block-editor__textarea--highlighted :deep(.el-textarea__inner) {
    background: rgba(254, 242, 242, 0.35);
}

.fact-block-editor__evidence-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-top: 10px;
    padding-top: 10px;
    border-top: 1px dashed rgba(239, 68, 68, 0.2);
}

.fact-block-editor__evidence-item {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;
    gap: 6px;
    font-size: 12px;
    line-height: 1.5;
}

.fact-block-editor__evidence-tag {
    flex-shrink: 0;
    padding: 1px 6px;
    border-radius: 4px;
    font-family: ui-monospace, monospace;
    font-size: 11px;
    font-weight: 600;
    color: #b91c1c;
    background: #fef2f2;
}

.fact-block-editor__evidence-snippet {
    padding: 2px 6px;
    border-radius: 4px;
    background: #fee2e2;
    color: #991b1b;
    word-break: break-all;
}

.fact-block-editor__evidence-reason {
    flex: 1 1 100%;
    color: #6b7280;
}
</style>
