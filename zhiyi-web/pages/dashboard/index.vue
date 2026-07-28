<template>
    <HomeDashboard :current-user="currentUser" />
</template>

<script setup>
definePageMeta({
    layout: 'app',
})

useHead({
    title: '工作台',
})

/** 页面浏览埋点 */
usePageTracker()

const { currentUser } = useAuth()
</script>
