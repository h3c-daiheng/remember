<template>
    <div class="quick-start-guide">
        <!-- 步骤列表：说明展开在对应步骤下方 -->
        <ol class="quick-start__steps space-y-2">
            <li
                v-for="(step, index) in QUICK_START_STEPS"
                :key="step.id"
                class="quick-start__step rounded-lg border transition overflow-hidden"
                :class="stepCardClass(step.id)"
            >
                <button
                    type="button"
                    class="quick-start__step-row w-full flex items-center gap-3 p-3 text-left"
                    @click="toggleStep(step.id)"
                >
                    <span
                        class="quick-start__step-index shrink-0"
                        :class="stepIndexClass(step.id)"
                    >
                        <el-icon v-if="isStepDone(step.id)" :size="14"><Check /></el-icon>
                        <span v-else>{{ index + 1 }}</span>
                    </span>
                    <span class="min-w-0 flex-1">
                        <span class="flex items-center gap-2 min-w-0">
                            <span class="text-sm font-medium truncate text-gray-900">
                                {{ step.title }}
                            </span>
                            <el-tag
                                v-if="!isStepDone(step.id) && currentStep?.id === step.id && activeStepId !== step.id"
                                size="small"
                                type="primary"
                                effect="plain"
                            >
                                推荐
                            </el-tag>
                        </span>
                        <span class="block text-xs mt-0.5 text-gray-500">
                            {{ step.summary }}
                        </span>
                    </span>
                    <el-icon
                        class="text-gray-300 shrink-0 transition-transform"
                        :class="{ 'text-primary rotate-90': activeStepId === step.id }"
                    >
                        <ArrowRight />
                    </el-icon>
                </button>

                <!-- 当前步骤的详细说明 -->
                <div
                    v-if="activeStepId === step.id"
                    class="quick-start__detail border-t border-gray-100 px-5 pb-5 pt-4"
                >
                    <p
                        v-if="step.id === 'mcp'"
                        class="text-sm text-gray-600 leading-relaxed"
                    >
                        {{ step.detail }}
                    </p>

                    <!-- MCP：一步一卡，说明与操作合并 -->
                    <ol v-if="step.id === 'mcp'" class="quick-start__substeps mt-3">
                        <li
                            v-for="(subStep, subIndex) in step.subSteps"
                            :key="subStep.id"
                            class="quick-start__substep"
                        >
                            <div
                                class="quick-start__substep-header"
                                :class="{ 'quick-start__substep-header--only': subStep.id === 'issue-key' }"
                            >
                                <span class="quick-start__substep-num">{{ subIndex + 1 }}</span>
                                <div class="min-w-0 flex-1">
                                    <span class="quick-start__substep-title">{{ subStep.title }}</span>
                                    <p class="quick-start__substep-desc">
                                        <template v-if="subStep.detailLink">
                                            {{ subStep.detailLink.before }}
                                            <button
                                                type="button"
                                                class="quick-start__inline-link"
                                                @click.stop="goTo(subStep.detailLink.path)"
                                            >
                                                {{ subStep.detailLink.label }}
                                            </button>
                                            {{ subStep.detailLink.after }}
                                            <el-tag
                                                v-if="subStep.id === 'issue-key' && hasApiKey"
                                                size="small"
                                                type="success"
                                                effect="plain"
                                                class="ml-1"
                                            >
                                                已签发
                                            </el-tag>
                                        </template>
                                        <template v-else-if="subStep.id === 'verify-connection'">
                                            {{ subStep.detail }}
                                            <button
                                                type="button"
                                                class="quick-start__inline-link"
                                                @click.stop="copyMcpTestPrompt"
                                            >
                                                复制测试提示词
                                            </button>
                                            发给 Agent 验证。
                                        </template>
                                        <template v-else>{{ subStep.detail }}</template>
                                    </p>
                                </div>
                            </div>

                            <!-- 步骤 2：配置 MCP（模式切换与复制融入说明文字） -->
                            <div
                                v-if="subStep.id === 'configure-mcp'"
                                class="quick-start__substep-panel"
                            >
                                <p class="text-xs text-gray-500 mb-2 leading-relaxed">
                                    已有 MCP 选
                                    <button
                                        type="button"
                                        class="quick-start__inline-link"
                                        :class="{ 'quick-start__inline-link--active': mcpConfigMode === 'append' }"
                                        @click="mcpConfigMode = 'append'"
                                    >
                                        追加片段
                                    </button>
                                    ，首次用
                                    <button
                                        type="button"
                                        class="quick-start__inline-link"
                                        :class="{ 'quick-start__inline-link--active': mcpConfigMode === 'full' }"
                                        @click="mcpConfigMode = 'full'"
                                    >
                                        完整配置
                                    </button>
                                    ，
                                    <button
                                        type="button"
                                        class="quick-start__inline-link"
                                        @click="copyMcpConfig"
                                    >
                                        {{ mcpConfigMode === 'append' ? '复制片段' : '复制配置' }}
                                    </button>
                                    后粘贴到
                                    <template v-if="mcpConfigMode === 'append'">
                                        <code class="text-gray-600">mcpServers</code>（前一项 <code class="text-gray-600">}</code> 后加逗号）。
                                    </template>
                                    <template v-else>
                                        <code class="text-gray-600">.cursor/mcp.json</code> 或 Cursor Settings → MCP。
                                    </template>
                                </p>
                                <pre class="quick-start__code text-xs text-gray-700 overflow-x-auto">{{ activeMcpConfigText }}</pre>
                                <p
                                    v-if="mcpConfigMode === 'append'"
                                    class="text-xs text-gray-400 mt-2 leading-relaxed"
                                >
                                    合并示例：{ "mcpServers": { "other-mcp": { ... }, ← 此处加逗号后粘贴上方片段 } }
                                </p>
                            </div>

                            <!-- 步骤 3：验证连通（复制操作已在说明文字中） -->
                            <div
                                v-else-if="subStep.id === 'verify-connection'"
                                class="quick-start__substep-panel"
                            >
                                <p class="text-sm text-gray-700 leading-relaxed whitespace-pre-line">{{ mcpTestPromptText }}</p>
                            </div>
                        </li>
                    </ol>

                    <!-- 沉淀经验 -->
                    <template v-else-if="step.id === 'remember'">
                        <p class="text-sm text-gray-600 leading-relaxed mt-1">{{ step.detail }}</p>
                        <div class="quick-start__substep mt-3">
                            <div class="quick-start__substep-panel">
                                <p class="text-xs text-gray-500 mb-2">
                                    <button
                                        type="button"
                                        class="quick-start__inline-link"
                                        @click="copyRememberPrompt"
                                    >
                                        复制提示词
                                    </button>
                                </p>
                                <p class="text-sm text-gray-700 leading-relaxed whitespace-pre-line">{{ rememberPromptText }}</p>
                            </div>
                        </div>
                        <p class="text-xs text-gray-500 mt-2">
                            提交后可在
                            <button
                                type="button"
                                class="quick-start__inline-link"
                                @click="goTo(step.actionPath)"
                            >
                                草稿列表
                            </button>
                            查看。
                        </p>
                    </template>

                    <!-- 审核发布 -->
                    <template v-else-if="step.id === 'review'">
                        <p class="text-sm text-gray-600 leading-relaxed mt-1">
                            打开
                            <button
                                type="button"
                                class="quick-start__inline-link"
                                @click="goTo(step.actionPath)"
                            >
                                草稿确认
                            </button>
                            ，{{ step.detail }}
                        </p>
                    </template>

                    <!-- 召回 -->
                    <template v-else-if="step.id === 'recall'">
                        <p class="text-sm text-gray-600 leading-relaxed mt-1">{{ step.detail }}</p>
                        <div class="quick-start__substep mt-3">
                            <div class="quick-start__substep-panel">
                                <p class="text-xs text-gray-500 mb-2">
                                    <button
                                        type="button"
                                        class="quick-start__inline-link"
                                        @click="copyRecallPrompt"
                                    >
                                        复制提示词
                                    </button>
                                    （记得改任务描述）
                                </p>
                                <p class="text-sm text-gray-700 leading-relaxed whitespace-pre-line">{{ recallPromptText }}</p>
                            </div>
                        </div>
                        <p class="text-xs text-gray-500 mt-2">
                            也可先
                            <button
                                type="button"
                                class="quick-start__inline-link"
                                @click="goTo(step.actionPath)"
                            >
                                查看已发布经验
                            </button>
                            了解团队沉淀。
                        </p>
                    </template>

                    <p v-if="step.hint" class="text-xs text-gray-400 mt-3">{{ step.hint }}</p>
                </div>
            </li>
        </ol>

        <!-- 底部状态栏 -->
        <div class="flex flex-wrap items-center justify-between gap-3 border-t border-gray-100 pt-4 mt-6">
            <div class="flex flex-wrap items-center gap-2 text-xs text-gray-400">
                <span>工作空间 <span class="text-gray-600">{{ workspaceCode }}</span></span>
                <span>·</span>
                <span>
                    Agent 密钥
                    <el-tag
                        size="small"
                        :type="hasApiKey ? 'success' : 'warning'"
                        effect="plain"
                        class="ml-1"
                    >
                        {{ hasApiKey ? '已签发' : '未签发' }}
                    </el-tag>
                </span>
                <span v-if="publishedCount > 0">· 已发布 {{ publishedCount }} 条经验</span>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ArrowRight, Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { copyTextToClipboard } from '~/utils/clipboard'
import { openGonlineWorkspaceSettings } from '~/utils/gonlineAuthLogin'

const props = defineProps({
    publishedCount: {
        type: Number,
        default: 0,
    },
    pendingDraftCount: {
        type: Number,
        default: 0,
    },
    workspaceCode: {
        type: String,
        default: 'default',
    },
    /** 当前展开步骤 ID */
    activeStepId: {
        type: String,
        default: '',
    },
})

const emit = defineEmits(['update:activeStepId'])

const router = useRouter()

const {
    QUICK_START_STEPS,
    hasApiKey,
    mcpConfigFullText,
    mcpConfigSnippetText,
    mcpTestPromptText,
    rememberPromptText,
    recallPromptText,
    currentStep,
    isStepDone,
    loadApiKeyStatus,
} = useQuickStart({
    publishedCount: () => props.publishedCount,
    pendingDraftCount: () => props.pendingDraftCount,
})

/** MCP 配置展示模式：append=追加片段（默认），full=完整 JSON */
const mcpConfigMode = ref('append')

const activeMcpConfigText = computed(() =>
    mcpConfigMode.value === 'append' ? mcpConfigSnippetText.value : mcpConfigFullText.value,
)

/** 展开/收起步骤，再次点击已展开步骤则收起 */
function toggleStep(stepId) {
    const nextStepId = props.activeStepId === stepId ? '' : stepId
    emit('update:activeStepId', nextStepId)
}

/** 步骤卡片样式：展开中优先高亮 */
function stepCardClass(stepId) {
    if (props.activeStepId === stepId) {
        return 'quick-start__step--expanded'
    }
    if (isStepDone(stepId)) {
        return 'quick-start__step--done'
    }
    return 'quick-start__step--idle'
}

/** 步骤序号圆圈样式 */
function stepIndexClass(stepId) {
    if (isStepDone(stepId)) {
        return 'quick-start__step-index--done'
    }
    if (props.activeStepId === stepId) {
        return 'quick-start__step-index--active'
    }
    return ''
}

/** 跳转到业务页面；主站工作空间设置走外链 */
function goTo(path) {
    if (path === 'gonline-workspace-settings') {
        openGonlineWorkspaceSettings()
        return
    }
    router.push(path)
}

/** 初始化展开步骤：已有选中则保留，否则默认展开第一个未完成步骤 */
function syncActiveStep() {
    if (props.activeStepId) {
        return
    }
    if (currentStep.value) {
        emit('update:activeStepId', currentStep.value.id)
    }
}

async function copyMcpConfig() {
    const successMessage = mcpConfigMode.value === 'append'
        ? '追加片段已复制，粘贴到 mcpServers 对象内即可'
        : '完整 MCP 配置已复制'
    await copyText(activeMcpConfigText.value, successMessage)
}

async function copyMcpTestPrompt() {
    await copyText(mcpTestPromptText.value, '测试提示词已复制，粘贴到智能体对话验证 MCP 连通')
}

async function copyRememberPrompt() {
    await copyText(rememberPromptText.value, '提示词已复制，粘贴到智能体对话即可')
}

async function copyRecallPrompt() {
    await copyText(recallPromptText.value, '提示词已复制')
}

/** 复制文本到剪贴板并提示 */
async function copyText(text, successMessage) {
    const copied = await copyTextToClipboard(text)
    if (copied) {
        ElMessage.success(successMessage)
        return
    }
    ElMessage.error('复制失败，请手动选择文本')
}

onMounted(async () => {
    await loadApiKeyStatus()
    syncActiveStep()
})

watch(
    () => [props.publishedCount, props.pendingDraftCount, hasApiKey.value],
    () => syncActiveStep(),
)
</script>

<style scoped>
.quick-start__step {
    border-color: #e5e7eb;
}

.quick-start__step--expanded {
    border-color: rgba(93, 101, 249, 0.35);
    background: #fafaff;
}

.quick-start__step--done {
    border-color: rgba(103, 194, 58, 0.25);
    background: #fafff5;
}

.quick-start__step-row {
    background: transparent;
    border: none;
    cursor: pointer;
}

.quick-start__step-row:hover {
    background: rgba(93, 101, 249, 0.04);
}

.quick-start__step-index {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 1.75rem;
    height: 1.75rem;
    border-radius: 9999px;
    font-size: 0.75rem;
    font-weight: 600;
    color: #6b7280;
    background: #f3f4f6;
}

.quick-start__step-index--done {
    color: #fff;
    background: #67c23a;
}

.quick-start__step-index--active {
    color: #fff;
    background: #5d65f9;
}

.quick-start__substeps {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 0.625rem;
}

/* 一步一卡：标题区说明 + 下方操作区 */
.quick-start__substep {
    overflow: hidden;
    border: 1px solid #e5e7eb;
    border-radius: 0.5rem;
    background: #fff;
}

.quick-start__substep-header {
    display: flex;
    gap: 0.75rem;
    padding: 0.75rem 0.875rem;
    background: #f9fafb;
    border-bottom: 1px solid #f3f4f6;
}

.quick-start__substep-header--only {
    border-bottom: none;
}

.quick-start__substep-num {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 1.25rem;
    height: 1.25rem;
    margin-top: 0.125rem;
    border-radius: 9999px;
    font-size: 0.6875rem;
    font-weight: 600;
    color: #5d65f9;
    background: #eff0fe;
}

.quick-start__substep-title {
    display: block;
    font-size: 0.8125rem;
    font-weight: 600;
    color: #1f2937;
}

.quick-start__substep-desc {
    margin: 0.25rem 0 0;
    font-size: 0.75rem;
    line-height: 1.55;
    color: #6b7280;
}

.quick-start__substep-panel {
    padding: 0.75rem 0.875rem;
}

.quick-start__code {
    margin: 0;
    white-space: pre-wrap;
    word-break: break-all;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.quick-start__code + p code,
.quick-start__detail code {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 0.6875rem;
    padding: 0.0625rem 0.25rem;
    border-radius: 0.25rem;
    background: #eef2ff;
    color: #4338ca;
}

.quick-start__inline-link {
    padding: 0;
    border: none;
    background: none;
    font: inherit;
    color: #5d65f9;
    text-decoration: underline;
    text-underline-offset: 2px;
    cursor: pointer;
}

.quick-start__inline-link:hover {
    color: #4338ca;
}

.quick-start__inline-link--active {
    font-weight: 600;
    color: #4338ca;
}
</style>
