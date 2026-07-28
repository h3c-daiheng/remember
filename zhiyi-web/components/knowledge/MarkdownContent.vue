<template>
    <!-- 渲染 Markdown / 纯文本，用于 Fact Block 等内容展示 -->
    <div class="markdown-content" v-html="htmlContent" />
</template>

<script setup>
import { renderMarkdown } from '~/utils/markdown'

const props = defineProps({
    /** Markdown 或纯文本内容 */
    content: {
        type: String,
        default: '',
    },
})

/** 将内容转为安全 HTML */
const htmlContent = computed(() => renderMarkdown(props.content))
</script>

<style scoped>
.markdown-content {
    font-size: 14px;
    line-height: 1.75;
    color: #374151;
    word-break: break-word;
}

.markdown-content :deep(p) {
    margin: 0 0 0.75em;
}

.markdown-content :deep(p:last-child) {
    margin-bottom: 0;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
    margin: 0.5em 0;
    padding-left: 1.5em;
}

.markdown-content :deep(li) {
    margin: 0.35em 0;
}

.markdown-content :deep(li > p) {
    margin: 0;
}

.markdown-content :deep(code) {
    padding: 0.15em 0.4em;
    border-radius: 4px;
    font-size: 0.9em;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    background: #f3f4f6;
    color: #5d65f9;
}

.markdown-content :deep(pre) {
    margin: 0.75em 0;
    padding: 12px 14px;
    border-radius: 8px;
    background: #f9fafb;
    border: 1px solid #e5e7eb;
    overflow-x: auto;
}

.markdown-content :deep(pre code) {
    padding: 0;
    background: transparent;
    color: #374151;
    font-size: 13px;
}

.markdown-content :deep(a) {
    color: #5d65f9;
    text-decoration: none;
}

.markdown-content :deep(a:hover) {
    text-decoration: underline;
}

.markdown-content :deep(blockquote) {
    margin: 0.75em 0;
    padding: 0.5em 0 0.5em 1em;
    border-left: 3px solid #ced1fd;
    color: #6b7280;
}

.markdown-content :deep(strong) {
    font-weight: 600;
    color: #111827;
}
</style>
