<template>
    <div class="ai-review-page max-w-layout mx-auto px-6 py-8">
        <!-- 页头 -->
        <div class="ai-review-page__header mb-6">
            <div class="flex flex-col lg:flex-row lg:items-start lg:justify-between gap-4">
                <div class="min-w-0">
                    <h1 class="text-2xl font-semibold text-gray-900 tracking-tight">
                        {{ PAGE_LABELS.aiReview }}
                    </h1>
                    <p class="text-gray-500 mt-1.5 text-sm leading-relaxed max-w-2xl">
                        查看 Capture 草稿的 AI 审查记录与轨迹；待审操作请前往草稿确认
                    </p>
                    <p v-if="!loading && total > 0" class="text-xs text-gray-400 mt-2">
                        共 {{ total }} 条记录
                        <span v-if="keyword"> · 搜索「{{ keyword }}」</span>
                        <span v-if="hasRunningRecords"> · 有进行中任务，自动刷新中</span>
                    </p>
                </div>
                <el-button class="shrink-0 self-start" @click="router.push('/capture')">
                    <el-icon class="mr-1"><DocumentChecked /></el-icon>
                    前往草稿确认
                </el-button>
            </div>
        </div>

        <!-- 筛选工具条：横向对齐，降低纵向占用 -->
        <section class="ai-review-page__toolbar mb-6">
            <el-input
                v-model="keyword"
                placeholder="搜索草稿标题"
                clearable
                class="ai-review-page__search"
                @keyup.enter="handleSearch"
                @clear="handleSearch"
            >
                <template #prefix>
                    <el-icon class="text-gray-400"><Search /></el-icon>
                </template>
            </el-input>
            <el-select
                v-model="statusFilter"
                placeholder="状态"
                clearable
                class="ai-review-page__filter"
                @change="handleSearch"
            >
                <el-option
                    v-for="option in statusOptions"
                    :key="String(option.value)"
                    :label="option.label"
                    :value="option.value"
                />
            </el-select>
            <el-select
                v-model="decisionFilter"
                placeholder="决策"
                clearable
                class="ai-review-page__filter"
                @change="handleSearch"
            >
                <el-option
                    v-for="option in decisionOptions"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                />
            </el-select>
            <el-select
                v-model="similarHitFilter"
                placeholder="相似命中"
                clearable
                class="ai-review-page__filter"
                @change="handleSearch"
            >
                <el-option label="已命中" value="true" />
                <el-option label="未命中" value="false" />
            </el-select>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
        </section>

        <div v-if="loading" class="space-y-3">
            <div
                v-for="index in 4"
                :key="index"
                class="bg-white rounded-xl border border-gray-200 p-5"
            >
                <el-skeleton :rows="2" />
            </div>
        </div>

        <PageEmptyState
            v-else-if="recordList.length === 0"
            title="暂无 AI 审查记录"
            subtitle="Agent 提交草稿后会自动产生审查记录；也可在草稿确认或详情抽屉中重跑 AI 审查"
            compact
        >
            <el-button type="primary" plain @click="router.push('/capture')">
                前往草稿确认
            </el-button>
        </PageEmptyState>

        <div v-else class="space-y-3">
            <AiReviewRecordListItem
                v-for="record in recordList"
                :key="record.id"
                :record="record"
                @open="openRecordDrawer"
            />
        </div>

        <div v-if="total > pageSize" class="flex justify-center mt-10">
            <el-pagination
                background
                layout="total, prev, pager, next"
                :total="total"
                :page-size="pageSize"
                :current-page="pageNum"
                @current-change="handlePageChange"
            />
        </div>

        <AiReviewDetailDrawer
            ref="detailDrawerRef"
            :visible="drawerVisible"
            :record-id="activeRecordId"
            :can-review="canEdit"
            @update:visible="drawerVisible = $event"
            @retried="handleRetried"
            @record-changed="handleRecordChanged"
            @go-capture="handleGoCapture"
        />
    </div>
</template>

<script setup>
import { DocumentChecked, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { PAGE_LABELS } from '~/constants/terminology'
import {
    AI_REVIEW_DECISION,
    AI_REVIEW_STATUS,
    AI_REVIEW_STATUS_LABELS,
    formatAiReviewDecision,
} from '~/utils/aiReview'

definePageMeta({
    layout: 'app',
})

useHead({ title: PAGE_LABELS.aiReview })

usePageTracker()

const router = useRouter()
const { canEdit } = useCanEditKnowledge()

const {
    loading,
    keyword,
    statusFilter,
    decisionFilter,
    similarHitFilter,
    pageNum,
    pageSize,
    total,
    recordList,
    hasRunningRecords,
    loadList,
    changePage,
    search,
} = useAiReviewRecords()

const drawerVisible = ref(false)
const activeRecordId = ref(null)
const detailDrawerRef = ref(null)

/** 列表轮询定时器（queued/running 每 3s 静默刷新） */
let listPollTimer = null

const statusOptions = [
    { value: AI_REVIEW_STATUS.QUEUED, label: AI_REVIEW_STATUS_LABELS[AI_REVIEW_STATUS.QUEUED] },
    { value: AI_REVIEW_STATUS.RUNNING, label: AI_REVIEW_STATUS_LABELS[AI_REVIEW_STATUS.RUNNING] },
    { value: AI_REVIEW_STATUS.DONE, label: AI_REVIEW_STATUS_LABELS[AI_REVIEW_STATUS.DONE] },
    { value: AI_REVIEW_STATUS.FAILED, label: AI_REVIEW_STATUS_LABELS[AI_REVIEW_STATUS.FAILED] },
]

const decisionOptions = [
    { value: AI_REVIEW_DECISION.APPROVE, label: formatAiReviewDecision(AI_REVIEW_DECISION.APPROVE) },
    { value: AI_REVIEW_DECISION.REJECT, label: formatAiReviewDecision(AI_REVIEW_DECISION.REJECT) },
    { value: AI_REVIEW_DECISION.ROUTE, label: formatAiReviewDecision(AI_REVIEW_DECISION.ROUTE) },
    {
        value: AI_REVIEW_DECISION.ESCALATE_HUMAN,
        label: formatAiReviewDecision(AI_REVIEW_DECISION.ESCALATE_HUMAN),
    },
]

/** 停止列表轮询 */
function stopListPoll() {
    if (listPollTimer != null) {
        clearTimeout(listPollTimer)
        listPollTimer = null
    }
}

/**
 * 若当前页存在进行中记录，则每 3s 静默刷新列表（并同步详情）
 */
function scheduleListPollIfNeeded() {
    stopListPoll()
    if (!hasRunningRecords.value) {
        return
    }
    listPollTimer = setTimeout(async () => {
        try {
            await loadList({ silent: true })
            if (drawerVisible.value && detailDrawerRef.value?.reload) {
                await detailDrawerRef.value.reload()
            }
        } catch (error) {
            console.warn('AI 审查列表静默刷新失败', error)
        }
        scheduleListPollIfNeeded()
    }, 3000)
}

/** 打开记录详情 */
function openRecordDrawer(record) {
    activeRecordId.value = record?.id || null
    drawerVisible.value = true
}

/** 搜索 / 筛选 */
async function handleSearch() {
    try {
        await search()
        scheduleListPollIfNeeded()
    } catch (error) {
        ElMessage.error(error.message || '加载失败')
    }
}

/** 分页 */
async function handlePageChange(currentPage) {
    try {
        await changePage(currentPage)
        scheduleListPollIfNeeded()
    } catch (error) {
        ElMessage.error(error.message || '加载失败')
    }
}

/** 重跑后刷新列表并继续轮询 */
async function handleRetried() {
    try {
        await loadList({ silent: true })
        scheduleListPollIfNeeded()
    } catch (error) {
        ElMessage.error(error.message || '刷新失败')
    }
}

/** 抽屉重跑后切到最新记录 ID */
function handleRecordChanged(recordId) {
    if (recordId != null) {
        activeRecordId.value = recordId
    }
}

/** 跳转草稿确认并打开对应草稿 */
function handleGoCapture(draftId) {
    router.push({
        path: '/capture',
        query: { draftId: String(draftId) },
    })
}

onMounted(async () => {
    try {
        await loadList()
        scheduleListPollIfNeeded()
    } catch (error) {
        ElMessage.error(error.message || '加载失败')
    }
})

useWorkspaceChange(async () => {
    stopListPoll()
    drawerVisible.value = false
    activeRecordId.value = null
    pageNum.value = 1
    try {
        await loadList({ silent: true })
        scheduleListPollIfNeeded()
    } catch (error) {
        ElMessage.error(error.message || '加载失败')
    }
})

onBeforeUnmount(() => {
    stopListPoll()
})
</script>

<style scoped>
.ai-review-page__toolbar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 10px;
    padding: 14px 16px;
    border-radius: 12px;
    background: #fff;
    border: 1px solid #e5e7eb;
}

.ai-review-page__search {
    flex: 1 1 220px;
    min-width: 180px;
}

.ai-review-page__filter {
    width: 140px;
}

@media (max-width: 640px) {
    .ai-review-page__filter {
        width: 100%;
        flex: 1 1 100%;
    }
}
</style>
