<template>
    <!-- 顶栏用户头像：有 URL 时展示图片，否则回退昵称/用户名首字（对齐 imagehub 28px 圆形头像） -->
    <el-avatar
        :size="size"
        :src="avatarSource"
        class="layout-user-avatar shrink-0"
    >
        {{ nameInitial }}
    </el-avatar>
</template>

<script setup>
const props = defineProps({
    /** 当前登录用户摘要 */
    user: {
        type: Object,
        default: null,
    },
    /** 头像直径，默认与顶栏一致 */
    size: {
        type: Number,
        default: 28,
    },
})

/** 头像图片地址；空字符串视为未设置，走首字回退 */
const avatarSource = computed(() => props.user?.avatar || undefined)

/** 无头像时展示的首字，优先昵称再用户名，小写以贴近主站默认样式 */
const nameInitial = computed(() => {
    const displayName = props.user?.nickname || props.user?.username || '用'
    const initial = String(displayName).trim().slice(0, 1).toLowerCase()
    return initial || '用'
})
</script>
