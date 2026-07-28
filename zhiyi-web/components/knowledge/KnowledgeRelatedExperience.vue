<template>
    <div class="related-experience" :class="{ 'related-experience--compact': compact }">
        <header class="related-experience__head">
            <h3 class="related-experience__title">
                <span class="related-experience__icon">
                    <el-icon :size="13"><Connection /></el-icon>
                </span>
                <span class="related-experience__title-text">相关经验</span>
                <span v-if="relatedList.length" class="related-experience__count">{{ relatedList.length }}</span>
            </h3>
        </header>

        <el-skeleton v-if="loading" :rows="compact ? 2 : 3" animated />

        <div v-else-if="!relatedList.length" class="related-panel__empty">
            <p class="related-panel__empty-text">暂无相关经验</p>
            <p class="related-panel__empty-hint">
                同工作空间内发布更多经验后，将按模块、标签与语义相似度推荐关联内容
            </p>
        </div>

        <ul v-else class="related-experience__list">
            <li
                v-for="item in relatedList"
                :key="item.id"
                class="related-experience__item"
            >
                <NuxtLink :to="`/experience/${item.id}`" class="related-experience__link">
                    <p class="related-experience__item-title">{{ item.title }}</p>
                    <KnowledgeMeta
                        v-if="hasMeta(item)"
                        :knowledge="item"
                        :variant="compact ? 'compact' : 'inline'"
                        class="related-experience__meta"
                    />
                    <div
                        v-if="item.relationTypes && item.relationTypes.length"
                        class="related-experience__relation-types"
                    >
                        <span
                            v-for="relationType in item.relationTypes"
                            :key="relationType"
                            class="related-experience__relation-type"
                        >
                            {{ formatRelationType(relationType) }}
                        </span>
                    </div>
                    <div
                        v-if="item.sharedTags && item.sharedTags.length"
                        class="related-experience__shared-tags"
                    >
                        <span
                            v-for="tag in item.sharedTags"
                            :key="tag"
                            class="related-experience__tag"
                        >
                            {{ tag }}
                        </span>
                    </div>
                </NuxtLink>
            </li>
        </ul>
    </div>
</template>

<script setup>
import { Connection } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchKnowledgeRelated } from '~/services/knowledge.service'

const props = defineProps({
    /** 当前经验 ID */
    knowledgeId: {
        type: [String, Number],
        default: null,
    },
    /** 返回条数上限 */
    limit: {
        type: Number,
        default: 10,
    },
    /** 侧边栏紧凑模式 */
    compact: {
        type: Boolean,
        default: false,
    },
})

const loading = ref(false)
const relatedList = ref([])

/** 关系类型中文映射 */
const relationTypeLabelMap = {
    same_module: '同模块',
    same_tag: '同标签',
    related_semantic: '语义相近',
    same_artifact: '同源 Artifact',
    related_decision: '关联决策',
    references: '引用',
    depends_on: '依赖',
    supersedes: '替代',
}

/** 格式化关系类型展示文案 */
function formatRelationType(relationType) {
    return relationTypeLabelMap[relationType] || relationType
}

/** 判断相关经验是否包含项目/模块/仓库元信息 */
function hasMeta(item) {
    return Boolean(item.project || item.module || item.repository)
}

/** 加载相关经验列表 */
async function loadRelatedList(options = {}) {
    if (!props.knowledgeId) {
        relatedList.value = []
        return
    }
    if (!options.silent) {
        loading.value = true
    }
    try {
        relatedList.value = await fetchKnowledgeRelated(props.knowledgeId, props.limit)
    } catch (error) {
        relatedList.value = []
        if (!options.silent) {
            ElMessage.error(error.message || '加载相关经验失败')
        }
    } finally {
        if (!options.silent) {
            loading.value = false
        }
    }
}

watch(
    () => [props.knowledgeId, props.limit],
    () => {
        loadRelatedList()
    },
    { immediate: true },
)
</script>

<style scoped>
.related-experience {
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 10px;
    padding: 12px 14px;
}

.related-experience--compact {
    padding: 10px 12px;
}

.related-experience__head {
    margin-bottom: 10px;
}

.related-experience__title {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0;
    font-size: 13px;
    font-weight: 600;
    color: #111827;
}

.related-experience__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    border-radius: 6px;
    color: #5d65f9;
    background: rgba(93, 101, 249, 0.1);
    flex-shrink: 0;
}

.related-experience__title-text {
    flex: 1;
    min-width: 0;
}

.related-experience__count {
    flex-shrink: 0;
    min-width: 18px;
    padding: 0 6px;
    border-radius: 9999px;
    font-size: 11px;
    font-weight: 500;
    line-height: 18px;
    text-align: center;
    color: #5d65f9;
    background: rgba(93, 101, 249, 0.08);
}

.related-experience__list {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.related-experience--compact .related-experience__list {
    max-height: 300px;
    overflow-y: auto;
    padding-right: 2px;
    scrollbar-width: thin;
    scrollbar-color: #d1d5db transparent;
}

.related-experience__item {
    border: 1px solid #eef2f7;
    border-radius: 8px;
    background: #fafbfc;
    transition: border-color 0.15s, background 0.15s, box-shadow 0.15s;
}

.related-experience__item:hover {
    border-color: rgba(93, 101, 249, 0.22);
    background: rgba(93, 101, 249, 0.03);
    box-shadow: 0 1px 2px rgba(93, 101, 249, 0.06);
}

.related-experience__link {
    display: block;
    padding: 8px 10px;
    text-decoration: none;
    color: inherit;
}

.related-experience__item-title {
    margin: 0;
    font-size: 12px;
    font-weight: 600;
    color: #111827;
    line-height: 1.45;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.related-experience__meta {
    margin-top: 5px;
}

.related-experience__relation-types {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    margin-top: 5px;
}

.related-experience__relation-type {
    display: inline-block;
    padding: 0 6px;
    border-radius: 9999px;
    font-size: 10px;
    line-height: 16px;
    color: #4338ca;
    background: #eef2ff;
}

.related-experience__shared-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    margin-top: 5px;
}

.related-experience__tag {
    display: inline-block;
    padding: 0 6px;
    border-radius: 9999px;
    font-size: 10px;
    line-height: 16px;
    color: #5d65f9;
    background: rgba(93, 101, 249, 0.08);
}
</style>
