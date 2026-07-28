<template>
    <!-- 源码授权落地页：结构对齐画境，视觉沿用智忆 FAQ / 营销页风格 -->
    <div class="zhiyi-source">
        <section class="zhiyi-source-hero"
            aria-labelledby="source-hero-title">
            <div class="zhiyi-source-hero-layer zhiyi-source-hero-layer--gradient"
                aria-hidden="true" />
            <div class="zhiyi-source-hero-layer zhiyi-source-hero-layer--glow"
                aria-hidden="true" />
            <div class="zhiyi-source-hero-inner">
                <span class="zhiyi-source-kicker">{{ heroKicker }}</span>
                <h1 id="source-hero-title"
                    class="zhiyi-source-hero-title">
                    {{ heroTitle }}
                </h1>
                <p class="zhiyi-source-hero-lead">
                    {{ heroSubtitle }}
                </p>
                <div class="zhiyi-source-hero-actions">
                    <NuxtLink class="zhiyi-source-btn zhiyi-source-btn--primary"
                        to="/contact"
                        @click="handleConsultClick('hero')">
                        {{ primaryCta }}
                    </NuxtLink>
                    <NuxtLink class="zhiyi-source-btn zhiyi-source-btn--ghost"
                        to="/"
                        @click="handleExperienceClick">
                        {{ secondaryCta }}
                    </NuxtLink>
                </div>
            </div>
        </section>

        <!-- 授权包含什么 -->
        <section class="zhiyi-source-section"
            aria-labelledby="source-include-title">
            <div class="zhiyi-source-section-inner">
                <h2 id="source-include-title"
                    class="zhiyi-source-section-title">
                    {{ includeSection.title }}
                </h2>
                <p class="zhiyi-source-section-desc">
                    {{ includeSection.description }}
                </p>
                <div class="zhiyi-source-include-list">
                    <article v-for="(item, index) in includeSection.items"
                        :key="item.title"
                        class="zhiyi-source-include-card">
                        <span class="zhiyi-source-include-index"
                            aria-hidden="true">
                            {{ index + 1 }}
                        </span>
                        <div>
                            <h3 class="zhiyi-source-card-title">{{ item.title }}</h3>
                            <p class="zhiyi-source-card-desc">{{ item.description }}</p>
                        </div>
                    </article>
                </div>
            </div>
        </section>

        <!-- 核心能力 -->
        <section class="zhiyi-source-section zhiyi-source-section--muted"
            aria-labelledby="source-capability-title">
            <div class="zhiyi-source-section-inner">
                <h2 id="source-capability-title"
                    class="zhiyi-source-section-title">
                    {{ capabilitySection.title }}
                </h2>
                <p class="zhiyi-source-section-desc">
                    {{ capabilitySection.description }}
                </p>
                <div class="zhiyi-source-capability-grid">
                    <article v-for="item in capabilitySection.items"
                        :key="item.title"
                        class="zhiyi-source-capability-card">
                        <h3 class="zhiyi-source-card-title">{{ item.title }}</h3>
                        <p class="zhiyi-source-card-desc">{{ item.description }}</p>
                    </article>
                </div>
            </div>
        </section>

        <!-- 授权说明 FAQ -->
        <section class="zhiyi-source-section"
            aria-labelledby="source-faq-title">
            <div class="zhiyi-source-section-inner zhiyi-source-section-inner--narrow">
                <h2 id="source-faq-title"
                    class="zhiyi-source-section-title">
                    {{ faqSection.title }}
                </h2>
                <p class="zhiyi-source-section-desc">
                    {{ faqSection.description }}
                </p>
                <div class="zhiyi-source-faq-list">
                    <details v-for="(item, index) in faqSection.items"
                        :key="item.question"
                        class="zhiyi-source-faq-item"
                        :open="index === 0"
                        @toggle="handleFaqToggle($event, item, index)">
                        <summary class="zhiyi-source-faq-question">
                            <span>{{ item.question }}</span>
                            <span class="zhiyi-source-faq-icon"
                                aria-hidden="true" />
                        </summary>
                        <div class="zhiyi-source-faq-answer">
                            <p>{{ item.answer }}</p>
                        </div>
                    </details>
                </div>
            </div>
        </section>

        <!-- 页底 CTA -->
        <section class="zhiyi-source-final"
            aria-labelledby="source-final-title">
            <div class="zhiyi-source-final-inner">
                <h2 id="source-final-title"
                    class="zhiyi-source-final-title">
                    {{ finalCta.title }}
                </h2>
                <p class="zhiyi-source-final-desc">
                    {{ finalCta.description }}
                </p>
                <NuxtLink class="zhiyi-source-btn zhiyi-source-btn--primary"
                    to="/contact"
                    @click="handleConsultClick('final')">
                    {{ finalCta.buttonText }}
                </NuxtLink>
            </div>
        </section>
    </div>
</template>

<script setup>
import { BRAND_PRODUCT_NAME } from '~/constants/brand'
import { getRouterMenuTitleByPath } from '~/config/routerMenus'
import {
    SOURCE_LICENSE_CAPABILITY_SECTION,
    SOURCE_LICENSE_FAQ_SECTION,
    SOURCE_LICENSE_FINAL_CTA,
    SOURCE_LICENSE_HERO_KICKER,
    SOURCE_LICENSE_HERO_SUBTITLE,
    SOURCE_LICENSE_HERO_TITLE,
    SOURCE_LICENSE_INCLUDE_SECTION,
    SOURCE_LICENSE_PAGE_DESCRIPTION,
    SOURCE_LICENSE_PAGE_KEYWORDS,
    SOURCE_LICENSE_PRIMARY_CTA,
    SOURCE_LICENSE_SECONDARY_CTA,
} from '~/config/sourceLicense'
import { SITE_TRACK_EVENTS } from '~/config/tracker'

definePageMeta({
    layout: 'default',
})

const pageTitle = getRouterMenuTitleByPath('/source') || '源码授权'

useHead({
    title: pageTitle,
    meta: [
        { name: 'description', content: SOURCE_LICENSE_PAGE_DESCRIPTION },
        { name: 'keywords', content: SOURCE_LICENSE_PAGE_KEYWORDS },
    ],
})

/** 页面浏览埋点 */
const { reportEvent } = usePageTracker()

const heroKicker = SOURCE_LICENSE_HERO_KICKER
const heroTitle = SOURCE_LICENSE_HERO_TITLE
const heroSubtitle = SOURCE_LICENSE_HERO_SUBTITLE
const primaryCta = SOURCE_LICENSE_PRIMARY_CTA
const secondaryCta = SOURCE_LICENSE_SECONDARY_CTA
const includeSection = SOURCE_LICENSE_INCLUDE_SECTION
const capabilitySection = SOURCE_LICENSE_CAPABILITY_SECTION
const faqSection = SOURCE_LICENSE_FAQ_SECTION
const finalCta = SOURCE_LICENSE_FINAL_CTA

/** 挂载完成后再接受 FAQ toggle，避免首条默认 open 误报 */
const faqToggleReady = ref(false)

onMounted(() => {
    nextTick(() => {
        faqToggleReady.value = true
    })
})

/** 咨询授权：跳转联系我们并上报 */
function handleConsultClick(from) {
    reportEvent(SITE_TRACK_EVENTS.CLICK_SOURCE_LICENSE, {
        action: 'consult',
        from: from || '',
        brand: BRAND_PRODUCT_NAME,
    })
}

/** 在线体验：回首页并上报 */
function handleExperienceClick() {
    reportEvent(SITE_TRACK_EVENTS.CLICK_SOURCE_LICENSE, {
        action: 'experience',
        from: 'hero',
        brand: BRAND_PRODUCT_NAME,
    })
}

/**
 * FAQ 展开时上报（折叠不报）
 * @param {Event} event
 * @param {{ question?: string }} item
 * @param {number} index
 */
function handleFaqToggle(event, item, index) {
    if (!faqToggleReady.value) {
        return
    }
    const detailsElement = event?.target
    if (!detailsElement?.open) {
        return
    }
    reportEvent(SITE_TRACK_EVENTS.EXPAND_FAQ, {
        group: 'source-license',
        index: Number.isFinite(index) ? index : -1,
        question: item?.question || '',
    })
}
</script>

<style scoped>
.zhiyi-source {
    width: 100%;
    --zhiyi-source-text-primary: #0f172a;
    --zhiyi-source-text-secondary: #64748b;
    --zhiyi-source-accent: #5d65f9;
    --zhiyi-source-border: #e2e8f0;
    background: #fff;
}

.zhiyi-source-hero {
    position: relative;
    overflow: hidden;
    padding: 72px 24px 64px;
}

.zhiyi-source-hero-layer {
    position: absolute;
    inset: 0;
    pointer-events: none;
}

.zhiyi-source-hero-layer--gradient {
    background:
        radial-gradient(ellipse 80% 60% at 20% 0%, rgba(93, 101, 249, 0.12), transparent 55%),
        radial-gradient(ellipse 70% 50% at 90% 20%, rgba(93, 101, 249, 0.08), transparent 50%),
        linear-gradient(180deg, #f8fafc 0%, #ffffff 100%);
}

.zhiyi-source-hero-layer--glow {
    background: radial-gradient(circle at 50% 0%, rgba(93, 101, 249, 0.08), transparent 45%);
}

.zhiyi-source-hero-inner {
    position: relative;
    z-index: 1;
    width: 100%;
    max-width: 720px;
    margin: 0 auto;
    text-align: center;
}

.zhiyi-source-kicker {
    display: inline-flex;
    align-items: center;
    padding: 4px 12px;
    border-radius: 999px;
    background: rgba(93, 101, 249, 0.1);
    color: var(--zhiyi-source-accent);
    font-size: 12px;
    font-weight: 600;
}

.zhiyi-source-hero-title {
    margin: 16px 0 0;
    font-size: 40px;
    font-weight: 700;
    line-height: 1.25;
    letter-spacing: -0.02em;
    color: var(--zhiyi-source-text-primary);
}

.zhiyi-source-hero-lead {
    margin: 20px 0 0;
    font-size: 17px;
    line-height: 1.75;
    color: var(--zhiyi-source-text-secondary);
}

.zhiyi-source-hero-actions {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: center;
    gap: 12px;
    margin-top: 28px;
}

.zhiyi-source-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 44px;
    padding: 0 28px;
    border-radius: 999px;
    font-size: 15px;
    font-weight: 600;
    text-decoration: none;
    transition: opacity 0.2s ease, transform 0.2s ease, background 0.2s ease;
}

.zhiyi-source-btn--primary {
    border: none;
    background: var(--zhiyi-source-accent);
    color: #fff;
    box-shadow: 0 8px 20px rgba(93, 101, 249, 0.22);
}

.zhiyi-source-btn--primary:hover {
    opacity: 0.92;
    transform: translateY(-1px);
}

.zhiyi-source-btn--ghost {
    border: 1px solid var(--zhiyi-source-border);
    background: #fff;
    color: #475569;
}

.zhiyi-source-btn--ghost:hover {
    border-color: rgba(93, 101, 249, 0.35);
    color: var(--zhiyi-source-accent);
}

.zhiyi-source-section {
    padding: 64px 24px;
}

.zhiyi-source-section--muted {
    background: #f8fafc;
}

.zhiyi-source-section-inner {
    width: 100%;
    max-width: 960px;
    margin: 0 auto;
}

.zhiyi-source-section-inner--narrow {
    max-width: 720px;
}

.zhiyi-source-section-title {
    margin: 0;
    font-size: 28px;
    font-weight: 700;
    line-height: 1.3;
    color: var(--zhiyi-source-text-primary);
    text-align: center;
}

.zhiyi-source-section-desc {
    margin: 12px 0 0;
    font-size: 16px;
    line-height: 1.7;
    color: var(--zhiyi-source-text-secondary);
    text-align: center;
}

.zhiyi-source-include-list {
    display: grid;
    gap: 16px;
    margin-top: 32px;
}

.zhiyi-source-include-card {
    display: flex;
    gap: 16px;
    align-items: flex-start;
    padding: 20px 22px;
    border: 1px solid var(--zhiyi-source-border);
    border-radius: 16px;
    background: #fff;
}

.zhiyi-source-include-index {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border-radius: 999px;
    background: rgba(93, 101, 249, 0.1);
    color: var(--zhiyi-source-accent);
    font-size: 14px;
    font-weight: 700;
    flex-shrink: 0;
}

.zhiyi-source-card-title {
    margin: 0;
    font-size: 17px;
    font-weight: 650;
    color: var(--zhiyi-source-text-primary);
}

.zhiyi-source-card-desc {
    margin: 8px 0 0;
    font-size: 14px;
    line-height: 1.7;
    color: var(--zhiyi-source-text-secondary);
}

.zhiyi-source-capability-grid {
    display: grid;
    gap: 16px;
    margin-top: 32px;
    grid-template-columns: repeat(2, minmax(0, 1fr));
}

.zhiyi-source-capability-card {
    padding: 20px 22px;
    border: 1px solid var(--zhiyi-source-border);
    border-radius: 16px;
    background: #fff;
}

.zhiyi-source-faq-list {
    display: grid;
    gap: 12px;
    margin-top: 28px;
}

.zhiyi-source-faq-item {
    border: 1px solid var(--zhiyi-source-border);
    border-radius: 14px;
    background: #fff;
    overflow: hidden;
}

.zhiyi-source-faq-question {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 16px 18px;
    cursor: pointer;
    list-style: none;
    font-size: 15px;
    font-weight: 600;
    color: var(--zhiyi-source-text-primary);
}

.zhiyi-source-faq-question::-webkit-details-marker {
    display: none;
}

.zhiyi-source-faq-icon {
    width: 10px;
    height: 10px;
    border-right: 2px solid #94a3b8;
    border-bottom: 2px solid #94a3b8;
    transform: rotate(45deg);
    transition: transform 0.2s ease;
    flex-shrink: 0;
}

.zhiyi-source-faq-item[open] .zhiyi-source-faq-icon {
    transform: rotate(225deg);
    margin-top: 4px;
}

.zhiyi-source-faq-answer {
    padding: 0 18px 16px;
}

.zhiyi-source-faq-answer p {
    margin: 0;
    font-size: 14px;
    line-height: 1.75;
    color: var(--zhiyi-source-text-secondary);
}

.zhiyi-source-final {
    padding: 72px 24px 88px;
    background:
        radial-gradient(ellipse 70% 60% at 50% 0%, rgba(93, 101, 249, 0.1), transparent 60%),
        #f8fafc;
    text-align: center;
}

.zhiyi-source-final-inner {
    max-width: 640px;
    margin: 0 auto;
}

.zhiyi-source-final-title {
    margin: 0;
    font-size: 28px;
    font-weight: 700;
    color: var(--zhiyi-source-text-primary);
}

.zhiyi-source-final-desc {
    margin: 14px 0 28px;
    font-size: 16px;
    line-height: 1.7;
    color: var(--zhiyi-source-text-secondary);
}

@media (max-width: 720px) {
    .zhiyi-source-hero-title {
        font-size: 30px;
    }

    .zhiyi-source-capability-grid {
        grid-template-columns: 1fr;
    }

    .zhiyi-source-section-title,
    .zhiyi-source-final-title {
        font-size: 24px;
    }
}
</style>
