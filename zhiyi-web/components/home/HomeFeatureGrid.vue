<template>
    <section id="home-features" class="py-16 md:py-20 bg-white scroll-mt-16">
        <div class="max-w-layout mx-auto px-6">
            <div class="text-center max-w-2xl mx-auto mb-12">
                <h2 class="text-2xl md:text-3xl font-bold text-gray-900">核心能力</h2>
                <p class="mt-3 text-gray-500 leading-relaxed">
                    AEC 不管理文档，管理 Experience。为 AI Agent 与企业之间的统一经验层而生。
                </p>
            </div>

            <div class="grid sm:grid-cols-2 lg:grid-cols-3 gap-5">
                <div
                    v-for="feature in features"
                    :key="feature.title"
                    class="group rounded-xl border border-gray-200 p-5 hover:border-primary/30 hover:shadow-md hover:shadow-primary/5 transition"
                >
                    <span class="inline-block text-[11px] font-medium text-primary bg-primary/10 px-2 py-0.5 rounded mb-3">
                        {{ feature.tag }}
                    </span>
                    <h3 class="text-base font-semibold text-gray-900 group-hover:text-primary transition-colors">
                        {{ feature.title }}
                    </h3>
                    <p class="mt-2 text-sm text-gray-500 leading-relaxed">{{ feature.description }}</p>
                </div>
            </div>
        </div>
    </section>
</template>

<script setup>
import { HOME_FEATURES } from '~/constants/home'

const features = HOME_FEATURES
</script>
