/**
 * 文档导入页状态与 API 编排
 */
import { ElMessage } from 'element-plus'
import { extractDocumentImport, submitDocumentImport } from '~/services/import.service'

const TARGET_TYPE_OPTIONS = [
    { value: 'auto', label: '自动识别' },
    { value: 'experience', label: '经验（Experience）' },
    { value: 'decision', label: '决策（Decision）' },
    { value: 'rule', label: '规则（Rule）' },
    { value: 'workflow', label: '流程（Workflow）' },
]

const MAX_CONTENT_LENGTH = 100000
const MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024

export function useDocumentImport() {
    const router = useRouter()
    const { refreshPendingDraftCount } = useAppNavigation()

    const inputMode = ref('paste')
    const form = reactive({
        title: '',
        content: '',
        fileName: '',
        targetType: 'auto',
        module: '',
        repository: '',
        tags: [],
    })

    const tagInput = ref('')
    const extracting = ref(false)
    const submitting = ref(false)
    const extractResult = ref(null)
    const editableDraft = ref(null)

    const contentLength = computed(() => form.content?.length || 0)
    const canExtract = computed(() => contentLength.value > 0 && contentLength.value <= MAX_CONTENT_LENGTH)
    const hasPreview = computed(() => extractResult.value?.submit && editableDraft.value)

    /**
     * 读取上传的文本文件内容
     */
    async function handleFileChange(uploadFile) {
        const rawFile = uploadFile?.raw
        if (!rawFile) {
            return
        }
        if (rawFile.size > MAX_FILE_SIZE_BYTES) {
            ElMessage.warning('文件大小不能超过 2MB')
            return
        }
        const fileName = rawFile.name || ''
        const lowerName = fileName.toLowerCase()
        if (!lowerName.endsWith('.md') && !lowerName.endsWith('.txt') && !lowerName.endsWith('.markdown')) {
            ElMessage.warning('当前仅支持 .md / .txt 文本文件')
            return
        }
        try {
            const textContent = await rawFile.text()
            form.content = textContent
            form.fileName = fileName
            if (!form.title) {
                form.title = fileName.replace(/\.(md|txt|markdown)$/i, '')
            }
            inputMode.value = 'upload'
            extractResult.value = null
            editableDraft.value = null
        } catch (error) {
            ElMessage.error('文件读取失败')
        }
    }

    /**
     * 添加标签
     */
    function addTag() {
        const tagName = tagInput.value?.trim()
        if (!tagName) {
            return
        }
        if (!form.tags.includes(tagName)) {
            form.tags.push(tagName)
        }
        tagInput.value = ''
    }

    /**
     * 移除标签
     */
    function removeTag(tagName) {
        form.tags = form.tags.filter((item) => item !== tagName)
    }

    /**
     * 调用 AI 抽取预览
     */
    async function runExtract() {
        if (!canExtract.value) {
            ElMessage.warning('请先输入文档内容')
            return
        }
        extracting.value = true
        extractResult.value = null
        editableDraft.value = null
        try {
            const result = await extractDocumentImport({
                sourceType: inputMode.value === 'upload' ? 'upload' : 'paste',
                title: form.title,
                content: form.content,
                fileName: form.fileName,
                targetType: form.targetType,
                module: form.module,
                repository: form.repository,
                tags: form.tags,
            })
            extractResult.value = result
            if (result.submit && result.draft) {
                editableDraft.value = JSON.parse(JSON.stringify(result.draft))
                ElMessage.success('抽取完成，请核对预览内容')
            } else {
                ElMessage.warning(result.skipReason || '文档不适合沉淀为经验，请检查内容或更换文档')
            }
        } catch (error) {
            ElMessage.error(error?.message || 'AI 抽取失败，请检查模型网关配置')
        } finally {
            extracting.value = false
        }
    }

    /**
     * 提交为 Capture 草稿
     */
    async function runSubmit() {
        if (!editableDraft.value) {
            ElMessage.warning('请先完成 AI 抽取')
            return
        }
        submitting.value = true
        try {
            const result = await submitDocumentImport({
                sourceType: inputMode.value === 'upload' ? 'upload' : 'paste',
                title: form.title,
                content: form.content,
                fileName: form.fileName,
                suggestedType: extractResult.value?.suggestedType,
                routeHint: extractResult.value?.routeHint,
                draftContent: editableDraft.value,
            })
            await refreshPendingDraftCount()
            ElMessage.success('已提交为 Capture 草稿，请前往草稿确认页审核')
            if (result?.draftId) {
                router.push('/capture')
            } else {
                router.push('/capture')
            }
        } catch (error) {
            ElMessage.error(error?.message || '提交失败')
        } finally {
            submitting.value = false
        }
    }

    /**
     * 重置预览状态
     */
    function resetPreview() {
        extractResult.value = null
        editableDraft.value = null
    }

    return {
        TARGET_TYPE_OPTIONS,
        MAX_CONTENT_LENGTH,
        inputMode,
        form,
        tagInput,
        extracting,
        submitting,
        extractResult,
        editableDraft,
        contentLength,
        canExtract,
        hasPreview,
        handleFileChange,
        addTag,
        removeTag,
        runExtract,
        runSubmit,
        resetPreview,
    }
}
