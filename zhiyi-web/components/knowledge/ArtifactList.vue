<template>
    <section class="artifact-list">
        <header class="artifact-list__head">
            <h2 class="artifact-list__title">
                <el-icon :size="16"><Link /></el-icon>
                关联产物
            </h2>
            <span class="artifact-list__count">{{ artifacts.length }} 项</span>
        </header>

        <div class="artifact-list__items">
            <div
                v-for="(artifact, index) in artifacts"
                :key="index"
                class="artifact-list__item"
            >
                <div class="artifact-list__item-icon" :class="roleIconClass(artifact.artifactRole)">
                    <el-icon :size="16">
                        <component :is="roleIcon(artifact.artifactRole)" />
                    </el-icon>
                </div>
                <div class="artifact-list__item-body">
                    <div class="artifact-list__item-meta">
                        <span
                            class="artifact-list__role-badge"
                            :class="roleBadgeClass(artifact.artifactRole)"
                        >
                            {{ ARTIFACT_ROLE_LABELS[artifact.artifactRole] || artifact.artifactRole }}
                        </span>
                        <span class="artifact-list__type">{{ artifact.artifactType }}</span>
                    </div>
                    <p v-if="artifact.contentRef" class="artifact-list__ref">
                        {{ artifact.contentRef }}
                    </p>
                    <a
                        v-if="artifact.artifactUrl"
                        :href="artifact.artifactUrl"
                        target="_blank"
                        rel="noopener noreferrer"
                        class="artifact-list__link"
                    >
                        <el-icon :size="12"><TopRight /></el-icon>
                        查看链接
                    </a>
                </div>
            </div>
        </div>
    </section>
</template>

<script setup>
import { Document, Link, Paperclip, TopRight, TrophyBase } from '@element-plus/icons-vue'
import { ARTIFACT_ROLE_LABELS } from '~/constants/knowledge'

defineProps({
    artifacts: {
        type: Array,
        default: () => [],
    },
})

/** Artifact 角色对应图标 */
function roleIcon(role) {
    const iconMap = {
        origin: Document,
        evidence: TrophyBase,
        attachment: Paperclip,
        reference: Link,
    }
    return iconMap[role] || Link
}

/** 角色图标背景色 */
function roleIconClass(role) {
    const classMap = {
        origin: 'artifact-list__item-icon--origin',
        evidence: 'artifact-list__item-icon--evidence',
        attachment: 'artifact-list__item-icon--attachment',
        reference: 'artifact-list__item-icon--reference',
    }
    return classMap[role] || ''
}

/** 角色徽章样式 */
function roleBadgeClass(role) {
    const classMap = {
        origin: 'artifact-list__role-badge--origin',
        evidence: 'artifact-list__role-badge--evidence',
        attachment: 'artifact-list__role-badge--attachment',
        reference: 'artifact-list__role-badge--reference',
    }
    return classMap[role] || ''
}
</script>

<style scoped>
.artifact-list {
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 10px;
    padding: 12px 14px;
}

.artifact-list__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
}

.artifact-list__title {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0;
    font-size: 13px;
    font-weight: 600;
    color: #111827;
}

.artifact-list__count {
    font-size: 12px;
    color: #9ca3af;
}

.artifact-list__items {
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.artifact-list__item {
    display: flex;
    gap: 10px;
    padding: 8px 10px;
    border-radius: 10px;
    background: #fafafa;
    border: 1px solid #f3f4f6;
    transition: border-color 0.15s, background 0.15s;
}

.artifact-list__item:hover {
    background: #f9fafb;
    border-color: #e5e7eb;
}

.artifact-list__item-icon {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    border-radius: 10px;
    background: #f3f4f6;
    color: #6b7280;
}

.artifact-list__item-icon--origin {
    background: #eff6ff;
    color: #2563eb;
}

.artifact-list__item-icon--evidence {
    background: #ecfdf5;
    color: #059669;
}

.artifact-list__item-icon--attachment {
    background: #fffbeb;
    color: #d97706;
}

.artifact-list__item-icon--reference {
    background: #f5f3ff;
    color: #7c3aed;
}

.artifact-list__item-body {
    flex: 1;
    min-width: 0;
}

.artifact-list__item-meta {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
}

.artifact-list__role-badge {
    display: inline-flex;
    padding: 2px 8px;
    border-radius: 6px;
    font-size: 11px;
    font-weight: 600;
}

.artifact-list__role-badge--origin {
    color: #2563eb;
    background: #eff6ff;
}

.artifact-list__role-badge--evidence {
    color: #059669;
    background: #ecfdf5;
}

.artifact-list__role-badge--attachment {
    color: #d97706;
    background: #fffbeb;
}

.artifact-list__role-badge--reference {
    color: #7c3aed;
    background: #f5f3ff;
}

.artifact-list__type {
    font-size: 13px;
    color: #6b7280;
}

.artifact-list__ref {
    margin: 6px 0 0;
    font-size: 13px;
    color: #374151;
    word-break: break-all;
    line-height: 1.5;
}

.artifact-list__link {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    margin-top: 8px;
    font-size: 13px;
    color: #5d65f9;
    text-decoration: none;
    transition: color 0.15s;
}

.artifact-list__link:hover {
    color: #4338ca;
    text-decoration: underline;
}
</style>
