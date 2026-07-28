<template>
    <aside class="graph-node-preview">
        <div class="graph-node-preview__header">
            <h3 class="graph-node-preview__title">节点预览</h3>
            <el-tag size="small" effect="plain">
                {{ KNOWLEDGE_TYPE_LABELS[node.knowledgeType] || node.knowledgeType || '经验' }}
            </el-tag>
        </div>

        <p class="graph-node-preview__name">{{ node.title }}</p>

        <dl class="graph-node-preview__meta">
            <div v-if="node.project" class="graph-node-preview__meta-row">
                <dt>项目</dt>
                <dd>{{ node.project }}</dd>
            </div>
            <div v-if="node.module" class="graph-node-preview__meta-row">
                <dt>模块</dt>
                <dd>{{ node.module }}</dd>
            </div>
            <div class="graph-node-preview__meta-row">
                <dt>召回次数</dt>
                <dd>{{ node.recallCount ?? 0 }}</dd>
            </div>
        </dl>

        <div class="graph-node-preview__actions">
            <el-button type="primary" size="small" @click="emit('open-detail', node)">
                查看详情
            </el-button>
            <el-button size="small" @click="emit('recenter', node)">
                以此为中心
            </el-button>
        </div>
    </aside>
</template>

<script setup>
import { KNOWLEDGE_TYPE_LABELS } from '~/constants/knowledge'

defineProps({
    /** 当前选中的图谱节点 */
    node: {
        type: Object,
        required: true,
    },
})

const emit = defineEmits(['open-detail', 'recenter'])
</script>

<style scoped>
.graph-node-preview {
    border: 1px solid #e8eaef;
    border-radius: 14px;
    background: #fff;
    padding: 16px 18px;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
}

.graph-node-preview__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
}

.graph-node-preview__title {
    margin: 0;
    font-size: 12px;
    font-weight: 600;
    color: #6b7280;
    text-transform: uppercase;
    letter-spacing: 0.04em;
}

.graph-node-preview__name {
    margin: 10px 0 0;
    font-size: 15px;
    font-weight: 600;
    line-height: 1.5;
    color: #111827;
}

.graph-node-preview__meta {
    margin: 14px 0 0;
}

.graph-node-preview__meta-row {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    padding: 7px 0;
    border-bottom: 1px dashed #f3f4f6;
    font-size: 13px;
}

.graph-node-preview__meta-row:last-child {
    border-bottom: none;
}

.graph-node-preview__meta-row dt {
    margin: 0;
    color: #9ca3af;
}

.graph-node-preview__meta-row dd {
    margin: 0;
    color: #374151;
    text-align: right;
    font-weight: 500;
}

.graph-node-preview__actions {
    display: flex;
    gap: 8px;
    margin-top: 16px;
    padding-top: 14px;
    border-top: 1px solid #f3f4f6;
}
</style>
