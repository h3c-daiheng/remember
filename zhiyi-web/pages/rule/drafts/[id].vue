<template>
    <div class="max-w-layout mx-auto px-6 py-8">
        <el-skeleton v-if="loading" :rows="10" animated />

        <KnowledgeDraftEditorShell
            v-else-if="editForm"
            :edit-form="editForm"
            :back-path="`/memory?type=${editForm.knowledgeType || 'rule'}`"
            back-label="返回记忆中心"
            :type-label="KNOWLEDGE_TYPE_LABELS[editForm.knowledgeType] || editForm.knowledgeType"
            :title-placeholder="editForm.knowledgeType === KNOWLEDGE_TYPES.WORKFLOW ? '输入流程标题' : '输入规范标题'"
            meta-hint="发布后 Agent Recall 可优先召回团队规范"
            :knowledge-id="knowledgeId"
            :saving="saving"
            :publishing="publishing"
            :show-modify="canModify"
            @save="handleSave"
            @publish="handlePublish"
        >
            <KnowledgeDraftFactSection
                :facts="editForm.facts"
                :knowledge-type="editForm.knowledgeType"
                section-title="规范内容"
                :section-desc="factSectionDesc"
            />
        </KnowledgeDraftEditorShell>

        <el-empty v-else description="草稿不存在" />
    </div>
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { KNOWLEDGE_TYPE_LABELS, KNOWLEDGE_TYPES } from '~/constants/knowledge'
import { publishKnowledge, supersedeKnowledge, updateKnowledge } from '~/services/knowledge.service'
import {
    buildKnowledgeSaveRequest,
    cloneKnowledgeForEdit,
    resolveKnowledgeDetailPath,
    validateKnowledgeDraftForPublish,
} from '~/utils/knowledge'

definePageMeta({
    layout: 'app',
    key: (route) => route.params.id,
})

useHead({ title: '编辑 Rule 草稿' })

/** 页面浏览埋点 */
usePageTracker()

const route = useRoute()
const router = useRouter()
const knowledgeId = computed(() => route.params.id)
const { loading, detail, loadDetail } = useKnowledgeDetail(knowledgeId)
const { canModify } = useKnowledgeActionPermission(detail)

const editForm = ref(null)
const saving = ref(false)
const publishing = ref(false)

const factSectionDesc = computed(() =>
    editForm.value?.knowledgeType === KNOWLEDGE_TYPES.WORKFLOW
        ? '按顺序填写 action 步骤，constraint 标注前置条件'
        : '填写 rule 条文与 constraint 适用条件',
)

watch(detail, (value) => {
    editForm.value = cloneKnowledgeForEdit(value)
}, { immediate: true })

/** 保存 Rule / Workflow 草稿 */
async function handleSave() {
    if (!canModify.value || !editForm.value) {
        ElMessage.warning('当前角色无权限编辑该草稿')
        return
    }
    saving.value = true
    try {
        await updateKnowledge(knowledgeId.value, buildKnowledgeSaveRequest(editForm.value))
        ElMessage.success('草稿已保存')
        await loadDetail({ silent: true })
    } catch (error) {
        ElMessage.error(error.message || '保存失败')
    } finally {
        saving.value = false
    }
}

/** 发布 Rule / Workflow 草稿 */
async function handlePublish() {
    if (!canModify.value) {
        ElMessage.warning('当前角色无权限发布该草稿')
        return
    }
    const validationMessage = validateKnowledgeDraftForPublish(editForm.value)
    if (validationMessage) {
        ElMessage.warning(validationMessage)
        return
    }
    try {
        await ElMessageBox.confirm(
            '发布后规范将写入向量索引，Agent Recall 可优先召回。确定发布吗？',
            '发布规范',
            { type: 'info' },
        )
    } catch {
        return
    }
    publishing.value = true
    try {
        await updateKnowledge(knowledgeId.value, buildKnowledgeSaveRequest(editForm.value))
        await publishKnowledge(knowledgeId.value)
        ElMessage.success('已发布')
        // 修订重发场景:若存在 predecessor,引导用户选择是否替代原规则
        const predecessorId = sessionStorage.getItem(`ruleRevisePredecessor:${knowledgeId.value}`)
        if (predecessorId) {
            sessionStorage.removeItem(`ruleRevisePredecessor:${knowledgeId.value}`)
            try {
                await ElMessageBox.confirm('是否用此修订版本替代原规则?原规则将下架。', '替代原版本', {
                    type: 'warning', confirmButtonText: '替代原版本', cancelButtonText: '暂不替代',
                })
                await supersedeKnowledge(knowledgeId.value, { predecessorId: Number(predecessorId), comment: '规则修订重发' })
                ElMessage.success('已替代原规则')
            } catch (e) {
                // 用户选择「暂不替代」或取消,不报错;草稿已发布,可后续手动 supersede
            }
        }
        router.push(resolveKnowledgeDetailPath(editForm.value.knowledgeType, knowledgeId.value))
    } catch (error) {
        ElMessage.error(error.message || '发布失败')
    } finally {
        publishing.value = false
    }
}

onMounted(async () => {
    try {
        await loadDetail()
    } catch (error) {
        ElMessage.error(error.message || '加载失败')
    }
})
</script>
