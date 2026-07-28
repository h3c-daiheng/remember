<template>
    <!-- FAQ：折叠面板交互参照 imagehub-web（details/summary） -->
    <div class="zhiyi-faq">
        <section class="zhiyi-faq-hero">
            <div
                class="zhiyi-faq-hero-layer zhiyi-faq-hero-layer--gradient"
                aria-hidden="true"
            />
            <div
                class="zhiyi-faq-hero-layer zhiyi-faq-hero-layer--glow"
                aria-hidden="true"
            />
            <div class="zhiyi-faq-hero-inner">
                <h1 class="zhiyi-faq-hero-title">常见问题</h1>
                <p class="zhiyi-faq-hero-lead">
                    围绕开始使用、经验沉淀、规则与决策、账号与生态等常见意图，帮你快速找到合适入口。
                </p>
                <button
                    type="button"
                    class="zhiyi-faq-cta"
                    @click="handleTryNowClick"
                >
                    {{ primaryCtaLabel }}
                </button>
            </div>
        </section>

        <div class="zhiyi-faq-body">
            <!-- 使用与沉淀：对应顶栏飞轮/能力与页脚产品能力、使用入口 -->
            <section
                class="zhiyi-faq-section"
                aria-labelledby="faq-usage"
            >
                <h2 id="faq-usage">使用与沉淀</h2>
                <p class="zhiyi-faq-section-desc">
                    从「怎么少踩坑」到「去哪搜、记规则还是记决策」。
                </p>
                <div class="zhiyi-faq-list">
                    <details
                        v-for="(item, index) in usageFaqItems"
                        :key="`usage-${index}`"
                        class="zhiyi-faq-item"
                        :open="index === 0"
                        @toggle="handleFaqToggle($event, item, 'usage', index)"
                    >
                        <summary class="zhiyi-faq-item-question">
                            <span>{{ item.question }}</span>
                            <span
                                class="zhiyi-faq-item-icon"
                                aria-hidden="true"
                            />
                        </summary>
                        <div class="zhiyi-faq-item-answer">
                            <p>{{ item.answer }}</p>
                        </div>
                    </details>
                </div>
            </section>

            <!-- 账号与生态：对应登录与页脚/首页生态产品入口 -->
            <section
                class="zhiyi-faq-section"
                aria-labelledby="faq-ecosystem"
            >
                <h2 id="faq-ecosystem">账号与生态</h2>
                <p class="zhiyi-faq-section-desc">
                    统一登录、个人试用与巨人肩膀各站怎么选。
                </p>
                <div class="zhiyi-faq-list">
                    <details
                        v-for="(item, index) in ecosystemFaqItems"
                        :key="`ecosystem-${index}`"
                        class="zhiyi-faq-item"
                        @toggle="handleFaqToggle($event, item, 'ecosystem', index)"
                    >
                        <summary class="zhiyi-faq-item-question">
                            <span>{{ item.question }}</span>
                            <span
                                class="zhiyi-faq-item-icon"
                                aria-hidden="true"
                            />
                        </summary>
                        <div class="zhiyi-faq-item-answer">
                            <p>{{ item.answer }}</p>
                        </div>
                    </details>
                </div>
            </section>
        </div>
    </div>
</template>

<script setup>
import { BRAND_PRODUCT_NAME } from '~/constants/brand'
import {
    FAQ_USAGE_ITEMS,
    FAQ_ECOSYSTEM_ITEMS,
    buildFaqPageMainEntity,
} from '~/config/faq'
import { getRouterMenuTitleByPath } from '~/config/routerMenus'
import { SITE_TRACK_EVENTS } from '~/config/tracker'

definePageMeta({
    layout: 'default',
})

const pageTitle = getRouterMenuTitleByPath('/faq') || '常见问题'
const faqPageDescription =
    '智忆常见问题：经验怎么沉淀与查找、规则与决策怎么记、账号与巨人肩膀生态怎么选。'

useHead({
    title: pageTitle,
    meta: [
        { name: 'description', content: faqPageDescription },
        {
            name: 'keywords',
            content: '智忆,常见问题,经验沉淀,经验搜索,规则中心,决策中心,巨人肩膀',
        },
    ],
    script: [
        {
            type: 'application/ld+json',
            // FAQPage 结构化数据，便于搜索引擎展示折叠问答
            innerHTML: JSON.stringify({
                '@context': 'https://schema.org',
                '@type': 'FAQPage',
                name: `${pageTitle} · ${BRAND_PRODUCT_NAME}`,
                description: faqPageDescription,
                mainEntity: buildFaqPageMainEntity(),
            }),
        },
    ],
})

/** 页面浏览埋点 */
const { reportEvent } = usePageTracker()

const { currentUser, redirectToLogin } = useAuth()
const clientMounted = useClientMounted()

/** 使用与沉淀问答 */
const usageFaqItems = FAQ_USAGE_ITEMS
/** 账号与生态问答 */
const ecosystemFaqItems = FAQ_ECOSYSTEM_ITEMS

/** 挂载完成后再接受 toggle，避免首条默认 open 误报 */
const faqToggleReady = ref(false)

onMounted(() => {
    nextTick(() => {
        faqToggleReady.value = true
    })
})

/** 已登录展示「进入工作台」，未登录展示「免费开始」 */
const primaryCtaLabel = computed(() => {
    if (clientMounted.value && currentUser.value) {
        return '进入工作台'
    }
    return '免费开始'
})

/** 首屏主按钮：未登录去登录，已登录进工作台 */
function handleTryNowClick() {
    reportEvent(SITE_TRACK_EVENTS.CLICK_TRY_NOW, {
        from: 'faq',
        loggedIn: Boolean(currentUser.value),
    })
    if (currentUser.value) {
        reportEvent(SITE_TRACK_EVENTS.CLICK_ENTER_DASHBOARD, {
            from: 'faq',
        })
        navigateTo('/dashboard')
        return
    }
    reportEvent(SITE_TRACK_EVENTS.CLICK_LOGIN, {
        from: 'faq',
    })
    redirectToLogin('/dashboard')
}

/**
 * FAQ 展开时上报（折叠不报，避免噪声）
 * @param {Event} event
 * @param {{ question?: string }} item
 * @param {string} group usage / ecosystem
 * @param {number} index
 */
function handleFaqToggle(event, item, group, index) {
    if (!faqToggleReady.value) {
        return
    }
    const detailsElement = event?.target
    if (!detailsElement?.open) {
        return
    }
    reportEvent(SITE_TRACK_EVENTS.EXPAND_FAQ, {
        group: group || '',
        index: Number.isFinite(index) ? index : -1,
        question: item?.question || '',
    })
}
</script>

<style scoped>
.zhiyi-faq {
    width: 100%;
    --zhiyi-faq-text-primary: #0f172a;
    --zhiyi-faq-text-secondary: #64748b;
    --zhiyi-faq-accent: #5d65f9;
    --zhiyi-faq-border: #e2e8f0;
    background: #fff;
}

.zhiyi-faq-hero {
    position: relative;
    overflow: hidden;
    padding: 72px 24px 64px;
}

.zhiyi-faq-hero-layer {
    position: absolute;
    inset: 0;
    pointer-events: none;
}

.zhiyi-faq-hero-layer--gradient {
    background:
        radial-gradient(ellipse 80% 60% at 20% 0%, rgba(93, 101, 249, 0.12), transparent 55%),
        radial-gradient(ellipse 70% 50% at 90% 20%, rgba(93, 101, 249, 0.08), transparent 50%),
        linear-gradient(180deg, #f8fafc 0%, #f5f6f8 100%);
}

.zhiyi-faq-hero-layer--glow {
    background: radial-gradient(circle at 50% 0%, rgba(93, 101, 249, 0.08), transparent 45%);
}

.zhiyi-faq-hero-inner {
    position: relative;
    z-index: 1;
    width: 100%;
    max-width: 720px;
    margin: 0 auto;
    text-align: center;
}

.zhiyi-faq-hero-title {
    margin: 0;
    font-size: 40px;
    font-weight: 700;
    line-height: 1.25;
    letter-spacing: -0.02em;
    color: var(--zhiyi-faq-text-primary);
}

.zhiyi-faq-hero-lead {
    margin: 20px 0 28px;
    font-size: 17px;
    line-height: 1.75;
    color: var(--zhiyi-faq-text-secondary);
    text-align: left;
}

.zhiyi-faq-cta {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 44px;
    padding: 0 28px;
    border: none;
    border-radius: 999px;
    background: var(--zhiyi-faq-accent);
    color: #fff;
    font-size: 15px;
    font-weight: 600;
    cursor: pointer;
    transition: opacity 0.2s ease, transform 0.2s ease;
}

.zhiyi-faq-cta:hover {
    opacity: 0.92;
    transform: translateY(-1px);
}

.zhiyi-faq-body {
    width: 100%;
    max-width: 720px;
    margin: 0 auto;
    padding: 16px 24px 72px;
    box-sizing: border-box;
}

.zhiyi-faq-section {
    margin-bottom: 48px;
}

.zhiyi-faq-section:last-child {
    margin-bottom: 0;
}

.zhiyi-faq-section h2 {
    margin: 0 0 8px;
    font-size: 24px;
    font-weight: 700;
    line-height: 1.35;
    color: var(--zhiyi-faq-text-primary);
}

.zhiyi-faq-section-desc {
    margin: 0 0 20px;
    font-size: 15px;
    line-height: 1.7;
    color: var(--zhiyi-faq-text-secondary);
}

/* 手风琴列表：交互容器，便于展开阅读与 SEO 正文保留 */
.zhiyi-faq-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.zhiyi-faq-item {
    border-bottom: 1px solid var(--zhiyi-faq-border);
}

.zhiyi-faq-item-question {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    padding: 16px 4px;
    font-size: 16px;
    font-weight: 600;
    line-height: 1.5;
    color: var(--zhiyi-faq-text-primary);
    cursor: pointer;
    list-style: none;
    user-select: none;
    transition: color 0.2s ease;
}

.zhiyi-faq-item-question::-webkit-details-marker {
    display: none;
}

.zhiyi-faq-item-question:hover {
    color: var(--zhiyi-faq-accent);
}

.zhiyi-faq-item-icon {
    position: relative;
    flex-shrink: 0;
    width: 20px;
    height: 20px;
    margin-top: 2px;
}

.zhiyi-faq-item-icon::before,
.zhiyi-faq-item-icon::after {
    content: '';
    position: absolute;
    top: 50%;
    left: 50%;
    background: currentColor;
    transition: transform 0.25s ease, opacity 0.2s ease;
}

.zhiyi-faq-item-icon::before {
    width: 12px;
    height: 1.5px;
    transform: translate(-50%, -50%);
}

.zhiyi-faq-item-icon::after {
    width: 1.5px;
    height: 12px;
    transform: translate(-50%, -50%);
}

.zhiyi-faq-item[open] .zhiyi-faq-item-icon::after {
    transform: translate(-50%, -50%) rotate(90deg);
    opacity: 0;
}

.zhiyi-faq-item-answer {
    padding: 0 4px 18px;
    animation: zhiyi-faq-answer-in 0.28s ease;
}

.zhiyi-faq-item-answer p {
    margin: 0;
    font-size: 15px;
    line-height: 1.8;
    color: var(--zhiyi-faq-text-secondary);
}

@keyframes zhiyi-faq-answer-in {
    from {
        opacity: 0;
        transform: translateY(-4px);
    }

    to {
        opacity: 1;
        transform: translateY(0);
    }
}

@media (max-width: 640px) {
    .zhiyi-faq-hero {
        padding: 56px 16px 48px;
    }

    .zhiyi-faq-hero-title {
        font-size: 30px;
    }

    .zhiyi-faq-hero-lead {
        font-size: 15px;
    }

    .zhiyi-faq-body {
        padding: 8px 16px 56px;
    }
}
</style>
