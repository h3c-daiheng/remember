<template>
    <!-- 经验列表卡片：点击跳转详情页 -->
    <NuxtLink
        :to="detailPath"
        class="knowledge-card group block no-underline text-inherit"
    >
        <div class="knowledge-card__inner flex items-stretch gap-3">
            <!-- 多选 checkbox：外层 @click.stop 阻止冒泡到 NuxtLink，避免勾选触发跳转 -->
            <div v-if="selectable" class="shrink-0 self-center pl-1" @click.stop>
                <el-checkbox :model-value="selected" @change="onCheckboxChange" />
            </div>
            <!-- 主内容：标题/归属/元信息占满宽度，避免左挤右空 -->
            <div class="flex-1 min-w-0">
                <!-- 标题行：类型徽章 + 标题 + 召回次数 -->
                <div class="flex items-start gap-3">
                    <div class="flex-1 min-w-0 flex items-start gap-2">
                        <span
                            v-if="showTypeBadge && typeLabel"
                            class="knowledge-card__type-badge shrink-0"
                            :class="typeTheme.badge"
                        >
                            {{ typeLabel }}
                        </span>
                        <h2 class="flex-1 min-w-0 text-base font-semibold text-gray-900 leading-snug group-hover:text-primary transition-colors line-clamp-2">
                            {{ knowledge.title }}
                        </h2>
                    </div>
                    <span
                        class="knowledge-card__recall shrink-0 inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium"
                        :class="recallCountClass"
                    >
                        <el-icon :size="12"><DataLine /></el-icon>
                        召回 {{ knowledge.recallCount || 0 }} 次
                    </span>
                </div>

                <span
                    v-if="showLifecycleBadge && lifecycleLabel"
                    class="knowledge-card__status-badge"
                >
                    {{ lifecycleLabel }}
                </span>

                <KnowledgeMeta
                    v-if="hasMeta"
                    :knowledge="knowledge"
                    variant="pills"
                    class="mt-3"
                />

                <!-- 底栏：标签靠左，头像/昵称/时间靠右，同一行 -->
                <div class="knowledge-card__footer">
                    <div
                        v-if="knowledge.tags && knowledge.tags.length"
                        class="knowledge-card__tags"
                    >
                        <span
                            v-for="tag in knowledge.tags"
                            :key="tag"
                            class="knowledge-card__tag"
                        >
                            {{ tag }}
                        </span>
                    </div>
                    <KnowledgeSubmitter
                        :knowledge="knowledge"
                        size="compact"
                        :time-text="updateTimeText"
                    />
                </div>
            </div>

            <!-- 右侧仅保留进入箭头，垂直居中 -->
            <div class="knowledge-card__arrow shrink-0 flex items-center">
                <el-icon
                    :size="14"
                    class="text-gray-300 group-hover:text-primary group-hover:translate-x-0.5 transition-all"
                >
                    <ArrowRight />
                </el-icon>
            </div>
        </div>
    </NuxtLink>
</template>

<script setup>
import { ArrowRight, DataLine } from '@element-plus/icons-vue'
import {
    getKnowledgeTypeTheme,
    KNOWLEDGE_LIFECYCLE_LABELS,
    KNOWLEDGE_TYPE_LABELS,
} from '~/constants/knowledge'
import { formatDateTime, resolveKnowledgeDetailPath } from '~/utils/knowledge'

const props = defineProps({
    knowledge: {
        type: Object,
        required: true,
    },
    /** 详情页路由前缀；不传则按 knowledgeType 自动解析 */
    detailBasePath: {
        type: String,
        default: '',
    },
    /** 是否在卡片标题旁展示知识类型徽章 */
    showTypeBadge: {
        type: Boolean,
        default: false,
    },
    /** 是否在卡片上展示生命周期状态角标 */
    showLifecycleBadge: {
        type: Boolean,
        default: false,
    },
    /** 是否展示多选 checkbox（owner/admin 可批量删除时开启） */
    selectable: {
        type: Boolean,
        default: false,
    },
    /** 当前卡片是否处于选中态 */
    selected: {
        type: Boolean,
        default: false,
    },
})

const emit = defineEmits(['select'])

/** checkbox 勾选变化：向外抛 { id, checked }，由父组件维护多选状态 */
function onCheckboxChange(val) {
    emit('select', { id: props.knowledge.id, checked: val })
}

/** 详情页路由 */
const detailPath = computed(() => {
    if (props.detailBasePath) {
        return `${props.detailBasePath}/${props.knowledge.id}`
    }
    return resolveKnowledgeDetailPath(props.knowledge.knowledgeType, props.knowledge.id)
})

/** 知识类型中文标签 */
const typeLabel = computed(() =>
    KNOWLEDGE_TYPE_LABELS[props.knowledge.knowledgeType] || '',
)

/** 知识类型徽章配色 */
const typeTheme = computed(() =>
    getKnowledgeTypeTheme(props.knowledge.knowledgeType),
)

/** 卡片归属行展示的更新时间 */
const updateTimeText = computed(() => formatDateTime(props.knowledge.updateTime) || '')

/** 生命周期中文标签 */
const lifecycleLabel = computed(() =>
    KNOWLEDGE_LIFECYCLE_LABELS[props.knowledge.lifecycleStatus] || '',
)

/** 是否存在项目/模块/仓库等元信息 */
const hasMeta = computed(() => {
    const item = props.knowledge
    return Boolean(item.project || item.module || item.repository)
})

/** 按召回次数区分徽章颜色：高频经验更醒目 */
const recallCountClass = computed(() => {
    const count = props.knowledge.recallCount || 0
    if (count >= 5) {
        return 'bg-primary/10 text-primary'
    }
    if (count >= 1) {
        return 'bg-blue-50 text-blue-600'
    }
    return 'bg-gray-100 text-gray-500'
})
</script>

<style scoped>
.knowledge-card {
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 12px;
    padding: 20px 24px;
    transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
    cursor: pointer;
}

.knowledge-card:hover {
    border-color: rgba(93, 101, 249, 0.35);
    box-shadow: 0 4px 16px rgba(93, 101, 249, 0.08);
    transform: translateY(-1px);
}

.knowledge-card__inner {
    position: relative;
}

/* 底栏：标签与归属信息同一行，标签可换行、归属贴右 */
.knowledge-card__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-top: 12px;
    min-width: 0;
}

.knowledge-card__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    min-width: 0;
    flex: 1;
}

.knowledge-card__footer :deep(.knowledge-submitter) {
    margin-top: 0;
    margin-left: auto;
    flex-shrink: 0;
}

/* 左侧品牌色指示条，hover 时展开 */
.knowledge-card::before {
    content: '';
    position: absolute;
    left: 0;
    top: 16px;
    bottom: 16px;
    width: 3px;
    border-radius: 0 3px 3px 0;
    background: transparent;
    transition: background 0.2s;
}

.knowledge-card {
    position: relative;
    overflow: hidden;
}

.knowledge-card:hover::before {
    background: #5d65f9;
}

.knowledge-card__tag {
    display: inline-block;
    padding: 2px 10px;
    border-radius: 9999px;
    font-size: 12px;
    line-height: 20px;
    color: #6b7280;
    background: #f3f4f6;
    transition: background 0.15s, color 0.15s;
}

.knowledge-card:hover .knowledge-card__tag {
    background: #eff0fe;
    color: #5d65f9;
}

.knowledge-card__status-badge {
    display: inline-block;
    margin-top: 8px;
    padding: 2px 10px;
    border-radius: 9999px;
    font-size: 12px;
    line-height: 20px;
    color: #9ca3af;
    background: #f3f4f6;
}

.knowledge-card__type-badge {
    display: inline-flex;
    align-items: center;
    margin-top: 2px;
    padding: 2px 8px;
    border-radius: 9999px;
    font-size: 11px;
    line-height: 18px;
    font-weight: 500;
}
</style>
