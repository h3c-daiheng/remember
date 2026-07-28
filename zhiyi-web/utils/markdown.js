/**
 * Markdown 渲染工具：将 Fact Block 等文本内容转为安全的 HTML
 */
import DOMPurify from 'isomorphic-dompurify'
import { marked } from 'marked'

/** 配置 marked：启用 GFM，单行换行转 <br> */
marked.setOptions({
    breaks: true,
    gfm: true,
})

/** DOMPurify 允许的标签与属性，防止 XSS（含 img，供关于/联系整篇 Markdown） */
const sanitizeOptions = {
    ALLOWED_TAGS: [
        'p', 'br', 'strong', 'b', 'em', 'i', 'code', 'pre',
        'ul', 'ol', 'li', 'a', 'blockquote', 'img',
        'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'hr',
    ],
    ALLOWED_ATTR: ['href', 'target', 'rel', 'class', 'src', 'alt', 'width', 'height', 'loading'],
}

/**
 * 将 Markdown 文本渲染为经过消毒的 HTML 字符串
 * @param {string} text 原始 Markdown 或纯文本
 * @returns {string} 安全 HTML
 */
export function renderMarkdown(text) {
    if (!text) {
        return ''
    }
    const rawHtml = marked.parse(String(text))
    return DOMPurify.sanitize(rawHtml, sanitizeOptions)
}
