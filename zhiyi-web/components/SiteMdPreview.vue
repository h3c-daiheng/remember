<script setup>
/**
 * 站点静态页 Markdown 只读预览（关于/联系等）
 * 基于 md-editor-v3 MdPreview，与主站 MyMdEditor 只读模式对齐，沿用编辑器默认排版。
 */
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'

/** Markdown 正文（只读预览，不写回） */
const modelValue = defineModel({ type: String, default: '' })

defineProps({
    /** 预览实例唯一标识；同页多实例不可重复，SSR 须稳定 */
    editorId: {
        type: String,
        required: true,
    },
})
</script>

<template>
    <div class="site-md-preview">
        <MdPreview v-model="modelValue"
            :editor-id="editorId"
            :auto-fold-threshold="9999" />
    </div>
</template>

<style scoped>
.site-md-preview {
    width: 100%;
}

/* 去掉编辑器外壳边框/底色，避免与页面底色形成断层 */
.site-md-preview :deep(.md-editor) {
    border: none;
    border-radius: 0;
    box-shadow: none;
    background-color: transparent;
    /* 参照主站：边框悬停/激活 → 站点主题色（智忆全局 --el-color-primary） */
    --md-border-hover-color: var(--el-color-primary-light-5);
    --md-border-active-color: var(--el-color-primary);
    --md-hover-color: var(--el-color-primary);
}

.site-md-preview :deep(.md-editor-preview-wrapper) {
    padding: 0;
    background-color: transparent;
}

.site-md-preview :deep(.md-editor-preview) {
    background-color: transparent;
    font-size: inherit;
    /* 参照主站：链接 / 行内代码 → 站点主题色 */
    --md-theme-link-color: var(--el-color-primary) !important;
    --md-theme-link-hover-color: var(--el-color-primary-light-3) !important;
    --md-theme-code-inline-color: var(--el-color-primary) !important;
    --md-theme-code-inline-bg-color: var(--el-color-primary-light-9) !important;
}

.site-md-preview :deep(.md-editor-preview img) {
    display: block;
    max-width: 100%;
    height: auto;
    margin-left: auto;
    margin-right: auto;
}

.site-md-preview :deep(.md-editor-preview ul) {
    list-style-type: disc;
    padding-left: 1.35em;
    margin: 0.75em 0;
}

.site-md-preview :deep(.md-editor-preview ol) {
    list-style-type: decimal;
    padding-left: 1.35em;
    margin: 0.75em 0;
}

.site-md-preview :deep(.md-editor-preview li) {
    display: list-item;
    margin: 0.35em 0;
}
</style>
