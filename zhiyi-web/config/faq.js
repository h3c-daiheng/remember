/**
 * FAQ 问答配置
 * 按顶栏锚点、页脚入口与首页生态可见能力编写「用户意图型」问答；
 * 同时驱动页面折叠面板与 FAQPage 结构化数据
 */

/** 使用与沉淀：覆盖首页痛点/飞轮/能力与页脚产品能力、使用入口 */
export const FAQ_USAGE_ITEMS = [
    {
        question: '团队踩过的坑总是反复出现，怎么让后来的人少走弯路？',
        answer:
            '把真实做过的事写进经验中心：说清背景、做法、结果和适用条件。同事可在经验搜索里按任务查找；编程助手也能在开工前取用相关经验，减少同类问题一再踩坑。',
    },
    {
        question: '经验写进文档后，人和助手都用不顺手，怎么办？',
        answer:
            '通读型文档缺少「为什么」和适用边界，助手取用时噪音大。智忆用结构化经验卡沉淀方案与条件，人可以浏览确认，助手也可以按当前任务精准取用，而不是整篇塞进上下文。',
    },
    {
        question: '一次做好的方案，怎样留下来给下次同类任务用？',
        answer:
            '按「自动采集 → 人工确认 → 智能召回」走：任务结束后生成草稿，负责人在草稿确认里校验后发布；之后新任务开始前就能自动拿到相关经验。可从工作台进入，或先看快速指南走通第一圈。',
    },
    {
        question: '登录后从哪里开始整理团队经验？',
        answer:
            '登录后进入工作台，可按快速指南完成首条经验。日常在记忆中心维护已发布内容，待审草稿在草稿确认处理；需要找历史方案时用经验搜索。工作空间里可管理团队与接入配置。',
    },
    {
        question: '想找以前做过的类似方案，去哪里搜？',
        answer:
            '打开经验搜索，按任务描述或关键词检索已发布经验。比翻聊天记录或整页文档更直接，也能把命中内容整理后交给编程助手继续用。',
    },
    {
        question: '规则和决策分别记什么？什么时候用哪个？',
        answer:
            '记忆中心统一管理四类知识：经验沉淀可复用的做事方法；规则与流程记录长期要遵守的约定；决策记录「为什么选了这条路」的关键取舍。可在闭环追踪里查看被引用与反馈情况。',
    },
    {
        question: '怎么知道某条经验有没有被真正用上、是否该更新？',
        answer:
            '到闭环追踪查看经验被取用、反馈和失效情况。长期无人采用或反馈不佳的，可回到记忆中心修订或标记失效，让库越用越准。',
    },
    {
        question: '想把经验留下来，发社区帖子还是用智忆？',
        answer:
            '社区适合人读全文、靠点赞传播；智忆面向「人浏览 + 助手按任务取用」，强调验证、采纳和失效标记。若目标是让团队和助手持续复用真实做过的事，优先用智忆。',
    },
]

/** 账号与生态：覆盖登录、工作空间与首页/页脚生态产品入口 */
export const FAQ_ECOSYSTEM_ITEMS = [
    {
        question: '在巨人肩膀生态里，图片编辑、对话交付和经验沉淀分别去哪个站？',
        answer:
            '图片在线编辑用画境；对话驱动交付用言达；品牌 AI 可见性用智见；把真实做过的事沉淀成可复用经验用智忆。巨人肩膀是生态官网与统一账号入口，各子站可共用同一套登录。',
    },
    {
        question: '账号怎么注册？已有巨人肩膀账号还要再开吗？',
        answer:
            '智忆使用巨人肩膀统一账号。已有账号可直接登录；新用户在登录页注册一次即可，无需为智忆单独再开账号。',
    },
    {
        question: '个人可以先试用吗？团队怎么开始？',
        answer:
            '可以。个人登录后从工作台与快速指南体验「采集 → 确认 → 查找」闭环即可。团队建议先约定工作空间，把常用规则与关键决策记入库，再逐步让编程助手接到同一套经验上。',
    },
    {
        question: '怎么让编程助手在开工前自动用上团队经验？',
        answer:
            '在工作空间完成接入配置后，助手可在新任务开始前按上下文取用相关经验与决策。具体步骤见快速指南；接入后请持续在草稿确认中把关质量，保证助手用到的都是可信内容。',
    },
]

/**
 * 将 FAQ 条目转为 Schema.org Question / Answer 结构
 * @param {{ question: string, answer: string }} item FAQ 条目
 */
function toFaqSchemaEntity(item) {
    return {
        '@type': 'Question',
        name: item.question,
        acceptedAnswer: {
            '@type': 'Answer',
            text: item.answer,
        },
    }
}

/**
 * 合并两组 FAQ 并按问题去重，供 FAQPage.mainEntity 使用
 * 同名问题保留首次出现（使用组优先）
 */
export function buildFaqPageMainEntity() {
    const seenQuestionNames = new Set()
    const mergedItems = [...FAQ_USAGE_ITEMS, ...FAQ_ECOSYSTEM_ITEMS]
    const uniqueEntities = []

    for (const item of mergedItems) {
        if (seenQuestionNames.has(item.question)) {
            continue
        }
        seenQuestionNames.add(item.question)
        uniqueEntities.push(toFaqSchemaEntity(item))
    }

    return uniqueEntities
}
