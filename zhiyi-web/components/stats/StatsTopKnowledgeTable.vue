<template>
    <StatsPanel title="热点经验 Top 10" subtitle="按 Recall 调用次数排序">
        <el-skeleton v-if="loading" :rows="6" animated />

        <PageEmptyState
            v-else-if="topKnowledgeList.length === 0"
            title="暂无 Recall 记录"
            subtitle="经验被 Agent 召回后，将按调用热度在此排行"
            :tags="['Recall 次数', 'helpful 数', '模块', 'Top 10']"
            compact
            :bordered="false"
        />

        <el-table
            v-else
            :data="topKnowledgeList"
            stripe
            class="stats-top__table"
            @row-click="handleRowClick"
        >
            <el-table-column label="#" width="52" align="center">
                <template #default="{ $index }">
                    <span
                        class="stats-top__rank"
                        :class="{ 'stats-top__rank--top': $index < 3 }"
                    >
                        {{ $index + 1 }}
                    </span>
                </template>
            </el-table-column>
            <el-table-column label="标题" min-width="220">
                <template #default="{ row }">
                    <span class="stats-top__title">
                        {{ row.title }}
                    </span>
                </template>
            </el-table-column>
            <el-table-column label="模块" prop="module" width="120">
                <template #default="{ row }">
                    <span v-if="row.module" class="stats-top__module">{{ row.module }}</span>
                    <span v-else class="stats-top__placeholder">—</span>
                </template>
            </el-table-column>
            <el-table-column label="Recall 次数" prop="recallCount" width="110" align="right">
                <template #default="{ row }">
                    <span class="stats-top__count">{{ row.recallCount }}</span>
                </template>
            </el-table-column>
            <el-table-column label="helpful 数" prop="helpfulCount" width="100" align="right">
                <template #default="{ row }">
                    <span class="stats-top__helpful">{{ row.helpfulCount }}</span>
                </template>
            </el-table-column>
        </el-table>
    </StatsPanel>
</template>

<script setup>
const props = defineProps({
    loading: {
        type: Boolean,
        default: false,
    },
    topKnowledgeList: {
        type: Array,
        default: () => [],
    },
})

const router = useRouter()

/** 点击行跳转经验详情 */
function handleRowClick(row) {
    if (!row?.id) {
        return
    }
    router.push(`/experience/${row.id}`)
}
</script>

<style scoped>
.stats-top__table :deep(.el-table__row) {
    cursor: pointer;
}

.stats-top__table :deep(.el-table__header th) {
    background: #f9fafb !important;
    color: #6b7280;
    font-weight: 500;
}

.stats-top__rank {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: 6px;
    font-size: 12px;
    font-weight: 600;
    color: #9ca3af;
    background: #f3f4f6;
}

.stats-top__rank--top {
    color: #5d65f9;
    background: #eff0fe;
}

.stats-top__title {
    color: #111827;
    font-weight: 500;
    transition: color 0.15s ease;
}

.stats-top__table :deep(.el-table__row:hover) .stats-top__title {
    color: #5d65f9;
}

.stats-top__module {
    display: inline-block;
    padding: 2px 8px;
    border-radius: 6px;
    font-size: 12px;
    color: #6b7280;
    background: #f3f4f6;
}

.stats-top__placeholder {
    color: #d1d5db;
}

.stats-top__count {
    font-weight: 600;
    color: #111827;
    font-variant-numeric: tabular-nums;
}

.stats-top__helpful {
    font-weight: 500;
    color: #059669;
    font-variant-numeric: tabular-nums;
}
</style>
