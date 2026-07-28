<template>
    <section id="home-platform-compare" class="py-16 md:py-20 bg-white scroll-mt-16">
        <div class="max-w-layout mx-auto px-6">
            <div class="text-center max-w-2xl mx-auto mb-12">
                <h2 class="text-2xl md:text-3xl font-bold text-gray-900">不是 AI 版知乎</h2>
                <p class="mt-3 text-gray-500 leading-relaxed">
                    CSDN、知乎是为人阅读时代设计的。智忆是 Agent 与人类共同消费的经验网络。
                </p>
            </div>

            <div class="rounded-xl border border-gray-200 overflow-hidden">
                <!-- 表头 -->
                <div class="grid grid-cols-3 bg-gray-50 text-sm font-medium text-gray-500 border-b border-gray-200">
                    <div class="px-5 py-3">对比维度</div>
                    <div class="px-5 py-3">{{ compare.oldLabel }}</div>
                    <div class="px-5 py-3 text-primary">{{ compare.newLabel }}</div>
                </div>
                <!-- 对比行 -->
                <div
                    v-for="(row, index) in compare.rows"
                    :key="row.dimension"
                    :class="[
                        'grid grid-cols-3 text-sm border-b border-gray-100 last:border-b-0',
                        index % 2 === 0 ? 'bg-white' : 'bg-gray-50/50',
                    ]"
                >
                    <div class="px-5 py-4 font-medium text-gray-700">{{ row.dimension }}</div>
                    <div class="px-5 py-4 text-gray-400">{{ row.old }}</div>
                    <div class="px-5 py-4 text-gray-800 font-medium">{{ row.new }}</div>
                </div>
            </div>
        </div>
    </section>
</template>

<script setup>
import { HOME_PLATFORM_COMPARE } from '~/constants/home'

const compare = HOME_PLATFORM_COMPARE
</script>
