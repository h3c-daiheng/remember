<template>
    <div class="max-w-layout mx-auto px-6 py-8">
        <el-skeleton v-if="loading" :rows="10" animated />

        <KnowledgeDraftEditorShell
            v-else-if="editForm"
            :edit-form="editForm"
            back-path="/memory?type=experience"
            back-label="返回记忆中心"
            type-label="经验"
            title-placeholder="输入经验标题"
            meta-hint="发布后 Agent Recall 可按上下文召回"
            :knowledge-id="knowledgeId"
            :saving="saving"
            :publishing="publishing"
            :show-modify="canModify"
            :show-delete="canDelete"
            :deleting="deleting"
            @save="handleSave"
            @publish="handlePublish"
            @delete="handleDelete"
        >
            <KnowledgeDraftFactSection
                :facts="editForm.facts"
                knowledge-type="experience"
                section-title="经验内容"
                section-desc="人工经验卡：建议填写 observation、decision、action、outcome"
            />
        </KnowledgeDraftEditorShell>

        <el-empty v-else description="草稿不存在" />
    </div>
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteKnowledge, publishKnowledge, updateKnowledge } from '~/services/knowledge.service'
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

useHead({ title: '编辑经验草稿' })

/** 页面浏览埋点 */
usePageTracker()

const route = useRoute()
const router = useRouter()
const knowledgeId = computed(() => route.params.id)
const { loading, detail, loadDetail } = useKnowledgeDetail(knowledgeId)
const { canModify, canDelete } = useKnowledgeActionPermission(detail)

const editForm = ref(null)
const saving = ref(false)
const publishing = ref(false)
const deleting = ref(false)

watch(detail, (value) => {
    editForm.value = cloneKnowledgeForEdit(value)
}, { immediate: true })

/** 保存人工经验草稿 */
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

/** 发布人工经验草稿 */
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
            '发布后经验将写入向量索引，Agent Recall 可召回。确定发布吗？',
            '发布经验',
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

/** 删除经验草稿（逻辑删除） */
async function handleDelete() {
    if (!canDelete.value) {
        ElMessage.warning('当前角色无权限删除该草稿')
        return
    }
    try {
        await ElMessageBox.confirm(
            '删除后草稿将不可恢复。确定继续吗？',
            '确认删除',
            { type: 'error', confirmButtonText: '确认删除', cancelButtonText: '取消' },
        )
    } catch {
        return
    }

    deleting.value = true
    try {
        await deleteKnowledge(knowledgeId.value)
        ElMessage.success('草稿已删除')
        await router.replace('/memory?type=experience')
    } catch (error) {
        ElMessage.error(error.message || '删除失败')
    } finally {
        deleting.value = false
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
