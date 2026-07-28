<template>
    <div class="zhiyi-about">
        <div class="zhiyi-about-body">
            <!-- 字典整篇 Markdown：MdPreview 只读，沿用编辑器默认排版 -->
            <div v-if="hasAboutContent"
                class="zhiyi-about-markdown"
                aria-label="关于我们">
                <SiteMdPreview v-model="aboutMarkdown"
                    editor-id="site-about-md-zhiyi" />
            </div>

            <p v-else
                class="zhiyi-about-empty"
                aria-live="polite">
                内容加载失败，请稍后重试
            </p>
        </div>
    </div>
</template>

<script setup>
import configApi from '~/api/configApi'
import { getRouterMenuTitleByPath } from '~/config/routerMenus'
import { fetchSiteAboutPage } from '~/utils/siteAboutContactDict'

definePageMeta({
    layout: 'default',
})

const pageTitle = getRouterMenuTitleByPath('/about') || '关于我们'

/**
 * SSR 拉取关于我们字典；页面正文仅使用 content Markdown。
 * 使用 useSsrAsyncData：SSR 失败返回 null 时不把空缓存当成有效 payload，水合后客户端可重拉。
 */
const { data: aboutPageFromDict } = await useSsrAsyncData(
    'site-about-page-dict-zhiyi',
    () => fetchSiteAboutPage(configApi),
    { default: () => null },
)

const hasAboutContent = computed(() => Boolean(aboutPageFromDict.value?.content))
const aboutMarkdown = ref(aboutPageFromDict.value?.content || '')

watch(
    () => aboutPageFromDict.value?.content,
    (content) => {
        if (content) {
            aboutMarkdown.value = content
        }
    },
)

const seoDescription = aboutPageFromDict.value?.description ?? ''
const seoKeywords = aboutPageFromDict.value?.keywords ?? ''

useHead({
    title: aboutPageFromDict.value?.title || pageTitle,
    meta: [
        { name: 'description', content: seoDescription },
        { name: 'keywords', content: seoKeywords },
    ],
})
</script>
