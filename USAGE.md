# 智忆（zhiyi）使用说明书

> AI 时代的经验基础设施——把真实做过的事沉淀成可被人和 AI 持续复用的经验。
>
> 产品代码 `zhiyi`，编号 `4300`。端口规划 `43xx`：本地 `430x` / 测试 `431x` / 生产 `432x`，前端奇数、后端偶数。

---

## 目录

1. [产品简介](#1-产品简介)
2. [架构与三端分工](#2-架构与三端分工)
3. [快速部署（本地 Docker）](#3-快速部署本地-docker)
4. [Web 端使用指南](#4-web-端使用指南)
5. [Agent 接入指南（MCP）](#5-agent-接入指南mcp)
6. [核心业务闭环](#6-核心业务闭环)
7. [配置参考](#7-配置参考)
8. [约束与常见问题](#8-约束与常见问题)
9. [项目结构](#9-项目结构)

---

## 1. 产品简介

智忆是一个**团队级 AI 经验云**。它解决的核心问题是：团队和 Agent 在开发过程中积累的经验、规则、决策，往往散落在聊天记录、文档、个人脑子里，无法被复用。智忆把这些沉淀成结构化的「知识」，并提供**召回（Recall）**能力，让 Agent 在做新任务时能读到相关经验，越用越准。

### 核心闭环

```
Remember（沉淀）  →  Capture（草稿）  →  Review（人工确认）  →  Recall（召回复用）  →  Feedback（反馈优化）
```

- **Remember**：Agent 完成任务后，把经验提交进来。
- **Capture**：所有提交先进草稿池，不直接发布，保证质量。
- **Review**：团队成员在 Web 端人工确认草稿，决定发布路径。
- **Recall**：Agent 做新任务时，按任务意图召回相关经验，注入上下文。
- **Feedback**：Agent 对召回结果反馈有用/无用，用于排序优化。

### 知识类型

| 类型 | 说明 | 典型场景 |
|------|------|----------|
| `experience` | 经验（有取舍、可验证的真实认知） | 「某框架升级踩坑及解法」 |
| `rule` | 规范 / 规则 | 「增量 SQL 只保留一个文档」 |
| `workflow` | 流程 | 「发布前检查清单」 |
| `decision` | 决策 | 「为什么选 A 不选 B」 |

---

## 2. 架构与三端分工

```
┌─────────────────────────────────────────────────────────────┐
│  Agent (Cursor / Claude Code / 其它 MCP 客户端)             │
│         │ stdio                                              │
│         ▼                                                    │
│  zhiyi-mcp   (TypeScript, 用户本地运行, 非服务)             │
│         │ HTTP /memory/*                                     │
│         ▼                                                    │
│  zhiyi-server (Spring Boot 2.7 / JDK8)  ◄── MySQL 8         │
│         ▲                          ▲                         │
│         │ /api (nginx 同域)       │ OAuth/SSO · workspace   │
│         │                          │   check_token · API Key │
│  zhiyi-web (Nuxt3 SSR)             │                         │
│         │ /gonline-api ───────────► gonline (巨人肩膀主站)  │
│         │                          │                         │
│         ▼                          ▼                         │
│    浏览器                       model-gateway (AI 模型网关)  │
│                                  embedding / chat / route    │
└─────────────────────────────────────────────────────────────┘
```

| 模块 | 技术栈 | 端口（dev/test/pro） | 职责 |
|------|--------|----------------------|------|
| `zhiyi-web` | Nuxt 3 + Vue3 + Element Plus | 4301 / 4311 / 4321 | 团队 Web 入口：经验浏览、草稿 Review、搜索、决策/规则中心、图谱、统计 |
| `zhiyi-server` | Spring Boot 2.7 + MyBatis-Plus + MySQL | 4302 / 4312 / 4322 | 后端 API、JWT 鉴权、workspace 隔离、Memory API |
| `zhiyi-mcp` | TypeScript + MCP SDK | 无端口（stdio） | Agent 工具桥：recall / submit / graph / feedback |

**外部依赖**（非本仓库，需独立部署或可达）：

- **gonline**（巨人肩膀主站）：SSO 登录、workspace 管理、Agent API Key 签发。端口 `39xx`。
- **model-gateway**（AI 模型网关）：embedding / chat / route，Recall 召回依赖。端口 `45xx`。

---

## 3. 快速部署（本地 Docker）

适用于本地联调。需已安装 Docker（带 compose 插件）。

### 3.1 一键启动

在项目根目录执行：

```bash
docker compose up -d --build
```

首启会构建两个镜像（maven 打包后端 + npm 构建前端），约 2~5 分钟。后续启动秒级。

启动后自动完成：
- MySQL 8 建库 `zhiyi`，初始化 `sql/schema.sql`（22 张表 + admin/alice/bob 种子账号）。
- zhiyi-server 以 `dev` profile 启动，连接 mysql 容器。
- zhiyi-web 以 Nitro SSR 模式运行。
- nginx 同域反代。

### 3.2 访问与验证

| 入口 | 地址 | 说明 |
|------|------|------|
| 统一入口（推荐） | `http://localhost` | nginx 80，`/api` 自动转后端 |
| 直连前端 | `http://localhost:4301` | Nuxt SSR |
| 直连后端 | `http://localhost:4302` | Spring Boot |
| MySQL | `localhost:3306` | root / `changeme`，库 `zhiyi` |

验证链路：

```bash
# 前端首页（期望 HTTP 200）
curl -I http://localhost/

# 后端鉴权（期望 {"code":401,"message":"请先登录"}）
curl http://localhost/api/auth/me
```

### 3.3 常用命令

```bash
docker compose up -d --build   # 启动 / 重建
docker compose logs -f server  # 跟踪后端日志
docker compose logs -f web     # 跟踪前端日志
docker compose ps              # 查看容器状态
docker compose down            # 停止
docker compose down -v         # 停止并清空数据库（重来会自动重新初始化 schema）
```

### 3.4 源码方式运行（备选）

若需热重载开发，可分别源码启动各模块（需本机具备对应运行时）：

```bash
# 后端（需 JDK8 + Maven + MySQL）
cd zhiyi-server
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 前端（需 Node 18+，推荐 bun）
cd zhiyi-web
bun install && bun run dev        # 访问 http://localhost:4301

# MCP（需 Node 18+）
cd zhiyi-mcp
npm install && npm run build
```

---

## 4. Web 端使用指南

### 4.1 登录

登录走**本地账号密码**（已替代 gonline SSO）。访问 `/login` 输入用户名密码，后端校验 `sys_user` 后签发 JWT，写入 Cookie。

- 默认账号：`admin` / `123456`（种子数据，**公网部署务必改密**）
- 登录后业务页正常可用（经验浏览、Capture Review、统计等）

### 4.2 工作空间（Workspace）

智忆以**工作空间**隔离业务数据与统计。切换空间在 Header 的 `WorkspaceSwitcher`。

- **列表 / 切换当前空间**：智忆 Header。
- **创建 / 成员 / 邀请 / 改名 / 删空间 / 签发 Agent API Key**：在**主站** `/workspace/settings`（侧栏「工作空间」外链打开）。
- **数据隔离**：所有业务 API 按当前 `workspaceId` 过滤。
- **角色**（主站整型 1~4，智忆归一为 `owner/admin/editor/viewer`），控制编辑权限。

### 4.3 主要页面

按功能分组：

**公开页**（无需登录）

| 路由 | 职责 |
|------|------|
| `/` | 首页 |
| `/about` · `/contact` · `/faq` · `/source` | 关于 / 联系 / 常见问题 / 授权源码 |
| `/login` | 登录（跳转主站统一登录） |

**经验浏览与检索**

| 路由 | 职责 |
|------|------|
| `/experience` | 已发布经验列表 |
| `/experience/:id` | 经验详情：Fact Blocks + Artifacts（可编辑） |
| `/experience/drafts/:id` | 经验草稿编辑 |
| `/search` | 经验搜索（语义检索 + Task Match 排序） |
| `/graph` | 经验图谱（关系子图可视化） |

**沉淀与审核**

| 路由 | 职责 |
|------|------|
| `/import` | **文档导入**：粘贴/上传文档 → AI 抽取 Fact Blocks → 进 Capture 草稿 |
| `/capture` | **草稿 Review**：确认 Agent/导入提交的草稿并发布 |
| `/ai-review` | AI 审查记录（提交草稿后自动产生，可重跑） |

**知识中心**

| 路由 | 职责 |
|------|------|
| `/decision` · `/decision/:id` · `/decision/drafts/:id` | 决策中心（已发布 / 详情 / 草稿编辑） |
| `/rule` · `/rule/:id` · `/rule/drafts/:id` | 规则中心（已发布 / 详情 / 草稿编辑） |
| `/memory` | 记忆中心 |

**运维与追踪**

| 路由 | 职责 |
|------|------|
| `/recalls` | Agent 召回请求记录 |
| `/trace` | 闭环追踪（采集 → 确认 → 发布 → 召回全链路） |
| `/stats` | 数据统计（召回 / 沉淀 / 反馈用量与质量） |
| `/governance` | 记忆治理 |
| `/dashboard` | 仪表盘 |

**设置**

| 路由 | 职责 |
|------|------|
| `/settings` | Agent API Key 引导（签发/吊销已迁移至主站） |

### 4.4 上传经验/踩坑记录

所有上传途径**都先进 Capture 草稿**，需在 `/capture` Review 确认后才发布，不会直接公开。有三条途径：

**途径一：Web 文档导入（人工上传，推荐）**

非 Agent 用户手动上传踩坑记录的入口，访问 `/import`：

1. 左侧输入区二选一：**粘贴文本**（PR 描述、复盘记录、ADR 等）或 **上传文件**。
2. AI 抽取为结构化 Fact Blocks 预览（不直接入库）。
3. 确认结果后提交 → 进入 Capture 草稿。
4. 去 `/capture` Review 发布。

> 需**编辑者及以上角色**；AI 抽取依赖 model-gateway，未接入时报错。

**途径二：Agent 通过 MCP 自动沉淀**

配好 zhiyi-mcp 后，Agent 完成任务时调 `memory_submit`（详见 [第 5 节](#5-agent-接入指南mcp)）：

```json
{
  "knowledgeType": "experience",
  "payload": {
    "task": "任务背景",
    "observation": "踩坑现象",
    "decision": "原因分析/决策",
    "action": "解决动作",
    "outcome": "验证结果"
  }
}
```

**途径三：直接调 API（脚本化）**

- Agent 途径：`POST /memory/submit`，Header 带 Agent API Key。

```bash
curl -X POST http://localhost/api/memory/submit \
  -H "Authorization: Bearer <你的 Agent API Key>" \
  -H "Content-Type: application/json" \
  -d '{
    "knowledgeType": "experience",
    "repository": "zhiyi-server",
    "module": "memory",
    "payload": {
      "task": "修复 MySQL 容器初始化建表失败",
      "observation": "init 脚本用 -h 127.0.0.1 连不上，表数为 0",
      "decision": "临时服务器 --skip-networking，改走默认 socket",
      "action": "init-schema.sh 去掉 -h 并加 --get-server-public-key",
      "outcome": "22 张表自动建好"
    }
  }'
```

> 直连后端时改为 `http://localhost:4302/memory/submit`（去掉 `/api` 前缀）。成功返回 `draftId` / `eventId`，草稿进入 `/capture` 待审。

- Web 登录态途径：`POST /import/extract` 抽取 → `POST /import/submit` 提交，带登录 Cookie。

**踩坑记录字段映射**：踩坑记录即 `knowledgeType=experience`，`observation`（现象）+ `decision`（决策）+ `action`（动作）天然契合。注意「提交门」：未遵守既有规范的执行问题不提交 experience，应补成 `rule`/`workflow` 或改规范；只有有取舍且可验证的真实新认知才提交 experience。

### 4.5 Review 流程（Capture 草稿确认）

1. 草稿来源：Agent `memory_submit` 或 Web `/import` 文档导入 → 进入 Capture 草稿池（`pending_review`）。
2. 团队成员在 `/capture` 查看草稿，Review 页**按 Agent 声明的 `knowledgeType` 预选确认路径**。
3. 确认后：
   - `experience` → 直接发布为已发布经验。
   - `rule` / `workflow` / `decision` → 写入对应类型的草稿，可继续编辑后发布。
4. 发布后的知识即可被 `memory_recall` 召回。

---

## 5. Agent 接入指南（MCP）

zhiyi-mcp 通过 stdio 与 MCP 客户端通信，把工具调用转发到 zhiyi-server 的 Memory API（`/memory/*`）。

### 5.1 前置：获取 Agent API Key

本地 Docker 部署时，`docker/seed-apikey.sh` 会在初始化时自动生成一条默认 API Key 并写入 `api_key` 表，明文打印在 MySQL 容器日志里：

```bash
docker logs zhiyi-mysql 2>&1 | grep -A2 "seed-apikey.*API Key"
```

默认 Key 由 `seed-apikey.sh` 随机生成并打印在 mysql 日志中（仅显示一次，绑定 workspace 1，权限 recall+remember）。**公网部署请吊销并重新签发**。

### 5.2 Cursor / Claude Code 配置

本地已构建的 MCP（`.env.production` 的 `BIGAPP_API_BASE` 已设为 `http://<你的访问地址>/api` 并烘焙进 `dist`）。在 MCP 配置（Cursor 的 `.cursor/mcp.json` 或 Claude Code 全局设置）中添加：

```json
{
  "mcpServers": {
    "zhiyi": {
      "command": "node",
      "args": ["<项目根>/zhiyi-mcp/dist/index.js"],
      "env": {
        "BIGAPP_API_KEY": "bigapp_替换为你的Key"
      }
    }
  }
}
```

> 本地构建步骤：`cd zhiyi-mcp && npm install && npm run build:production`。`memory_submit` / `memory_feedback` / `memory_graph_explore` 可用；`memory_recall` 需 model-gateway。

### 5.3 四个工具

| 工具 | 后端 API | 用途 |
|------|----------|------|
| `memory_recall` | `POST /memory/recall` | 按任务上下文召回相关经验 Fact Blocks |
| `memory_submit` | `POST /memory/submit` | 统一提交记忆草稿（experience/rule/workflow/decision） |
| `memory_graph_explore` | `POST /memory/graph-explore` | 以 centerId 为中心探索经验关系子图 |
| `memory_feedback` | `POST /memory/feedback` | 对 Recall 结果提交效果反馈 |

#### memory_recall —— 召回经验

**调用约定（重要）**：
- `task` **只写业务意图**（目标、约束、现象），例如「添加关于我们、联系我们页面」。
- **禁止**把项目代号（`智测`、`zhice`、`zhiyi` 等）拼进 `task`，会污染检索排序。
- 项目定位用独立字段：`project` / `module` / `repository`。

必填 `task`；可选 `repository` / `project` / `module` / `currentFile` / `modifiedFiles` / `factTypes` / `knowledgeTypes` / `limit` / `expandGraph` / `graphDepth` / `graphRelationTypes`。

返回 `sessionId`（供 feedback 关联）、`items`（含 `knowledgeId`、`title`、`facts`、`score`）、`promptBlock`（可直接注入 Agent 上下文）。

#### memory_submit —— 提交经验

**提交门（Submit Gate）**——提交前先自检根因归类：

1. **执行问题**（未遵守既有规范）→ **不提交** experience。
2. **规范缺口** → 用 `knowledgeType=rule/workflow` 提交，或改 README/规范。
3. **真实新认知**（有取舍且可验证）→ 可提交 experience。

按类型必填：

| knowledgeType | 必填内容 |
|---------------|----------|
| `experience`（默认） | `observation` + `decision` + `action`，或 `facts` |
| `rule` / `workflow` / `decision` | `title`（或 `task`）+ `facts`（至少一条非空） |

> 禁止传 `"artifacts": [{}, {}]` 空对象。不传时 MCP 会从 `modifiedFiles` 与 `payload.action` 中的路径自动推断。

示例（Experience）：
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

**踩坑记录**：直接用上面的 experience 形式，`observation` 写现象/坑、`decision` 写根因与决策、`action` 写改动、`outcome` 写验证结果。若踩坑根因是**规范缺口**（应沉淀为流程而非记个人经验），按提交门改用 `rule` 提交：

示例（Rule）：
```json
{
  "knowledgeType": "rule",
  "module": "deploy",
  "title": "MySQL 8 容器初始化脚本须走 socket",
  "facts": [
    { "type": "rule", "text": "docker mysql entrypoint 临时服务器为 --skip-networking，init 脚本必须走默认 socket，不能 -h 127.0.0.1" },
    { "type": "constraint", "text": "caching_sha2_password 首次连接需 --get-server-public-key，否则认证失败、表建不上" }
  ]
}
```

#### memory_feedback —— 反馈

必填 `knowledgeId` + `feedbackType`（`used` / `helpful` / `not_helpful` / `outdated` / `wrong`）；可选 `sessionId` / `comment`。

> 各工具完整参数与返回字段详见 `zhiyi-mcp/README.md`。

---

## 6. 核心业务闭环

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

**典型使用姿势**：

1. 开始任务前，Agent 调 `memory_recall`，把 `promptBlock` 注入上下文。
2. 任务完成后，按「提交门」判断是否调 `memory_submit` 沉淀经验。
3. 团队在 Web `/capture` Review 草稿，发布有效知识。
4. Agent 对召回结果调 `memory_feedback`，让排序越来越准。

---

## 7. 配置参考

### 7.1 端口规划

| 服务 | 本地 dev | 测试 test | 生产 pro |
|------|----------|-----------|----------|
| zhiyi-web | 4301 | 4311 | 4321 |
| zhiyi-server | 4302 | 4312 | 4322 |
| gonline | 3905 / 3902 | 3913 / 3912 | 392x |
| model-gateway | 4502 | — | 4522 |

### 7.2 后端关键配置（`zhiyi-server/src/main/resources/application-*.yml`）

部署前**必须替换**以下占位符：

| 配置项 | 说明 |
|--------|------|
| `spring.datasource.*` | MySQL 地址 / 账号 / 密码（默认密码 `changeme`） |
| `zhiyi.auth.gonline.client-id` / `client-secret` | gonline OAuth 凭据（本地登录已替代，可不配） |
| `zhiyi.auth.jwt-secret` | JWT 签名密钥，**长度须 ≥ 32**，部署前换随机值 |
| `zhiyi.model-gateway.base-url` / `app-token` | AI 模型网关地址与凭据 |
| `zhiyi.cors.allowed-origin-patterns` | 生产改为真实前端域名，**禁止 `*`** |

> Docker 本地联调通过环境变量覆盖（`SPRING_DATASOURCE_URL` 等），无需改 yml。

### 7.3 前端环境变量（`zhiyi-web/.env.*`）

| 变量 | 说明 |
|------|------|
| `NUXT_PUBLIC_GONLINE_WEB_ORIGIN` | 已改本地登录，仅占位 |
| `NUXT_PUBLIC_COOKIE_DOMAIN` | 本地同域留空 |
| `NUXT_GONLINE_API_SERVER_TARGET` | SSR `/gonline-api` 代理目标（gonline 未接入，占位指向 server） |

### 7.4 MCP 运行时变量

| 变量 | 必填 | 说明 |
|------|------|------|
| `BIGAPP_API_KEY` | 是 | 工作空间签发的 Agent 密钥 |
| `BIGAPP_API_BASE` | 否 | 覆盖构建时烘焙的后端地址（一般不需要） |

---

## 8. 约束与常见问题

### 8.1 外部依赖

- **登录已本地化（不依赖 gonline）**：`/auth/login` 走本地账号密码 + JWT，`ApiKeyService` 本地查表验证。gonline 相关类保留为死代码，不再调用。
- **AI 召回强依赖 model-gateway**：Recall 的 embedding、`/import` 文档抽取依赖它。未接入时这两块报错，但不影响登录、经验 CRUD、Capture Review、Agent 提交。
- **后端可独立启动**：启动期不调用外部服务。

> 若要解锁 AI 召回/文档导入，需接入 model-gateway（`zhiyi.model-gateway.base-url` 指向它）。gonline 不再需要。

### 8.2 安全注意

- 仓库内配置均为占位符，**公开部署前必须替换**数据库密码、OAuth、JWT、模型网关 Token。
- `sql/schema.sql` 含 admin/alice/bob 种子账号（密码 `123456`），**公网部署务必改密**：`UPDATE sys_user SET password=SHA2(CONCAT('新密码','chat2x_salt'),256) WHERE username='admin';`
- 默认 Agent API Key（`bigapp_...`）打印在 mysql 日志，**公网部署请吊销重签**。
- 生产 CORS 须改为真实前端域名，**禁止 `*`**。
- 若仓库历史曾含真实密钥，开源前请轮换全部相关凭据。

### 8.3 常见问题

| 现象 | 原因 / 处理 |
|------|-------------|
| 容器内 Web 调 `/gonline-api` 502 | gonline 未接入（预期），不影响登录与经验功能 |
| 后端启动报 `jwt-secret 长度至少 32` | 已通过 `.env` 的 `JWT_SECRET` 注入；自建时替换为 ≥32 位随机字符串 |
| schema.sql 初始化报 `Duplicate column` | 该 SQL 是「全量+增量」混合脚本，首次初始化用 `mysql -f` 容错（Docker 已通过 `init-schema.sh` 处理） |
| `memory_recall` / `/import` 报错 | 需接入 model-gateway（embedding/AI 抽取），非登录问题 |
| 忘记 API Key | `docker logs zhiyi-mysql 2>&1 \| grep -A2 seed-apikey` |

---

## 9. 项目结构

```
zhiyi/
├── docker-compose.yml          # 本地 Docker 联调编排
├── docker/
│   ├── init-schema.sh          # schema.sql 容错初始化脚本
│   └── seed-apikey.sh          # 生成默认 Agent API Key
├── nginx/
│   └── zhiyi.conf              # 同域反代规则
├── zhiyi-web/                  # Nuxt 3 团队 Web 入口
│   ├── pages/                  # 路由页面
│   ├── components/             # layout / workspace / knowledge / capture
│   ├── composables/            # useAuth / useWorkspace / useKnowledgeList ...
│   ├── services/               # API 层（http / knowledge / capture / auth / workspace）
│   └── server/routes/gonline-api/  # SSR 同源代理
├── zhiyi-server/               # Spring Boot 后端
│   ├── src/main/java/com/zhiyi/
│   │   ├── controller/         # AuthController 等
│   │   ├── memory/             # Memory API / 引擎 / 图谱 / 治理
│   │   ├── auth/               # JWT / gonline OAuth / Agent 鉴权
│   │   └── modelgateway/       # AI 模型网关客户端
│   ├── sql/schema.sql          # 数据库初始化
│   ├── Dockerfile              # 生产镜像（依赖预编译 jar）
│   └── Dockerfile.dev          # 本地多阶段构建
└── zhiyi-mcp/                  # MCP Server（Agent 工具桥）
    ├── src/                    # index / submit / remember / artifact
    └── scripts/build-env.mjs   # 按环境烘焙 API 地址
```

---

> 各模块更详细的开发说明见各自 `README.md`：`zhiyi-web/README.md`、`zhiyi-server/README.md`、`zhiyi-mcp/README.md`。
