<template>
    <section id="home-integration" class="py-16 md:py-20 bg-gray-50 scroll-mt-16">
        <div class="max-w-layout mx-auto px-6">
            <div class="text-center max-w-2xl mx-auto mb-12">
                <h2 class="text-2xl md:text-3xl font-bold text-gray-900">接入你的 Agent  toolchain</h2>
                <p class="mt-3 text-gray-500 leading-relaxed">
                    无论 Cursor、Copilot 还是自研 Agent，通过 MCP / Memory API 共享同一套 Experience Layer。
                </p>
            </div>

            <div class="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <div
                    v-for="item in integrations"
                    :key="item.name"
                    class="rounded-xl bg-white border border-gray-200 p-5 text-center hover:border-primary/30 transition"
                >
                    <div class="w-12 h-12 mx-auto rounded-xl bg-primary/10 flex items-center justify-center mb-3">
                        <span class="text-lg font-bold text-primary">{{ item.name.charAt(0) }}</span>
                    </div>
                    <h3 class="text-sm font-semibold text-gray-900">{{ item.name }}</h3>
                    <p class="mt-1 text-xs text-gray-500">{{ item.description }}</p>
                </div>
            </div>
        </div>
    </section>
</template>

<script setup>
import { HOME_INTEGRATIONS } from '~/constants/home'

const integrations = HOME_INTEGRATIONS
</script>
