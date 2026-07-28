/**
 * Capture 草稿 Review 页业务逻辑：列表加载、采纳、拒绝、路由
 */
import {
    approveCaptureDraft,
    fetchCaptureDraftList,
    mergeCaptureDraftIntoRule,
    rejectCaptureDraft,
    routeCaptureDraft,
} from '~/services/capture.service'

export function useCaptureReview() {
    const loading = ref(false)
    const draftList = ref([])
    const approvingId = ref(null)
    const rejectingId = ref(null)
    const routingId = ref(null)
    const mergingId = ref(null)

    /**
     * 加载待确认草稿列表
     * @param {{ silent?: boolean }} options silent 为 true 时保留当前列表，后台拉取新数据
     */
    async function loadDraftList(options = {}) {
        const silent = options.silent === true
        if (!silent) {
            loading.value = true
        }
        try {
            draftList.value = await fetchCaptureDraftList()
        } finally {
            if (!silent) {
                loading.value = false
            }
        }
    }

    /** 采纳草稿并直接发布到经验中心 */
    async function approveDraft(draft) {
        approvingId.value = draft.id
        try {
            return await approveCaptureDraft(draft.id, draft.draftContent)
        } finally {
            approvingId.value = null
        }
    }

    /** 拒绝草稿，可选携带拒绝码与备注 */
    async function rejectDraft(draftId, rejectRequest) {
        rejectingId.value = draftId
        try {
            await rejectCaptureDraft(draftId, rejectRequest)
        } finally {
            rejectingId.value = null
        }
    }

    /** 路由草稿为 Rule / Workflow / Decision 并直接发布 */
    async function routeDraft(draft, targetType, rejectReason, reviewComment) {
        routingId.value = draft.id
        try {
            return await routeCaptureDraft(draft.id, {
                targetType,
                rejectReason,
                reviewComment,
                editedContent: draft.draftContent,
            })
        } finally {
            routingId.value = null
        }
    }

    /** 合并草稿到已有 Rule */
    async function mergeDraftIntoRule(draft, mergeRequest) {
        mergingId.value = draft.id
        try {
            return await mergeCaptureDraftIntoRule(draft.id, {
                ...mergeRequest,
                editedContent: draft.draftContent,
            })
        } finally {
            mergingId.value = null
        }
    }

    return {
        loading,
        draftList,
        approvingId,
        rejectingId,
        routingId,
        mergingId,
        loadDraftList,
        approveDraft,
        rejectDraft,
        routeDraft,
        mergeDraftIntoRule,
    }
}
