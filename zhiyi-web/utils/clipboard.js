/**
 * 复制文本到剪贴板：优先 Clipboard API，失败时降级为 execCommand
 * @param {string} text 待复制内容
 * @returns {Promise<boolean>} 是否复制成功
 */
export async function copyTextToClipboard(text) {
    if (!import.meta.client) {
        return false
    }

    const content = text == null ? '' : String(text)
    if (!content) {
        return false
    }

    // 优先使用 Clipboard API（需安全上下文；弹层内偶发因焦点问题失败）
    if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
        try {
            await navigator.clipboard.writeText(content)
            return true
        } catch (error) {
            // 继续走降级方案
        }
    }

    return copyTextWithExecCommand(content)
}

/**
 * 通过临时 textarea + execCommand 复制，兼容 HTTP 与非安全上下文
 */
function copyTextWithExecCommand(text) {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.setAttribute('readonly', '')
    textarea.style.position = 'fixed'
    textarea.style.left = '-9999px'
    textarea.style.top = '0'
    document.body.appendChild(textarea)
    textarea.focus()
    textarea.select()

    let copied = false
    try {
        copied = document.execCommand('copy')
    } catch (error) {
        copied = false
    }
    document.body.removeChild(textarea)
    return copied
}
