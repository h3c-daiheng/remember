# 工作空间与知识管理增强 — 前端实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 zhiyi-web 前端实现「删除工作空间、导出知识 Markdown、批量导入经验、规则修订重发、列表多选批量删除」5 项能力的 UI 与调用层。

**Architecture:** 复用现有 `apiRequest` 封装与组件模式。新增 service 函数、composable 方法、UI 入口。规则修订复用 `createKnowledge` + publish + supersede 已有端点;其余 4 项调后端新端点(见后端 plan)。

**Tech Stack:** Nuxt 3.16 + Vue 3 + Element Plus 2.9,非 TypeScript,原生 fetch,`useState` 状态管理。

## Global Constraints

- 所有前端文件绝对前缀:`/root/zhiyi/zhiyi-web/`。
- API 调用统一走 `services/http.js` 的 `apiRequest(path, options)`:GET 拼 `?query`,POST/PUT/DELETE 用 `{ method, body: JSON.stringify(...) }`;返回值已解包(`code===0` 时返回 `data`,否则抛 `Error`)。
- `apiBase = '/api'`,dev 经 `devProxy` 转发到后端 `127.0.0.1:4302`。
- 组件自动注册:`components/<dir>/<X>.vue` → 全局标签 `<DirX>`(如 `ExperienceEditDrawer.vue` → `<KnowledgeExperienceEditDrawer>`)。新组件放对目录即生效。
- 权限函数在 `constants/workspace.js`:`isWorkspaceOwner(role)`(删空间)、`canEditKnowledge(role)`(导入/修订)、`canManageWorkspace(role)`(批删入口)。
- **前端零测试基础设施**(无 vitest)。本计划采用「实现 → 构建验证(`npm run build`)→ 手动验证(角色矩阵)」,不为本次功能从零搭建 vitest。
- **依赖后端**:执行本计划前,后端 plan 的对应端点须已实现并运行(`mvn -Dspring-boot.run.profiles=dev`)。端点对照:
  - 删空间:`DELETE /workspace/{workspaceId}`,body `{ confirmName }`
  - 导出:`GET /knowledge/export?knowledgeType=`
  - 批量导入:`POST /knowledge/import`,multipart `file`
  - 批量删除:`DELETE /knowledge/batch`,body `{ ids: [] }`
  - 规则修订:`POST /knowledge`(publish=false)+ `POST /knowledge/{id}/publish` + `POST /knowledge/{id}/supersede` body `{ predecessorId }`

---

## Task 1: 删除工作空间

**Files:**
- Modify: `/root/zhiyi/zhiyi-web/services/workspace.service.js`(新增 `deleteWorkspaceRequest`)
- Modify: `/root/zhiyi/zhiyi-web/composables/useWorkspace.js`(新增 `deleteWorkspace`)
- Modify: `/root/zhiyi/zhiyi-web/composables/useWorkspacePermission.js`(新增 `canDeleteWorkspace`)
- Modify: `/root/zhiyi/zhiyi-web/components/workspace/WorkspaceSwitcher.vue`(新增 dropdown 项 + 输名称确认)

**Interfaces:**
- Consumes: 后端 `DELETE /workspace/{workspaceId}`;现有 `useWorkspace` 的 `loadWorkspaceList` / `switchWorkspace` / `notifyWorkspaceContextChanged`。
- Produces: `deleteWorkspace(workspaceId, confirmName)` composable 方法。

- [ ] **Step 1: 新增 service 函数**

在 `/root/zhiyi/zhiyi-web/services/workspace.service.js` 末尾(`createWorkspaceRequest` 之后)新增:

```js
export function deleteWorkspaceRequest(workspaceId, confirmName) {
  return apiRequest(`/workspace/${workspaceId}`, {
    method: 'DELETE',
    body: JSON.stringify({ confirmName })
  })
}
```

- [ ] **Step 2: 新增 composable 方法**

在 `/root/zhiyi/zhiyi-web/composables/useWorkspace.js` 中,导入 `deleteWorkspaceRequest`(顶部 import 区补),并在 `createWorkspace` 方法之后、`return` 之前新增:

```js
async function deleteWorkspace(workspaceId, confirmName) {
  await deleteWorkspaceRequest(workspaceId, confirmName)
  await loadWorkspaceList()
  if (String(currentWorkspace.value?.workspaceId) === String(workspaceId)) {
    const remaining = workspaceList.value.find((item) => String(item.workspaceId) !== String(workspaceId))
    if (remaining) {
      await switchWorkspace(remaining.workspaceId)
    } else {
      // 无剩余空间:清空当前上下文,提示用户重新登录/创建
      currentWorkspace.value = null
    }
  }
  notifyWorkspaceContextChanged()
}
```

并在 `return { ... }` 中追加 `deleteWorkspace`。

- [ ] **Step 3: 新增权限计算属性**

在 `/root/zhiyi/zhiyi-web/composables/useWorkspacePermission.js` 中,导入 `isWorkspaceOwner`(若未导入),在现有 computed 之后新增:

```js
const canDeleteWorkspace = computed(() => isWorkspaceOwner(memberRole.value))
```

并在 `return { ... }` 中追加 `canDeleteWorkspace`。

- [ ] **Step 4: WorkspaceSwitcher 新增入口与确认**

修改 `/root/zhiyi/zhiyi-web/components/workspace/WorkspaceSwitcher.vue`:

a) script setup 中解构 `deleteWorkspace` 与 `canDeleteWorkspace`:
```js
const { currentWorkspace, workspaceList, switchWorkspace, createWorkspace, deleteWorkspace } = useWorkspace()
const { canDeleteWorkspace } = useWorkspacePermission()
const deleting = ref(false)
```

b) 在 `<el-dropdown-menu>` 内「工作空间设置」项(`command="settings"`)之后新增:
```html
<el-dropdown-item divided command="delete" :disabled="!canDeleteWorkspace">删除当前工作空间</el-dropdown-item>
```

c) 在 `handleSwitch` 中新增分支(与 `create`/`settings` 分支同级):
```js
if (command === 'delete') { openDeleteConfirm(); return }
```

d) 新增删除确认逻辑(参考 `openCreateDialog` 的 dialog 模式,或用 `ElMessageBox.prompt` 输名称)。推荐用 `ElMessageBox.prompt` 让用户输入空间名:

```js
import { ElMessageBox, ElMessage } from 'element-plus'

async function openDeleteConfirm() {
  const wsName = currentWorkspace.value?.workspaceName || ''
  const wsId = currentWorkspace.value?.workspaceId
  if (!wsId) return
  try {
    const { value } = await ElMessageBox.prompt(
      `此操作不可恢复,将永久删除工作空间「${wsName}」及其下全部知识、规则、草稿、API Key。\n请输入工作空间名称「${wsName}」以确认:`,
      '删除工作空间',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'error',
        inputPlaceholder: wsName, inputValidator: (v) => !!v && v.trim() !== '' || '请输入工作空间名称' }
    )
    if (value.trim() !== wsName) {
      ElMessage.error('输入的名称与工作空间名称不符,已取消')
      return
    }
    deleting.value = true
    await deleteWorkspace(wsId, value.trim())
    ElMessage.success('工作空间已删除')
  } catch (e) {
    if (e !== 'cancel' && e?.message !== 'cancel') {
      ElMessage.error(e?.message || '删除失败')
    }
  } finally {
    deleting.value = false
  }
}
```

> 注:前端已做名称校验,后端也会校验(双保险)。`ElMessageBox.prompt` 的 reject 值为 `'cancel'`,需排除。

- [ ] **Step 5: 构建验证**

Run: `cd /root/zhiyi/zhiyi-web && npm run build`
Expected: 构建成功无报错。

- [ ] **Step 6: 手动验证**

启动前后端,登录 owner 账号(admin):
- WorkspaceSwitcher 下拉出现「删除当前工作空间」,点击弹出输入框。
- 输入错误名称 → 提示「名称不符」,不删除。
- 输入正确名称 → 确认后空间从列表消失;若删的是当前空间,自动切换到剩余空间。
- 切换到 editor/viewer 账号 → 「删除当前工作空间」项 disabled。

- [ ] **Step 7: 提交**

```bash
cd /root/zhiyi/zhiyi-web
git add services/workspace.service.js composables/useWorkspace.js composables/useWorkspacePermission.js components/workspace/WorkspaceSwitcher.vue
git commit -m "feat(web): 工作空间删除入口(owner+输名称确认)"
```

---

## Task 2: 导出知识为 Markdown

**Files:**
- Create: `/root/zhiyi/zhiyi-web/utils/fileDownload.js`(下载工具)
- Modify: `/root/zhiyi/zhiyi-web/services/knowledge.service.js`(新增 `exportKnowledgeMarkdown`)
- Modify: `/root/zhiyi/zhiyi-web/pages/memory/index.vue`(工具栏「导出」按钮)

**Interfaces:**
- Consumes: 后端 `GET /knowledge/export?knowledgeType=`(返回 `text/markdown` 文件流,非 JSON)。
- Produces: `downloadTextFile(filename, text, mime)` 工具;`exportKnowledgeMarkdownRaw(knowledgeType)` 返回原始文本。

> 注意:后端导出端点返回**文件流**(`Content-Type: text/markdown`),不走 `apiRequest` 的 JSON 解包。需直接 `fetch` 并读 `response.text()`。

- [ ] **Step 1: 新建下载工具**

Create `/root/zhiyi/zhiyi-web/utils/fileDownload.js`:

```js
export function downloadTextFile(filename, text, mime = 'text/markdown') {
  const blob = new Blob([text], { type: `${mime};charset=utf-8` })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
```

- [ ] **Step 2: 新增导出 service(绕过 apiRequest,直接 fetch 文件流)**

在 `/root/zhiyi/zhiyi-web/services/knowledge.service.js` 顶部 import 区补:`getStoredToken`(与 http.js 同源,从 `utils/token` 导入,参考 http.js:11)。在文件末尾新增:

```js
export async function exportKnowledgeMarkdown(knowledgeType = 'all') {
  const runtimeConfig = useRuntimeConfig()
  const token = getStoredToken()
  const query = knowledgeType ? `?knowledgeType=${encodeURIComponent(knowledgeType)}` : ''
  const response = await fetch(`${runtimeConfig.public.apiBase}/knowledge/export${query}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  if (!response.ok) {
    throw new Error('导出失败')
  }
  return response.text()
}
```

> `getStoredToken` 的确切导入路径以 `services/http.js:11` 的导入为准(可能是 `~/utils/token` 或 `@/utils/token`)。照搬 http.js 的 import。

- [ ] **Step 3: /memory 工具栏新增「导出」按钮**

修改 `/root/zhiyi/zhiyi-web/pages/memory/index.vue`:

a) script setup 导入:
```js
import { exportKnowledgeMarkdown } from '~/services/knowledge.service'
import { downloadTextFile } from '~/utils/fileDownload'
const exporting = ref(false)
```

b) 在 `KnowledgeListToolbar` 的 `#actions-end` 插槽(现有「草稿确认」按钮附近)新增:
```html
<el-button :loading="exporting" @click="handleExport">
  <el-icon class="mr-1"><Download /></el-icon>导出 Markdown
</el-button>
```

c) 新增处理函数:
```js
async function handleExport() {
  try {
    exporting.value = true
    const text = await exportKnowledgeMarkdown(typeFilter.value || 'all')
    const wsName = currentWorkspace.value?.workspaceName || '工作空间'
    const date = new Date().toISOString().slice(0, 10).replace(/-/g, '')
    downloadTextFile(`${wsName}-知识导出-${date}.md`, text)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error(e?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}
```

> `Download` 图标来自 `@element-plus/icons-vue`(已全局注册)。`ElMessage` 已在页面使用。`currentWorkspace`、`typeFilter` 已存在。

- [ ] **Step 4: 构建验证**

Run: `cd /root/zhiyi/zhiyi-web && npm run build`
Expected: 构建成功。

- [ ] **Step 5: 手动验证**

- 进入 `/memory`,工具栏出现「导出 Markdown」按钮。
- 点击 → 浏览器下载 `<空间名>-知识导出-<日期>.md`,内容含已发布知识的 frontmatter + Facts + Artifacts。
- 空空间 → 下载仅含文件头的 md。
- 切换类型筛选后导出 → 文件内容随类型变化(若后端支持 knowledgeType 过滤)。

- [ ] **Step 6: 提交**

```bash
cd /root/zhiyi/zhiyi-web
git add utils/fileDownload.js services/knowledge.service.js pages/memory/index.vue
git commit -m "feat(web): 导出工作空间知识为 Markdown"
```

---

## Task 3: 批量导入经验

**Files:**
- Modify: `/root/zhiyi/zhiyi-web/services/knowledge.service.js`(新增 `importKnowledgeMarkdown`)
- Modify: `/root/zhiyi/zhiyi-web/pages/memory/index.vue`(工具栏「导入」按钮 + 上传对话框)

**Interfaces:**
- Consumes: 后端 `POST /knowledge/import`,multipart `file`。
- Produces: `importKnowledgeMarkdown(file)` 返回 `{ total, imported, failed, failures }`。

> multipart 上传需 `FormData`,不用 `apiRequest`(它设了 `Content-Type: application/json`)。直接 fetch。

- [ ] **Step 1: 新增导入 service**

在 `/root/zhiyi/zhiyi-web/services/knowledge.service.js` 末尾新增:

```js
export async function importKnowledgeMarkdown(file) {
  const runtimeConfig = useRuntimeConfig()
  const token = getStoredToken()
  const form = new FormData()
  form.append('file', file)
  const response = await fetch(`${runtimeConfig.public.apiBase}/knowledge/import`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: form
  })
  const result = await response.json()
  if (result.code !== 0) {
    throw new Error(result.message || '导入失败')
  }
  return result.data
}
```

> 不要手动设 `Content-Type`,浏览器会自动带 `multipart/form-data; boundary=...`。

- [ ] **Step 2: /memory 工具栏新增「导入」按钮与对话框**

修改 `/root/zhiyi/zhiyi-web/pages/memory/index.vue`:

a) script setup 导入与状态:
```js
import { importKnowledgeMarkdown } from '~/services/knowledge.service'
import { UploadFilled } from '@element-plus/icons-vue'
const importDialogVisible = ref(false)
const importing = ref(false)
const importResult = ref(null)
const pendingFile = ref(null)

async function handleImportFileChange(uploadFile) {
  pendingFile.value = uploadFile?.raw || null
}
async function handleConfirmImport() {
  if (!pendingFile.value) { ElMessage.warning('请先选择文件'); return }
  try {
    importing.value = true
    const result = await importKnowledgeMarkdown(pendingFile.value)
    importResult.value = result
    ElMessage.success(`导入完成:成功 ${result.imported} 条,失败 ${result.failed} 条`)
    await loadList()
  } catch (e) {
    ElMessage.error(e?.message || '导入失败')
  } finally {
    importing.value = false
  }
}
function closeImportDialog() {
  importDialogVisible.value = false
  pendingFile.value = null
  importResult.value = null
}
```

b) 工具栏 `#actions-end` 新增按钮(导出按钮旁):
```html
<el-button @click="importDialogVisible = true" :disabled="!canEdit">
  <el-icon class="mr-1"><UploadFilled /></el-icon>批量导入
</el-button>
```

> `canEdit` 来自 `useWorkspacePermission`(owner/admin/editor)。若页面未解构,从 `useWorkspacePermission()` 取。

c) 模板末尾新增对话框:
```html
<el-dialog v-model="importDialogVisible" title="批量导入经验" width="560px" @close="closeImportDialog">
  <el-upload drag :auto-upload="false" :show-file-list="false" accept=".md,.markdown,.txt" @change="handleImportFileChange" :disabled="importing">
    <el-icon class="el-icon--upload" :size="40"><UploadFilled /></el-icon>
    <div class="el-upload__text">拖拽或点击选择 .md 文件</div>
    <template #tip><div class="el-upload__tip text-center">格式须与「导出 Markdown」一致;导入后为草稿,需在「草稿」Tab 审核</div></template>
  </el-upload>
  <div v-if="importResult" class="mt-3">
    <el-alert :title="`成功 ${importResult.imported} / ${importResult.total} 条,失败 ${importResult.failed} 条`"
              :type="importResult.failed > 0 ? 'warning' : 'success'" :closable="false" />
    <div v-if="importResult.failures?.length" class="mt-2 max-h-40 overflow-auto text-sm text-red-500">
      <div v-for="(f, i) in importResult.failures" :key="i">第 {{ f.index }} 条:{{ f.reason }}</div>
    </div>
  </div>
  <template #footer>
    <el-button @click="closeImportDialog">取消</el-button>
    <el-button type="primary" :loading="importing" @click="handleConfirmImport">开始导入</el-button>
  </template>
</el-dialog>
```

- [ ] **Step 3: 构建验证**

Run: `cd /root/zhiyi/zhiyi-web && npm run build`
Expected: 构建成功。

- [ ] **Step 4: 手动验证**

- editor 及以上角色:「批量导入」按钮启用;viewer 禁用。
- 先用 Task 2 导出一个 md,再用它导入 → 提示「成功 N 条」,列表「草稿」Tab 出现导入的草稿。
- 上传格式错误的 md(缺 title)→ 失败明细列出第 N 条原因,合法条目仍导入。

- [ ] **Step 5: 提交**

```bash
cd /root/zhiyi/zhiyi-web
git add services/knowledge.service.js pages/memory/index.vue
git commit -m "feat(web): 批量导入经验(Markdown→草稿)"
```

---

## Task 4: 规则修订重发

**Files:**
- Modify: `/root/zhiyi/zhiyi-web/pages/rule/[id].vue`(已发布态新增「修订」按钮 + `handleRevise`)
- Modify: `/root/zhiyi/zhiyi-web/pages/rule/drafts/[id].vue`(发布后引导 supersede,见 Step 3)

**Interfaces:**
- Consumes: `createKnowledge`(`POST /knowledge`,publish=false)+ `publishKnowledge` + supersede(后端 plan 未新增端点,用已有 `POST /knowledge/{id}/supersede`,需新增 service 函数)。
- Produces: `handleRevise`(克隆当前规则 → 建草稿 → 跳草稿编辑页);草稿页发布后 supersede 原版。

- [ ] **Step 1: 新增 supersede service**

在 `/root/zhiyi/zhiyi-web/services/knowledge.service.js` 新增:

```js
export function supersedeKnowledge(successorId, predecessorId, comment = '') {
  return apiRequest(`/knowledge/${successorId}/supersede`, {
    method: 'POST',
    body: JSON.stringify({ predecessorId, comment })
  })
}
```

- [ ] **Step 2: rule 详情页新增「修订」入口**

修改 `/root/zhiyi/zhiyi-web/pages/rule/[id].vue`:

a) script setup 导入:
```js
import { createKnowledge } from '~/services/knowledge.service'
import { buildKnowledgeSaveRequest, cloneKnowledgeForEdit } from '~/utils/knowledge'
import { resolveKnowledgeDraftPath } from '~/utils/knowledge'
const revising = ref(false)
```

b) 在 `<template #actions>` 内,「下架」按钮(`v-if="...PUBLISHED"`)之后、「删除」按钮之前新增:
```html
<el-button v-if="detail.lifecycleStatus === KNOWLEDGE_LIFECYCLE.PUBLISHED && canModify"
           size="small" :loading="revising" @click="handleRevise">
  <el-icon class="mr-1"><EditPen /></el-icon>修订
</el-button>
```

c) 新增处理函数(克隆当前规则 → 建草稿 → 把原版 id 存 sessionStorage 供草稿页发布后 supersede → 跳草稿编辑页):
```js
async function handleRevise() {
  try {
    revising.value = true
    const clone = cloneKnowledgeForEdit(detail.value)
    clone.publish = false
    clone.knowledgeType = KNOWLEDGE_TYPES.RULE
    const payload = buildKnowledgeSaveRequest(clone)
    payload.publish = false
    const newId = await createKnowledge(payload)
    sessionStorage.setItem(`ruleRevisePredecessor:${newId}`, String(knowledgeId.value))
    await router.push(resolveKnowledgeDraftPath(newId, KNOWLEDGE_TYPES.RULE))
  } catch (e) {
    ElMessage.error(e?.message || '创建修订草稿失败')
  } finally {
    revising.value = false
  }
}
```

> `KNOWLEDGE_TYPES`、`KNOWLEDGE_LIFECYCLE`、`canModify`、`knowledgeId`、`detail`、`router`、`ElMessage` 在该页已存在或已导入。`EditPen` 图标来自 `@element-plus/icons-vue`。

- [ ] **Step 3: 草稿页发布后引导 supersede**

修改 `/root/zhiyi/zhiyi-web/pages/rule/drafts/[id].vue`,在现有 `handlePublish`(发布成功)之后,检查 sessionStorage 是否有 predecessor,引导 supersede:

a) 导入:
```js
import { supersedeKnowledge } from '~/services/knowledge.service'
import { ElMessageBox } from 'element-plus'
```

b) 在发布成功流程末尾(`ElMessage.success('发布成功')` 之后)追加:
```js
const predecessorId = sessionStorage.getItem(`ruleRevisePredecessor:${knowledgeId.value}`)
if (predecessorId) {
  sessionStorage.removeItem(`ruleRevisePredecessor:${knowledgeId.value}`)
  try {
    await ElMessageBox.confirm('是否用此修订版本替代原规则?原规则将下架。', '替代原版本', {
      type: 'warning', confirmButtonText: '替代原版本', cancelButtonText: '暂不替代'
    })
    await supersedeKnowledge(knowledgeId.value, Number(predecessorId), '规则修订重发')
    ElMessage.success('已替代原规则')
  } catch (e) {
    // 用户选择「暂不替代」或取消,不报错;草稿已发布,可后续手动 supersede
  }
}
```

> 若草稿页 `handlePublish` 内部结构不同(如调用了独立的 publish 函数),把上述逻辑接到 publish 成功的 then 分支。`knowledgeId` 以该页实际变量名为准。

- [ ] **Step 4: 构建验证**

Run: `cd /root/zhiyi/zhiyi-web && npm run build`
Expected: 构建成功。

- [ ] **Step 5: 手动验证**

- 打开一条已发布规则详情 → editor 及以上可见「修订」按钮,viewer 不可见。
- 点击「修订」→ 跳到草稿编辑页,字段已预填(标题/facts/tags/artifacts)。
- 改动后发布 → 弹「是否替代原规则」→ 确认 → 原规则变为已失效,新规则为已发布,supersedes 关系建立(详情页时间线可见)。
- 选「暂不替代」→ 草稿正常发布为独立规则,不影响原规则。

- [ ] **Step 6: 提交**

```bash
cd /root/zhiyi/zhiyi-web
git add services/knowledge.service.js pages/rule/[id].vue pages/rule/drafts/[id].vue
git commit -m "feat(web): 已发布规则修订重发(克隆草稿→发布→supersede)"
```

---

## Task 5: 列表多选 + 批量删除

**Files:**
- Modify: `/root/zhiyi/zhiyi-web/composables/useMemoryList.js`(新增 `selectedIds` 与清空逻辑)
- Modify: `/root/zhiyi/zhiyi-web/components/knowledge/KnowledgeCard.vue`(新增可选 checkbox)
- Modify: `/root/zhiyi/zhiyi-web/services/knowledge.service.js`(新增 `batchDeleteKnowledge`)
- Modify: `/root/zhiyi/zhiyi-web/pages/memory/index.vue`(多选状态 + 工具栏「批量删除」按钮)

**Interfaces:**
- Consumes: 后端 `DELETE /knowledge/batch` body `{ ids }`;`useMemoryList.loadList`。
- Produces: `selectedIds` 多选状态;`batchDeleteKnowledge(ids)` service。

- [ ] **Step 1: 新增批量删除 service**

在 `/root/zhiyi/zhiyi-web/services/knowledge.service.js` 新增:

```js
export function batchDeleteKnowledge(ids) {
  return apiRequest('/knowledge/batch', {
    method: 'DELETE',
    body: JSON.stringify({ ids })
  })
}
```

- [ ] **Step 2: useMemoryList 新增多选状态**

修改 `/root/zhiyi/zhiyi-web/composables/useMemoryList.js`:

a) 新增状态:
```js
const selectedIds = ref([])
function toggleSelect(id, checked) {
  if (checked) {
    if (!selectedIds.value.includes(id)) selectedIds.value.push(id)
  } else {
    selectedIds.value = selectedIds.value.filter((x) => x !== id)
  }
}
function clearSelection() {
  selectedIds.value = []
}
```

b) 在 `loadList` 成功写入列表后(`memoryList.value = ...` 附近)调用 `clearSelection()`(翻页/刷新清空选中,避免残留):
```js
// loadList 内,数据写入后:
clearSelection()
```

> 若 loadList 有 `silent` 分支,silent 刷新也需清空(切空间后旧选中无意义)。

c) 在 `return { ... }` 追加 `selectedIds, toggleSelect, clearSelection`。

- [ ] **Step 3: KnowledgeCard 新增可选 checkbox**

修改 `/root/zhiyi/zhiyi-web/components/knowledge/KnowledgeCard.vue`:

a) props / emits 扩展:
```js
const props = defineProps({
  // ...现有 props...
  selectable: { type: Boolean, default: false },
  selected: { type: Boolean, default: false }
})
const emit = defineEmits(['select' /*, ...现有... */])
function onCheckboxChange(val) {
  emit('select', { id: props.knowledge.id, checked: val })
}
```

b) 在 `__inner`(`:7` 那个 `flex items-stretch gap-3` 的 div)最前面插入 checkbox,**用包裹 div + `@click.stop` 阻止冒泡到 NuxtLink**:
```html
<div v-if="selectable" class="shrink-0 self-center pl-1" @click.stop>
  <el-checkbox :model-value="selected" @change="onCheckboxChange" />
</div>
```

> `@click.stop` 在外层 div 阻止点击冒泡到 `<NuxtLink>`,避免勾选触发跳转。若实测仍跳转,改用 `@click.prevent.stop` 并在 `onCheckboxChange` 里 `event.preventDefault()`。

- [ ] **Step 4: /memory 列表接入选选与批量删除**

修改 `/root/zhiyi/zhiyi-web/pages/memory/index.vue`:

a) script setup 解构新增状态、导入:
```js
const { /* ...现有... */ selectedIds, toggleSelect, clearSelection, loadList } = useMemoryList()
import { batchDeleteKnowledge } from '~/services/knowledge.service'
const { canManage } = useWorkspacePermission()
const batchDeleting = ref(false)

function onCardSelect({ id, checked }) {
  toggleSelect(id, checked)
}
async function handleBatchDelete() {
  if (!selectedIds.value.length) return
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 条?`, '批量删除', {
      type: 'error', confirmButtonText: '确认删除', cancelButtonText: '取消'
    })
  } catch (e) {
    return // 用户取消
  }
  try {
    batchDeleting.value = true
    const result = await batchDeleteKnowledge([...selectedIds.value])
    ElMessage.success(`已删除 ${result.deleted} 条,失败 ${result.failed} 条`)
    await loadList()
  } catch (e) {
    ElMessage.error(e?.message || '批量删除失败')
  } finally {
    batchDeleting.value = false
  }
}
```

> `ElMessageBox` 已在页面使用(或在 Task 1/2 导入)。`canManage` 用于控制批删按钮可见性。

b) 列表循环给 KnowledgeCard 传 selectable/selected 并监听 select:
```html
<KnowledgeCard
  v-for="item in memoryList"
  :key="item.id"
  :knowledge="item"
  :show-type-badge="true"
  :show-lifecycle-badge="activeTab === 'deprecated'"
  :selectable="canManage"
  :selected="selectedIds.includes(item.id)"
  @select="onCardSelect"
/>
```

> 批删入口限定 `canManage`(owner/admin 可删任意)。若要允许 editor 删本人创建的,可放宽 `selectable`,但批量场景无法逐条判权限,后端会逐条校验返回 failures——故前端入口用 canManage 更稳妥。

c) 工具栏 `#actions-end` 新增批量删除按钮(选中时显示):
```html
<el-button v-if="selectedIds.length" type="danger" plain :loading="batchDeleting" @click="handleBatchDelete">
  批量删除({{ selectedIds.length }})
</el-button>
```

- [ ] **Step 5: 构建验证**

Run: `cd /root/zhiyi/zhiyi-web && npm run build`
Expected: 构建成功。

- [ ] **Step 6: 手动验证**

- owner/admin:`/memory` 卡片左侧出现 checkbox;勾选后工具栏出现「批量删除(N)」。
- 勾选后点「批量删除」→ 二次确认 → 删除成功,列表刷新,选中清空。
- 翻页 / 切空间 / 切类型 → 选中自动清空。
- 勾选 checkbox **不触发**跳转到详情页(验证 `@click.stop` 生效)。
- viewer:无 checkbox,无批量删除按钮。
- 后端逐条权限:editor 若放宽 selectable,删他人创建的会进 failures 明细(本方案 editor 不显示 checkbox,跳过此场景)。

- [ ] **Step 7: 提交**

```bash
cd /root/zhiyi/zhiyi-web
git add composables/useMemoryList.js components/knowledge/KnowledgeCard.vue services/knowledge.service.js pages/memory/index.vue
git commit -m "feat(web): 列表多选 + 批量删除经验/规则"
```

---

## Self-Review(作者自查记录)

- **Spec 覆盖**:
  - 删除工作空间 → Task 1 ✓
  - 导出 Markdown → Task 2 ✓
  - 批量导入经验 → Task 3 ✓
  - 编辑经验/规则 → 经验沿用现有内联编辑(spec 非目标);规则修订重发 → Task 4 ✓
  - 批量删除经验/规则 → Task 5 ✓
- **类型/命名一致性**:`deleteWorkspaceRequest` / `deleteWorkspace` / `canDeleteWorkspace`(Task 1);`exportKnowledgeMarkdown` / `downloadTextFile`(Task 2);`importKnowledgeMarkdown`(Task 3);`supersedeKnowledge` / `handleRevise`(Task 4);`batchDeleteKnowledge` / `selectedIds` / `toggleSelect`(Task 5)——service 函数名与调用点一致。
- **后端端点对齐**:前端调用路径与后端 plan 定义一致(`DELETE /workspace/{id}`、`GET /knowledge/export`、`POST /knowledge/import`、`DELETE /knowledge/batch`、`POST /knowledge/{id}/supersede`)。
- **已知风险点**(实现时核对):
  - `getStoredToken` 的导入路径(照搬 `services/http.js:11`)。
  - KnowledgeCard checkbox 阻止 NuxtLink 跳转需实测,必要时加 `@click.prevent`。
  - `pages/rule/drafts/[id].vue` 的 `handlePublish` 实际结构与变量名,以源码为准接入 supersede 引导。
  - `useMemoryList.loadList` 的 silent 分支也要 `clearSelection()`。
