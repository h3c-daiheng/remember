<template>
    <!-- Capture 拒绝弹窗：选择拒绝码与备注 -->
    <el-dialog
        :model-value="visible"
        title="拒绝草稿"
        width="480px"
        destroy-on-close
        @update:model-value="emit('update:visible', $event)"
    >
        <el-form label-position="top">
            <el-form-item label="拒绝理由" required>
                <el-select
                    v-model="form.rejectReason"
                    placeholder="请选择拒绝理由"
                    class="w-full"
                >
                    <el-option
                        v-for="reason in CAPTURE_REJECT_REASONS"
                        :key="reason.code"
                        :label="reason.label"
                        :value="reason.code"
                    />
                </el-select>
            </el-form-item>
            <el-form-item label="备注（可选）">
                <el-input
                    v-model="form.reviewComment"
                    type="textarea"
                    :rows="3"
                    placeholder="补充说明，便于后续追溯"
                />
            </el-form-item>
            <el-alert
                v-if="form.rejectReason === 'REJECT_DOC_GAP'"
                type="info"
                :closable="false"
                show-icon
                class="mb-2"
            >
                该草稿更宜写入规范层，建议通过「发布为 ▼」选择「规则」而非直接拒绝
            </el-alert>
        </el-form>
        <template #footer>
            <el-button @click="emit('update:visible', false)">取消</el-button>
            <el-button
                type="danger"
                :loading="loading"
                :disabled="!form.rejectReason"
                @click="handleConfirm"
            >
                确认拒绝
            </el-button>
        </template>
    </el-dialog>
</template>

<script setup>
import { CAPTURE_REJECT_REASONS } from '~/constants/knowledge'

const props = defineProps({
    visible: {
        type: Boolean,
        default: false,
    },
    loading: {
        type: Boolean,
        default: false,
    },
})

const emit = defineEmits(['update:visible', 'confirm'])

const form = reactive({
    rejectReason: '',
    reviewComment: '',
})

watch(
    () => props.visible,
    (value) => {
        if (value) {
            form.rejectReason = ''
            form.reviewComment = ''
        }
    },
)

/** 确认拒绝并回传拒绝码与备注 */
function handleConfirm() {
    emit('confirm', {
        rejectReason: form.rejectReason,
        reviewComment: form.reviewComment,
    })
}
</script>
