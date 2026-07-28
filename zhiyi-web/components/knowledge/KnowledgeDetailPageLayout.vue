<template>
    <div
        class="knowledge-detail-page max-w-layout mx-auto px-6 py-5"
        :class="`knowledge-detail-page--${themeVariant}`"
    >
        <!-- 加载骨架：模拟详情页双栏结构 -->
        <template v-if="loading">
            <div class="knowledge-detail-page__skeleton-nav mb-6">
                <el-skeleton animated>
                    <template #template>
                        <el-skeleton-item variant="text" style="width: 180px; height: 20px" />
                    </template>
                </el-skeleton>
            </div>
            <div class="bg-white rounded-2xl border border-gray-200 p-8 mb-8">
                <el-skeleton :rows="4" animated />
            </div>
            <div class="lg:grid lg:grid-cols-[minmax(0,1fr)_280px] lg:gap-8">
                <div class="space-y-4">
                    <div
                        v-for="index in 3"
                        :key="index"
                        class="bg-white rounded-xl border border-gray-200 p-5"
                    >
                        <el-skeleton :rows="3" animated />
                    </div>
                </div>
                <div class="hidden lg:block space-y-4">
                    <div class="bg-white rounded-xl border border-gray-200 p-5">
                        <el-skeleton :rows="4" animated />
                    </div>
                </div>
            </div>
        </template>

        <template v-else-if="detail">
            <!-- 面包屑与操作栏 -->
            <header class="knowledge-detail-page__header mb-4">
                <nav class="knowledge-detail-page__breadcrumb" aria-label="页面导航">
                    <NuxtLink :to="centerPath" class="knowledge-detail-page__breadcrumb-link">
                        {{ centerLabel }}
                    </NuxtLink>
                    <el-icon :size="12" class="knowledge-detail-page__breadcrumb-sep">
                        <ArrowRight />
                    </el-icon>
                    <span class="knowledge-detail-page__breadcrumb-current">{{ detail.title }}</span>
                </nav>

                <div class="knowledge-detail-page__actions">
                    <el-button size="small" @click="handleCopyMarkdown">
                        <el-icon class="mr-1"><CopyDocument /></el-icon>
                        复制为 Markdown
                    </el-button>
                    <slot name="actions" />
                </div>
            </header>

            <!-- 主内容 + 侧边栏 -->
            <div class="knowledge-detail-page__layout">
                <main class="knowledge-detail-page__main">
                    <!-- 标题区 Hero -->
                    <section class="knowledge-detail-page__hero">
                        <div class="knowledge-detail-page__hero-accent" />
                        <div class="knowledge-detail-page__hero-body">
                            <div class="knowledge-detail-page__hero-top">
                                <div class="flex flex-wrap items-center gap-1.5">
                                    <span class="knowledge-detail-page__type-badge">{{ typeLabel }}</span>
                                    <span
                                        v-if="detail.lifecycleStatus !== KNOWLEDGE_LIFECYCLE.PUBLISHED"
                                        class="knowledge-detail-page__status-badge"
                                        :class="lifecycleBadgeClass"
                                    >
                                        {{ KNOWLEDGE_LIFECYCLE_LABELS[detail.lifecycleStatus] || '未知状态' }}
                                    </span>
                                    <span
                                        class="knowledge-detail-page__recall-badge"
                                        :class="recallBadgeClass"
                                    >
                                        <el-icon :size="12"><DataLine /></el-icon>
                                        召回 {{ detail.recallCount || 0 }} 次
                                    </span>
                                    <span
                                        v-if="detail.updateTime"
                                        class="knowledge-detail-page__updated"
                                    >
                                        <el-icon :size="12"><Clock /></el-icon>
                                        {{ formatDateTime(detail.updateTime) }}
                                    </span>
                                </div>
                                <span v-if="factCount" class="knowledge-detail-page__fact-count">
                                    {{ factCount }} 段内容
                                </span>
                            </div>

                            <h1 class="knowledge-detail-page__title">{{ detail.title }}</h1>

                            <KnowledgeSubmitter
                                :knowledge="detail"
                                size="default"
                            />

                            <KnowledgeMeta
                                :knowledge="detail"
                                variant="pills"
                                class="mt-2"
                            />

                            <div v-if="detail.tags && detail.tags.length" class="flex flex-wrap gap-1 mt-2">
                                <span
                                    v-for="tag in detail.tags"
                                    :key="tag"
                                    class="knowledge-detail-page__tag"
                                >
                                    {{ tag }}
                                </span>
                            </div>
                        </div>
                    </section>

                    <!-- Fact Blocks：知识核心内容 -->
                    <KnowledgeFactBlockList :fact-groups="factGroups" />

                    <!-- Artifacts -->
                    <KnowledgeArtifactList
                        v-if="detail.artifacts && detail.artifacts.length"
                        :artifacts="detail.artifacts"
                    />

                    <!-- Agent 注入预览：默认折叠，减少首屏干扰 -->
                    <el-collapse v-model="promptPreviewExpanded" class="knowledge-detail-page__collapse">
                        <el-collapse-item name="prompt">
                            <template #title>
                                <div class="knowledge-detail-page__collapse-title">
                                    <el-icon :size="16"><Monitor /></el-icon>
                                    <span>Agent 注入片段预览</span>
                                    <span class="knowledge-detail-page__collapse-hint">模拟 Recall 写入内容</span>
                                </div>
                            </template>
                            <KnowledgePromptSlicePreview :knowledge="detail" embedded />
                        </el-collapse-item>
                    </el-collapse>
                </main>

                <aside class="knowledge-detail-page__sidebar">
                    <div class="knowledge-detail-page__trace-card">
                        <MemoryTraceLink
                            :knowledge-id="knowledgeId"
                            :link-style="false"
                            size="small"
                            button-type="primary"
                            :plain="true"
                            class="knowledge-detail-page__trace-button"
                        />
                        <p class="knowledge-detail-page__trace-hint">
                            查看 Recall 注入与反馈链路
                        </p>
                    </div>
                    <KnowledgeRelatedSection
                        :knowledge-id="knowledgeId"
                        :knowledge-type="detail.knowledgeType"
                        compact
                    />
                </aside>
            </div>
        </template>

        <!-- 不存在 -->
        <div v-else class="knowledge-detail-page__not-found">
            <div class="knowledge-detail-page__not-found-icon">
                <el-icon :size="32"><DocumentDelete /></el-icon>
            </div>
            <h2 class="text-lg font-medium text-gray-900 mt-4">{{ notFoundTitle }}</h2>
            <p class="text-sm text-gray-500 mt-2">{{ notFoundDescription }}</p>
            <el-button type="primary" class="mt-6" @click="handleBackToCenter">
                返回{{ centerLabel }}
            </el-button>
        </div>

        <slot name="append" />
    </div>
</template>

<script setup>
import {
    ArrowRight,
    Clock,
    CopyDocument,
    DataLine,
    DocumentDelete,
    Monitor,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { KNOWLEDGE_LIFECYCLE, KNOWLEDGE_LIFECYCLE_LABELS, KNOWLEDGE_TYPE_LABELS } from '~/constants/knowledge'
import { copyTextToClipboard } from '~/utils/clipboard'
import {
    buildKnowledgeDetailMarkdown,
    formatDateTime,
    resolveKnowledgeDetailPath,
} from '~/utils/knowledge'

const props = defineProps({
    /** 是否处于加载中 */
    loading: {
        type: Boolean,
        default: false,
    },
    /** 知识详情对象 */
    detail: {
        type: Object,
        default: null,
    },
    /** 当前知识 ID */
    knowledgeId: {
        type: [String, Number],
        default: null,
    },
    /** 按 Fact 类型分组后的展示数据 */
    factGroups: {
        type: Array,
        default: () => [],
    },
    /** 列表页路径，用于面包屑与空态返回 */
    centerPath: {
        type: String,
        required: true,
    },
    /** 列表页名称，用于面包屑 */
    centerLabel: {
        type: String,
        required: true,
    },
    /** 视觉主题：experience / rule / decision */
    themeVariant: {
        type: String,
        default: 'experience',
        validator: (value) => ['experience', 'rule', 'decision'].includes(value),
    },
    /** 空态标题 */
    notFoundTitle: {
        type: String,
        default: '内容不存在',
    },
    /** 空态说明 */
    notFoundDescription: {
        type: String,
        default: '该内容可能已被删除，或您没有访问权限',
    },
})

const router = useRouter()

/** Agent 注入预览折叠面板：默认收起 */
const promptPreviewExpanded = ref([])

/** 类型徽章文案：优先使用详情中的 knowledgeType */
const typeLabel = computed(() => {
    const knowledgeType = props.detail?.knowledgeType
    if (knowledgeType && KNOWLEDGE_TYPE_LABELS[knowledgeType]) {
        return KNOWLEDGE_TYPE_LABELS[knowledgeType]
    }
    if (props.themeVariant === 'rule') {
        return '规则'
    }
    if (props.themeVariant === 'decision') {
        return '决策'
    }
    return '经验'
})

/** Fact Block 总段数 */
const factCount = computed(() => props.detail?.facts?.length || 0)

/** 生命周期状态徽章样式 */
const lifecycleBadgeClass = computed(() => {
    const status = props.detail?.lifecycleStatus
    if (status === KNOWLEDGE_LIFECYCLE.DRAFT) {
        return 'knowledge-detail-page__status-badge--draft'
    }
    if (status === KNOWLEDGE_LIFECYCLE.DEPRECATED) {
        return 'knowledge-detail-page__status-badge--deprecated'
    }
    return ''
})

/** 召回次数徽章颜色：与列表卡片保持一致 */
const recallBadgeClass = computed(() => {
    const count = props.detail?.recallCount || 0
    if (count >= 5) {
        return 'knowledge-detail-page__recall-badge--high'
    }
    if (count >= 1) {
        return 'knowledge-detail-page__recall-badge--medium'
    }
    return 'knowledge-detail-page__recall-badge--low'
})

/** 空态返回列表页 */
function handleBackToCenter() {
    router.push(props.centerPath)
}

/** 将当前详情复制为 Markdown 并写入剪贴板 */
async function handleCopyMarkdown() {
    if (!props.detail) {
        return
    }

    let detailUrl = ''
    if (import.meta.client && props.knowledgeId) {
        const detailPath = resolveKnowledgeDetailPath(props.detail.knowledgeType, props.knowledgeId)
        detailUrl = `${window.location.origin}${detailPath}`
    }

    const markdownText = buildKnowledgeDetailMarkdown(props.detail, { detailUrl })
    const copied = await copyTextToClipboard(markdownText)
    if (copied) {
        ElMessage.success('已复制 Markdown')
        return
    }
    ElMessage.error('复制失败，请手动选择文本')
}
</script>

<style scoped>
.knowledge-detail-page--experience {
    --kdp-accent: #5d65f9;
    --kdp-accent-light: #eff0fe;
    --kdp-accent-gradient: linear-gradient(90deg, #5d65f9 0%, #818cf8 50%, #a5b4fc 100%);
    --kdp-link-hover: #5d65f9;
    --kdp-recall-high: #5d65f9;
    --kdp-recall-high-bg: rgba(93, 101, 249, 0.1);
}

.knowledge-detail-page--rule {
    --kdp-accent: #ea580c;
    --kdp-accent-light: #fff7ed;
    --kdp-accent-gradient: linear-gradient(90deg, #ea580c 0%, #fb923c 50%, #fdba74 100%);
    --kdp-link-hover: #ea580c;
    --kdp-recall-high: #ea580c;
    --kdp-recall-high-bg: rgba(234, 88, 12, 0.1);
}

.knowledge-detail-page--decision {
    --kdp-accent: #7c3aed;
    --kdp-accent-light: #f5f3ff;
    --kdp-accent-gradient: linear-gradient(90deg, #7c3aed 0%, #8b5cf6 50%, #a78bfa 100%);
    --kdp-link-hover: #7c3aed;
    --kdp-recall-high: #7c3aed;
    --kdp-recall-high-bg: rgba(124, 58, 237, 0.1);
}

.knowledge-detail-page__header {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

@media (min-width: 640px) {
    .knowledge-detail-page__header {
        flex-direction: row;
        align-items: center;
        justify-content: space-between;
    }
}

.knowledge-detail-page__breadcrumb {
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
    font-size: 13px;
}

.knowledge-detail-page__breadcrumb-link {
    flex-shrink: 0;
    color: #6b7280;
    text-decoration: none;
    transition: color 0.15s;
}

.knowledge-detail-page__breadcrumb-link:hover {
    color: var(--kdp-link-hover);
}

.knowledge-detail-page__breadcrumb-sep {
    flex-shrink: 0;
    color: #d1d5db;
}

.knowledge-detail-page__breadcrumb-current {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: #374151;
    font-weight: 500;
}

.knowledge-detail-page__actions {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
}

.knowledge-detail-page__hero {
    position: relative;
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 10px;
    overflow: hidden;
}

.knowledge-detail-page__hero-accent {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 3px;
    background: var(--kdp-accent-gradient);
}

.knowledge-detail-page__hero-body {
    padding: 16px 18px;
}

.knowledge-detail-page__hero-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 8px;
}

.knowledge-detail-page__fact-count {
    flex-shrink: 0;
    font-size: 12px;
    color: #9ca3af;
}

.knowledge-detail-page__type-badge {
    display: inline-flex;
    align-items: center;
    padding: 2px 10px;
    border-radius: 9999px;
    font-size: 12px;
    font-weight: 500;
    color: var(--kdp-accent);
    background: var(--kdp-accent-light);
}

.knowledge-detail-page__status-badge {
    display: inline-flex;
    align-items: center;
    padding: 2px 10px;
    border-radius: 9999px;
    font-size: 12px;
    font-weight: 500;
    color: #6b7280;
    background: #f3f4f6;
}

.knowledge-detail-page__status-badge--draft {
    color: #d97706;
    background: #fffbeb;
}

.knowledge-detail-page__status-badge--deprecated {
    color: #dc2626;
    background: #fef2f2;
}

.knowledge-detail-page__recall-badge {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 2px 10px;
    border-radius: 9999px;
    font-size: 12px;
    font-weight: 500;
}

.knowledge-detail-page__recall-badge--high {
    color: var(--kdp-recall-high);
    background: var(--kdp-recall-high-bg);
}

.knowledge-detail-page__recall-badge--medium {
    color: #2563eb;
    background: #eff6ff;
}

.knowledge-detail-page__recall-badge--low {
    color: #6b7280;
    background: #f3f4f6;
}

.knowledge-detail-page__title {
    font-size: 1.25rem;
    font-weight: 600;
    line-height: 1.4;
    color: #111827;
}

.knowledge-detail-page__tag {
    display: inline-block;
    padding: 3px 12px;
    border-radius: 9999px;
    font-size: 12px;
    line-height: 20px;
    color: #6b7280;
    background: #f3f4f6;
}

.knowledge-detail-page__updated {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    font-size: 12px;
    color: #9ca3af;
}

.knowledge-detail-page__layout {
    display: flex;
    flex-direction: column;
    gap: 20px;
}

@media (min-width: 1024px) {
    .knowledge-detail-page__layout {
        display: grid;
        grid-template-columns: minmax(0, 1fr) 260px;
        gap: 20px;
        align-items: start;
    }
}

.knowledge-detail-page__main {
    display: flex;
    flex-direction: column;
    gap: 12px;
    min-width: 0;
}

.knowledge-detail-page__sidebar {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

@media (min-width: 1024px) {
    .knowledge-detail-page__sidebar {
        position: sticky;
        top: 16px;
    }
}

.knowledge-detail-page__trace-card {
    padding: 10px 12px;
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 10px;
    text-align: center;
}

.knowledge-detail-page__trace-button {
    width: 100%;
}

.knowledge-detail-page__trace-button :deep(.el-button) {
    width: 100%;
}

.knowledge-detail-page__trace-hint {
    margin: 6px 0 0;
    font-size: 11px;
    color: #9ca3af;
    line-height: 1.4;
}

.knowledge-detail-page__collapse {
    border: 1px solid #e5e7eb;
    border-radius: 12px;
    overflow: hidden;
    background: #fff;
}

.knowledge-detail-page__collapse :deep(.el-collapse-item__header) {
    padding: 0 16px;
    height: 44px;
    border-bottom: none;
    background: #fafafa;
    font-weight: 500;
}

.knowledge-detail-page__collapse :deep(.el-collapse-item__wrap) {
    border-top: 1px solid #f3f4f6;
}

.knowledge-detail-page__collapse :deep(.el-collapse-item__content) {
    padding: 0;
}

.knowledge-detail-page__collapse-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    color: #374151;
}

.knowledge-detail-page__collapse-hint {
    font-size: 12px;
    font-weight: 400;
    color: #9ca3af;
    margin-left: 4px;
}

.knowledge-detail-page__not-found {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 64px 24px;
    text-align: center;
}

.knowledge-detail-page__not-found-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 64px;
    height: 64px;
    border-radius: 16px;
    background: #f3f4f6;
    color: #9ca3af;
}
</style>
