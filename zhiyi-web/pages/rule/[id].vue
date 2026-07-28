<template>
    <KnowledgeDetailPageLayout
        :loading="loading"
        :detail="detail"
        :knowledge-id="knowledgeId"
        :fact-groups="factGroups"
        center-path="/memory?type=rule"
        center-label="记忆中心"
        theme-variant="rule"
        not-found-title="规范不存在"
        not-found-description="该规范可能已被删除，或您没有访问权限"
    >
        <template #actions>
            <template v-if="canModify">
                <el-button
                    v-if="detail.lifecycleStatus === KNOWLEDGE_LIFECYCLE.DRAFT"
                    size="small"
                    @click="router.push(`/rule/drafts/${detail.id}`)"
                >
                    <el-icon class="mr-1"><EditPen /></el-icon>
                    编辑草稿
                </el-button>
                <el-button
                    v-if="detail.lifecycleStatus === KNOWLEDGE_LIFECYCLE.DRAFT"
                    size="small"
                    type="primary"
                    :loading="publishing"
                    @click="handlePublish"
                >
                    发布
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
    </KnowledgeDetailPageLayout>
</template>

<script setup>
import { Delete, EditPen, Remove } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { KNOWLEDGE_LIFECYCLE } from '~/constants/knowledge'
import { deleteKnowledge, deprecateKnowledge, publishKnowledge } from '~/services/knowledge.service'

definePageMeta({
    layout: 'app',
    key: (route) => route.params.id,
})

/** 动态页面标题 */
const route = useRoute()
const router = useRouter()
const knowledgeId = computed(() => route.params.id)
const { loading, detail, factGroups, loadDetail } = useKnowledgeDetail(knowledgeId)
const { canModify, canDelete } = useKnowledgeActionPermission(detail)

useHead(computed(() => ({
    title: detail.value?.title ? `${detail.value.title} · 规则详情` : '规则详情',
})))

/** 页面浏览埋点 */
usePageTracker()

const publishing = ref(false)
const deprecating = ref(false)
const deleting = ref(false)

/** 加载当前路由对应的规则详情 */
async function loadCurrentDetail(options = {}) {
    try {
        await loadDetail(options)
    } catch (error) {
        ElMessage.error(error.message || '加载失败')
    }
}

/** 发布规范：写入向量索引，供 Agent Recall 召回 */
async function handlePublish() {
    if (!canModify.value) {
        return
    }
    try {
        await ElMessageBox.confirm('确定发布该规范吗？发布后 Agent Recall 可注入相关条文。', '发布规范', {
            type: 'info',
            confirmButtonText: '确认发布',
            cancelButtonText: '取消',
        })
    } catch {
        return
    }

    publishing.value = true
    try {
        await publishKnowledge(knowledgeId.value)
        ElMessage.success('规范已发布')
        await loadCurrentDetail({ silent: true })
    } catch (error) {
        ElMessage.error(error.message || '发布失败')
    } finally {
        publishing.value = false
    }
}

/** 下架规范：Recall 不再返回 */
async function handleDeprecate() {
    if (!canModify.value) {
        return
    }
    try {
        await ElMessageBox.confirm(
            '下架后该规范将从规则中心列表移除，Agent 也无法再召回。此操作不可撤销为「已发布」状态。',
            '确认下架',
            { type: 'warning', confirmButtonText: '确认下架', cancelButtonText: '取消' },
        )
    } catch {
        return
    }

    deprecating.value = true
    try {
        await deprecateKnowledge(knowledgeId.value)
        ElMessage.success('规范已下架')
        await loadCurrentDetail({ silent: true })
    } catch (error) {
        ElMessage.error(error.message || '下架失败')
    } finally {
        deprecating.value = false
    }
}

/** 删除规范 */
async function handleDelete() {
    if (!canDelete.value) {
        ElMessage.warning('当前角色无权限删除该规范')
        return
    }
    try {
        await ElMessageBox.confirm(
            '删除后规范将不可恢复。确定继续吗？',
            '确认删除',
            { type: 'error', confirmButtonText: '确认删除', cancelButtonText: '取消' },
        )
    } catch {
        return
    }

    deleting.value = true
    try {
        await deleteKnowledge(knowledgeId.value)
        ElMessage.success('规范已删除')
        await router.replace('/memory?type=rule')
    } catch (error) {
        ElMessage.error(error.message || '删除失败')
    } finally {
        deleting.value = false
    }
}

onMounted(() => loadCurrentDetail())

/** 同页组件复用时重新拉取详情 */
watch(knowledgeId, (nextId, previousId) => {
    if (previousId !== undefined && nextId !== previousId) {
        loadCurrentDetail()
    }
})

/** 切换工作空间后静默刷新；若当前规范不属于新空间则回到列表 */
useWorkspaceChange(async () => {
    try {
        await loadDetail({ silent: true })
    } catch (error) {
        await router.replace('/memory?type=rule')
    }
})
</script>
