<template>
    <section id="home-pain" class="py-16 md:py-20 bg-white scroll-mt-16">
        <div class="max-w-layout mx-auto px-6">
            <div class="text-center max-w-2xl mx-auto mb-12">
                <h2 class="text-2xl md:text-3xl font-bold text-gray-900">AI 能写代码，但不会积累经验</h2>
                <p class="mt-3 text-gray-500 leading-relaxed">
                    团队已有 Git、Wiki、Notion，但 Agent 仍然不知道「为什么这样设计」「这个 Bug 曾经怎么修」——真正缺失的是 Experience。
                </p>
            </div>

            <div class="grid md:grid-cols-3 gap-6">
                <div
                    v-for="point in painPoints"
                    :key="point.title"
                    class="rounded-xl border border-gray-200 p-6 hover:border-primary/20 hover:shadow-sm transition"
                >
                    <div class="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center mb-4">
                        <el-icon :size="20" class="text-primary">
                            <component :is="iconMap[point.icon]" />
                        </el-icon>
                    </div>
                    <h3 class="text-base font-semibold text-gray-900 mb-2">{{ point.title }}</h3>
                    <p class="text-sm text-gray-500 leading-relaxed">{{ point.description }}</p>
                </div>
            </div>
        </div>
    </section>
</template>

<script setup>
import { Document, Refresh, User } from '@element-plus/icons-vue'
import { HOME_PAIN_POINTS } from '~/constants/home'

const painPoints = HOME_PAIN_POINTS

/** 痛点卡片图标映射 */
const iconMap = {
    Refresh,
    Document,
    User,
}
</script>
