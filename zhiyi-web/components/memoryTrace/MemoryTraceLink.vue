<template>
    <!-- 闭环追踪入口：跳转至 /trace 并携带 draftId 或 knowledgeId -->
    <component
        :is="linkStyle ? 'button' : 'el-button'"
        v-if="linkStyle"
        type="button"
        class="memory-trace-link memory-trace-link--text"
        @click="handleNavigate"
    >
        <el-icon v-if="showIcon" :size="12"><Connection /></el-icon>
        {{ label }}
    </component>
    <el-button
        v-else
        :type="buttonType"
        :plain="plain"
        :size="size"
        @click="handleNavigate"
    >
        <el-icon v-if="showIcon" class="mr-1"><Connection /></el-icon>
        {{ label }}
    </el-button>
</template>

<script setup>
import { Connection } from '@element-plus/icons-vue'
import { buildMemoryTracePath } from '~/utils/memoryTrace'

const props = defineProps({
    /** Capture 草稿 ID */
    draftId: {
        type: [String, Number],
        default: null,
    },
    /** Knowledge ID */
    knowledgeId: {
        type: [String, Number],
        default: null,
    },
    /** 按钮文案 */
    label: {
        type: String,
        default: '闭环追踪',
    },
    /** Element Plus 按钮类型 */
    buttonType: {
        type: String,
        default: 'primary',
    },
    /** 是否 plain 样式 */
    plain: {
        type: Boolean,
        default: true,
    },
    /** 是否文字链样式（用于顶栏、抽屉等操作区，避免 plain 主色按钮样式冲突） */
    linkStyle: {
        type: Boolean,
        default: true,
    },
    /** 是否显示图标 */
    showIcon: {
        type: Boolean,
        default: true,
    },
    /** 按钮尺寸 */
    size: {
        type: String,
        default: 'default',
    },
})

const router = useRouter()

/** 跳转到闭环追踪页 */
function handleNavigate(event) {
    if (event?.stopPropagation) {
        event.stopPropagation()
    }
    const tracePath = buildMemoryTracePath({
        draftId: props.draftId,
        knowledgeId: props.knowledgeId,
    })
    if (tracePath) {
        router.push(tracePath)
    }
}
</script>

<style scoped>
.memory-trace-link--text {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 0;
    border: none;
    background: transparent;
    font-size: 12px;
    color: #4f46e5;
    cursor: pointer;
}

.memory-trace-link--text:hover {
    text-decoration: underline;
}
</style>
