<template>
    <section class="relative overflow-hidden">
        <!-- 品牌色渐变背景 -->
        <div class="absolute inset-0 bg-gradient-to-b from-primary/5 via-white to-white pointer-events-none" />
        <div class="absolute -top-24 -right-24 w-96 h-96 rounded-full bg-primary/10 blur-3xl pointer-events-none" />
        <div class="absolute top-1/2 -left-32 w-72 h-72 rounded-full bg-accent/10 blur-3xl pointer-events-none" />

        <div class="relative max-w-layout mx-auto px-6 pt-16 pb-20 md:pt-24 md:pb-28">
            <div class="grid lg:grid-cols-2 gap-12 lg:gap-16 items-center">
                <!-- 左侧文案 -->
                <div class="text-center lg:text-left">
                    <span class="inline-flex items-center px-3 py-1 rounded-full text-xs font-medium bg-primary/10 text-primary mb-6">
                        {{ hero.badge }}
                    </span>
                    <h1 class="text-3xl md:text-4xl lg:text-[2.75rem] font-bold text-gray-900 leading-tight tracking-tight">
                        {{ hero.title }}
                    </h1>
                    <p class="mt-5 text-base md:text-lg text-gray-500 leading-relaxed max-w-xl mx-auto lg:mx-0">
                        {{ hero.subtitle }}
                    </p>
                    <div class="mt-8 flex flex-col sm:flex-row items-center justify-center lg:justify-start gap-3">
                        <el-button type="primary" size="large" round @click="handlePrimaryClick">
                            {{ primaryCtaText }}
                        </el-button>
                        <el-button size="large" round @click="scrollToFlywheel">
                            {{ hero.secondaryCta }}
                        </el-button>
                    </div>
                    <p class="mt-6 text-xs text-gray-400">
                        {{ flywheelText }}
                    </p>
                </div>

                <!-- 右侧：结构化经验卡示意 -->
                <div class="relative mx-auto w-full max-w-md lg:max-w-none">
                    <div class="rounded-2xl border border-gray-200 bg-white shadow-xl shadow-primary/5 p-6 md:p-7">
                        <div class="flex items-center justify-between mb-5">
                            <span class="text-xs font-medium text-primary bg-primary/10 px-2.5 py-1 rounded-md">
                                Experience
                            </span>
                            <span class="text-xs text-gray-400">Redis 缓存穿透修复</span>
                        </div>
                        <div class="space-y-4">
                            <div v-for="field in previewFields" :key="field.label" class="rounded-lg bg-gray-50 px-4 py-3">
                                <p class="text-[11px] font-medium text-gray-400 uppercase tracking-wide mb-1">
                                    {{ field.label }}
                                </p>
                                <p class="text-sm text-gray-700 leading-relaxed">{{ field.content }}</p>
                            </div>
                        </div>
                        <div class="mt-5 flex items-center justify-between pt-4 border-t border-gray-100">
                            <div class="flex items-center gap-2">
                                <span class="w-2 h-2 rounded-full bg-green-400" />
                                <span class="text-xs text-gray-500">Human Verified</span>
                            </div>
                            <span class="text-xs text-gray-400">Agent Recall × 128</span>
                        </div>
                    </div>
                    <!-- 装饰：Recall 连线示意 -->
                    <div class="hidden md:block absolute -bottom-4 -left-6 rounded-xl border border-dashed border-primary/30 bg-white/80 backdrop-blur px-4 py-2.5 text-xs text-gray-500 shadow-sm">
                        <span class="text-primary font-medium">Recall</span>
                        改支付模块前自动读取相关经验
                    </div>
                </div>
            </div>
        </div>
    </section>
</template>

<script setup>
import { HOME_HERO } from '~/constants/home'
import { PRODUCT_FLYWHEEL_TEXT } from '~/constants/terminology'

const props = defineProps({
    /** 已登录时主按钮文案切换为「进入工作台」 */
    isLoggedIn: {
        type: Boolean,
        default: false,
    },
})

const emit = defineEmits(['login', 'enter'])

const hero = HOME_HERO
const flywheelText = PRODUCT_FLYWHEEL_TEXT

/** 主按钮文案：未登录引导注册，已登录进入工作台 */
const primaryCtaText = computed(() => (
    props.isLoggedIn ? hero.loggedInPrimaryCta : hero.primaryCta
))

/** 主按钮点击：已登录进入工作台，未登录跳转登录 */
function handlePrimaryClick() {
    if (props.isLoggedIn) {
        emit('enter')
        return
    }
    emit('login')
}

/** 首页 Hero 右侧经验卡预览字段，展示结构化 Experience 形态 */
const previewFields = [
    {
        label: '背景',
        content: '大促期间 Redis 缓存穿透导致 DB 压力飙升，接口 P99 超过 3s。',
    },
    {
        label: '为什么',
        content: '布隆过滤器误判率过高；团队曾讨论过本地缓存 + 空值缓存方案。',
    },
    {
        label: '方案',
        content: '热点 Key 本地缓存 30s + 空值缓存 5min，穿透请求降级走只读副本。',
    },
]

const { scrollToSection } = useHomeAnchorScroll()

/** 平滑滚动到飞轮区块 */
function scrollToFlywheel() {
    scrollToSection('home-flywheel')
}
</script>
