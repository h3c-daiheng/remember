/**
 * 工作台「快速开始」引导步骤配置
 * 对应产品闭环：接入 Agent → 沉淀经验 → 确认发布 → 智能召回
 */

/** @typedef {Object} QuickStartDetailLink
 * @property {string} before - 链接前文案
 * @property {string} label - 链接展示文字
 * @property {string} path - 跳转路由或外链标识
 * @property {string} after - 链接后文案
 */

/** @typedef {Object} QuickStartSubStep
 * @property {string} id - 子步骤标识（用于挂载对应操作区）
 * @property {string} title - 子步骤标题
 * @property {string} [detail] - 补充说明（无内联链接时使用）
 * @property {QuickStartDetailLink} [detailLink] - 含可点击链接的说明
 */

/** @typedef {Object} QuickStartStep
 * @property {string} id - 步骤唯一标识
 * @property {string} title - 步骤标题
 * @property {string} summary - 折叠时的一句话说明
 * @property {string} detail - 展开后的引导语
 * @property {QuickStartSubStep[]} [subSteps] - 分步清单（降低新手理解成本）
 * @property {string} [actionPath] - 内联跳转路由
 * @property {string} [hint] - 补充提示
 */

/** 四步闭环引导（文案面向新手，避免堆砌英文术语） */
export const QUICK_START_STEPS = [
    {
        id: 'mcp',
        title: '接入智忆 MCP',
        summary: '签发密钥并配置 MCP，智能体才能读写经验',
        detail: '配置一次即可，Cursor 等 Agent 能召回经验、提交草稿。',
        subSteps: [
            {
                id: 'issue-key',
                title: '签发 Agent 密钥',
                detailLink: {
                    before: '前往',
                    label: '工作空间设置',
                    path: 'gonline-workspace-settings',
                    after: '，在 Agent API Key 点击「签发密钥」，勾选「召回经验」与「提交草稿」，复制明文密钥（仅显示一次）。',
                },
            },
            {
                id: 'configure-mcp',
                title: '配置 MCP',
                detail: '将 BIGAPP_API_KEY 换成你的密钥后写入 MCP 配置。',
            },
            {
                id: 'verify-connection',
                title: '确认连接成功',
                detail: '重载 MCP 后确认 zhiyi-mcp 已连接，',
            },
        ],
        actionPath: 'gonline-workspace-settings',
        hint: '只需填写 BIGAPP_API_KEY；需 Node.js 18+',
    },
    {
        id: 'remember',
        title: '让 Agent 记录本次实践',
        summary: '完成一项真实任务后，请 Agent 提交经验草稿',
        detail: '选一件刚完成的事，任务结束后把提示词发给 Agent（须调用 memory_submit）。',
        actionPath: '/capture',
        hint: '若未调用工具，补充：「请立即调用 memory_submit MCP 工具提交」',
    },
    {
        id: 'review',
        title: '审核并发布经验',
        summary: '检查草稿内容，确认后发布到经验中心',
        detail: '核对标题与观察、决策、行动等内容，确认无敏感信息后发布。',
        actionPath: '/capture',
        hint: '质量不高的草稿可以直接拒绝，不影响后续使用',
    },
    {
        id: 'recall',
        title: '新任务前自动召回',
        summary: '开始相关任务时，Agent 会读取已发布经验',
        detail: '同类任务开始前把提示词发给 Agent（须调用 memory_recall），读完后再写代码。',
        actionPath: '/memory',
        hint: '若未调用工具，补充：「请立即调用 memory_recall MCP 工具，不要凭记忆回答」',
    },
]

/** 引导区标题与副标题 */
export const QUICK_START_HEADER = {
    title: '快速开始',
    subtitle: '跟着下面 4 步，约 15 分钟跑通「采集 → 确认 → 召回」闭环',
    completedTitle: '恭喜，闭环已跑通',
    completedSubtitle: '继续让 Agent 沉淀经验，团队记忆会越来越准',
    nextStepPrefix: '当前进度：',
}
