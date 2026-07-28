<template>
    <section
        class="quick-start"
        :class="{ 'quick-start--done': allStepsDone }"
        @click="goToGuide"
    >
        <div class="quick-start__accent" />

        <div class="quick-start__body">
            <div class="quick-start__main">
                <div class="quick-start__icon" :class="{ 'quick-start__icon--done': allStepsDone }">
                    <el-icon :size="20">
                        <CircleCheckFilled v-if="allStepsDone" />
                        <Guide v-else />
                    </el-icon>
                </div>

                <div class="quick-start__content">
                    <div class="quick-start__head">
                        <h2 class="quick-start__title">
                            {{ allStepsDone ? QUICK_START_HEADER.completedTitle : QUICK_START_HEADER.title }}
                        </h2>
                        <span class="quick-start__inline-desc">{{ headerSubtitle }}</span>
                        <el-tag v-if="allStepsDone" size="small" type="success" effect="light">全部完成</el-tag>
                        <el-tag v-else size="small" type="primary" effect="light">
                            {{ completedStepCount }}/{{ QUICK_START_STEPS.length }}
                        </el-tag>
                    </div>
                    <el-progress
                        class="quick-start__progress"
                        :percentage="progressPercent"
                        :stroke-width="8"
                        :show-text="false"
                        :color="allStepsDone ? '#22c55e' : '#5d65f9'"
                    />
                </div>
            </div>

            <el-button
                :type="allStepsDone ? undefined : 'primary'"
                :plain="!allStepsDone"
                :text="allStepsDone"
                class="quick-start__action"
                :class="{ 'quick-start__action--done': allStepsDone }"
                @click.stop="goToGuide"
            >
                {{ allStepsDone ? '查看引导' : '进入引导' }}
            </el-button>
        </div>
    </section>
</template>

<script setup>
import { CircleCheckFilled, Guide } from '@element-plus/icons-vue'

const props = defineProps({
    publishedCount: {
        type: Number,
        default: 0,
    },
    pendingDraftCount: {
        type: Number,
        default: 0,
    },
})

const router = useRouter()

const {
    QUICK_START_HEADER,
    QUICK_START_STEPS,
    currentStep,
    completedStepCount,
    progressPercent,
    allStepsDone,
    headerSubtitle,
    loadApiKeyStatus,
} = useQuickStart({
    publishedCount: () => props.publishedCount,
    pendingDraftCount: () => props.pendingDraftCount,
})

/** 跳转到引导页，自动定位当前步骤 */
function goToGuide() {
    const query = currentStep.value ? { step: currentStep.value.id } : {}
    router.push({ path: '/dashboard/guide', query })
}

onMounted(() => {
    loadApiKeyStatus()
})
</script>

<style scoped>
.quick-start {
    position: relative;
    overflow: hidden;
    border-radius: 14px;
    border: 1px solid #e8eaef;
    background: #fff;
    box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
    cursor: pointer;
    transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.quick-start:hover {
    border-color: #ced1fd;
    box-shadow: 0 4px 14px rgba(93, 101, 249, 0.08);
    transform: translateY(-1px);
}

/* 闭环跑通后弱化展示：保留成功语义，降低视觉权重，避免与主内容抢焦点 */
.quick-start--done {
    border-color: #d1fae5;
    background: #fafffe;
    box-shadow: none;
}

.quick-start--done:hover {
    border-color: #bbf7d0;
    box-shadow: none;
    transform: none;
}

.quick-start__accent {
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 4px;
    background: linear-gradient(180deg, #8e93fb 0%, #5d65f9 100%);
}

.quick-start--done .quick-start__accent {
    background: #4ade80;
}

.quick-start__body {
    display: flex;
    flex-direction: column;
    gap: 16px;
    padding: 18px 20px 18px 24px;
}

@media (min-width: 640px) {
    .quick-start__body {
        flex-direction: row;
        align-items: center;
        justify-content: space-between;
    }
}

.quick-start__main {
    display: flex;
    align-items: flex-start;
    gap: 14px;
    flex: 1;
    min-width: 0;
}

.quick-start__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    width: 40px;
    height: 40px;
    border-radius: 10px;
    background: linear-gradient(135deg, #eff0fe 0%, #dfe0fe 100%);
    color: #5d65f9;
}

.quick-start__icon--done {
    background: #ecfdf5;
    color: #22c55e;
}

.quick-start__content {
    min-width: 0;
    flex: 1;
}

.quick-start__head {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
}

.quick-start__title {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: #111827;
}

.quick-start--done .quick-start__title {
    font-weight: 500;
    color: #374151;
}

/* 副文案紧跟标题，单行展示（空间不足时自动换行） */
.quick-start__inline-desc {
    font-size: 13px;
    color: #6b7280;
    line-height: 1.5;
}

.quick-start--done .quick-start__inline-desc {
    color: #9ca3af;
}

.quick-start__progress {
    margin-top: 12px;
    max-width: 280px;
}

.quick-start--done .quick-start__progress {
    opacity: 0.85;
}

.quick-start__action {
    flex-shrink: 0;
}

/* 闭环完成后按钮弱化：文字链样式，hover 时才略强调 */
.quick-start__action--done {
    color: #6b7280;
    font-weight: 400;
}

.quick-start__action--done:hover {
    color: #5d65f9;
    background: transparent;
}
</style>
