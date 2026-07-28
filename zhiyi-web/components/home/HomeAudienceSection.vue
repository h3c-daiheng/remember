<template>
    <section id="home-audience" class="py-16 md:py-20 bg-gray-50 scroll-mt-16">
        <div class="max-w-layout mx-auto px-6">
            <div class="text-center max-w-2xl mx-auto mb-12">
                <h2 class="text-2xl md:text-3xl font-bold text-gray-900">为谁而建</h2>
                <p class="mt-3 text-gray-500 leading-relaxed">
                    先 B 后 C：团队场景跑通 Review 闭环，公开经验池自然生长为 AI 时代的经验社区。
                </p>
            </div>

            <div class="grid md:grid-cols-3 gap-5">
                <div
                    v-for="audience in audiences"
                    :key="audience.title"
                    :class="[
                        'rounded-xl p-6 border transition',
                        audience.highlight
                            ? 'bg-primary text-white border-primary shadow-lg shadow-primary/20'
                            : 'bg-white border-gray-200 hover:border-primary/20',
                    ]"
                >
                    <h3
                        :class="[
                            'text-base font-semibold mb-2',
                            audience.highlight ? 'text-white' : 'text-gray-900',
                        ]"
                    >
                        {{ audience.title }}
                    </h3>
                    <p
                        :class="[
                            'text-sm leading-relaxed',
                            audience.highlight ? 'text-white/80' : 'text-gray-500',
                        ]"
                    >
                        {{ audience.description }}
                    </p>
                </div>
            </div>
        </div>
    </section>
</template>

<script setup>
import { HOME_AUDIENCES } from '~/constants/home'

const audiences = HOME_AUDIENCES
</script>
