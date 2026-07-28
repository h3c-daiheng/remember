<template>
    <!-- Capture 合并到已有 Rule 弹窗 -->
    <el-dialog
        :model-value="visible"
        title="合并到已有 Rule"
        width="520px"
        destroy-on-close
        @update:model-value="emit('update:visible', $event)"
    >
        <p class="text-sm text-gray-600 mb-4">
            将本草稿中的 rule/constraint Fact 追加到目标 Rule（文本去重），草稿将标记为已合并关闭。
        </p>

        <el-form label-position="top">
            <el-form-item label="目标 Rule" required>
                <el-select
                    v-model="selectedRuleId"
                    placeholder="选择要合并到的 Rule"
                    class="w-full"
                    filterable
                >
                    <el-option
                        v-for="item in ruleOptionList"
                        :key="item.knowledgeId"
                        :label="`#${item.knowledgeId} ${item.title || '未命名'}`"
                        :value="item.knowledgeId"
                    >
                        <div class="flex items-center justify-between gap-2">
                            <span class="truncate">#{{ item.knowledgeId }} {{ item.title || '未命名' }}</span>
                            <span class="text-xs text-gray-400 shrink-0">
                                {{ Math.round((item.similarityScore || 0) * 100) }}%
                            </span>
                        </div>
                    </el-option>
                </el-select>
            </el-form-item>
            <el-form-item label="备注（可选）">
                <el-input
                    v-model="reviewComment"
                    type="textarea"
                    :rows="2"
                    placeholder="合并说明"
                />
            </el-form-item>
        </el-form>

        <template #footer>
            <el-button @click="emit('update:visible', false)">取消</el-button>
            <el-button
                type="primary"
                :loading="loading"
                :disabled="!selectedRuleId"
                @click="handleConfirm"
            >
                确认合并
            </el-button>
        </template>
    </el-dialog>
</template>

<script setup>
const props = defineProps({
    visible: {
        type: Boolean,
        default: false,
    },
    loading: {
        type: Boolean,
        default: false,
    },
    /** 相似 Rule 候选列表 */
    similarRuleList: {
        type: Array,
        default: () => [],
    },
})

const emit = defineEmits(['update:visible', 'confirm'])

const selectedRuleId = ref(null)
const reviewComment = ref('')

/** 下拉选项：优先展示相似 Rule */
const ruleOptionList = computed(() => props.similarRuleList || [])

watch(
    () => props.visible,
    (value) => {
        if (value) {
            const firstRule = ruleOptionList.value[0]
            selectedRuleId.value = firstRule?.knowledgeId || null
            reviewComment.value = ''
        }
    },
)

/** 确认合并 */
function handleConfirm() {
    if (!selectedRuleId.value) {
        return
    }
    emit('confirm', {
        targetKnowledgeId: selectedRuleId.value,
        rejectReason: 'REJECT_DUPLICATE_RULE',
        reviewComment: reviewComment.value,
    })
}
</script>
