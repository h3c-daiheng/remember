/**
 * 工作台「快速开始」引导：步骤进度与密钥状态（各步骤可独立查看，无顺序锁定）
 */
import { toValue } from 'vue'
import {
    QUICK_START_HEADER,
    QUICK_START_STEPS,
} from '~/constants/quickStart'
import { fetchApiKeyListRequest } from '~/services/api-key.service'

/**
 * @param {Object} options
 * @param {import('vue').MaybeRefOrGetter<number>} options.publishedCount 已发布经验数
 * @param {import('vue').MaybeRefOrGetter<number>} options.pendingDraftCount 待确认草稿数
 */
export function useQuickStart(options = {}) {
    const hasApiKey = ref(false)
    const apiKeyLoading = ref(false)

    /** 智忆 MCP 单条服务配置；键名与 npm 包名 zhiyi-mcp 保持一致即可 */
    const mcpServerEntry = {
        command: 'npx',
        args: ['-y', 'zhiyi-mcp'],
        env: {
            BIGAPP_API_KEY: '粘贴你在工作空间签发的密钥',
        },
    }

    /** 首次配置：完整 mcp.json 内容 */
    const mcpConfigFullText = computed(() => JSON.stringify({
        mcpServers: {
            'zhiyi-mcp': mcpServerEntry,
        },
    }, null, 2))

    /**
     * 追加到已有 mcpServers：仅服务条目，可直接粘贴进现有对象
     * 例：在 "other-mcp": { ... } 后加逗号，再粘贴本段
     */
    const mcpConfigSnippetText = computed(() => {
        const entryLines = JSON.stringify(mcpServerEntry, null, 2).split('\n')
        entryLines[0] = `"zhiyi-mcp": ${entryLines[0]}`
        return entryLines.join('\n')
    })

    /** MCP 配置完成后的连通性测试提示词（首次配置后用来验证 Agent 能否调用工具） */
    const mcpTestPromptText = computed(
        () => `请调用智忆 MCP 工具 memory_recall，测试智忆是否已连通。

【必须执行】
1. 使用 MCP 工具 memory_recall（不要猜测或模拟返回）
2. task 填：MCP 连接测试

调用成功后，请告诉我：
- 是否成功调用工具
- 返回了几条经验（可以为 0）
- 若失败，把报错信息原样贴出`,
    )

    /** 任务结束后沉淀经验的提示词（明确要求调用 memory_submit，避免 Agent 只在对话里总结） */
    const rememberPromptText = computed(
        () => `请调用智忆 MCP 工具 memory_submit，把刚才完成的任务提交为记忆草稿。

【必须执行】
1. 使用 MCP 工具 memory_submit 提交（不要只在聊天里总结）
2. knowledgeType 填 experience（默认）
3. payload 各字段分开写、内容不要重复：
   - task：任务标题（80 字以内）
   - observation：遇到了什么问题/现象
   - decision：为什么选这个方案
   - action：具体改了哪些文件、配置或命令
   - outcome：结果如何、怎样验证已解决
4. 如有改动过的文件，传入 modifiedFiles 数组

请根据我们刚才完成的工作整理以上内容，整理完成后立即调用工具提交。`,
    )

    /** 新任务开始前召回经验的提示词（明确要求调用 memory_recall，避免 Agent 凭记忆回答） */
    const recallPromptText = computed(
        () => `请先调用智忆 MCP 工具 memory_recall 搜索团队历史经验，读完后再动手写代码。

【必须执行】
1. 使用 MCP 工具 memory_recall（不要用网页搜索，也不要凭记忆回答）
2. 将下方「任务描述」填入 task 参数
3. 若已知当前文件或模块，一并传入 currentFile、module、repository

任务描述：【在这里写上你要做的事，例如：修复登录页 OAuth 回调 404】

召回成功后请：
1. 列出最相关的 1～3 条经验标题
2. 用 2～3 句话说明对你当前任务的建议
3. 再给出你的实施计划`,
    )

    /**
     * 各步骤完成条件（独立判定，互不影响）
     * mcp：已签发密钥；remember：有草稿或已发布；review/recall：有已发布经验
     */
    const stepDoneMap = computed(() => ({
        mcp: hasApiKey.value,
        remember: toValue(options.pendingDraftCount) > 0 || toValue(options.publishedCount) > 0,
        review: toValue(options.publishedCount) > 0,
        recall: toValue(options.publishedCount) > 0,
    }))

    /** 默认聚焦的第一个未完成步骤（仅用于进度提示，不限制用户切换） */
    const currentStep = computed(() =>
        QUICK_START_STEPS.find((step) => !stepDoneMap.value[step.id]),
    )

    const currentStepIndex = computed(() =>
        currentStep.value ? QUICK_START_STEPS.findIndex((step) => step.id === currentStep.value.id) : -1,
    )

    const completedStepCount = computed(() =>
        QUICK_START_STEPS.filter((step) => stepDoneMap.value[step.id]).length,
    )

    const progressPercent = computed(() =>
        Math.round((completedStepCount.value / QUICK_START_STEPS.length) * 100),
    )

    const allStepsDone = computed(() => completedStepCount.value === QUICK_START_STEPS.length)

    const headerSubtitle = computed(() => {
        if (allStepsDone.value) {
            return QUICK_START_HEADER.completedSubtitle
        }
        if (currentStep.value) {
            return `第 ${currentStepIndex.value + 1} 步：${currentStep.value.summary}`
        }
        return QUICK_START_HEADER.subtitle
    })

    /** 判断指定步骤是否已完成 */
    function isStepDone(stepId) {
        return Boolean(stepDoneMap.value[stepId])
    }

    /** 拉取 Agent 密钥签发状态 */
    async function loadApiKeyStatus() {
        apiKeyLoading.value = true
        try {
            const result = await fetchApiKeyListRequest()
            const enabledKeys = (result?.keyList || []).filter((item) => item.enabled)
            hasApiKey.value = enabledKeys.length > 0
        } catch (error) {
            hasApiKey.value = false
        } finally {
            apiKeyLoading.value = false
        }
    }

    return {
        QUICK_START_HEADER,
        QUICK_START_STEPS,
        hasApiKey,
        apiKeyLoading,
        mcpConfigFullText,
        mcpConfigSnippetText,
        mcpTestPromptText,
        rememberPromptText,
        recallPromptText,
        stepDoneMap,
        currentStep,
        currentStepIndex,
        completedStepCount,
        progressPercent,
        allStepsDone,
        headerSubtitle,
        isStepDone,
        loadApiKeyStatus,
    }
}
