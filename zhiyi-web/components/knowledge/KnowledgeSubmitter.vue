<template>
    <!-- 知识提交人：头像 + 昵称（可选时间），用于列表卡片与详情页 -->
    <div
        v-if="displayName || timeText"
        class="knowledge-submitter"
        :class="sizeClass"
    >
        <template v-if="displayName">
            <LayoutUserAvatar
                :user="submitterUser"
                :size="avatarSize"
            />
            <span class="knowledge-submitter__name">{{ displayName }}</span>
        </template>
        <!-- 时间紧跟昵称，避免列表卡片左右拉开形成空白带 -->
        <template v-if="timeText">
            <span
                v-if="displayName"
                class="knowledge-submitter__separator"
                aria-hidden="true"
            >·</span>
            <span class="knowledge-submitter__time">
                <el-icon :size="12"><Clock /></el-icon>
                {{ timeText }}
            </span>
        </template>
    </div>
</template>

<script setup>
import { Clock } from '@element-plus/icons-vue'
import { buildKnowledgeSubmitterUser, resolveKnowledgeSubmitterName } from '~/utils/knowledge'

const props = defineProps({
    /** 含 creatorNickname / creatorAvatar 或 submitterNickname / submitterAvatar 的对象 */
    knowledge: {
        type: Object,
        default: null,
    },
    /** 展示尺寸：compact 用于列表卡片，default 用于详情页 */
    size: {
        type: String,
        default: 'compact',
        validator: (value) => ['compact', 'default'].includes(value),
    },
    /** 可选更新/提交时间文案，与昵称同一行紧凑展示 */
    timeText: {
        type: String,
        default: '',
    },
})

/** 头像直径：列表略小、详情略大 */
const avatarSize = computed(() => (props.size === 'default' ? 24 : 20))

/** 提交人展示昵称 */
const displayName = computed(() => resolveKnowledgeSubmitterName(props.knowledge))

/** 传给 UserAvatar 的用户对象 */
const submitterUser = computed(() => buildKnowledgeSubmitterUser(props.knowledge))

/** 尺寸样式类 */
const sizeClass = computed(() =>
    props.size === 'default' ? 'knowledge-submitter--default' : 'knowledge-submitter--compact',
)
</script>

<style scoped>
.knowledge-submitter {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
    max-width: 100%;
}

.knowledge-submitter--compact {
    margin-top: 8px;
}

.knowledge-submitter--default {
    margin-top: 10px;
}

.knowledge-submitter__name {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: #6b7280;
    font-weight: 500;
}

.knowledge-submitter--compact .knowledge-submitter__name {
    font-size: 12px;
    line-height: 18px;
}

.knowledge-submitter--default .knowledge-submitter__name {
    font-size: 13px;
    line-height: 20px;
}

.knowledge-submitter__separator {
    flex-shrink: 0;
    color: #d1d5db;
    font-size: 12px;
    line-height: 1;
}

.knowledge-submitter__time {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    flex-shrink: 0;
    color: #9ca3af;
    font-size: 12px;
    line-height: 18px;
    white-space: nowrap;
}
</style>
