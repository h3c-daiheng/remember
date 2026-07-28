<template>
    <section class="stats-panel">
        <div
            v-if="title || $slots.header || $slots.actions"
            class="stats-panel__header"
        >
            <div class="stats-panel__header-main">
                <slot name="header">
                    <div v-if="title">
                        <h2 class="stats-panel__title">{{ title }}</h2>
                        <p v-if="subtitle" class="stats-panel__subtitle">{{ subtitle }}</p>
                    </div>
                </slot>
            </div>
            <div v-if="$slots.actions" class="stats-panel__actions">
                <slot name="actions" />
            </div>
        </div>

        <div class="stats-panel__body">
            <slot />
        </div>
    </section>
</template>

<script setup>
defineProps({
    /** 面板主标题 */
    title: {
        type: String,
        default: '',
    },
    /** 面板副标题说明 */
    subtitle: {
        type: String,
        default: '',
    },
})
</script>

<style scoped>
.stats-panel {
    background: #fff;
    border-radius: 14px;
    border: 1px solid #e8eaef;
    box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
    padding: 20px 22px;
}

.stats-panel__header {
    display: flex;
    flex-direction: column;
    gap: 12px;
    margin-bottom: 18px;
}

@media (min-width: 640px) {
    .stats-panel__header {
        flex-direction: row;
        align-items: flex-start;
        justify-content: space-between;
    }
}

.stats-panel__header-main {
    min-width: 0;
}

.stats-panel__title {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: #111827;
    letter-spacing: -0.01em;
}

.stats-panel__subtitle {
    margin: 4px 0 0;
    font-size: 12px;
    color: #9ca3af;
    line-height: 1.5;
}

.stats-panel__actions {
    flex-shrink: 0;
}
</style>
