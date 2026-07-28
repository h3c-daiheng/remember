<template>
    <div class="zhiyi-contact">
        <div class="zhiyi-contact-body">
            <!-- 字典整篇 Markdown：MdPreview 只读，沿用编辑器默认排版 -->
            <div v-if="hasContactContent"
                class="zhiyi-contact-markdown"
                aria-label="联系我们">
                <SiteMdPreview v-model="contactMarkdown"
                    editor-id="site-contact-md-zhiyi" />
            </div>

            <p v-else
                class="zhiyi-contact-empty"
                aria-live="polite">
                内容加载失败，请稍后重试
            </p>
        </div>
    </div>
</template>

<script setup>
import configApi from '~/api/configApi'
import { getRouterMenuTitleByPath } from '~/config/routerMenus'
import { fetchSiteContactPage } from '~/utils/siteAboutContactDict'

definePageMeta({
    layout: 'default',
})

const pageTitle = getRouterMenuTitleByPath('/contact') || '联系我们'

/**
 * SSR 拉取联系我们字典；页面正文仅使用 content Markdown。
 * 使用 useSsrAsyncData：SSR 失败返回 null 时不把空缓存当成有效 payload，水合后客户端可重拉。
 */
const { data: contactPageFromDict } = await useSsrAsyncData(
    'site-contact-page-dict-zhiyi',
    () => fetchSiteContactPage(configApi),
    { default: () => null },
)

const hasContactContent = computed(() => Boolean(contactPageFromDict.value?.content))
const contactMarkdown = ref(contactPageFromDict.value?.content || '')

watch(
    () => contactPageFromDict.value?.content,
    (content) => {
        if (content) {
            contactMarkdown.value = content
        }
    },
)

const seoDescription = contactPageFromDict.value?.description ?? ''
const seoKeywords = contactPageFromDict.value?.keywords ?? ''

useHead({
    title: contactPageFromDict.value?.title || pageTitle,
    meta: [
        { name: 'description', content: seoDescription },
        { name: 'keywords', content: seoKeywords },
    ],
})
</script>
