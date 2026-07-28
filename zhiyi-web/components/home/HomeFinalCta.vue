<template>
    <section class="py-16 md:py-20">
        <div class="max-w-layout mx-auto px-6">
            <div class="relative rounded-2xl overflow-hidden bg-gradient-to-br from-primary to-primary-dark px-8 py-14 md:px-16 md:py-16 text-center">
                <div class="absolute inset-0 bg-[url('data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNjAiIGhlaWdodD0iNjAiIHZpZXdCb3g9IjAgMCA2MCA2MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48ZyBmaWxsPSJub25lIiBmaWxsLXJ1bGU9ImV2ZW5vZGQiPjxnIGZpbGw9IiNmZmYiIGZpbGwtb3BhY2l0eT0iMC4wNSI+PGNpcmNsZSBjeD0iMSIgY3k9IjEiIHI9IjEiLz48L2c+PC9nPjwvc3ZnPg==')] opacity-60 pointer-events-none" />
                <div class="relative">
                    <h2 class="text-2xl md:text-3xl font-bold text-white">让经验不再消失</h2>
                    <p class="mt-3 text-white/80 max-w-lg mx-auto leading-relaxed">
                        创建 Workspace，配置 MCP，完成首条 Remember → Review → Recall 闭环。
                    </p>
                    <el-button
                        size="large"
                        round
                        class="mt-8 !bg-white !text-primary !border-white hover:!bg-white/90"
                        @click="handlePrimaryClick"
                    >
                        {{ primaryCtaText }}
                    </el-button>
                </div>
            </div>
        </div>
    </section>
</template>

<script setup>
import { HOME_HERO } from '~/constants/home'

const props = defineProps({
    isLoggedIn: {
        type: Boolean,
        default: false,
    },
})

const emit = defineEmits(['login', 'enter'])

const primaryCtaText = computed(() => (
    props.isLoggedIn ? HOME_HERO.loggedInPrimaryCta : HOME_HERO.primaryCta
))

function handlePrimaryClick() {
    if (props.isLoggedIn) {
        emit('enter')
        return
    }
    emit('login')
}
</script>
