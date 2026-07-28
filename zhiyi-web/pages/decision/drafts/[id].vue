<template>
    <div class="max-w-layout mx-auto px-6 py-8">
        <el-skeleton v-if="loading" :rows="10" animated />

        <KnowledgeDraftEditorShell
            v-else-if="editForm"
            :edit-form="editForm"
            back-path="/memory?type=decision"
            back-label="返回记忆中心"
            :type-label="KNOWLEDGE_TYPE_LABELS[editForm.knowledgeType] || editForm.knowledgeType"
            title-placeholder="输入决策标题"
            meta-hint="发布后 Agent Recall 可注入架构取舍依据"
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
                section-title="决策内容"
                section-desc="建议包含 decision（方案取舍）、evidence（依据）、constraint（适用条件）"
            />
        </KnowledgeDraftEditorShell>

        <el-empty v-else description="草稿不存在" />
    </div>
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { KNOWLEDGE_TYPE_LABELS } from '~/constants/knowledge'
import { publishKnowledge, updateKnowledge } from '~/services/knowledge.service'
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

useHead({ title: '编辑 Decision 草稿' })

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

watch(detail, (value) => {
    editForm.value = cloneKnowledgeForEdit(value)
}, { immediate: true })

/** 保存 Decision 草稿 */
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

/** 发布 Decision 草稿 */
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
            '发布后决策将写入向量索引，Agent Recall 可注入架构取舍依据。确定发布吗？',
            '发布决策',
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
