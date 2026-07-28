// 智忆 Web 应用 Nuxt 配置：集成 Element Plus、WindiCSS，登录/注册走 gonline 统一账号
// 43xx 端口规划：本地 430x / 测试 431x / 生产 432x；前端奇数、后端偶数
import {
    BRAND_COMPANY_NAME,
    BRAND_PRODUCT_ENGLISH_NAME,
    BRAND_PRODUCT_NAME,
} from './constants/brand.js'

const WEB_DEV_PORT = 4301
const SERVER_DEV_PORT = 4302

// 工厂导出：避免 dev 热重启时复用被 createPortalProperties 标记为不可配置的 config（与 chat2x-web 一致）
export default () => defineNuxtConfig({
    compatibilityDate: '2025-03-07',

    devtools: {
        enabled: false,
    },

    modules: [
        'nuxt-windicss',
        '@element-plus/nuxt',
    ],

    // Element Plus 使用 SCSS 编译期主题，保证主色按钮等为智忆品牌色 #5d65f9
    elementPlus: {
        importStyle: 'scss',
    },

    css: [
        '~/assets/css/main.css',
        '~/assets/css/element-plus-theme.css',
        '~/assets/css/about-contact.css',
    ],

    app: {
        head: {
            title: BRAND_PRODUCT_NAME,
            titleTemplate: `%s · ${BRAND_PRODUCT_NAME}`,
            charset: 'utf-8',
            meta: [
                { name: 'viewport', content: 'width=device-width, initial-scale=1' },
                {
                    name: 'description',
                    content: `${BRAND_PRODUCT_NAME} — AI 时代的经验基础设施，把真实做过的事沉淀成可被人和 AI 持续复用的经验。`,
                },
                { name: 'author', content: BRAND_COMPANY_NAME },
                {
                    name: 'copyright',
                    content: `${BRAND_COMPANY_NAME} · ${BRAND_PRODUCT_NAME} · ${BRAND_PRODUCT_ENGLISH_NAME}`,
                },
                { name: 'theme-color', content: '#5d65f9' },
            ],
            link: [
                { rel: 'icon', type: 'image/x-icon', href: '/favicon.ico' },
                { rel: 'icon', type: 'image/png', sizes: '512x512', href: '/favicon.png' },
            ],
        },
    },

    devServer: {
        host: '0.0.0.0',
        port: WEB_DEV_PORT,
    },

    vite: {
        server: {
            host: '0.0.0.0',
            port: WEB_DEV_PORT,
            // 允许本地 hosts 联调域名（如 zhiyi.local.example.com）访问 Vite 开发服务
            allowedHosts: ['.local.example.com'],
        },
        css: {
            preprocessorOptions: {
                scss: {
                    additionalData: '@use "~/assets/scss/element-plus.scss" as *;',
                },
            },
        },
        optimizeDeps: {
            include: ['element-plus/es', '@element-plus/icons-vue'],
        },
    },

    runtimeConfig: {
        // SSR/dev 经 server/routes/gonline-api/[...].js 转发到 gonline（无 /gonline-api 前缀）；
        // 见 .env.* 与部署注入的 NUXT_GONLINE_API_SERVER_TARGET（宿主机或内网可达地址，勿用容器内 127.0.0.1）
        gonlineApiServerTarget: process.env.NUXT_GONLINE_API_SERVER_TARGET,
        public: {
            // 浏览器侧 /api 前缀，由 nginx 或 devProxy 转发时去掉后再访问 zhiyi-server
            apiBase: '/api',
            // 巨人肩膀 gonline API 同源前缀；SSR 走 Nitro 代理，CSR 走 nginx / 同源转发
            gonlineApiBase: '/gonline-api',
            // 巨人肩膀 Web 登录页 origin；须 NUXT_PUBLIC_GONLINE_WEB_ORIGIN（.env.development）
            gonlineWebOrigin: process.env.NUXT_PUBLIC_GONLINE_WEB_ORIGIN || '',
            // 跨子域 SSO Cookie 父域，须与 gonline-ssr NUXT_PUBLIC_COOKIE_DOMAIN 一致
            cookieDomain: process.env.NUXT_PUBLIC_COOKIE_DOMAIN || '',
        },
    },

    nitro: {
        devProxy: {
            // /api 仅作浏览器侧前缀，转发到后端时去掉（zhiyi-server）
            '/api': {
                target: `http://127.0.0.1:${SERVER_DEV_PORT}`,
                changeOrigin: true,
                prependPath: false,
                rewrite: (path) => path.replace(/^\/api/, ''),
            },
            // /gonline-api 由 server/routes/gonline-api/[...].js + NUXT_GONLINE_API_SERVER_TARGET 统一代理（对齐 gonline-ssr /api）
        },
    },

    // 与 useSsrAsyncData 配套：避免 payload 抽取导致详情/字典页水合复用空缓存
    experimental: {
        payloadExtraction: false,
    },

    routeRules: {
        '/': { prerender: false },
        '/login': { prerender: false },
        // 登录后业务页仅客户端渲染：避免 SSR/水合与 Cookie 鉴权不一致，且可更早发起 /api 拉数
        '/memory/**': { ssr: false },
        '/dashboard/**': { ssr: false },
        '/experience/**': { ssr: false },
        '/capture': { ssr: false },
        '/trace': { ssr: false },
        '/recalls': { ssr: false },
        '/rule/**': { ssr: false },
        '/settings/**': { ssr: false },
        '/search/**': { ssr: false },
        '/decision/**': { ssr: false },
        '/stats/**': { ssr: false },
        '/graph/**': { ssr: false },
    },

    build: {
        transpile: ['element-plus/es'],
    },
})
