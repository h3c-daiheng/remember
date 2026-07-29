# 工作空间与知识管理增强设计

- 日期:2026-07-28
- 模块:zhiyi-server(后端)+ zhiyi-web(前端)
- 状态:待评审

## 1. 背景与目标

智忆当前已具备工作空间隔离的知识 CRUD(单条)、Capture 草稿 Review、文档 AI 导入等能力,但在工作空间与知识的管理操作上存在缺口。本次新增 5 个需求,补齐 Web 端的管理与流转能力:

1. 在 Web 上删除某个工作空间
2. 导出某工作空间的知识为 Markdown
3. 批量导入经验(需求 2 导出 Markdown 的逆向)
4. 编辑工作空间的「经验、规则」(经验已支持,重点补已发布规则)
5. 批量删除工作空间的「经验、规则」

### 定调决策(已与需求方确认)

| 维度 | 决策 |
|---|---|
| 删除工作空间 | 物理删除 + 输入工作空间名称二次确认(不可逆) |
| 批量导入流程 | 直接结构化导入为草稿(不经 AI),人工 Review 后发布 |
| 导出范围 | 全部四种类型(experience / rule / workflow / decision),仅已发布 |
| 编辑已发布规则 | 草稿修订重发(基于旧版创建草稿 → 发布 → supersede 替代旧版) |

## 2. 现状与缺口

### 后端(zhiyi-server)

- `WorkspaceController` 仅有 `GET /workspace/list`、`POST /workspace/switch`、`POST /workspace`(创建),**无删除端点**;无独立 WorkspaceService,逻辑写在 Controller。
- `KnowledgeController` 有单条 CRUD,**无批量删除、无导出、无批量导入端点**。
- 现有文档导入 `/import/extract` + `/import/submit` 走 AI 抽取 → capture_draft → Review,与「结构化 Markdown 逆向导入」语义不同。
- **单删隐患**:`KnowledgeService.deleteKnowledge`(`KnowledgeService.java:342`)逻辑删除 knowledge 行,清了 relation 与 vector_ref,但**未调 `deleteChildren`**,导致 fact / artifact / tag 残留为孤儿;feedback 也未清理。
- 数据库无外键约束,删除工作空间需应用层手动级联清理。

### 前端(zhiyi-web,Nuxt 3 + Element Plus)

- 无工作空间删除入口;`WorkspaceSwitcher` 有「切换/新建/设置」三项,权限工具 `isWorkspaceOwner` 已就绪。
- 知识列表 `/memory` 为**卡片式,无多选**;单删已有,批量删除无。
- 经验已支持内联编辑(`ExperienceEditDrawer` + `PUT /knowledge/{id}`);**已发布规则不可编辑**,仅草稿态可改。
- 仅有单条「复制为 Markdown」(`utils/knowledge.js:buildKnowledgeDetailMarkdown`),无工作空间级导出 / 下载。
- 有单文档导入页 `pages/import`(el-upload + AI 抽取),可参考其文件读取模式。

## 3. 整体架构

- **后端新增 4 个端点**:`DELETE /workspace/{id}`、`GET /knowledge/export`、`POST /knowledge/import`、`DELETE /knowledge/batch`。
- **规则编辑**复用已有端点:`POST /knowledge`(建草稿)+ `POST /knowledge/{id}/publish` + `POST /knowledge/{id}/supersede`,无新端点。
- **前端**新增对应 service 函数、composable 方法与 UI 入口,复用 `ElMessageBox.confirm`、`el-upload`、`buildKnowledgeDetailMarkdown` 等既有资产。
- **导入 / 导出共用一套 Markdown 格式规范**(单一真相),后端导出端序列化、导入端反序列化,前端不参与解析。

## 4. Markdown 格式规范(导入 / 导出共用)

设计目标:既可人读,又可机器稳定往返解析(round-trip)。导出产生该格式,导入消费该格式。

````markdown
# 工作空间「{workspaceName}」知识导出

> 导出时间:{yyyy-MM-dd HH:mm}  工作空间:{workspaceName}  共 {N} 条

<!-- KNOWLEDGE START -->
---
title: "如何处理模型网关 504 超时"
knowledgeType: experience
project: zhiyi-server
module: memory
repository: zhiyi-server
language: java
framework: spring-boot
tags: ["memory", "timeout"]
lifecycleStatus: 1
---

## Facts

### observation
调用模型网关偶发 504,集中在高峰期……

### decision
接入侧加 3s 超时并重试一次……

### action
在 RetrievalEngine 增加重试与降级……

### outcome
504 下降约 90%……

## Artifacts
- [commit] origin: 588e6da (https://example.com/commit/588e6da)
- [issue] reference: #123 (https://example.com/issues/123)
<!-- KNOWLEDGE END -->

<!-- KNOWLEDGE START -->
---
...
<!-- KNOWLEDGE END -->
````

### 规则细节

- **分块**:每条知识由 `<!-- KNOWLEDGE START -->` 与 `<!-- KNOWLEDGE END -->` 包裹,导入端按此切分。
- **frontmatter**:YAML 风格 `key: value`,字段固定为 `title` / `knowledgeType` / `project` / `module` / `repository` / `language` / `framework` / `tags` / `lifecycleStatus`。`tags` 为 JSON 数组字符串(如 `["a","b"]`),用 Jackson 解析。不引入新 YAML 依赖,采用逐行 `key: value` 解析(字段均为标量或数组)。`lifecycleStatus` 仅用于导出记录,导入时忽略并统一创建为草稿(0)。
- **Facts**:`## Facts` 下每个 `### {block_type}` 子节,正文为 `block_text`。`block_type` 取值:observation / decision / constraint / rule / evidence / action / outcome。多个同 type 的 fact 允许重复出现同一 `###` 标题,按出现顺序保留 `sort_order`。
- **Artifacts**:`## Artifacts` 下列表项 `- [type] role: contentRef (url)`,`url` 可缺省。仅往返 `artifactType` / `artifactRole` / `contentRef` / `artifactUrl`;`authorId` / `artifactTime` 不导出。
- **导出端**用 `StringBuilder` 拼接,复用 `KnowledgeService.getDetail` 聚合的 facts / tags / artifacts。
- **导入端**按 `<!-- KNOWLEDGE -->` 标记切分 → 提取 frontmatter → 解析 Facts / Artifacts → 构造 `KnowledgeSaveRequest`。

## 5. 功能详细设计

### 5.1 删除工作空间

**后端**:`DELETE /workspace/{workspaceId}`

- 请求体:`{ "confirmName": "{必须等于该空间 workspaceName}" }`
- 权限:仅 `owner`(`WorkspaceMemberRole.canManageWorkspace` 且为 owner;用 `isWorkspaceOwner`)。
- 安全校验:`confirmName` 必须与目标空间 `workspace_name` 完全相等,否则 400。
- 目标空间须为当前登录用户激活空间(沿用 `WorkspaceAccessGuard.requireCurrentWorkspaceMember` 模式),即删除当前所在空间;删除其他空间需先 switch。

**级联清理(事务内,按依赖顺序)**:

| 顺序 | 表 | 清理方式 | 关联键 |
|---|---|---|---|
| 1 | governance_issue | 物理删 | workspace_id |
| 2 | governance_scan_batch | 物理删 | workspace_id |
| 3 | capture_ai_review | 物理删 | workspace_id |
| 4 | capture_draft | 物理删 | workspace_id |
| 5 | system_event | 物理删 | workspace_id |
| 6 | memory_feedback | 物理删 | knowledge_id(先查本空间 knowledge_id 集合) |
| 7 | knowledge_vector_ref | 物理删 | knowledge_id |
| 8 | knowledge_relation | 物理删 | workspace_id |
| 9 | knowledge_timeline | 物理删 | workspace_id |
| 10 | knowledge_fact | 物理删 | knowledge_id |
| 11 | knowledge_artifact | 物理删 | knowledge_id |
| 12 | knowledge_tag | 物理删 | knowledge_id |
| 13 | knowledge | **物理删**(自定义 `DELETE FROM knowledge WHERE workspace_id=?`,绕过 @TableLogic) | workspace_id |
| 14 | memory_operation_log | 物理删 | workspace_id |
| 15 | usage_daily | 物理删 | workspace_id |
| 16 | api_key | 物理删 | workspace_id |
| 17 | workspace_governance_config | 物理删 | workspace_id |
| 18 | workspace_member | 物理删 | workspace_id |
| 19 | sys_user | 将 `last_workspace_id` 指向该空间的行置 NULL | last_workspace_id |
| 20 | workspace | 物理删 | id |

> 说明:除 `knowledge` 表有 `@TableLogic` 需自定义 SQL 物理删外,其余表 mapper 的 `delete(Wrapper)` 即物理删。`ai_model_config`、`organization`、`sys_user` 本身不按工作空间隔离,不清理。

**前端**:

- `WorkspaceSwitcher` 下拉新增「删除工作空间」项,仅 owner 可见(`useWorkspacePermission`)。
- 点击弹 `ElMessageBox`(自定义内容含 `el-input`),要求输入工作空间名称;输入与当前空间名相等时才允许确认。
- service 新增 `deleteWorkspaceRequest(workspaceId, confirmName)`;composable `useWorkspace` 新增 `deleteWorkspace`,成功后刷新 `workspaceList`;若删除的是当前激活空间,自动切换到列表中第一个剩余空间,无剩余则提示重新登录/创建空间。

### 5.2 导出工作空间知识为 Markdown

**后端**:`GET /knowledge/export`

- 查询参数:`knowledgeType`(可选,默认全部四种)、`lifecycleStatus`(默认 1,仅已发布)。
- 权限:当前工作空间成员(沿用 `requireWorkspaceId`)。
- 响应:`Content-Type: text/markdown; charset=utf-8`,`Content-Disposition: attachment; filename="{workspaceName}-知识导出-{yyyyMMdd}.md"`(中文文件名 URL 编码)。
- 流程:查当前空间已发布知识 id 列表 → 逐条 `getDetail`(含 facts / tags / artifacts)→ 按第 4 节格式序列化 → 直接写 `HttpServletResponse` 输出流(不落盘)。
- 空间无已发布知识时,返回仅含文件头的空导出(不报错)。

**前端**:

- `/memory` 列表页工具栏新增「导出 Markdown」按钮。
- 点击调 `exportKnowledgeRequest(params)` → 用响应 Blob 触发浏览器下载(`a[download]`)。
- service 新增 `exportKnowledgeRequest`。

### 5.3 批量导入经验

**后端**:`POST /knowledge/import`

- 请求:`multipart/form-data`,字段 `file`(`.md`,大小上限沿用 `MemoryConstants.IMPORT_MAX_CONTENT_LENGTH`)。
- 权限:`canEditKnowledge`(owner / admin / editor)。
- 流程:
  1. 读取文件文本 → 按 `<!-- KNOWLEDGE START/END -->` 切分 → 逐块解析为 `KnowledgeSaveRequest`(第 4 节格式)。
  2. 解析阶段校验:每块必须有 `title` 与至少一个 Fact;`knowledgeType` 必须合法。格式错误收集为明细,不创建。
  3. 合法条目在**一个事务内**逐条调 `KnowledgeService.create(saveRequest, operatorUserId, workspaceId, null)`(`publish=false`),生成 `lifecycleStatus=0` 的 knowledge 草稿,`sourceCaptureDraftId=null`(手工来源)。
  4. 返回 `{ total, imported, failed, failures: [{ index, title, reason }] }`。
- 整批事务策略:解析全部通过后,创建阶段任一失败则整批回滚(避免半导入);解析阶段的部分格式错误不影响其他合法条目,但创建阶段不可部分成功。

> 草稿语义:导入产物是 knowledge 草稿(status=0),而非 capture_draft。用户在 `/memory` 的「草稿」Tab 中 Review、编辑后 `publish` 发布。不进入 capture_draft / system_event 流程,避免污染 Agent/文档导入的来源追踪。

**前端**:

- `/memory` 列表页工具栏新增「批量导入」按钮。
- 弹出上传对话框(`el-upload`,drag,`.md`,`auto-upload=false`),参考 `pages/import` 的文件读取模式;点击「开始导入」上传。
- 上传后展示结果:成功 N 条,失败 M 条 + 明细列表(序号 / 标题 / 原因)。
- service 新增 `importKnowledgeRequest(file)`。

### 5.4 编辑经验 / 规则

**经验**:沿用现有内联编辑(`ExperienceEditDrawer` + `PUT /knowledge/{id}`),本需求对其无新增改动。

**已发布规则(新增「修订」入口)**:走草稿修订重发,复用已有端点,不新增后端端点。

流程:
1. 规则详情页 `pages/rule/[id].vue` 已发布态新增「修订」按钮(权限 `canEditKnowledge`)。
2. 点击:读取当前规则详情(facts / tags / artifacts / 元信息)预填 → 调 `POST /knowledge`(`publish=false`,`knowledgeType=rule`)创建新草稿 → 跳转 `/rule/drafts/{newId}`。
3. 用户在草稿编辑器(`KnowledgeDraftEditorShell`)修改 → `POST /knowledge/{newId}/publish` 发布。
4. 发布成功后弹确认「是否替代原版本?」→ 调 `POST /knowledge/{newId}/supersede`,body `{ "predecessorId": {原规则id} }`。
5. `GraphGovernanceService.supersede` 建立 supersedes 边、下架原规则、写时间线。原规则 `lifecycleStatus=2`,新规则成为当前版本。

**前端**:

- `pages/rule/[id].vue` 已发布态加「修订」按钮。
- composable 新增 `useRuleRevise`:封装「预填 + 创建草稿 + 跳转」。
- 发布后 supersede 确认弹窗;若用户跳过,草稿已发布但不替代旧版(可后续手动在详情页 supersede,留作兜底)。

### 5.5 批量删除经验 / 规则

**后端**:`DELETE /knowledge/batch`

- 请求体:`{ "ids": [Long] }`。
- 权限:逐条校验 `WorkspaceMemberRole.canDeleteKnowledge(role, operator, creator)`(管理者可删任意,其余仅删本人创建),与单删一致。
- 流程:对每个 id,在**单条事务**内执行:
  1. `requireKnowledgeInWorkspace`(校验归属当前空间)
  2. `requireDeletePermission`(逐条权限)
  3. `relationEngine.deleteRelationsByKnowledgeId`(物理删 relation)
  4. `deleteChildren`(物理删 fact / artifact / tag / vector,**补齐单删遗漏**)
  5. 物理删 `memory_feedback` 按 knowledge_id(**补齐单删遗漏**)
  6. `knowledgeMapper.deleteById`(逻辑删 knowledge)
- 整体不整批回滚:逐条独立,成功即删,失败(无权限 / 不存在)收集为明细返回。
- 返回 `{ total, deleted, failed, failures: [{ id, reason }] }`。

> 同时建议在本期顺手修复单删 `deleteKnowledge` 的子表清理缺口(补调 `deleteChildren` 与 feedback 清理),使单删与批量删行为一致。该修复纳入本期范围。

**前端**:

- 列表卡片新增 checkbox(选中态),顶部出现批量操作栏「批量删除(N)」,选中数 > 0 时启用。
- 点击 → `ElMessageBox.confirm` 二次确认 → 调 `batchDeleteKnowledgeRequest(ids)` → 展示结果(成功 N,失败 M + 明细)→ 刷新列表。
- service 新增 `batchDeleteKnowledgeRequest(ids)`。
- 多选参考 `composables/useGovernance.js` 的多选范式。

## 6. 权限矩阵

| 操作 | 角色 |
|---|---|
| 删除工作空间 | owner |
| 导出知识 | 当前空间任意成员 |
| 批量导入 | owner / admin / editor |
| 编辑经验 / 规则修订 | owner / admin / editor |
| 批量删除 | 管理者(owner/admin)可删任意;editor/viewer 仅删本人创建(逐条) |

## 7. 错误处理

| 场景 | 处理 |
|---|---|
| 删空间:非 owner | 403 |
| 删空间:confirmName 不匹配 | 400「输入的名称与工作空间名称不符」 |
| 删空间:目标非当前激活空间 | 400「请先切换到目标工作空间」 |
| 删空间:事务失败 | 回滚,500,空间与数据保持原状 |
| 导出:空间无知识 | 返回仅含文件头的空 md,不报错 |
| 导入:文件超限 / 非 .md | 400 |
| 导入:格式错误(缺 title / Fact / 类型非法) | 该条计入 failures,合法条目继续;创建阶段失败整批回滚 |
| 批量删:逐条无权限 / 不存在 | 计入 failures,其他继续删除 |
| 规则修订:创建草稿失败 | 提示错误,不跳转 |
| 规则修订:supersede 失败 | 草稿已发布,提示可手动 supersede(不回滚发布) |

## 8. 测试策略

### 后端

- **删空间**:删后查询全部 20 张关联表,该空间数据为空;`sys_user.last_workspace_id` 已置空;非 owner 调用 403;confirmName 不符 400;事务中途失败回滚。
- **导出**:导出内容包含全部已发布知识;frontmatter 与 Facts 结构符合规范;空空间返回仅文件头;文件名中文编码正确。
- **导入**:用导出产物 round-trip 导入,生成的草稿 title / facts / tags / artifacts(type/role/contentRef/url)与原知识一致,`lifecycleStatus` 为草稿(0);格式错误条目进 failures;创建阶段失败整批回滚;非 editor 403。
- **批量删**:删后 fact / artifact / tag / vector / relation / feedback 均无残留(无孤儿);逐条权限失败进 failures;单删修复后行为与批量删一致。
- **规则修订**:创建草稿 → 发布 → supersede 后,原规则 status=2、新规则 status=1、存在 supersedes 边、timeline 记录 supersede 事件。

### 前端

- 删空间:owner 可见入口、输名称确认、成功后列表刷新与空间切换。
- 导出:点击下载得到 .md 文件。
- 导入:上传 .md、展示成功/失败明细。
- 批量删:多选、二次确认、结果展示、列表刷新。
- 规则修订:已发布规则「修订」按钮可见、跳转草稿编辑器预填正确、发布后 supersede 确认。

## 9. 非目标(YAGNI)

- 工作空间改名、成员管理、转让(本期仅删除)。
- 导出为 JSON / CSV 等其他格式。
- 导入查重 / 合并 / 覆盖。
- 经验 ↔ 规则类型转换。
- 批量编辑(本期仅批量删除)。
- 导入直接发布(本期仅导入为草稿)。
- 工作空间级导出含草稿(仅已发布)。

## 10. 落地清单

### 后端新增 / 修改

- `WorkspaceController` + 新建 `WorkspaceService`(承载删除与级联清理逻辑)
- `KnowledgeController` 新增 `export` / `import` / `batch` 端点
- `KnowledgeService` 新增 `deleteByIds`(批量删,补子表清理)、`exportMarkdown`、`importMarkdown` 及 Markdown 序列化 / 反序列化工具
- 修复 `deleteKnowledge` 补调 `deleteChildren` 与 feedback 清理
- 自定义 SQL:knowledge 表物理删(删空间用)

### 前端新增 / 修改

- `services/workspace.service.js` + `deleteWorkspaceRequest`
- `services/knowledge.service.js` + `exportKnowledgeRequest` / `importKnowledgeRequest` / `batchDeleteKnowledgeRequest`
- `composables/useWorkspace.js` + `deleteWorkspace`
- `components/workspace/WorkspaceSwitcher.vue` 删空间入口 + 输名称确认
- `pages/memory/index.vue` 工具栏:导出 / 导入 / 批量删除;列表卡片多选
- `pages/rule/[id].vue` 已发布态「修订」入口 + `useRuleRevise`
