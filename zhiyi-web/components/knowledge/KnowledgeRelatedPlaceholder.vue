<template>
    <div class="related-placeholder" :class="{ 'related-placeholder--compact': compact }">
        <div class="related-placeholder__head">
            <h3 class="related-placeholder__title">{{ title }}</h3>
            <span class="related-placeholder__phase">{{ phase }}</span>
        </div>
        <p v-if="!compact" class="related-placeholder__desc">{{ description }}</p>
        <p class="related-placeholder__hint">开发中</p>
    </div>
</template>

<script setup>
/** 经验详情页「相关内容」占位卡片 */
defineProps({
    title: {
        type: String,
        required: true,
    },
    description: {
        type: String,
        default: '',
    },
    phase: {
        type: String,
        default: 'V2',
    },
    /** 侧边栏紧凑模式 */
    compact: {
        type: Boolean,
        default: false,
    },
})
</script>

<style scoped>
.related-placeholder {
    background: #fff;
    border: 1px dashed #e5e7eb;
    border-radius: 10px;
    padding: 12px 14px;
}

.related-placeholder--compact {
    padding: 8px 12px;
}

.related-placeholder__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
}

.related-placeholder__title {
    margin: 0;
    font-size: 12px;
    font-weight: 600;
    color: #374151;
}

.related-placeholder__phase {
    flex-shrink: 0;
    padding: 0 6px;
    border-radius: 4px;
    font-size: 10px;
    font-weight: 500;
    color: #9ca3af;
    background: #f3f4f6;
    line-height: 18px;
}

.related-placeholder__desc {
    margin: 4px 0 0;
    font-size: 11px;
    color: #9ca3af;
    line-height: 1.45;
}

.related-placeholder__hint {
    margin: 4px 0 0;
    font-size: 10px;
    color: #d1d5db;
}

.related-placeholder--compact .related-placeholder__hint {
    margin: 2px 0 0;
}
</style>
