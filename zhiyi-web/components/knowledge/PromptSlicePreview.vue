<template>
    <!-- 单条 Knowledge 在 Recall promptBlock 中的注入片段，非完整 promptBlock -->
    <section
        class="prompt-slice-preview"
        :class="embedded ? 'prompt-slice-preview--embedded' : 'prompt-slice-preview--standalone'"
    >
        <!-- 独立卡片模式：完整标题区 -->
        <div v-if="!embedded" class="prompt-slice-preview__standalone-head">
            <div>
                <h2 class="prompt-slice-preview__title">Agent 注入片段预览</h2>
                <p class="prompt-slice-preview__desc">
                    模拟本条知识在默认 Recall 参数下写入 promptBlock 的内容；实际召回会与其他条目合并，并受 task、factTypes、limit 影响。
                </p>
            </div>
            <div class="prompt-slice-preview__actions">
                <NuxtLink to="/search" class="prompt-slice-preview__action-link">经验搜索</NuxtLink>
                <NuxtLink to="/trace" class="prompt-slice-preview__action-link">闭环追踪</NuxtLink>
                <el-button
                    v-if="previewResult.promptSlice"
                    size="small"
                    @click="copyPromptSlice"
                >
                    复制片段
                </el-button>
            </div>
        </div>

        <!-- 折叠嵌入模式：单行工具栏，排除类型与说明合并展示 -->
        <div v-else class="prompt-slice-preview__toolbar">
            <div class="prompt-slice-preview__toolbar-left">
                <span class="prompt-slice-preview__toolbar-desc">
                    模拟默认 Recall 参数下的注入内容
                </span>
                <span
                    v-if="previewResult.excludedTypeLabels.length && !previewResult.usedFallback"
                    class="prompt-slice-preview__excluded-inline"
                >
                    <span class="prompt-slice-preview__excluded-label">不注入</span>
                    <span
                        v-for="typeLabel in previewResult.excludedTypeLabels"
                        :key="typeLabel"
                        class="prompt-slice-preview__excluded-chip"
                    >
                        {{ typeLabel }}
                    </span>
                </span>
            </div>
            <div class="prompt-slice-preview__toolbar-right">
                <NuxtLink to="/search" class="prompt-slice-preview__action-link">经验搜索</NuxtLink>
                <el-button
                    v-if="previewResult.promptSlice"
                    size="small"
                    @click="copyPromptSlice"
                >
                    复制片段
                </el-button>
            </div>
        </div>

        <!-- 生命周期 / 兜底提示：紧凑一行 -->
        <p v-if="lifecycleHint" class="prompt-slice-preview__hint prompt-slice-preview__hint--info">
            {{ lifecycleHint }}
        </p>
        <p v-if="previewResult.usedFallback" class="prompt-slice-preview__hint">
            默认 Fact 类型过滤后无匹配项，Recall 将回退注入全部 Fact。
        </p>

        <!-- 独立模式：排除类型单独展示 -->
        <div
            v-if="!embedded && previewResult.excludedTypeLabels.length && !previewResult.usedFallback"
            class="prompt-slice-preview__excluded"
        >
            <span class="prompt-slice-preview__excluded-label">以下类型默认不注入 Agent：</span>
            <span
                v-for="typeLabel in previewResult.excludedTypeLabels"
                :key="typeLabel"
                class="prompt-slice-preview__excluded-chip"
            >
                {{ typeLabel }}
            </span>
        </div>

        <pre
            v-if="previewResult.promptSlice"
            class="prompt-slice-preview__code"
        >{{ previewResult.promptSlice }}</pre>
        <p v-else class="prompt-slice-preview__empty">暂无可注入的 Fact 内容</p>
    </section>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { KNOWLEDGE_LIFECYCLE } from '~/constants/knowledge'
import { buildKnowledgePromptSlicePreview } from '~/utils/promptSlice'

const props = defineProps({
    /** 当前 Knowledge 详情对象 */
    knowledge: {
        type: Object,
        required: true,
    },
    /** 嵌入折叠面板时使用，去除外层卡片样式 */
    embedded: {
        type: Boolean,
        default: false,
    },
})

/** 注入片段与过滤摘要 */
const previewResult = computed(() => buildKnowledgePromptSlicePreview(props.knowledge))

/** 非已发布状态时提示 Recall 行为 */
const lifecycleHint = computed(() => {
    const lifecycleStatus = props.knowledge?.lifecycleStatus
    if (lifecycleStatus === KNOWLEDGE_LIFECYCLE.DRAFT) {
        return '当前为草稿，Agent 仅在发布后才会 Recall 到本条知识。'
    }
    if (lifecycleStatus === KNOWLEDGE_LIFECYCLE.DEPRECATED) {
        return '当前已下架，Agent 不会再 Recall 到本条知识。'
    }
    return ''
})

/** 复制注入片段到剪贴板 */
async function copyPromptSlice() {
    if (!previewResult.value.promptSlice) {
        return
    }
    try {
        await navigator.clipboard.writeText(previewResult.value.promptSlice)
        ElMessage.success('已复制注入片段')
    } catch {
        ElMessage.error('复制失败，请手动选择文本')
    }
}
</script>

<style scoped>
.prompt-slice-preview--standalone {
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 12px;
    padding: 16px 18px;
}

.prompt-slice-preview--embedded {
    padding: 10px 14px 12px;
}

.prompt-slice-preview__standalone-head {
    display: flex;
    flex-direction: column;
    gap: 10px;
    margin-bottom: 10px;
}

@media (min-width: 640px) {
    .prompt-slice-preview__standalone-head {
        flex-direction: row;
        align-items: flex-start;
        justify-content: space-between;
    }
}

.prompt-slice-preview__title {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: #111827;
}

.prompt-slice-preview__desc {
    margin: 4px 0 0;
    font-size: 12px;
    color: #9ca3af;
    line-height: 1.5;
    max-width: 560px;
}

.prompt-slice-preview__actions,
.prompt-slice-preview__toolbar-right {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-shrink: 0;
}

/* 嵌入模式工具栏：说明 + 排除类型 + 操作同一区域 */
.prompt-slice-preview__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding-bottom: 8px;
    border-bottom: 1px solid #f3f4f6;
    margin-bottom: 8px;
}

.prompt-slice-preview__toolbar-left {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    min-width: 0;
}

.prompt-slice-preview__toolbar-desc {
    font-size: 12px;
    color: #6b7280;
    line-height: 1.4;
}

.prompt-slice-preview__excluded-inline {
    display: inline-flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 4px;
}

.prompt-slice-preview__excluded {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
    margin-bottom: 8px;
}

.prompt-slice-preview__excluded-label {
    font-size: 11px;
    color: #9ca3af;
}

.prompt-slice-preview__excluded-chip {
    display: inline-flex;
    padding: 0 6px;
    border-radius: 4px;
    font-size: 11px;
    line-height: 18px;
    color: #6b7280;
    background: #f3f4f6;
}

.prompt-slice-preview__action-link {
    font-size: 12px;
    color: #5d65f9;
    text-decoration: none;
    white-space: nowrap;
}

.prompt-slice-preview__action-link:hover {
    text-decoration: underline;
}

.prompt-slice-preview__hint {
    margin: 0 0 6px;
    font-size: 11px;
    color: #9ca3af;
    line-height: 1.45;
}

.prompt-slice-preview__hint--info {
    color: #6b7280;
}

.prompt-slice-preview__empty {
    margin: 0;
    font-size: 12px;
    color: #9ca3af;
}

.prompt-slice-preview__code {
    margin: 0;
    padding: 10px 12px;
    border-radius: 8px;
    background: #f9fafb;
    border: 1px solid #e5e7eb;
    font-size: 12px;
    line-height: 1.55;
    color: #374151;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 280px;
    overflow: auto;
}

@media (max-width: 639px) {
    .prompt-slice-preview__toolbar {
        flex-direction: column;
        align-items: stretch;
    }

    .prompt-slice-preview__toolbar-right {
        justify-content: flex-end;
    }
}
</style>
