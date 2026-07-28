<template>
    <KnowledgeDetailPageLayout
        :loading="loading"
        :detail="detail"
        :knowledge-id="knowledgeId"
        :fact-groups="factGroups"
        center-path="/memory?type=experience"
        center-label="记忆中心"
        theme-variant="experience"
        not-found-title="经验不存在"
        not-found-description="该经验可能已被删除，或您没有访问权限"
    >
        <template #actions>
            <template v-if="canModify">
                <el-button
                    v-if="detail.lifecycleStatus !== KNOWLEDGE_LIFECYCLE.DEPRECATED"
                    size="small"
                    @click="openEditDrawer"
                >
                    <el-icon class="mr-1"><EditPen /></el-icon>
                    编辑
                </el-button>
                <el-button
                    v-if="detail.lifecycleStatus === KNOWLEDGE_LIFECYCLE.PUBLISHED"
                    size="small"
                    :loading="deprecating"
                    @click="handleDeprecate"
                >
                    <el-icon class="mr-1"><Remove /></el-icon>
                    下架
                </el-button>
                <el-button
                    v-if="detail.lifecycleStatus === KNOWLEDGE_LIFECYCLE.DEPRECATED"
                    size="small"
                    type="primary"
                    :loading="reactivating"
                    @click="handleReactivate"
                >
                    <el-icon class="mr-1"><RefreshRight /></el-icon>
                    重新启用
                </el-button>
            </template>
            <el-button
                v-if="canDelete"
                size="small"
                type="danger"
                plain
                :loading="deleting"
                @click="handleDelete"
            >
                <el-icon class="mr-1"><Delete /></el-icon>
                删除
            </el-button>
        </template>

        <template #append>
            <KnowledgeExperienceEditDrawer
                :visible="editDrawerVisible"
                :edit-form="editForm"
                :saving="saving"
                @update:visible="editDrawerVisible = $event"
                @save="handleSaveEdit"
            />
        </template>
    </KnowledgeDetailPageLayout>
</template>

<script setup>
import { Delete, EditPen, RefreshRight, Remove } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { KNOWLEDGE_LIFECYCLE } from '~/constants/knowledge'
import {
    deleteKnowledge,
    deprecateKnowledge,
    reactivateKnowledge,
    updateKnowledge,
} from '~/services/knowledge.service'
import { buildKnowledgeSaveRequest, cloneKnowledgeForEdit } from '~/utils/knowledge'

definePageMeta({
    layout: 'app',
    /** 切换不同经验时强制重建页面，避免详情缓存错乱 */
    key: (route) => route.params.id,
})

const route = useRoute()
const router = useRouter()
const knowledgeId = computed(() => route.params.id)
const { loading, detail, factGroups, loadDetail } = useKnowledgeDetail(knowledgeId)
const { canModify, canDelete } = useKnowledgeActionPermission(detail)

/** 动态页面标题 */
useHead(computed(() => ({
    title: detail.value?.title ? `${detail.value.title} · 经验详情` : '经验详情',
})))

/** 页面浏览埋点 */
usePageTracker()

const editDrawerVisible = ref(false)
const editForm = ref(null)
const saving = ref(false)
const deprecating = ref(false)
const reactivating = ref(false)
const deleting = ref(false)

/** 加载当前路由对应的经验详情 */
async function loadCurrentDetail(options = {}) {
    try {
        await loadDetail(options)
    } catch (error) {
        ElMessage.error(error.message || '加载失败')
    }
}

/** 打开编辑抽屉，深拷贝避免污染详情展示 */
function openEditDrawer() {
    if (!canModify.value) {
        ElMessage.warning('当前角色无权限编辑该经验')
        return
    }
    editForm.value = cloneKnowledgeForEdit(detail.value)
    editDrawerVisible.value = true
}

/** 保存编辑内容 */
async function handleSaveEdit(formData) {
    if (!canModify.value) {
        return
    }
    saving.value = true
    try {
        await updateKnowledge(knowledgeId.value, buildKnowledgeSaveRequest(formData))
        ElMessage.success('经验已更新')
        editDrawerVisible.value = false
        await loadCurrentDetail({ silent: true })
    } catch (error) {
        ElMessage.error(error.message || '保存失败')
    } finally {
        saving.value = false
    }
}

/** 下架经验：标记为已失效，Recall 不再返回 */
async function handleDeprecate() {
    if (!canModify.value) {
        return
    }
    try {
        await ElMessageBox.confirm(
            '下架后该经验将移入「已失效」列表，Agent 也无法再召回。您可在已失效 Tab 中重新启用。',
            '确认下架',
            { type: 'warning', confirmButtonText: '确认下架', cancelButtonText: '取消' },
        )
    } catch {
        return
    }

    deprecating.value = true
    try {
        await deprecateKnowledge(knowledgeId.value)
        ElMessage.success('经验已下架')
        await router.replace('/memory?type=experience&tab=deprecated')
    } catch (error) {
        ElMessage.error(error.message || '下架失败')
    } finally {
        deprecating.value = false
    }
}

/** 重新启用已失效经验：恢复为已发布并重建 Recall 索引 */
async function handleReactivate() {
    try {
        await ElMessageBox.confirm(
            '重新启用后该经验将恢复为已发布，Agent 可再次 Recall 到本条知识。',
            '确认重新启用',
            { type: 'info', confirmButtonText: '确认启用', cancelButtonText: '取消' },
        )
    } catch {
        return
    }

    reactivating.value = true
    try {
        await reactivateKnowledge(knowledgeId.value)
        ElMessage.success('经验已重新启用')
        await loadCurrentDetail({ silent: true })
    } catch (error) {
        ElMessage.error(error.message || '重新启用失败')
    } finally {
        reactivating.value = false
    }
}

/** 删除经验（逻辑删除） */
async function handleDelete() {
    if (!canDelete.value) {
        ElMessage.warning('当前角色无权限删除该经验')
        return
    }
    try {
        await ElMessageBox.confirm(
            '删除后经验将不可恢复，Recall 索引也会一并清除。确定继续吗？',
            '确认删除',
            { type: 'error', confirmButtonText: '确认删除', cancelButtonText: '取消' },
        )
    } catch {
        return
    }

    deleting.value = true
    try {
        await deleteKnowledge(knowledgeId.value)
        ElMessage.success('经验已删除')
        await router.replace('/memory?type=experience')
    } catch (error) {
        ElMessage.error(error.message || '删除失败')
    } finally {
        deleting.value = false
    }
}

onMounted(() => loadCurrentDetail())

watch(knowledgeId, (nextId, previousId) => {
    if (previousId !== undefined && nextId !== previousId) {
        loadCurrentDetail()
    }
})

useWorkspaceChange(async () => {
    try {
        await loadDetail({ silent: true })
    } catch (error) {
        await router.replace('/memory?type=experience')
    }
})
</script>
