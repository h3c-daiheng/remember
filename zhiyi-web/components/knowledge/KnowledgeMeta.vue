<template>
    <!-- inline：详情页纯文本；pills：列表卡片胶囊；compact：侧边栏窄列紧凑标签 -->
    <div
        class="flex flex-wrap gap-x-4 gap-y-1.5"
        :class="metaContainerClass"
    >
        <template v-if="variant === 'compact'">
            <span
                v-for="item in metaItems"
                :key="item.label"
                class="knowledge-meta__compact-chip"
                :title="`${item.label}：${item.value}`"
            >
                <span class="knowledge-meta__compact-label">{{ item.label }}</span>
                <span class="knowledge-meta__compact-value">{{ item.value }}</span>
            </span>
        </template>
        <template v-else-if="variant === 'pills'">
            <span
                v-for="item in metaItems"
                :key="item.label"
                class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-gray-50 text-xs text-gray-600"
            >
                <el-icon :size="12" class="text-gray-400">
                    <component :is="item.icon" />
                </el-icon>
                <span class="text-gray-400">{{ item.label }}</span>
                <span class="font-medium text-gray-700">{{ item.value }}</span>
            </span>
        </template>
        <template v-else>
            <span v-for="item in metaItems" :key="item.label">
                {{ item.label }}：{{ item.value }}
            </span>
        </template>
        <span v-if="showRecallCount" :class="variant === 'pills' ? 'inline-flex items-center gap-1 text-xs text-gray-400' : ''">
            召回 {{ recallCount || 0 }} 次
        </span>
    </div>
</template>

<script setup>
import { Files, Folder, Grid } from '@element-plus/icons-vue'
import { buildKnowledgeMetaSummary } from '~/utils/knowledge'

const props = defineProps({
    /** Knowledge 对象或含 project/module/repository 的元数据 */
    knowledge: {
        type: Object,
        default: null,
    },
    /** 是否展示召回次数 */
    showRecallCount: {
        type: Boolean,
        default: false,
    },
    /** 召回次数（knowledge 未传 recallCount 时可单独传入） */
    recallCount: {
        type: Number,
        default: 0,
    },
    /** 展示样式：inline 纯文本 / pills 胶囊标签 / compact 侧边栏紧凑标签 */
    variant: {
        type: String,
        default: 'inline',
        validator: (value) => ['inline', 'pills', 'compact'].includes(value),
    },
})

/** 元信息容器样式：按 variant 区分间距与字号 */
const metaContainerClass = computed(() => {
    if (props.variant === 'pills') {
        return 'gap-2'
    }
    if (props.variant === 'compact') {
        return 'gap-1'
    }
    return 'text-sm text-gray-500'
})

/** 元信息字段与对应图标映射 */
const metaIconMap = {
    项目: Folder,
    模块: Grid,
    仓库: Files,
}

/** 元信息条目列表（附带图标组件） */
const metaItems = computed(() =>
    buildKnowledgeMetaSummary(props.knowledge).map((item) => ({
        ...item,
        icon: metaIconMap[item.label] || Files,
    })),
)
</script>

<style scoped>
.knowledge-meta__compact-chip {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    max-width: 100%;
    padding: 2px 6px;
    border-radius: 4px;
    background: rgba(243, 244, 246, 0.9);
    font-size: 10px;
    line-height: 16px;
}

.knowledge-meta__compact-label {
    flex-shrink: 0;
    color: #9ca3af;
}

.knowledge-meta__compact-value {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: #4b5563;
    font-weight: 500;
}
</style>
