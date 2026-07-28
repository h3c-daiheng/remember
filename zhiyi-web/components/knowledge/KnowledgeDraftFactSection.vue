<template>
    <!-- 知识草稿 Fact 编辑区：可增删段落，供各中心直达创建使用 -->
    <section class="knowledge-draft-fact-section">
        <div v-if="sectionTitle || sectionDesc" class="knowledge-draft-fact-section__head">
            <div>
                <h2 v-if="sectionTitle" class="knowledge-draft-fact-section__title">
                    {{ sectionTitle }}
                </h2>
                <p v-if="sectionDesc" class="knowledge-draft-fact-section__desc">
                    {{ sectionDesc }}
                </p>
            </div>
            <span v-if="facts.length" class="knowledge-draft-fact-section__count">
                {{ facts.length }} 段
            </span>
        </div>

        <KnowledgeFactBlockEditor
            v-if="facts.length"
            :facts="facts"
            removable
            @remove="handleRemoveFact"
        />

        <div v-else class="knowledge-draft-fact-section__empty">
            <div class="knowledge-draft-fact-section__empty-icon">
                <el-icon :size="28"><DocumentAdd /></el-icon>
            </div>
            <h3 class="knowledge-draft-fact-section__empty-title">开始撰写内容</h3>
            <p class="knowledge-draft-fact-section__empty-desc">
                点击下方推荐段落快速开始，或通过「添加段落」自选类型
            </p>
            <div class="knowledge-draft-fact-section__quick-add">
                <button
                    v-for="factType in recommendedFactTypes"
                    :key="factType"
                    type="button"
                    class="knowledge-draft-fact-section__quick-card"
                    @click="handleAddFact(factType)"
                >
                    <span
                        class="knowledge-draft-fact-section__quick-accent"
                        :class="getFactTypeTheme(factType).accent"
                    />
                    <span class="knowledge-draft-fact-section__quick-body">
                        <span
                            class="knowledge-draft-fact-section__quick-label"
                            :class="getFactTypeTheme(factType).badge"
                        >
                            {{ FACT_TYPE_LABELS[factType] || factType }}
                        </span>
                        <span class="knowledge-draft-fact-section__quick-hint">
                            {{ FACT_TYPE_HINTS[factType] || '点击添加此类型段落' }}
                        </span>
                    </span>
                    <el-icon class="knowledge-draft-fact-section__quick-plus" :size="16">
                        <Plus />
                    </el-icon>
                </button>
            </div>
        </div>

        <div class="knowledge-draft-fact-section__toolbar">
            <div v-if="facts.length && remainingFactTypes.length" class="knowledge-draft-fact-section__chips">
                <span class="knowledge-draft-fact-section__chips-label">继续添加</span>
                <button
                    v-for="factType in remainingFactTypes"
                    :key="factType"
                    type="button"
                    class="knowledge-draft-fact-section__chip"
                    @click="handleAddFact(factType)"
                >
                    <el-icon :size="12"><Plus /></el-icon>
                    {{ FACT_TYPE_LABELS[factType] || factType }}
                </button>
            </div>

            <el-dropdown trigger="click" @command="handleAddFact">
                <button type="button" class="knowledge-draft-fact-section__add-button">
                    <el-icon :size="14"><Plus /></el-icon>
                    <span>添加段落</span>
                    <el-icon :size="12"><ArrowDown /></el-icon>
                </button>
                <template #dropdown>
                    <el-dropdown-menu>
                        <el-dropdown-item
                            v-for="factType in allowedFactTypes"
                            :key="factType"
                            :command="factType"
                        >
                            <span class="knowledge-draft-fact-section__dropdown-item">
                                <span>{{ FACT_TYPE_LABELS[factType] || factType }}</span>
                                <span class="knowledge-draft-fact-section__dropdown-hint">
                                    {{ FACT_TYPE_HINTS[factType] }}
                                </span>
                            </span>
                        </el-dropdown-item>
                    </el-dropdown-menu>
                </template>
            </el-dropdown>
        </div>
    </section>
</template>

<script setup>
import { ArrowDown, DocumentAdd, Plus } from '@element-plus/icons-vue'
import {
    ALLOWED_FACT_TYPES_BY_KNOWLEDGE_TYPE,
    DEFAULT_FACT_TYPES_BY_KNOWLEDGE_TYPE,
    FACT_TYPE_HINTS,
    FACT_TYPE_LABELS,
    KNOWLEDGE_TYPES,
    getFactTypeTheme,
} from '~/constants/knowledge'

const props = defineProps({
    facts: {
        type: Array,
        required: true,
    },
    knowledgeType: {
        type: String,
        default: KNOWLEDGE_TYPES.EXPERIENCE,
    },
    sectionTitle: {
        type: String,
        default: '内容',
    },
    sectionDesc: {
        type: String,
        default: '',
    },
})

/** 当前知识类型允许追加的 Fact 类型 */
const allowedFactTypes = computed(() =>
    ALLOWED_FACT_TYPES_BY_KNOWLEDGE_TYPE[props.knowledgeType]
    || ALLOWED_FACT_TYPES_BY_KNOWLEDGE_TYPE.experience,
)

/** 空状态下优先展示的推荐 Fact 类型 */
const recommendedFactTypes = computed(() => {
    const defaults = DEFAULT_FACT_TYPES_BY_KNOWLEDGE_TYPE[props.knowledgeType]
        || DEFAULT_FACT_TYPES_BY_KNOWLEDGE_TYPE.experience
    return defaults.filter((factType) => allowedFactTypes.value.includes(factType))
})

/** 已有内容时，快捷 chip 展示尚未添加的类型 */
const remainingFactTypes = computed(() => {
    const existingTypes = new Set(props.facts.map((fact) => fact.type))
    return allowedFactTypes.value.filter((factType) => !existingTypes.has(factType))
})

/** 追加空白 Fact Block */
function handleAddFact(factType) {
    props.facts.push({
        type: factType,
        text: '',
    })
}

/** 删除指定下标 Fact Block */
function handleRemoveFact(index) {
    props.facts.splice(index, 1)
}
</script>

<style scoped>
.knowledge-draft-fact-section__head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 20px;
    padding-bottom: 16px;
    border-bottom: 1px solid #f3f4f6;
}

.knowledge-draft-fact-section__title {
    font-size: 15px;
    font-weight: 600;
    color: #111827;
    letter-spacing: 0.01em;
}

.knowledge-draft-fact-section__desc {
    margin-top: 6px;
    max-width: 640px;
    font-size: 13px;
    color: #6b7280;
    line-height: 1.6;
}

.knowledge-draft-fact-section__count {
    flex-shrink: 0;
    padding: 4px 10px;
    border-radius: 9999px;
    font-size: 12px;
    font-weight: 500;
    color: #6366f1;
    background: #eef2ff;
}

.knowledge-draft-fact-section__empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 36px 20px 28px;
    border: 1px dashed #e5e7eb;
    border-radius: 14px;
    background: linear-gradient(180deg, #fafafa 0%, #fff 100%);
    text-align: center;
}

.knowledge-draft-fact-section__empty-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 56px;
    height: 56px;
    margin-bottom: 14px;
    border-radius: 14px;
    color: #6366f1;
    background: #eef2ff;
}

.knowledge-draft-fact-section__empty-title {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: #111827;
}

.knowledge-draft-fact-section__empty-desc {
    margin: 8px 0 0;
    max-width: 420px;
    font-size: 13px;
    color: #9ca3af;
    line-height: 1.6;
}

.knowledge-draft-fact-section__quick-add {
    display: grid;
    grid-template-columns: 1fr;
    gap: 10px;
    width: 100%;
    max-width: 560px;
    margin-top: 22px;
    text-align: left;
}

@media (min-width: 640px) {
    .knowledge-draft-fact-section__quick-add {
        grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
    }
}

.knowledge-draft-fact-section__quick-card {
    position: relative;
    display: flex;
    align-items: flex-start;
    gap: 10px;
    padding: 14px 14px 14px 12px;
    border: 1px solid #e5e7eb;
    border-radius: 12px;
    background: #fff;
    cursor: pointer;
    transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}

.knowledge-draft-fact-section__quick-card:hover {
    border-color: #c7d2fe;
    box-shadow: 0 4px 12px rgba(99, 102, 241, 0.08);
    transform: translateY(-1px);
}

.knowledge-draft-fact-section__quick-accent {
    flex-shrink: 0;
    width: 3px;
    align-self: stretch;
    border-radius: 9999px;
}

.knowledge-draft-fact-section__quick-body {
    display: flex;
    flex: 1;
    flex-direction: column;
    gap: 4px;
    min-width: 0;
}

.knowledge-draft-fact-section__quick-label {
    display: inline-flex;
    align-self: flex-start;
    padding: 2px 8px;
    border-radius: 9999px;
    font-size: 12px;
    font-weight: 500;
    line-height: 1.4;
}

.knowledge-draft-fact-section__quick-hint {
    font-size: 12px;
    color: #6b7280;
    line-height: 1.5;
}

.knowledge-draft-fact-section__quick-plus {
    flex-shrink: 0;
    margin-top: 2px;
    color: #9ca3af;
}

.knowledge-draft-fact-section__toolbar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: flex-end;
    gap: 12px;
    margin-top: 18px;
    padding-top: 16px;
    border-top: 1px solid #f3f4f6;
}

.knowledge-draft-fact-section__add-button {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 8px 14px;
    border: 1px solid #c7d2fe;
    border-radius: 8px;
    background: #eef2ff;
    font-size: 14px;
    font-weight: 500;
    line-height: 1;
    color: #4338ca;
    cursor: pointer;
    transition: background 0.15s, border-color 0.15s, color 0.15s;
}

.knowledge-draft-fact-section__add-button:hover {
    border-color: #a5b4fc;
    background: #e0e7ff;
    color: #3730a3;
}

.knowledge-draft-fact-section__add-button:focus-visible {
    outline: 2px solid #a5b4fc;
    outline-offset: 2px;
}

.knowledge-draft-fact-section__chips {
    display: flex;
    flex: 1;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    min-width: 0;
}

.knowledge-draft-fact-section__chips-label {
    font-size: 12px;
    color: #9ca3af;
}

.knowledge-draft-fact-section__chip {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 5px 10px;
    border: 1px solid #e5e7eb;
    border-radius: 9999px;
    background: #fff;
    font-size: 12px;
    color: #4b5563;
    cursor: pointer;
    transition: border-color 0.15s, color 0.15s, background 0.15s;
}

.knowledge-draft-fact-section__chip:hover {
    border-color: #c7d2fe;
    color: #4338ca;
    background: #f5f7ff;
}

.knowledge-draft-fact-section__dropdown-item {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 220px;
    padding: 2px 0;
}

.knowledge-draft-fact-section__dropdown-hint {
    font-size: 12px;
    color: #9ca3af;
    line-height: 1.4;
}
</style>
