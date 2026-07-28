# zhiyi-mcp

**智忆**（产品代码 `zhiyi`，编号 `4300`）MCP Server，为 Cursor、Claude Code 等 Agent 提供经验召回、沉淀与反馈能力。

本服务通过 stdio 与 MCP 客户端通信，将工具调用转发至 `zhiyi-server` 的 Memory API（`/memory/*`）。

## 前置条件

1. 已启动 `zhiyi-server`（本地 `dev` profile，端口 `4302`；测试 `test` profile，端口 `4312`）
2. Node.js 18+（需支持原生 `fetch`）

## 安装与构建

```bash
cd zhiyi-mcp
npm install
npm run build              # 本地开发，写入 http://127.0.0.1:4302
npm run build:test         # 测试环境
npm run build:production   # 生产环境
```

构建时会按命令读取对应 `.env.*` 文件，将 `BIGAPP_API_BASE` 写入 `dist/`（用户 MCP 配置无需再填该地址）。

构建产物位于 `dist/index.js`。`dist/` 含烘焙后的 API 地址，**不要把旧 dist 打进开源包或发布包**；发布前请先 `rm -rf dist` 再执行对应 `build:*`。

## 环境文件

| 文件 | 构建命令 | 说明 |
|------|----------|------|
| `.env.development` | `npm run build` | 本地 dev，后端 `4302` |
| `.env.test` | `npm run build:test` | 测试环境 |
| `.env.production` | `npm run build:production` | 生产环境 |

各文件中的 `BIGAPP_API_BASE` 为 Memory API 根地址（不含 `/memory` 后缀）：

| 环境 | 示例值 | 说明 |
|------|--------|------|
| 本地 dev | `http://127.0.0.1:4302` | 直连 zhiyi-server，无 `/api` 前缀 |
| 测试 / 生产 | `https://zhiyi.test.example.com/api` | 经 nginx 同域代理，**须带 `/api`** |

代码会在公网域名未写 `/api` 时自动补全（兼容旧配置）；直连 4302/4312/4322 端口不会追加。

## 运行时环境变量（Cursor MCP 配置）

| 变量 | 说明 | 是否必填 |
|------|------|----------|
| `BIGAPP_API_KEY` | 工作空间签发的 Agent 密钥 | 必填 |
| `BIGAPP_API_BASE` | 覆盖构建时写入的后端地址 | 一般不需要 |

## MCP 工具

| 工具 | 后端 API | 说明 |
|------|----------|------|
| `memory_recall` | `POST /memory/recall` | 根据任务上下文召回相关经验 Fact Blocks |
| `memory_graph_explore` | `POST /memory/graph-explore` | 以 centerId 为中心探索经验关系子图 |
| `memory_submit` | `POST /memory/submit` | 统一提交记忆草稿（experience/rule/workflow/decision） |
| `memory_feedback` | `POST /memory/feedback` | 对 Recall 结果提交效果反馈 |

### memory_submit

Agent 统一提交记忆，**全部进入 Capture 草稿**待 Web 端人工确认。Review 页默认按 Agent 声明的 `knowledgeType` 预选确认路径。

**可选参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| `knowledgeType` | string | `experience` / `rule` / `workflow` / `decision`，**默认 experience** |
| `type` | string | 事件类型，默认 `agent_finished` |
| `payload` | object | 记忆内容，见下表 |
| `title` | string | 标题（rule/workflow/decision 时使用，也可放在 payload.title） |
| `facts` | array | Fact Block 列表（rule/workflow/decision 必填） |
| `repository` / `module` / `project` | string | Recall 上下文 |
| `modifiedFiles` / `artifacts` | | 关联产物 |

**按类型的必填要求**

| knowledgeType | 必填内容 |
|---------------|----------|
| `experience`（默认） | `observation` + `decision` + `action`，或 `facts` |
| `rule` / `workflow` / `decision` | `title`（或 `task`）+ `facts`（至少一条非空） |

**返回字段**

- `draftId`：Capture 草稿 ID
- `eventId`：Event ID
- `knowledgeType`：Agent 声明的类型
- `status`：`pending_review`

**示例（Experience）**

```json
{
  "knowledgeType": "experience",
  "repository": "zhiyi-server",
  "module": "memory",
  "payload": {
    "task": "修复 PM2 热更新问题",
    "observation": "容器复用时 pm2 reload 找不到进程",
    "decision": "改用 pm2 startOrReload 并固定进程名",
    "action": "修改 Dockerfile 与 start.sh",
    "outcome": "三轮构建验证通过"
  }
}
```

**示例（Rule）**

```json
{
  "knowledgeType": "rule",
  "module": "memory",
  "title": "库表增量 SQL 规范",
  "facts": [
    { "type": "rule", "text": "增量 SQL 只保留一个文档，建表在前、迭代在后" },
    { "type": "constraint", "text": "迭代语句不要注释" }
  ]
}
```

**提交门（Submit Gate）：** experience 类型调用前须自检根因归类——
1. **执行问题**（未遵守既有规范）→ 不提交 experience；
2. **规范缺口** → 用 `knowledgeType=rule/workflow` 提交，或改 README/规范；
3. **真实新认知**（有取舍且可验证）→ 可提交 experience（须 observation + decision + action，或 facts）。

**注意**：禁止传 `"artifacts": [{}, {}]` 空对象。若不传 artifacts，MCP 会从 `modifiedFiles` 与 `payload.action` 中的文件路径自动推断。

### memory_recall

根据当前任务描述与代码上下文，召回已发布的经验知识。

**调用约定（重要）**

- `task` **只写业务意图**（目标、约束、现象），例如「添加关于我们、联系我们、授权源码相关页面及内容」
- **禁止**把 `project` / `module` / `repository` 的标识词（如 `智测`、`zhice-web`、`zhice`）再拼进 `task`，避免词面污染检索与排序
- 项目定位用独立字段：`project`、`module`、`repository`
- 服务端检索 Query 仅使用意图字段；项目上下文只参与 Ranking

**必填参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| `task` | string | 当前任务描述（仅业务意图，勿重复塞项目代号） |

**可选参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| `repository` | string | 代码仓库（排序上下文，不进检索 Query） |
| `project` | string | 项目名（排序上下文，不进检索 Query） |
| `module` | string | 模块名（排序上下文，不进检索 Query） |
| `currentFile` | string | 当前文件路径 |
| `modifiedFiles` | string[] | 修改过的文件列表 |
| `factTypes` | string[] | 需要返回的 Fact 类型 |
| `knowledgeTypes` | string[] | 召回的知识类型；**未传时 MCP 默认 `["rule","workflow","experience"]`**，Ranking 对 Rule 加权优先 |
| `limit` | number | 返回条数上限 |
| `expandGraph` | boolean | 是否启用 Hybrid Recall（向量种子 + 图谱扩展邻接经验） |
| `graphDepth` | number | 图谱扩展跳数，默认 2，最大 3 |
| `graphRelationTypes` | string[] | 扩展使用的关系类型；默认 `depends_on` / `related_semantic` / `related_decision` |

**返回示例字段**

- `sessionId`：Recall 会话 ID，供 `memory_feedback` 关联
- `items`：召回条目（含 `knowledgeId`、`title`、`facts`、`score`、`scoreBreakdown.graphProximity` 等）
- `promptBlock`：可直接注入 Agent 上下文的文本块

### memory_graph_explore

以指定经验为中心，返回关系子图 JSON，供 Agent 沿 `depends_on` / `related_semantic` 等边导航。

**必填参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| `centerId` | number | 中心节点 `knowledge.id` |

**可选参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| `depth` | number | 扩展跳数，默认 2，最大 3 |
| `limit` | number | 返回边数上限，默认 50 |
| `relationTypes` | string[] | 关系类型过滤，空则返回全部可见边 |

**返回字段**

- `centerId`：中心节点 ID
- `depth`：实际扩展深度
- `nodes`：节点列表（`id`、`title`、`knowledgeType`、`module`、`recallCount`）
- `edges`：边列表（`sourceId`、`targetId`、`relationType`、`confidence`）

### memory_feedback

对 Recall 召回结果提交使用效果反馈，用于后续排序与质量优化。

**必填参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| `knowledgeId` | number | 知识 ID |
| `feedbackType` | string | 反馈类型：`used` / `helpful` / `not_helpful` / `outdated` / `wrong` |

**可选参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| `sessionId` | string | Recall 会话 ID |
| `comment` | string | 补充说明 |

## Cursor 配置

在 Cursor 的 MCP 配置（`.cursor/mcp.json` 或全局设置）中添加：

```json
{
  "mcpServers": {
    "zhiyi": {
      "command": "node",
      "args": ["/path/to/zhiyi/zhiyi-mcp/dist/index.js"],
      "env": {
        "BIGAPP_API_KEY": "粘贴你在工作空间签发的密钥"
      }
    }
  }
}
```

请将 `args` 中的路径替换为本机 `zhiyi-mcp/dist/index.js` 的绝对路径；本地开发请先执行 `npm run build`。若你曾使用旧名 `aec`，请改为 `zhiyi` 并重启 Cursor。

## 闭环流程

```text
Agent 调用 memory_recall 读取相关经验
        │
        ▼
Agent 完成任务 → memory_submit(knowledgeType=...) → Capture 草稿（pending_review）
        │
        ▼
Web Review 按声明类型确认 → Experience 直接发布 / Rule·Workflow·Decision 写入对应草稿
        │
        ▼
Agent 调用 memory_feedback 反馈 Recall 效果
        │
        ▼
Ranking 优化，下次 Recall 越用越准
```

## 项目结构

```text
zhiyi-mcp/
├── src/
│   ├── index.ts              # MCP Server 入口
│   ├── submitRequest.ts      # memory_submit 入参规范化
│   ├── rememberRequest.ts    # Artifact / payload 补全
│   └── artifactSchema.ts     # Artifact 工具 schema
├── package.json
├── tsconfig.json
└── dist/                     # 构建输出（npm run build 后生成）
```

## npm 发布与用户安装

维护者发布：确认 `.env.production` 中 `BIGAPP_API_BASE` → 递增 `package.json` version → `rm -rf dist && npm run build:production` → `npm publish --registry=https://registry.npmjs.org/`。

**用户通过 npm 安装（无需克隆仓库）：**

```bash
npm install -g zhiyi-mcp
# 或使用 npx -y zhiyi-mcp
```

Cursor 配置示例（`npx` 方式）：

```json
{
  "mcpServers": {
    "zhiyi": {
      "command": "npx",
      "args": ["-y", "zhiyi-mcp"],
      "env": {
        "BIGAPP_API_KEY": "粘贴你在工作空间签发的密钥"
      }
    }
  }
}
```

发布包在 `build:production` 时已将生产 API 地址写入 `dist`，与下方「源码本地路径」方式二选一即可。

## 相关说明

- 提交门与 `knowledgeType` 约定：见上文 `memory_submit`
- 发布与分发：见上文「npm 发布与用户安装」
- 后端 Memory API：`zhiyi-server` → `MemoryApiController`

