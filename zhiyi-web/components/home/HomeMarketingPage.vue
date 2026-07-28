<template>
    <!-- 未登录营销落地页：自上而下讲述产品价值 -->
    <div class="bg-white">
        <HomeHeroSection
            :is-logged-in="isLoggedIn"
            @login="emit('login')"
            @enter="emit('enter')"
        />
        <HomePainSection />
        <HomeFlywheelSection />
        <HomeFeatureGrid />
        <HomeAudienceSection />
        <HomePlatformCompare />
        <HomeIntegrationSection />
        <HomeSourceLicenseSection />
        <HomeFinalCta
            :is-logged-in="isLoggedIn"
            @login="emit('login')"
            @enter="emit('enter')"
        />
    </div>
</template>

<script setup>
defineProps({
    /** 已登录时在 Hero / CTA 展示「进入工作台」 */
    isLoggedIn: {
        type: Boolean,
        default: false,
    },
})

const emit = defineEmits(['login', 'enter'])
</script>
