<template>
    <div class="trace-page max-w-layout mx-auto px-6 py-8">
        <div class="trace-page__header mb-8">
            <div class="flex flex-col lg:flex-row lg:items-start lg:justify-between gap-6">
                <div>
                        <h1 class="text-2xl font-semibold text-gray-900 tracking-tight">
                            闭环追踪
                        </h1>
                        <p class="text-gray-500 mt-1.5 text-sm leading-relaxed max-w-2xl">
                            搜索草稿或知识，验证采集 → 发布 → 召回全链路。
                            默认展示最近一次召回。
                        </p>
                </div>
                <el-button class="shrink-0 self-start" @click="router.push('/recalls')">
                    全部召回记录
                </el-button>
            </div>
        </div>

        <!-- 查询表单：单行对齐，与经验图谱子图配置区风格一致 -->
        <section class="trace-page__toolbar">
            <div class="trace-page__toolbar-head">
                <div>
                    <h2 class="trace-page__toolbar-title">定向查询</h2>
                </div>
                <el-button
                    v-if="traceResult"
                    link
                    type="primary"
                    class="trace-page__latest-link"
                    :loading="loading"
                    @click="handleLoadLatest"
                >
                    查看最近召回
                </el-button>
            </div>

            <el-form
                label-position="top"
                class="trace-page__form"
                @submit.prevent="handleSearch"
            >
                <div class="trace-page__form-row">
                    <el-form-item label="草稿" class="trace-page__field">
                        <el-select
                            v-model="searchForm.draftId"
                            filterable
                            remote
                            clearable
                            reserve-keyword
                            placeholder="搜索标题、项目、模块"
                            :remote-method="searchDraftCandidates"
                            :loading="draftSearchLoading"
                            @visible-change="handleDraftDropdownVisible"
                        >
                            <el-option
                                v-for="item in draftOptions"
                                :key="item.id"
                                :label="formatCaptureDraftOptionLabel(item)"
                                :value="String(item.id)"
                            >
                                <div class="trace-page__option">
                                    <span class="trace-page__option-title">
                                        {{ item.draftContent?.title || '未命名草稿' }}
                                    </span>
                                    <span class="trace-page__option-meta">
                                        #{{ item.id }}
                                        · {{ formatReviewStatus(item.reviewStatus) }}
                                        <template v-if="item.draftContent?.module">
                                            · {{ item.draftContent.module }}
                                        </template>
                                    </span>
                                </div>
                            </el-option>
                        </el-select>
                    </el-form-item>

                    <span class="trace-page__or" aria-hidden="true">或</span>

                    <el-form-item label="知识" class="trace-page__field">
                        <el-select
                            v-model="searchForm.knowledgeId"
                            filterable
                            remote
                            clearable
                            reserve-keyword
                            placeholder="搜索标题、项目、模块"
                            :remote-method="searchKnowledgeCandidates"
                            :loading="knowledgeSearchLoading"
                            @visible-change="handleKnowledgeDropdownVisible"
                        >
                            <el-option
                                v-for="item in knowledgeOptions"
                                :key="item.id"
                                :label="formatKnowledgeOptionLabel(item)"
                                :value="String(item.id)"
                            >
                                <div class="trace-page__option">
                                    <span class="trace-page__option-title">
                                        {{ item.title || '未命名知识' }}
                                    </span>
                                    <span class="trace-page__option-meta">
                                        #{{ item.id }}
                                        · {{ KNOWLEDGE_TYPE_LABELS[item.knowledgeType] || item.knowledgeType }}
                                        <template v-if="item.module">
                                            · {{ item.module }}
                                        </template>
                                    </span>
                                </div>
                            </el-option>
                        </el-select>
                    </el-form-item>

                    <div class="trace-page__actions">
                        <el-button
                            type="primary"
                            native-type="submit"
                            :loading="loading"
                            :disabled="!hasSearchSelection"
                        >
                            <el-icon class="mr-1"><Search /></el-icon>
                            查询追踪
                        </el-button>
                        <el-button
                            :disabled="!hasSearchSelection && !traceResult"
                            @click="handleReset"
                        >
                            清空
                        </el-button>
                    </div>
                </div>
            </el-form>

            <p class="trace-page__hint">
                至少选择一项进行定向查询；未选择时进入页面默认展示最近一次召回。
                第二阶段验收：路由发布规则后，同模块任务召回应首位命中。
            </p>
        </section>

        <!-- 加载中 -->
        <div v-if="loading" class="bg-white rounded-xl border border-gray-200 p-6">
            <el-skeleton :rows="8" animated />
        </div>

        <!-- 查询结果 -->
        <template v-else-if="traceResult">
            <div class="trace-page__summary bg-white rounded-xl border border-gray-200 p-5 mb-6">
                <div class="flex flex-wrap items-center gap-3">
                    <el-tag type="info" size="small">
                        入口：{{ formatTraceEntryLabel(traceResult.entryType, traceResult.entryId) }}
                    </el-tag>
                    <el-tag
                        v-if="verificationStatus === 'success'"
                        type="success"
                        size="small"
                    >
                        闭环验证通过（首位命中）
                    </el-tag>
                    <el-tag
                        v-else-if="verificationStatus === 'partial'"
                        type="warning"
                        size="small"
                    >
                        部分完成（已发布，待召回验证）
                    </el-tag>
                    <el-tag
                        v-else
                        type="info"
                        size="small"
                    >
                        链路进行中
                    </el-tag>
                </div>
            </div>

            <MemoryTraceTimeline :trace-result="traceResult" />
        </template>

        <!-- 初始空状态 -->
        <PageEmptyState
            v-else
            :flow-steps="memoryTraceSteps"
            title="追踪经验从沉淀到召回的全链路"
            subtitle="验证采集 → 草稿确认 → 发布 → 召回命中，确认经验飞轮是否闭环"
            :tags="['采集', '确认', '召回', '首位命中']"
            guide="搜索草稿或知识定向查询；留空则展示最近一次召回记录"
        >
            <el-button @click="router.push('/capture')">
                前往草稿确认
            </el-button>
        </PageEmptyState>
    </div>
</template>

<script setup>
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { MEMORY_TRACE_STEPS } from '~/constants/pageEmptyState'
import { TRACE_TRACK_EVENTS } from '~/config/tracker'
import { KNOWLEDGE_LIFECYCLE, KNOWLEDGE_TYPE_LABELS } from '~/constants/knowledge'
import {
    hasTopOneRecallHit,
    formatTraceEntryLabel,
    formatCaptureDraftOptionLabel,
    formatKnowledgeOptionLabel,
    formatReviewStatus,
} from '~/utils/memoryTrace'
import { searchCaptureDrafts, fetchCaptureDraftDetail } from '~/services/capture.service'
import { fetchKnowledgeList, fetchKnowledgeDetail } from '~/services/knowledge.service'

definePageMeta({
    layout: 'app',
})

useHead({ title: '闭环追踪' })

/** 页面浏览埋点 */
usePageTracker()
useRequireAuth()

const route = useRoute()
const router = useRouter()
const memoryTraceSteps = MEMORY_TRACE_STEPS
const { reportEvent } = useTracker()

const {
    loading,
    traceResult,
    searchForm,
    loadTrace,
    loadTraceFromRoute,
    resetTrace,
    loadLatestTrace,
} = useMemoryTrace()

/** 是否已选择至少一项查询条件 */
const hasSearchSelection = computed(() => {
    return Boolean(String(searchForm.draftId || '').trim() || String(searchForm.knowledgeId || '').trim())
})

/** Capture 草稿搜索候选 */
const draftOptions = ref([])
const draftSearchLoading = ref(false)
let draftSearchTimer = null

/** 知识搜索候选 */
const knowledgeOptions = ref([])
const knowledgeSearchLoading = ref(false)
let knowledgeSearchTimer = null

/**
 * 远程搜索 Capture 草稿，支持标题、项目、模块等关键词
 */
async function loadDraftCandidates(keyword) {
    draftSearchLoading.value = true
    try {
        draftOptions.value = await searchCaptureDrafts(keyword, 20) || []
    } catch (error) {
        ElMessage.error(error.message || '搜索草稿失败')
    } finally {
        draftSearchLoading.value = false
    }
}

/** 输入关键词时防抖搜索，减少频繁请求 */
function searchDraftCandidates(keyword) {
    if (draftSearchTimer) {
        clearTimeout(draftSearchTimer)
    }
    draftSearchTimer = setTimeout(() => {
        loadDraftCandidates(keyword).catch(() => {
            draftOptions.value = []
        })
    }, 300)
}

/**
 * 下拉展开时预加载最近草稿，避免空白面板
 */
function handleDraftDropdownVisible(visible) {
    if (visible && draftOptions.value.length === 0 && !draftSearchLoading.value) {
        loadDraftCandidates('').catch(() => {
            draftOptions.value = []
        })
    }
}

/**
 * 远程搜索知识，不限类型与生命周期
 */
async function loadKnowledgeCandidates(keyword) {
    knowledgeSearchLoading.value = true
    try {
        const response = await fetchKnowledgeList(1, 20, keyword)
        knowledgeOptions.value = response?.list || []
    } catch (error) {
        ElMessage.error(error.message || '搜索知识失败')
    } finally {
        knowledgeSearchLoading.value = false
    }
}

/** 输入关键词时防抖搜索 */
function searchKnowledgeCandidates(keyword) {
    if (knowledgeSearchTimer) {
        clearTimeout(knowledgeSearchTimer)
    }
    knowledgeSearchTimer = setTimeout(() => {
        loadKnowledgeCandidates(keyword).catch(() => {
            knowledgeOptions.value = []
        })
    }, 300)
}

/** 下拉展开时预加载最近知识 */
function handleKnowledgeDropdownVisible(visible) {
    if (visible && knowledgeOptions.value.length === 0 && !knowledgeSearchLoading.value) {
        loadKnowledgeCandidates('').catch(() => {
            knowledgeOptions.value = []
        })
    }
}

/**
 * URL 或外部跳转带入 ID 时，预加载选中项以便下拉展示标题
 */
async function ensureSelectedSearchOptions() {
    const draftId = String(searchForm.draftId || '').trim()
    const knowledgeId = String(searchForm.knowledgeId || '').trim()

    if (draftId && !draftOptions.value.some((item) => String(item.id) === draftId)) {
        try {
            const draft = await fetchCaptureDraftDetail(draftId)
            if (draft) {
                draftOptions.value.unshift(draft)
            }
        } catch (error) {
            draftOptions.value.unshift({
                id: Number(draftId),
                draftContent: { title: `草稿 #${draftId}` },
            })
        }
    }

    if (knowledgeId && !knowledgeOptions.value.some((item) => String(item.id) === knowledgeId)) {
        try {
            const knowledge = await fetchKnowledgeDetail(knowledgeId)
            if (knowledge) {
                knowledgeOptions.value.unshift(knowledge)
            }
        } catch (error) {
            knowledgeOptions.value.unshift({
                id: Number(knowledgeId),
                title: `知识 #${knowledgeId}`,
            })
        }
    }
}

/** 根据发布状态与 Recall 命中推断验收状态 */
const verificationStatus = computed(() => {
    if (!traceResult.value) {
        return 'pending'
    }
    const knowledgeList = traceResult.value.knowledgeList || []
    const hasPublished = knowledgeList.some(
        (item) => item.lifecycleStatus === KNOWLEDGE_LIFECYCLE.PUBLISHED,
    )
    const recallHitList = traceResult.value.recallHits || []
    if (hasTopOneRecallHit(recallHitList)) {
        return 'success'
    }
    if (hasPublished) {
        return 'partial'
    }
    return 'pending'
})

async function handleSearch() {
    const draftId = String(searchForm.draftId || '').trim()
    const knowledgeId = String(searchForm.knowledgeId || '').trim()
    if (!draftId && !knowledgeId) {
        ElMessage.warning('请选择草稿或知识')
        return
    }
    reportEvent(TRACE_TRACK_EVENTS.RUN_TRACE, {
        hasDraftId: !!draftId,
        hasKnowledgeId: !!knowledgeId,
    })
    const query = {}
    if (draftId) {
        query.draftId = draftId
    }
    if (knowledgeId) {
        query.knowledgeId = knowledgeId
    }
    await router.replace({ path: '/trace', query })
    await loadTrace({ draftId, knowledgeId })
}

/** 清空选择并回到最近一次 Recall 视图 */
function handleLoadLatest() {
    searchForm.draftId = ''
    searchForm.knowledgeId = ''
    draftOptions.value = []
    knowledgeOptions.value = []
    resetTrace()
    router.replace({ path: '/trace' })
    loadLatestTrace()
}

function handleReset() {
    handleLoadLatest()
}

onBeforeUnmount(() => {
    if (draftSearchTimer) {
        clearTimeout(draftSearchTimer)
    }
    if (knowledgeSearchTimer) {
        clearTimeout(knowledgeSearchTimer)
    }
})

onMounted(async () => {
    await loadTraceFromRoute(route)
    await ensureSelectedSearchOptions()
})
</script>

<style scoped>
.trace-page__toolbar {
    margin-bottom: 24px;
    padding: 22px 24px;
    background: #fff;
    border-radius: 14px;
    border: 1px solid #e8eaef;
    box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.trace-page__toolbar-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 16px;
}

.trace-page__toolbar-title {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: #111827;
}

.trace-page__toolbar-desc {
    margin: 4px 0 0;
    font-size: 12px;
    color: #9ca3af;
    line-height: 1.5;
}

.trace-page__latest-link {
    flex-shrink: 0;
    padding-top: 2px;
}

.trace-page__form-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr) auto;
    gap: 12px 16px;
    align-items: end;
}

.trace-page__field {
    margin-bottom: 0 !important;
}

.trace-page__or {
    display: flex;
    align-items: center;
    justify-content: center;
    padding-bottom: 10px;
    font-size: 12px;
    color: #d1d5db;
    user-select: none;
}

.trace-page__actions {
    display: flex;
    align-items: center;
    gap: 8px;
    padding-bottom: 2px;
    white-space: nowrap;
}

.trace-page__form :deep(.el-form-item__label) {
    font-size: 13px;
    font-weight: 500;
    color: #374151;
    padding-bottom: 4px;
}

.trace-page__form :deep(.el-select) {
    width: 100%;
}

.trace-page__option {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 2px 0;
    line-height: 1.35;
}

.trace-page__option-title {
    font-size: 13px;
    color: #111827;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.trace-page__option-meta {
    font-size: 11px;
    color: #9ca3af;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.trace-page__hint {
    margin: 14px 0 0;
    padding-top: 14px;
    border-top: 1px solid #f3f4f6;
    font-size: 12px;
    color: #9ca3af;
    line-height: 1.6;
}

@media (max-width: 960px) {
    .trace-page__form-row {
        grid-template-columns: 1fr;
    }

    .trace-page__or {
        display: none;
    }

    .trace-page__actions {
        width: 100%;
        padding-bottom: 0;
    }

    .trace-page__actions .el-button {
        flex: 1;
    }
}
</style>
