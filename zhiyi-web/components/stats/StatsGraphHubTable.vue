<template>
    <StatsPanel title="图谱枢纽 Top 10" subtitle="入度 + 出度高的核心经验，供治理关注">
        <el-skeleton v-if="loading" :rows="6" animated />

        <PageEmptyState
            v-else-if="graphHubList.length === 0"
            title="暂无图谱关系"
            subtitle="经验发布并建立关系边后，将识别连接度最高的枢纽节点"
            :tags="['入度', '出度', '核心经验', '治理']"
            compact
            :bordered="false"
        />

        <el-table
            v-else
            :data="graphHubList"
            stripe
            class="stats-graph-hub__table"
            @row-click="handleRowClick"
        >
            <el-table-column label="#" width="52" align="center">
                <template #default="{ $index }">
                    <span
                        class="stats-graph-hub__rank"
                        :class="{ 'stats-graph-hub__rank--top': $index < 3 }"
                    >
                        {{ $index + 1 }}
                    </span>
                </template>
            </el-table-column>
            <el-table-column label="标题" min-width="200">
                <template #default="{ row }">
                    <span class="stats-graph-hub__title">{{ row.title }}</span>
                </template>
            </el-table-column>
            <el-table-column label="模块" prop="module" width="110">
                <template #default="{ row }">
                    <span v-if="row.module">{{ row.module }}</span>
                    <span v-else class="stats-graph-hub__placeholder">—</span>
                </template>
            </el-table-column>
            <el-table-column label="入度" prop="inDegree" width="72" align="right" />
            <el-table-column label="出度" prop="outDegree" width="72" align="right" />
            <el-table-column label="总度数" prop="totalDegree" width="80" align="right">
                <template #default="{ row }">
                    <span class="stats-graph-hub__degree">{{ row.totalDegree }}</span>
                </template>
            </el-table-column>
            <el-table-column label="Recall" prop="recallCount" width="80" align="right" />
        </el-table>
    </StatsPanel>
</template>

<script setup>
const props = defineProps({
    loading: {
        type: Boolean,
        default: false,
    },
    graphHubList: {
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
.stats-graph-hub__table {
    width: 100%;
}

.stats-graph-hub__rank {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: 6px;
    font-size: 12px;
    font-weight: 600;
    color: #6b7280;
    background: #f3f4f6;
}

.stats-graph-hub__rank--top {
    color: #5d65f9;
    background: #eff0fe;
}

.stats-graph-hub__title {
    font-size: 13px;
    color: #111827;
}

.stats-graph-hub__degree {
    font-weight: 600;
    color: #5d65f9;
}

.stats-graph-hub__placeholder {
    color: #9ca3af;
}
</style>
