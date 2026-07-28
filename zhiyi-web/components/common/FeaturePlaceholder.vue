<template>
    <div class="max-w-layout mx-auto px-6 py-8">
        <PageEmptyState
            :title="title"
            :subtitle="subtitle"
            :tags="capabilities"
            :guide="placeholder ? '功能尚在规划中，可先使用记忆中心与草稿确认完成当前工作流' : ''"
        >
            <el-tag v-if="placeholder" type="warning" effect="light">即将上线</el-tag>
            <el-button type="primary" @click="router.push('/memory')">前往记忆中心</el-button>
            <el-button @click="router.push('/capture')">{{ PAGE_LABELS.draftReview }}</el-button>
        </PageEmptyState>

        <div v-if="relatedApi" class="mt-4 p-4 rounded-lg bg-gray-50 border border-gray-100 max-w-layout mx-auto">
            <div class="text-xs text-gray-400 mb-1">关联后端</div>
            <code class="text-sm text-gray-700">{{ relatedApi }}</code>
        </div>
    </div>
</template>

<script setup>
/** 功能占位页：展示路线图说明，MVP 阶段不实现具体业务 */
import { PAGE_LABELS } from '~/constants/terminology'

defineProps({
    title: {
        type: String,
        required: true,
    },
    subtitle: {
        type: String,
        default: '',
    },
    phase: {
        type: String,
        default: 'Beta',
    },
    capabilities: {
        type: Array,
        default: () => [],
    },
    relatedApi: {
        type: String,
        default: '',
    },
    placeholder: {
        type: Boolean,
        default: true,
    },
})

const router = useRouter()
</script>
