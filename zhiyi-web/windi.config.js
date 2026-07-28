import { defineConfig } from 'windicss/helpers'

// Zhiyi 品牌色与布局断点（色值取自 public/favicon.png Logo）
export default defineConfig({
    theme: {
        extend: {
            colors: {
                primary: '#5d65f9',
                'primary-dark': '#4a51c7',
                'primary-light': '#767ffb',
                accent: '#868dfc',
            },
            maxWidth: {
                layout: '1120px',
            },
        },
    },
    extract: {
        include: [
            'app.vue',
            'pages/**/*.{vue,html}',
            'components/**/*.{vue,html}',
            'layouts/**/*.{vue,html}',
        ],
    },
})
