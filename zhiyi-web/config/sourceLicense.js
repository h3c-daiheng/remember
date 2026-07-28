/**
 * 源码授权营销文案配置
 * 结构对齐画境 /source，内容按智忆产品能力改写
 */
import { BRAND_PRODUCT_NAME } from '~/constants/brand'

/** 源码授权落地页路由 */
export const SOURCE_LICENSE_PATH = '/source'

/** 源码授权页 SEO 描述 */
export const SOURCE_LICENSE_PAGE_DESCRIPTION =
    `${BRAND_PRODUCT_NAME}完整源码授权，含经验中心、规则与决策、Agent Recall / MCP 等能力，面向企业与个人，支持私有化部署与二次开发。`

/** 源码授权页 SEO 关键词 */
export const SOURCE_LICENSE_PAGE_KEYWORDS =
    '智忆源码授权,经验基础设施,私有化部署,Agent Recall,MCP,二次开发,Your Company'

/** Hero 区角标 */
export const SOURCE_LICENSE_HERO_KICKER = '源码授权'

/** Hero 主标题 */
export const SOURCE_LICENSE_HERO_TITLE = '智忆源码授权'

/** Hero 副文案 */
export const SOURCE_LICENSE_HERO_SUBTITLE =
    '获取与线上一致的完整源码，适合企业私有化、个人项目或二次开发。含部署说明，支持品牌定制与功能扩展。'

/** 主 CTA：跳转联系我们 */
export const SOURCE_LICENSE_PRIMARY_CTA = '咨询授权'

/** 次 CTA：回首页体验产品 */
export const SOURCE_LICENSE_SECONDARY_CTA = `在线体验 ${BRAND_PRODUCT_NAME}`

/** 授权包含区块 */
export const SOURCE_LICENSE_INCLUDE_SECTION = {
    title: '授权包含什么',
    description: '无论企业采购还是个人购买，均一站交付源码与说明。',
    items: [
        {
            title: '网站源码',
            description: '含营销首页、工作台、经验/规则/决策中心与常用页面，与当前线上产品一致',
        },
        {
            title: '后台服务源码',
            description: '含用户登录、知识库、Recall / Remember、MCP 接入与工作空间等后台服务',
        },
        {
            title: '部署与使用说明',
            description: '提供部署步骤与功能说明，帮助个人或团队快速上手',
        },
        {
            title: '授权与支持',
            description: '按方案提供源码交付、部署咨询与后续更新说明（具体以授权协议为准）',
        },
    ],
}

/** 核心能力区块（授权后可直接复用的产品能力） */
export const SOURCE_LICENSE_CAPABILITY_SECTION = {
    title: '核心能力',
    description: '以下能力已在产品中验证，授权后可直接使用，也可按业务需要裁剪或扩展。',
    items: [
        {
            title: '结构化经验',
            description: '把真实做过的事沉淀为可检索、可引用的 Experience，服务人与 Agent',
        },
        {
            title: '规则与决策',
            description: '沉淀可执行规则与关键决策，减少口头传承损耗',
        },
        {
            title: 'Agent Recall / MCP',
            description: '任务开始前自动召回相关经验，支持 MCP 接入主流 Agent 工作流',
        },
        {
            title: '闭环与图谱',
            description: '闭环追踪使用效果，经验图谱呈现关系，持续进化组织记忆',
        },
    ],
}

/** 授权说明 FAQ */
export const SOURCE_LICENSE_FAQ_SECTION = {
    title: '授权说明',
    description: '企业与个人购买前常见疑问，具体条款以授权协议为准。',
    items: [
        {
            question: '个人开发者可以购买吗？',
            answer:
                '可以。个人与企业均可申请源码授权，我们会根据您的使用场景（自用站点、接单项目、企业内部工具等）提供对应方案。',
        },
        {
            question: '授权后能否去掉智忆品牌？',
            answer:
                '支持按授权方案进行品牌与域名定制，具体范围以协议为准。部署后可替换 Logo、站点文案与页脚信息。',
        },
        {
            question: '是否包含 AI 服务与登录账号？',
            answer:
                '源码含 AI 与登录相关能力，具体模型服务与账号体系需按您的环境配置，我们可协助对接。',
        },
        {
            question: '能否转售或再分发源码？',
            answer:
                '默认授权面向自用或单项目部署，个人与企业均需遵守授权范围，禁止未授权的再分发。多实例或 OEM 方案请咨询我们。',
        },
        {
            question: '如何获取报价与交付流程？',
            answer:
                '请通过联系我们页扫码添加巨人肩膀小助，说明是个人还是企业用途、是否需要定制，我们会提供对应授权方案与报价。',
        },
    ],
}

/** 页底 CTA 区块 */
export const SOURCE_LICENSE_FINAL_CTA = {
    title: '准备搭建自己的经验基础设施？',
    description:
        '联系巨人肩膀小助，获取授权方案、交付清单与部署咨询。企业与个人均可咨询。',
    buttonText: SOURCE_LICENSE_PRIMARY_CTA,
}

/** 首页源码授权区块锚点 id */
export const HOME_SOURCE_LICENSE_SECTION_ID = 'home-source-license'

/** 首页源码授权区块文案 */
export const HOME_SOURCE_LICENSE_SECTION = {
    kicker: SOURCE_LICENSE_HERO_KICKER,
    title: '需要私有化部署或二次开发？',
    description:
        `${BRAND_PRODUCT_NAME}提供完整源码授权，面向企业与个人开发者。含经验中心、规则与决策、Agent Recall / MCP 等能力，支持私有化部署与品牌定制。`,
    primaryCta: '了解源码授权',
    secondaryCta: '联系我们',
}
