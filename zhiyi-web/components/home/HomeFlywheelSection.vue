<template>
    <section id="home-flywheel" class="py-16 md:py-20 bg-gray-50 scroll-mt-16">
        <div class="max-w-layout mx-auto px-6">
            <div class="text-center max-w-2xl mx-auto mb-12">
                <h2 class="text-2xl md:text-3xl font-bold text-gray-900">经验飞轮</h2>
                <p class="mt-3 text-gray-500 leading-relaxed">
                    不是又一个知识库，而是「采集 → 确认 → 召回 → 进化」的闭环。经验越积越多，Agent 越用越准。
                </p>
            </div>

            <div class="grid sm:grid-cols-2 lg:grid-cols-4 gap-5">
                <div
                    v-for="(step, index) in steps"
                    :key="step.step"
                    class="relative rounded-xl bg-white border border-gray-200 p-5"
                >
                    <span class="text-2xl font-bold text-primary/20">{{ step.step }}</span>
                    <h3 class="mt-2 text-base font-semibold text-gray-900">{{ step.title }}</h3>
                    <p class="mt-2 text-sm text-gray-500 leading-relaxed">{{ step.description }}</p>
                    <!-- 步骤间箭头（大屏） -->
                    <div
                        v-if="index < steps.length - 1"
                        class="hidden lg:block absolute top-1/2 -right-3 w-6 text-gray-300 text-lg leading-none -translate-y-1/2"
                    >
                        →
                    </div>
                </div>
            </div>

            <!-- 飞轮循环示意 -->
            <div class="mt-10 flex justify-center">
                <div class="inline-flex items-center gap-2 text-sm text-gray-400 bg-white border border-gray-200 rounded-full px-5 py-2.5">
                    <span class="w-2 h-2 rounded-full bg-primary animate-pulse" />
                    Agent 完成任务 → 产生更多经验 → 循环
                </div>
            </div>
        </div>
    </section>
</template>

<script setup>
import { HOME_FLYWHEEL_STEPS } from '~/constants/home'

const steps = HOME_FLYWHEEL_STEPS
</script>
