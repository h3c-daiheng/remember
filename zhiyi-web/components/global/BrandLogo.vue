<template>
    <!-- 显式使用 NuxtLink：动态 component :is="'NuxtLink'" 在 Nuxt 中无法正确解析 -->
    <NuxtLink
        v-if="linkTo"
        :to="linkTo"
        :class="rootClass"
    >
        <img
            :src="brandLogoSrc"
            alt="智忆"
            :class="imageClass"
            draggable="false"
        />
        <div v-if="showText || showSubtitle || showCompany" :class="textBlockClass">
            <span v-if="showText" :class="titleClass">{{ productName }}</span>
            <span v-if="showSubtitle" :class="subtitleClass">{{ productEnglishName }}</span>
            <span v-if="showCompany" :class="companyClass">{{ companyName }}</span>
        </div>
    </NuxtLink>
    <div v-else :class="rootClass">
        <img
            :src="brandLogoSrc"
            alt="智忆"
            :class="imageClass"
            draggable="false"
        />
        <div v-if="showText || showSubtitle || showCompany" :class="textBlockClass">
            <span v-if="showText" :class="titleClass">{{ productName }}</span>
            <span v-if="showSubtitle" :class="subtitleClass">{{ productEnglishName }}</span>
            <span v-if="showCompany" :class="companyClass">{{ companyName }}</span>
        </div>
    </div>
</template>

<script setup>
import {
    BRAND_COMPANY_NAME,
    BRAND_PRODUCT_ENGLISH_NAME,
    BRAND_PRODUCT_NAME,
} from '~/constants/brand'

/**
 * 品牌 Logo：导航栏、登录页、首页等统一展示标识
 * - 导航栏：图标 + 「智忆」（show-text）
 * - 登录页：图标 + 「智忆」+ 副标题 + 公司名称（show-text show-subtitle show-company layout="vertical"）
 */
const brandLogoSrc = '/favicon.png'
const productName = BRAND_PRODUCT_NAME
const productEnglishName = BRAND_PRODUCT_ENGLISH_NAME
const companyName = BRAND_COMPANY_NAME

const props = defineProps({
    /** 是否显示品牌主标题「智忆」 */
    showText: { type: Boolean, default: false },
    /** 是否显示英文副标题「AI Experience Cloud」 */
    showSubtitle: { type: Boolean, default: false },
    /** 是否显示出品公司名称（登录页等正式入口） */
    showCompany: { type: Boolean, default: false },
    /** 排列方式：horizontal 导航栏横向 / vertical 登录页与首页纵向居中 */
    layout: {
        type: String,
        default: 'horizontal',
        validator: (value) => ['horizontal', 'vertical'].includes(value),
    },
    /** 图标尺寸：small 导航栏 / large 登录页与首页 */
    size: {
        type: String,
        default: 'small',
        validator: (value) => ['small', 'large'].includes(value),
    },
    /** 点击跳转路径，为空则不包裹链接 */
    linkTo: { type: String, default: '' },
})

/** 根容器样式：链接态、横向/纵向布局 */
const rootClass = computed(() => {
    const layoutClass = props.layout === 'vertical'
        ? 'flex-col items-center text-center gap-2'
        : 'flex-row items-center gap-2'

    const linkClass = props.linkTo
        ? 'no-underline text-gray-900 hover:text-primary transition-colors cursor-pointer'
        : ''

    return ['inline-flex', layoutClass, linkClass].filter(Boolean).join(' ')
})

/** 文字区块：纵向布局时标题与副标题垂直堆叠 */
const textBlockClass = computed(() => (
    props.layout === 'vertical'
        ? 'flex flex-col items-center gap-0.5'
        : 'flex flex-col justify-center gap-0'
))

/** 图标尺寸：导航 32px，登录页与首页 48px */
const imageClass = computed(() => (
    props.size === 'large'
        ? 'w-12 h-12 object-contain shrink-0'
        : 'w-8 h-8 object-contain shrink-0'
))

/** 主标题样式 */
const titleClass = computed(() => (
    props.size === 'large'
        ? 'text-xl font-semibold text-gray-900 leading-tight'
        : 'text-lg font-semibold text-gray-900 leading-tight'
))

/** 英文副标题样式 */
const subtitleClass = computed(() => (
    props.size === 'large'
        ? 'text-xs font-medium tracking-wide text-gray-400 uppercase'
        : 'text-[10px] font-medium tracking-wide text-gray-400 uppercase'
))

/** 公司名称样式：置于副标题下方，弱化展示 */
const companyClass = computed(() => (
    props.size === 'large'
        ? 'text-xs text-gray-400 mt-0.5'
        : 'text-[10px] text-gray-400 mt-0.5'
))
</script>
