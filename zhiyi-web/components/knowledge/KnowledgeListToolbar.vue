<template>
  <!-- 知识列表页通用工具栏：操作与搜索横向排列，减少纵向占用 -->
  <div class="knowledge-list-toolbar">
    <!-- 紧凑模式：新建与搜索同一行（决策中心等简单场景） -->
    <div
      v-if="useInlineLayout"
      class="knowledge-list-toolbar__inline"
    >
      <el-button
        v-if="showCreate && canEdit"
        type="primary"
        :loading="creating"
        @click="emit('create')"
      >
        <el-icon class="mr-1"><Plus /></el-icon>
        {{ createLabel }}
      </el-button>
      <el-input
        :model-value="keyword"
        placeholder="搜索标题、项目、模块"
        clearable
        @update:model-value="emit('update:keyword', $event)"
        @keyup.enter="emit('search')"
        @clear="emit('search')"
      >
        <template #prefix>
          <el-icon class="text-gray-400"><Search /></el-icon>
        </template>
      </el-input>
      <el-button type="primary" @click="emit('search')">搜索</el-button>
    </div>

    <template v-else>
      <!-- 标准模式第一行：类型切换、新建、辅助入口 -->
      <div
        v-if="hasActionsRow"
        class="knowledge-list-toolbar__actions"
      >
        <slot name="actions-start" />
        <el-button
          v-if="showCreate && canEdit"
          type="primary"
          :loading="creating"
          @click="emit('create')"
        >
          <el-icon class="mr-1"><Plus /></el-icon>
          {{ createLabel }}
        </el-button>
        <slot name="actions-end" />
      </div>

      <!-- 标准模式第二行：关键词搜索 -->
      <div class="knowledge-list-toolbar__search">
        <el-input
          :model-value="keyword"
          placeholder="搜索标题、项目、模块"
          clearable
          @update:model-value="emit('update:keyword', $event)"
          @keyup.enter="emit('search')"
          @clear="emit('search')"
        >
          <template #prefix>
            <el-icon class="text-gray-400"><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="emit('search')">搜索</el-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { Plus, Search } from '@element-plus/icons-vue'
import { useSlots } from 'vue'

const props = defineProps({
  /** 搜索关键词，支持 v-model:keyword */
  keyword: {
    type: String,
    default: '',
  },
  /** 是否展示新建按钮 */
  showCreate: {
    type: Boolean,
    default: true,
  },
  canEdit: {
    type: Boolean,
    default: false,
  },
  creating: {
    type: Boolean,
    default: false,
  },
  createLabel: {
    type: String,
    default: '新建',
  },
})

const emit = defineEmits(['update:keyword', 'create', 'search'])

const slots = useSlots()

const hasActionSlots = computed(() =>
  Boolean(slots['actions-start']) || Boolean(slots['actions-end']),
)

/** 存在插槽内容或新建按钮时展示操作行 */
const hasActionsRow = computed(() =>
  props.showCreate && props.canEdit
  || hasActionSlots.value,
)

/** 无额外操作时，新建与搜索合并为一行 */
const useInlineLayout = computed(() =>
  !hasActionSlots.value && (!props.showCreate || props.canEdit),
)
</script>

<style scoped>
.knowledge-list-toolbar {
  width: 100%;
  max-width: 440px;
  padding: 12px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.knowledge-list-toolbar__actions,
.knowledge-list-toolbar__inline,
.knowledge-list-toolbar__search {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.knowledge-list-toolbar__inline :deep(.el-input),
.knowledge-list-toolbar__search :deep(.el-input) {
  flex: 1;
  min-width: 140px;
}
</style>
