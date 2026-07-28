/**
 * gonline 生态站点统一配置
 * 页脚、关于入口等共用；新增子站时在此追加即可
 *
 * @typedef {Object} EcosystemSiteItem
 * @property {string} id 唯一标识
 * @property {string} name 产品名称
 * @property {string} tagline 短标签（宜控制在 8 字内）
 * @property {string} description 能力说明
 * @property {string} href 外链地址
 * @property {boolean} [isOfficial] 是否为生态官网
 */

/** 生态官网（门户） */
export const ECOSYSTEM_OFFICIAL_SITE = {
    id: 'portal',
    name: '巨人肩膀',
    tagline: '生态官网',
    description: '站在巨人肩膀上，成就超级个体',
    href: 'https://www.example.com/',
    isOfficial: true,
}

/**
 * 生态子站列表（不含官网）
 * 顺序与官网首页入口保持一致：画境 → 智忆 → 言达 → 智见
 * @type {EcosystemSiteItem[]}
 */
export const ECOSYSTEM_SUB_SITES = [
    {
        id: 'huajing',
        name: '画境',
        tagline: '在线图片编辑',
        description: '简单快速的在线图片编辑与 AI 处理',
        href: 'https://huajing.example.com/',
    },
    {
        id: 'zhiyi',
        name: '智忆',
        tagline: '经验可复用',
        description: 'AI 时代的经验基础设施，可被 Agent 复用',
        href: 'https://zhiyi.example.com/',
    },
    {
        id: 'chat2x',
        name: '言达',
        tagline: '对话即交付',
        description: '对话驱动的 X 应用平台，说句话完成交付',
        href: 'https://chat2x.example.com/',
    },
    {
        id: 'zhijian',
        name: '智见',
        tagline: 'AI 可见性',
        description: '品牌 AI 可见性监测与优化工作区',
        href: 'https://zhijian.example.com/',
    },
]

/** 当前站点在生态列表中的 id（本站页脚不展示自身入口） */
export const CURRENT_ECOSYSTEM_SITE_ID = 'zhiyi'

/**
 * 页脚「生态产品」列：其他子站（排除本站与官网）
 * 官网入口放在「关于」列，避免生态产品列重复展示
 */
export const ECOSYSTEM_FOOTER_SITES = ECOSYSTEM_SUB_SITES.filter(
    (siteItem) => siteItem.id !== CURRENT_ECOSYSTEM_SITE_ID,
)
