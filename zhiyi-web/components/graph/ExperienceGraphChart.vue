<template>
    <div class="experience-graph-chart">
        <div ref="chartContainerRef" class="experience-graph-chart__canvas" />
    </div>
</template>

<script setup>
import * as echarts from 'echarts'
import {
    getGraphNodeColor,
    getRelationEdgeColor,
    getRelationTypeLabel,
    RELATION_TYPE_LINE_STYLE,
    resolveGraphNodeSize,
} from '~/constants/graph'

const props = defineProps({
    /** 子图节点列表 */
    nodes: {
        type: Array,
        default: () => [],
    },
    /** 子图边列表 */
    edges: {
        type: Array,
        default: () => [],
    },
    /** 中心节点 ID，用于高亮 */
    centerId: {
        type: [Number, String],
        default: null,
    },
})

const emit = defineEmits(['node-click', 'node-dblclick'])

const chartContainerRef = ref(null)
let chartInstance = null
let clickTimer = null

/** 统计节点入度，用于节点大小 */
function buildInboundDegreeMap(edgeList) {
    const degreeMap = new Map()
    for (const edge of edgeList) {
        const targetId = String(edge.targetId)
        degreeMap.set(targetId, (degreeMap.get(targetId) || 0) + 1)
    }
    return degreeMap
}

/** 将后端子图数据转换为 ECharts graph 配置 */
function buildChartOption(nodeList, edgeList, centerNodeId) {
    const inboundDegreeMap = buildInboundDegreeMap(edgeList)
    const chartNodes = nodeList.map((node) => {
        const nodeId = String(node.id)
        const isCenter = String(centerNodeId) === nodeId
        return {
            id: nodeId,
            name: node.title || `经验 #${node.id}`,
            symbolSize: resolveGraphNodeSize(node.recallCount, inboundDegreeMap.get(nodeId) || 0),
            itemStyle: {
                color: getGraphNodeColor(node.knowledgeType),
                borderColor: isCenter ? '#111827' : '#ffffff',
                borderWidth: isCenter ? 3 : 1,
            },
            label: {
                show: true,
                formatter: (params) => {
                    const title = params.data.name || ''
                    return title.length > 14 ? `${title.slice(0, 14)}…` : title
                },
                fontSize: 11,
                color: '#334155',
            },
            rawNode: node,
        }
    })

    const chartLinks = edgeList.map((edge) => ({
        source: String(edge.sourceId),
        target: String(edge.targetId),
        lineStyle: {
            color: getRelationEdgeColor(edge.relationType),
            type: RELATION_TYPE_LINE_STYLE[edge.relationType] || 'solid',
            width: edge.relationSource === 'manual' ? 2.5 : 1.5,
            curveness: 0.15,
        },
        label: {
            show: false,
        },
        rawEdge: edge,
    }))

    return {
        animationDurationUpdate: 600,
        tooltip: {
            trigger: 'item',
            formatter: (params) => {
                if (params.dataType === 'edge') {
                    const edge = params.data.rawEdge
                    if (!edge) return ''
                    return `${getRelationTypeLabel(edge.relationType)}<br/>${edge.sourceId} → ${edge.targetId}`
                }
                const node = params.data.rawNode
                if (!node) return params.name
                const moduleText = node.module ? `<br/>模块：${node.module}` : ''
                return `${node.title || params.name}<br/>类型：${node.knowledgeType || '-'}${moduleText}`
            },
        },
        series: [
            {
                type: 'graph',
                layout: 'force',
                roam: true,
                draggable: true,
                data: chartNodes,
                links: chartLinks,
                force: {
                    repulsion: 280,
                    edgeLength: [80, 180],
                    gravity: 0.08,
                },
                emphasis: {
                    focus: 'adjacency',
                    lineStyle: {
                        width: 3,
                    },
                },
            },
        ],
    }
}

/** 初始化或更新图表 */
function renderChart() {
    if (!chartContainerRef.value) {
        return
    }
    if (!chartInstance) {
        chartInstance = echarts.init(chartContainerRef.value)
        chartInstance.on('click', (params) => {
            if (params.dataType !== 'node' || !params.data?.rawNode) {
                return
            }
            if (clickTimer) {
                clearTimeout(clickTimer)
                clickTimer = null
                emit('node-dblclick', params.data.rawNode)
                return
            }
            clickTimer = setTimeout(() => {
                emit('node-click', params.data.rawNode)
                clickTimer = null
            }, 220)
        })
    }
    chartInstance.setOption(buildChartOption(props.nodes, props.edges, props.centerId), true)
}

function disposeChart() {
    if (chartInstance) {
        chartInstance.dispose()
        chartInstance = null
    }
}

function handleResize() {
    chartInstance?.resize()
}

watch(
    () => [props.nodes, props.edges, props.centerId],
    () => {
        nextTick(() => renderChart())
    },
    { deep: true },
)

onMounted(() => {
    nextTick(() => renderChart())
    window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
    window.removeEventListener('resize', handleResize)
    if (clickTimer) {
        clearTimeout(clickTimer)
    }
    disposeChart()
})
</script>

<style scoped>
.experience-graph-chart {
    position: relative;
    width: 100%;
    min-height: 560px;
    border: 1px solid #e8eaef;
    border-radius: 14px;
    background-color: #fafbfc;
    background-image:
        radial-gradient(circle, rgba(148, 163, 184, 0.35) 1px, transparent 1px),
        linear-gradient(180deg, #f8fafc 0%, #fff 100%);
    background-size: 22px 22px, 100% 100%;
    box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
    overflow: hidden;
}

.experience-graph-chart__canvas {
    width: 100%;
    height: 560px;
}
</style>
