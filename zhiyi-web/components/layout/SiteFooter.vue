<template>
    <!--
      生态统一页脚：结构对齐 gonline MyFooter（品牌 + 多列导航 + 微信码 + 版权底栏）
      背景用 gray-100，与营销页白底、布局 gray-50 均形成层次区分
    -->
    <footer class="site-footer"
        aria-label="页脚">
        <div class="site-footer__inner">
            <!-- 品牌区：Logo 与定位语 -->
            <div class="site-footer__brand">
                <BrandLogo link-to="/"
                    show-text
                    class="site-footer__logo" />
                <p class="site-footer__slogan">
                    {{ brandSlogan }}
                </p>
            </div>

            <!-- 导航链接列 -->
            <div class="site-footer__nav">
                <div v-for="navGroup in footerNavGroups"
                    :key="navGroup.title"
                    class="site-footer__col">
                    <div class="site-footer__title">{{ navGroup.title }}</div>
                    <div class="site-footer__links">
                        <template v-for="linkItem in navGroup.links"
                            :key="linkItem.label">
                            <NuxtLink v-if="linkItem.internal"
                                :to="linkItem.href"
                                class="site-footer__link">
                                {{ linkItem.label }}
                            </NuxtLink>
                            <a v-else
                                class="site-footer__link"
                                :href="linkItem.href"
                                target="_blank"
                                rel="noopener noreferrer">
                                {{ linkItem.label }}
                            </a>
                        </template>
                    </div>
                </div>
            </div>

            <!-- 右侧：微信交流/反馈二维码（与官网同源） -->
            <div class="site-footer__wechat">
                <div v-for="qrItem in wechatQrCodes"
                    :key="qrItem.title"
                    class="site-footer__wechat-item">
                    <el-image class="site-footer__wechat-image"
                        :src="qrItem.url"
                        fit="contain"
                        :alt="qrItem.title" />
                    <p class="site-footer__wechat-label">{{ qrItem.title }}</p>
                </div>
            </div>
        </div>

        <!-- 版权备案：与主站口径一致，站名回首页，备案号跳工信部 -->
        <div class="site-footer__bottom">
            <div class="site-footer__bottom-content">
                ©{{ copyrightStartYear }}-{{ currentYear }} {{ companyName }} -
                <NuxtLink to="/"
                    class="site-footer__bottom-link">
                    {{ brandName }}
                </NuxtLink>
                <a :href="icpRecordQueryUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="site-footer__bottom-link">
                    - {{ icpRecordNumber }}
                </a>
            </div>
        </div>
    </footer>
</template>

<script setup>
import { ElImage } from 'element-plus'
import {
    BRAND_COMPANY_NAME,
    BRAND_FOOTER_SLOGAN,
    BRAND_PRODUCT_NAME,
    COPYRIGHT_START_YEAR,
    ICP_RECORD_NUMBER,
    ICP_RECORD_QUERY_URL,
} from '~/constants/brand'
import { ECOSYSTEM_FOOTER_SITES } from '~/constants/ecosystemSites'
import { PAGE_LABELS } from '~/constants/terminology'

/** 品牌定位语 */
const brandSlogan = BRAND_FOOTER_SLOGAN
/** 法人主体名称 */
const companyName = BRAND_COMPANY_NAME
/** 本站产品名，版权区可点回首页 */
const brandName = BRAND_PRODUCT_NAME
/** 版权起始年 */
const copyrightStartYear = COPYRIGHT_START_YEAR
/** ICP 备案号 */
const icpRecordNumber = ICP_RECORD_NUMBER
/** 工信部备案查询地址 */
const icpRecordQueryUrl = ICP_RECORD_QUERY_URL
/** 版权结束年动态取当前年 */
const currentYear = new Date().getFullYear()

/** 页脚右侧微信二维码（与官网页脚同源，便于统一反馈入口） */
const wechatQrCodes = [
    {
        title: '微信(交流/反馈)',
        url: '',
    },
]

/**
 * 页脚导航分组
 * - 生态产品：其他子站外链（不含本站与官网）
 * - 产品能力 / 使用入口：站内核心路径，便于从营销页下钻
 * - 关于：官网与 FAQ 等入口
 */
const footerNavGroups = [
    {
        title: '生态产品',
        links: ECOSYSTEM_FOOTER_SITES.map((siteItem) => ({
            label: siteItem.name,
            href: siteItem.href,
            internal: false,
        })),
    },
    {
        title: '产品能力',
        links: [
            { label: PAGE_LABELS.memory, href: '/memory', internal: true },
            { label: '闭环追踪', href: '/trace', internal: true },
            { label: PAGE_LABELS.recalls, href: '/recalls', internal: true },
        ],
    },
    {
        title: '使用入口',
        links: [
            { label: '工作台', href: '/dashboard', internal: true },
            { label: '快速指南', href: '/dashboard/guide', internal: true },
            { label: '经验搜索', href: '/search', internal: true },
        ],
    },
    {
        title: '关于',
        links: [
            {
                label: '关于我们',
                href: '/about',
                internal: true,
            },
            {
                label: '源码授权',
                href: '/source',
                internal: true,
            },
            {
                label: '联系我们',
                href: '/contact',
                internal: true,
            },
            {
                label: '常见问题',
                href: '/faq',
                internal: true,
            },
            {
                label: '巨人肩膀',
                href: 'https://www.example.com/',
                internal: false,
            },
        ],
    },
]
</script>

<style scoped>
.site-footer {
    /* gray-100：相对营销白底、布局 gray-50 均有层次，避免页脚与页面区域无底色区分 */
    margin-top: 0;
    background: #f3f4f6;
    padding-top: 1.75rem;
    padding-bottom: 1.25rem;
    color: #1f2937;
}

.site-footer__inner {
    display: flex;
    align-items: flex-start;
    gap: 2.5rem;
    width: 100%;
    max-width: 1120px;
    margin: 0 auto;
    padding: 0 1rem 1.25rem;
}

.site-footer__brand {
    display: flex;
    flex-direction: column;
    flex-shrink: 0;
    width: 200px;
    gap: 0.75rem;
    padding-right: 0.5rem;
}

.site-footer__logo {
    align-self: flex-start;
}

.site-footer__slogan {
    margin: 0;
    font-size: 13px;
    line-height: 1.55;
    color: #6b7280;
}

.site-footer__nav {
    display: grid;
    flex: 1;
    min-width: 0;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    column-gap: 1.25rem;
    row-gap: 1.5rem;
}

.site-footer__title {
    margin-bottom: 0.75rem;
    font-size: 13px;
    font-weight: 700;
    color: #1f2937;
}

.site-footer__links {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
}

.site-footer__link {
    font-size: 13px;
    color: #6b7280;
    text-decoration: none;
    transition: color 0.15s ease;
}

.site-footer__link:hover {
    color: #5d65f9;
}

.site-footer__wechat {
    flex-shrink: 0;
    margin-left: auto;
}

.site-footer__wechat-item {
    display: flex;
    flex-direction: column;
    align-items: center;
}

.site-footer__wechat-image {
    width: 80px;
    height: 80px;
    padding: 0.5rem;
    border-radius: 0.5rem;
    border: 1px solid #e2e8f0;
    background: #fff;
}

.site-footer__wechat-label {
    margin: 0;
    padding-top: 0.25rem;
    font-size: 12px;
    text-align: center;
    color: #9ca3af;
}

.site-footer__bottom {
    width: 100%;
    border-top: 1px solid rgba(0, 0, 0, 0.06);
    padding-top: 1rem;
}

.site-footer__bottom-content {
    width: 100%;
    max-width: 1120px;
    margin: 0 auto;
    padding: 0 1rem;
    text-align: center;
    font-size: 12px;
    color: #9ca3af;
}

.site-footer__bottom-link {
    color: #9ca3af;
    text-decoration: none;
    transition: color 0.15s ease;
}

.site-footer__bottom-link:hover {
    color: #5d65f9;
}

@media (max-width: 1100px) {
    .site-footer__inner {
        gap: 1.75rem;
    }

    .site-footer__brand {
        width: 10rem;
    }
}

@media (max-width: 900px) {
    .site-footer__inner {
        flex-direction: column;
        gap: 1.5rem;
    }

    .site-footer__brand {
        width: 100%;
    }

    .site-footer__wechat {
        margin-left: 0;
    }

    .site-footer__nav {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }
}

@media (max-width: 640px) {
    .site-footer__nav {
        grid-template-columns: minmax(0, 1fr);
        row-gap: 1.25rem;
    }
}
</style>
