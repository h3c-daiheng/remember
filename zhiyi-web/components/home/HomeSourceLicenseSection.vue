<template>
    <!-- 首页源码授权入口：引导企业/个人开发者了解私有化与二次开发 -->
    <section :id="sectionId"
        class="py-16 md:py-20 bg-white scroll-mt-16"
        aria-labelledby="home-source-license-title">
        <div class="max-w-layout mx-auto px-6">
            <div class="rounded-2xl border border-gray-200 bg-gradient-to-br from-primary/5 via-white to-white px-6 py-10 md:px-10 md:py-12 text-center">
                <span class="inline-flex items-center px-3 py-1 rounded-full text-xs font-medium bg-primary/10 text-primary mb-4">
                    {{ section.kicker }}
                </span>
                <h2 id="home-source-license-title"
                    class="text-2xl md:text-3xl font-bold text-gray-900">
                    {{ section.title }}
                </h2>
                <p class="mt-3 text-gray-500 leading-relaxed max-w-2xl mx-auto">
                    {{ section.description }}
                </p>
                <div class="mt-8 flex flex-col sm:flex-row items-center justify-center gap-3">
                    <NuxtLink :to="sourcePath"
                        class="inline-flex"
                        @click="handlePrimaryClick">
                        <el-button type="primary"
                            size="large"
                            round>
                            {{ section.primaryCta }}
                        </el-button>
                    </NuxtLink>
                    <NuxtLink to="/contact"
                        class="inline-flex"
                        @click="handleSecondaryClick">
                        <el-button size="large"
                            round>
                            {{ section.secondaryCta }}
                        </el-button>
                    </NuxtLink>
                </div>
            </div>
        </div>
    </section>
</template>

<script setup>
import {
    HOME_SOURCE_LICENSE_SECTION,
    HOME_SOURCE_LICENSE_SECTION_ID,
    SOURCE_LICENSE_PATH,
} from '~/config/sourceLicense'
import { SITE_TRACK_EVENTS } from '~/config/tracker'

const section = HOME_SOURCE_LICENSE_SECTION
const sectionId = HOME_SOURCE_LICENSE_SECTION_ID
const sourcePath = SOURCE_LICENSE_PATH
const { reportEvent } = useTracker()

/** 了解源码授权 */
function handlePrimaryClick() {
    reportEvent(SITE_TRACK_EVENTS.CLICK_SOURCE_LICENSE, {
        action: 'open-page',
        from: 'home',
    })
}

/** 联系我们 */
function handleSecondaryClick() {
    reportEvent(SITE_TRACK_EVENTS.CLICK_SOURCE_LICENSE, {
        action: 'contact',
        from: 'home',
    })
}
</script>
