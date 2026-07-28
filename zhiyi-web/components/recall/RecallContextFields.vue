<template>
    <div class="recall-context-fields" :class="[layoutClass, sizeClass]">
        <el-form-item
            v-if="showProject"
            :label="contextFieldLabel('项目')"
            :label-position="formItemLabelPosition"
            class="recall-context-fields__item"
        >
            <el-select
                :model-value="project"
                :size="size"
                filterable
                clearable
                allow-create
                default-first-option
                :loading="optionsLoading"
                :placeholder="projectPlaceholder"
                class="recall-context-fields__select"
                @update:model-value="handleProjectChange"
            >
                <el-option
                    v-for="optionItem in projectOptions"
                    :key="`project-${optionItem}`"
                    :label="optionItem"
                    :value="optionItem"
                />
            </el-select>
        </el-form-item>

        <el-form-item
            v-if="showRepository"
            :label="contextFieldLabel('仓库')"
            :label-position="formItemLabelPosition"
            class="recall-context-fields__item"
        >
            <el-select
                :model-value="repository"
                :size="size"
                filterable
                clearable
                allow-create
                default-first-option
                :loading="optionsLoading"
                :placeholder="repositoryPlaceholder"
                class="recall-context-fields__select"
                @update:model-value="handleRepositoryChange"
            >
                <el-option
                    v-for="optionItem in repositoryOptions"
                    :key="`repository-${optionItem}`"
                    :label="optionItem"
                    :value="optionItem"
                />
            </el-select>
        </el-form-item>

        <el-form-item
            v-if="showModule"
            :label="contextFieldLabel('模块')"
            :label-position="formItemLabelPosition"
            class="recall-context-fields__item"
        >
            <el-select
                :model-value="module"
                :size="size"
                filterable
                clearable
                allow-create
                default-first-option
                :loading="optionsLoading"
                :placeholder="modulePlaceholder"
                class="recall-context-fields__select"
                @update:model-value="handleModuleChange"
            >
                <el-option
                    v-for="optionItem in moduleOptions"
                    :key="`module-${optionItem}`"
                    :label="optionItem"
                    :value="optionItem"
                />
            </el-select>
        </el-form-item>

        <el-form-item
            v-if="showTag"
            :label="contextFieldLabel('标签')"
            :label-position="formItemLabelPosition"
            class="recall-context-fields__item"
        >
            <el-select
                :model-value="tag"
                :size="size"
                filterable
                clearable
                allow-create
                default-first-option
                :loading="optionsLoading"
                :placeholder="tagPlaceholder"
                class="recall-context-fields__select"
                @update:model-value="handleTagChange"
            >
                <el-option
                    v-for="optionItem in tagOptions"
                    :key="`tag-${optionItem}`"
                    :label="optionItem"
                    :value="optionItem"
                />
            </el-select>
        </el-form-item>
    </div>
</template>

<script setup>
/**
 * Recall 上下文字段下拉：历史选项 + 手填新建（el-select filterable 负责本地过滤）
 */
const props = defineProps({
    project: {
        type: String,
        default: '',
    },
    module: {
        type: String,
        default: '',
    },
    repository: {
        type: String,
        default: '',
    },
    tag: {
        type: String,
        default: '',
    },
    showProject: {
        type: Boolean,
        default: true,
    },
    showModule: {
        type: Boolean,
        default: true,
    },
    showRepository: {
        type: Boolean,
        default: true,
    },
    showTag: {
        type: Boolean,
        default: false,
    },
    showLabels: {
        type: Boolean,
        default: true,
    },
    size: {
        type: String,
        default: 'default',
    },
    layout: {
        type: String,
        default: 'grid',
        validator: (value) => ['grid', 'inline', 'stack', 'context-grid'].includes(value),
    },
    projectPlaceholder: {
        type: String,
        default: '项目',
    },
    modulePlaceholder: {
        type: String,
        default: '模块',
    },
    repositoryPlaceholder: {
        type: String,
        default: '仓库',
    },
    tagPlaceholder: {
        type: String,
        default: '标签',
    },
})

const emit = defineEmits([
    'update:project',
    'update:module',
    'update:repository',
    'update:tag',
])

const {
    optionsLoading,
    repositoryOptions,
    projectOptions,
    moduleOptions,
    tagOptions,
} = useWorkspaceContextOptions()

const layoutClass = computed(() => `recall-context-fields--${props.layout}`)

/** 尺寸类名，供 context-grid 等布局按 size 调整控件高度 */
const sizeClass = computed(() => `recall-context-fields--${props.size}`)

/** context-grid 使用标签置顶，保证三列标签与下拉框各自对齐 */
const formItemLabelPosition = computed(() =>
    props.layout === 'context-grid' ? 'top' : undefined,
)

/** context-grid 布局始终展示字段标签，其余布局按 showLabels 控制 */
function contextFieldLabel(label) {
    if (props.layout === 'context-grid' || props.showLabels) {
        return label
    }
    return undefined
}

function handleProjectChange(value) {
    emit('update:project', value || '')
}

function handleModuleChange(value) {
    emit('update:module', value || '')
}

function handleRepositoryChange(value) {
    emit('update:repository', value || '')
}

function handleTagChange(value) {
    emit('update:tag', value || '')
}
</script>

<style scoped>
.recall-context-fields {
    display: contents;
}

.recall-context-fields--grid {
    display: grid;
    grid-template-columns: 1fr;
    gap: 0 16px;
}

@media (min-width: 768px) {
    .recall-context-fields--grid {
        grid-template-columns: repeat(2, 1fr);
    }
}

.recall-context-fields--context-grid {
    display: grid;
    grid-template-columns: 1fr;
    gap: 8px 12px;
    align-items: start;
}

@media (min-width: 768px) {
    .recall-context-fields--context-grid {
        grid-template-columns: repeat(3, minmax(0, 1fr));
    }
}

/* 抽屉等小容器：始终三列 */
.recall-context-fields--context-grid.recall-context-fields--small {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 6px 10px;
}

.recall-context-fields--context-grid .recall-context-fields__item {
    margin-bottom: 0 !important;
}

.recall-context-fields--context-grid .recall-context-fields__item :deep(.el-form-item__label) {
    display: block;
    width: 100% !important;
    height: auto;
    margin-bottom: 4px;
    padding: 0;
    text-align: left;
    font-size: 12px;
    font-weight: 500;
    color: #6b7280;
    line-height: 16px;
}

.recall-context-fields--context-grid .recall-context-fields__item :deep(.el-form-item__content) {
    width: 100%;
    margin-left: 0 !important;
    line-height: normal;
}

.recall-context-fields--context-grid .recall-context-fields__item :deep(.el-select__wrapper) {
    min-height: 38px;
}

/* small 尺寸：紧凑下拉，抽屉等窄容器使用 */
.recall-context-fields--context-grid.recall-context-fields--small .recall-context-fields__item :deep(.el-form-item__label) {
    margin-bottom: 3px;
    font-size: 11px;
    line-height: 14px;
}

.recall-context-fields--context-grid.recall-context-fields--small .recall-context-fields__item :deep(.el-select__wrapper) {
    min-height: 26px;
    padding: 1px 8px;
    font-size: 12px;
}

.recall-context-fields--context-grid.recall-context-fields--small .recall-context-fields__item :deep(.el-select__placeholder),
.recall-context-fields--context-grid.recall-context-fields--small .recall-context-fields__item :deep(.el-select__selected-item) {
    font-size: 12px;
}

.recall-context-fields--inline {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
}

.recall-context-fields--inline .recall-context-fields__item {
    margin-bottom: 0 !important;
}

.recall-context-fields--inline .recall-context-fields__select {
    width: 108px;
}

.recall-context-fields--stack {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.recall-context-fields__item {
    margin-bottom: 0 !important;
}

.recall-context-fields__select {
    width: 100%;
}
</style>
