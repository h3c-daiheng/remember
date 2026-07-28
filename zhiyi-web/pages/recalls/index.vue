<template>
    <div class="recalls-page max-w-layout mx-auto px-6 py-8">
        <!-- 页头 -->
        <div class="recalls-page__header mb-6">
            <div class="flex flex-col lg:flex-row lg:items-start lg:justify-between gap-4">
                <div class="min-w-0">
                    <h1 class="text-2xl font-semibold text-gray-900 tracking-tight">
                        {{ PAGE_LABELS.recalls }}
                    </h1>
                    <p class="text-gray-500 mt-1.5 text-sm leading-relaxed max-w-2xl">
                        查看 Agent 发起的全部 Recall / Search 请求，排查命中与质量问题
                    </p>
                    <p v-if="!loading && total > 0" class="text-xs text-gray-400 mt-2">
                        共 {{ total }} 条记录
                        <span v-if="keyword"> · 搜索「{{ keyword }}」</span>
                    </p>
                </div>
                <el-button class="shrink-0 self-start" @click="router.push('/trace')">
                    <el-icon class="mr-1"><Connection /></el-icon>
                    闭环追踪
                </el-button>
            </div>
        </div>

        <!-- 筛选工具条 -->
        <section class="recalls-page__toolbar mb-6">
            <el-input
                v-model="keyword"
                placeholder="搜索任务、Session、Trace ID"
                clearable
                class="recalls-page__search"
                @keyup.enter="handleSearch"
                @clear="handleSearch"
            >
                <template #prefix>
                    <el-icon class="text-gray-400"><Search /></el-icon>
                </template>
            </el-input>
            <el-select
                v-model="operationFilter"
                placeholder="操作类型"
                clearable
                class="recalls-page__filter"
                @change="handleSearch"
            >
                <el-option label="召回" value="recall" />
                <el-option label="搜索" value="search" />
            </el-select>
            <el-select
                v-model="successFilter"
                placeholder="结果"
                clearable
                class="recalls-page__filter"
                @change="handleSearch"
            >
                <el-option label="成功" value="1" />
                <el-option label="失败" value="0" />
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
            v-else-if="recordList.length === 0 && !hasActiveFilter"
            :flow-steps="memoryFlywheelSteps"
            title="暂无 Agent 召回请求"
            subtitle="Agent 通过 memory_recall 调用后会在此留下记录；也可在闭环追踪中查看最近一次命中"
            :tags="['Recall', 'Search', 'MCP']"
            guide="配置 MCP 后，在 Cursor 中对任务调用 memory_recall，再回到本页刷新"
        >
            <el-button type="primary" plain @click="router.push('/dashboard/guide')">
                查看 MCP 接入指南
            </el-button>
            <el-button @click="router.push('/trace')">闭环追踪</el-button>
        </PageEmptyState>

        <PageEmptyState
            v-else-if="recordList.length === 0"
            title="未找到匹配的召回记录"
            subtitle="尝试更换关键词或筛选条件"
            compact
        >
            <el-button @click="clearFilters">清除筛选</el-button>
        </PageEmptyState>

        <div v-else class="space-y-3">
            <RecallLogListItem
                v-for="record in recordList"
                :key="record.logId"
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

        <RecallLogDetailDrawer
            :visible="drawerVisible"
            :log-id="activeLogId"
            @update:visible="drawerVisible = $event"
        />
    </div>
</template>

<script setup>
import { Connection, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { MEMORY_FLYWHEEL_STEPS } from '~/constants/pageEmptyState'
import { PAGE_LABELS } from '~/constants/terminology'
import RecallLogDetailDrawer from '~/components/trace/RecallLogDetailDrawer.vue'
import RecallLogListItem from '~/components/trace/RecallLogListItem.vue'

definePageMeta({
    layout: 'app',
})

useHead({ title: PAGE_LABELS.recalls })

usePageTracker()

const router = useRouter()
const memoryFlywheelSteps = MEMORY_FLYWHEEL_STEPS

const {
    loading,
    keyword,
    operationFilter,
    successFilter,
    pageNum,
    pageSize,
    total,
    recordList,
    loadList,
    changePage,
    search,
} = useRecallLogList()

const drawerVisible = ref(false)
const activeLogId = ref(null)

/** 是否存在筛选条件（用于区分空库与筛选无结果） */
const hasActiveFilter = computed(() =>
    Boolean(keyword.value || operationFilter.value || successFilter.value !== ''),
)

/** 打开召回详情抽屉 */
function openRecordDrawer(record) {
    activeLogId.value = record?.logId || null
    drawerVisible.value = true
}

/** 搜索 / 筛选 */
async function handleSearch() {
    try {
        await search()
    } catch (error) {
        ElMessage.error(error.message || '加载失败')
    }
}

/** 翻页 */
async function handlePageChange(currentPage) {
    try {
        await changePage(currentPage)
    } catch (error) {
        ElMessage.error(error.message || '加载失败')
    }
}

/** 清除全部筛选 */
async function clearFilters() {
    keyword.value = ''
    operationFilter.value = ''
    successFilter.value = ''
    await handleSearch()
}

onMounted(async () => {
    try {
        await loadList()
    } catch (error) {
        ElMessage.error(error.message || '加载失败')
    }
})

useWorkspaceChange(async () => {
    drawerVisible.value = false
    activeLogId.value = null
    pageNum.value = 1
    try {
        await loadList()
    } catch (error) {
        ElMessage.error(error.message || '加载失败')
    }
})
</script>

<style scoped>
.recalls-page__toolbar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 10px;
    padding: 14px 16px;
    border-radius: 12px;
    background: #fff;
    border: 1px solid #e5e7eb;
}

.recalls-page__search {
    flex: 1 1 220px;
    min-width: 180px;
}

.recalls-page__filter {
    width: 140px;
}

@media (max-width: 640px) {
    .recalls-page__filter {
        width: 100%;
        flex: 1 1 100%;
    }
}
</style>
