/**
 * 首页锚点平滑滚动
 * 顶栏为 sticky 固定高度，目标区块需配合 scroll-mt-16 避免被遮挡
 */
export function useHomeAnchorScroll() {
    /**
     * 滚动到指定区块
     * @param {string} targetId 区块 DOM id
     */
    function scrollToSection(targetId) {
        if (!import.meta.client || !targetId) {
            return
        }
        document.getElementById(targetId)?.scrollIntoView({
            behavior: 'smooth',
            block: 'start',
        })
    }

    return {
        scrollToSection,
    }
}
