<template>
    <div class="max-w-layout mx-auto px-6 py-8">
        <div class="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4 mb-6">
            <div>
                <h1 class="text-2xl font-semibold text-gray-900">Agent API Key</h1>
                <p class="text-gray-500 mt-1">
                    当前工作空间：{{ currentWorkspace.workspaceName || '—' }}
                    ；Agent（MCP）用 API Key 读写经验，环境变量 BIGAPP_API_KEY
                </p>
            </div>
            <el-button v-if="canManageKey" type="primary" @click="openCreate">
                签发密钥
            </el-button>
        </div>

        <section class="settings-panel">
            <el-alert
                v-if="!canManageKey"
                type="warning"
                :closable="false"
                show-icon
                title="当前角色无法签发或吊销密钥"
                description="仅拥有者与管理员可管理 Agent API Key。您可查看本空间密钥前缀与状态。"
                class="mb-4"
            />

            <el-table v-loading="loading" :data="list" stripe>
                <el-table-column label="名称" prop="keyName" min-width="140" />
                <el-table-column label="前缀" min-width="160">
                    <template #default="{ row }">
                        <code class="text-xs">{{ row.keyPrefix }}…</code>
                    </template>
                </el-table-column>
                <el-table-column label="权限" min-width="140">
                    <template #default="{ row }">
                        <el-tag v-if="row.permissionRecall" size="small" class="mr-1">Recall</el-tag>
                        <el-tag v-if="row.permissionRemember" size="small" type="success">Remember</el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="状态" min-width="90">
                    <template #default="{ row }">
                        <el-tag v-if="row.enabled" size="small" type="success">启用</el-tag>
                        <el-tag v-else size="small" type="info">已吊销</el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="创建时间" min-width="160">
                    <template #default="{ row }">{{ row.createTime || '—' }}</template>
                </el-table-column>
                <el-table-column v-if="canManageKey" label="操作" width="90" fixed="right">
                    <template #default="{ row }">
                        <el-button
                            v-if="row.enabled"
                            type="danger"
                            link
                            size="small"
                            @click="revoke(row)"
                        >
                            吊销
                        </el-button>
                    </template>
                </el-table-column>
                <template #empty>
                    <span class="text-gray-400">暂无 API Key</span>
                </template>
            </el-table>
        </section>

        <!-- 签发弹窗 -->
        <el-dialog v-model="createDialog" title="签发 Agent API Key" width="440px">
            <el-form label-width="92px">
                <el-form-item label="密钥名称">
                    <el-input v-model="form.keyName" placeholder="如 cursor-agent" maxlength="64" show-word-limit />
                </el-form-item>
                <el-form-item label="召回经验">
                    <el-switch v-model="form.permissionRecall" />
                    <span class="ml-2 text-xs text-gray-400">允许 Agent Recall</span>
                </el-form-item>
                <el-form-item label="提交草稿">
                    <el-switch v-model="form.permissionRemember" />
                    <span class="ml-2 text-xs text-gray-400">允许 Agent Remember</span>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="createDialog = false">取消</el-button>
                <el-button type="primary" :loading="creating" @click="submitCreate">签发</el-button>
            </template>
        </el-dialog>

        <!-- 明文展示弹窗（仅一次） -->
        <el-dialog v-model="plainKeyDialog" title="密钥已签发" width="520px" :close-on-click-modal="false">
            <el-alert
                type="warning"
                :closable="false"
                show-icon
                title="明文密钥仅显示一次，请立即复制保存！"
                description="关闭后将无法再次查看，丢失需重新签发。"
                class="mb-4"
            />
            <div class="flex items-center gap-2">
                <el-input v-model="newPlainKey" readonly>
                    <template #prepend>BIGAPP_API_KEY</template>
                </el-input>
                <el-button type="primary" @click="copyPlainKey">复制</el-button>
            </div>
            <template #footer>
                <el-button type="primary" @click="plainKeyDialog = false">我已保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import {
    createApiKeyRequest,
    fetchApiKeyListRequest,
    revokeApiKeyRequest,
} from '~/services/api-key.service'

definePageMeta({
    layout: 'app',
})

useHead({ title: 'Agent API Key' })

/** 页面浏览埋点 */
usePageTracker()

const { currentWorkspace, workspaceRevision } = useWorkspace()
const { canManageKey } = useWorkspacePermission()

const list = ref([])
const loading = ref(false)

const createDialog = ref(false)
const creating = ref(false)
const form = reactive({
    keyName: '',
    permissionRecall: true,
    permissionRemember: true,
})

const plainKeyDialog = ref(false)
const newPlainKey = ref('')

async function loadList() {
    loading.value = true
    try {
        const data = await fetchApiKeyListRequest()
        list.value = data?.keyList || []
    } catch (error) {
        ElMessage.error(error?.message || '密钥列表加载失败')
    } finally {
        loading.value = false
    }
}

function openCreate() {
    form.keyName = ''
    form.permissionRecall = true
    form.permissionRemember = true
    createDialog.value = true
}

async function submitCreate() {
    const keyName = form.keyName.trim()
    if (!keyName) {
        ElMessage.warning('请输入密钥名称')
        return
    }
    creating.value = true
    try {
        const data = await createApiKeyRequest({
            keyName,
            permissionRecall: form.permissionRecall,
            permissionRemember: form.permissionRemember,
        })
        newPlainKey.value = data.plainKey
        createDialog.value = false
        plainKeyDialog.value = true
        await loadList()
    } catch (error) {
        ElMessage.error(error?.message || '签发失败')
    } finally {
        creating.value = false
    }
}

async function revoke(item) {
    try {
        await ElMessageBox.confirm(
            `确定吊销「${item.keyName}」？吊销后使用该 Key 的 Agent 将无法访问本空间。`,
            '吊销确认',
            { type: 'warning', confirmButtonText: '吊销', cancelButtonText: '取消' },
        )
    } catch (_) {
        return
    }
    try {
        await revokeApiKeyRequest(item.id)
        ElMessage.success('已吊销')
        await loadList()
    } catch (error) {
        ElMessage.error(error?.message || '吊销失败')
    }
}

async function copyPlainKey() {
    try {
        await navigator.clipboard.writeText(newPlainKey.value)
        ElMessage.success('已复制到剪贴板')
    } catch (_) {
        ElMessage.warning('复制失败，请手动选择文本复制')
    }
}

onMounted(loadList)
/** 切换工作空间后刷新列表 */
watch(workspaceRevision, () => {
    loadList()
})
</script>

<style scoped>
.settings-panel {
    padding: 20px;
    border-radius: 12px;
    border: 1px solid #e5e7eb;
    background: #fff;
}
</style>
