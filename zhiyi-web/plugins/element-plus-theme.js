/**
 * Element Plus 品牌主题兜底：运行时再次注入 CSS 变量，
 * 防止按需加载的组件样式晚于全局样式时回退为默认蓝色。
 */
import '~/assets/css/element-plus-theme.css'

export default defineNuxtPlugin(() => {})
