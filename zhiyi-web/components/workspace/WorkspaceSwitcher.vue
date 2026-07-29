<template>
    <el-dropdown trigger="click"
        @command="handleSwitch"
        @visible-change="handleDropdownVisibleChange">
        <!-- 桌面与小屏均展示；小屏收窄最大宽度，避免顶栏拥挤 -->
        <button type="button"
            class="inline-flex items-center gap-1 px-2 py-1 rounded-md bg-gray-100 text-xs text-gray-600 hover:bg-gray-200 transition-colors max-w-[7.5rem] sm:max-w-[10rem] lg:max-w-[12.5rem] lg:gap-1.5 lg:px-2.5">
            <span class="truncate">{{ currentWorkspace.workspaceName }}</span>
            <el-icon :size="12"
                class="shrink-0">
                <ArrowDown />
            </el-icon>
        </button>
        <template #dropdown>
            <el-dropdown-menu>
                <el-dropdown-item v-for="item in workspaceList"
                    :key="item.workspaceId"
                    :command="item.workspaceId"
                    :disabled="item.current || switching"
                    :title="item.current ? '当前正在使用的工作空间' : undefined">
                    <!-- 对勾占位固定宽度，保证所有空间名称首字对齐 -->
                    <div class="workspace-switcher-item">
                        <span class="workspace-switcher-item__check"
                            aria-hidden="true">
                            <el-icon v-if="item.current"
                                :size="14"
                                class="workspace-switcher-item__check-icon">
                                <Check />
                            </el-icon>
                        </span>
                        <span :class="item.current ? 'workspace-switcher-item__name--current' : 'font-medium'">
                            {{ item.workspaceName }}
                        </span>
                    </div>
                </el-dropdown-item>
                <el-dropdown-item v-if="!workspaceList.length && listLoadError"
                    disabled>
                    加载失败，请重试
                </el-dropdown-item>
                <el-dropdown-item divided
                    command="create">
                    新建工作空间
                </el-dropdown-item>
                <!-- 设置：跳本地 /settings 页（API Key 管理等） -->
                <el-dropdown-item command="settings">
                    工作空间设置
                </el-dropdown-item>
                <el-dropdown-item divided command="delete" :disabled="!canDeleteWorkspace">
                    删除当前工作空间
                </el-dropdown-item>
            </el-dropdown-menu>
        </template>
    </el-dropdown>

    <el-dialog v-model="createDialog" title="新建工作空间" width="440px">
        <el-form label-width="80px">
            <el-form-item label="空间名称">
                <el-input v-model="createForm.workspaceName" placeholder="如 产品研发组" maxlength="64" show-word-limit />
            </el-form-item>
            <el-form-item label="空间标识">
                <el-input v-model="createForm.workspaceCode" placeholder="留空自动生成" maxlength="64" />
                <div class="text-xs text-gray-400 mt-1">用于 API 上下文标识，留空自动生成</div>
            </el-form-item>
        </el-form>
        <template #footer>
            <el-button @click="createDialog = false">取消</el-button>
            <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
        </template>
    </el-dialog>
</template>

<script setup>
import { ArrowDown, Check } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { openGonlineWorkspaceSettings } from '~/utils/gonlineAuthLogin'

const { currentWorkspace, workspaceList, loadWorkspaceList, switchWorkspace, createWorkspace, deleteWorkspace } = useWorkspace()
const { canDeleteWorkspace } = useWorkspacePermission()
const deleting = ref(false)

const switching = ref(false)
const workspaceListLoaded = ref(false)
const listLoadError = ref(false)

/** 下拉展开时再拉工作空间列表，避免首屏与业务页争抢主线程 */
async function handleDropdownVisibleChange(visible) {
    if (!visible || workspaceListLoaded.value) {
        return
    }
    try {
        await loadWorkspaceList()
        workspaceListLoaded.value = true
        listLoadError.value = false
    } catch (error) {
        listLoadError.value = true
        ElMessage.error(error?.message || '工作空间列表加载失败')
    }
}

/**
 * 处理工作空间切换；设置入口跳转主站管理页（新窗口）
 * 智忆侧不允许创建空间，创建请在主站完成
 */
async function handleSwitch(command) {
    if (command === 'settings') {
        openGonlineWorkspaceSettings()
        return
    }
    if (command === 'create') {
        openCreateDialog()
        return
    }
    if (command === 'delete') {
        openDeleteConfirm()
        return
    }
    if (switching.value) {
        return
    }
    switching.value = true
    try {
        await switchWorkspace(command)
        ElMessage.success('已切换工作空间')
    } catch (error) {
        ElMessage.error(error.message || '切换失败')
    } finally {
        switching.value = false
    }
}

const createDialog = ref(false)
const creating = ref(false)
const createForm = reactive({ workspaceName: '', workspaceCode: '' })

function openCreateDialog() {
    createForm.workspaceName = ''
    createForm.workspaceCode = ''
    createDialog.value = true
}

async function submitCreate() {
    const name = createForm.workspaceName.trim()
    if (!name) {
        ElMessage.warning('请输入工作空间名称')
        return
    }
    creating.value = true
    try {
        await createWorkspace(name, createForm.workspaceCode.trim() || undefined)
        ElMessage.success('已创建并切换到新工作空间')
        createDialog.value = false
    } catch (error) {
        ElMessage.error(error.message || '创建失败')
    } finally {
        creating.value = false
    }
}

async function openDeleteConfirm() {
    const wsName = currentWorkspace.value?.workspaceName || ''
    const wsId = currentWorkspace.value?.workspaceId
    if (!wsId) {
        return
    }
    try {
        const { value } = await ElMessageBox.prompt(
            `此操作不可恢复,将永久删除工作空间「${wsName}」及其下全部知识、规则、草稿、API Key。\n请输入工作空间名称「${wsName}」以确认:`,
            '删除工作空间',
            {
                confirmButtonText: '确认删除',
                cancelButtonText: '取消',
                type: 'error',
                inputPlaceholder: wsName,
                inputValidator: (v) => !!v && v.trim() !== '' || '请输入工作空间名称',
            },
        )
        if (value.trim() !== wsName) {
            ElMessage.error('输入的名称与工作空间名称不符,已取消')
            return
        }
        deleting.value = true
        await deleteWorkspace(wsId, value.trim())
        ElMessage.success('工作空间已删除')
    } catch (e) {
        if (e !== 'cancel' && e?.message !== 'cancel') {
            ElMessage.error(e?.message || '删除失败')
        }
    } finally {
        deleting.value = false
    }
}
</script>

<style scoped>
.workspace-switcher-item {
    display: flex;
    align-items: center;
    gap: 0.35rem;
    min-width: 10rem;
}

/* 固定占位，当前项显示对勾，非当前项留空，名称首字对齐 */
.workspace-switcher-item__check {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 1rem;
    flex-shrink: 0;
}

.workspace-switcher-item__check-icon {
    color: var(--el-color-primary);
}

.workspace-switcher-item__name--current {
    font-weight: 500;
    color: var(--el-color-primary);
}

/* disabled 项内对勾与名称仍保持可读 */
:deep(.el-dropdown-menu__item.is-disabled) .workspace-switcher-item__check-icon,
:deep(.el-dropdown-menu__item.is-disabled) .workspace-switcher-item__name--current {
    opacity: 1;
}
</style>
