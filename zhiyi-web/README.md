# zhiyi-web

**智忆**（产品代码 `zhiyi`，编号 `4300`）团队 Web 入口，基于 Nuxt 3。

MVP 聚焦 **Remember → Capture → Review → Recall** 闭环中的 Web 侧：**经验浏览**与**草稿 Review**。

## 本地开发

```bash
bun install
bun run dev
```

访问 [http://localhost:4301](http://localhost:4301)。本地开发时 `/api`、`/gonline-api` 由 Nitro `devProxy` 转发至 `127.0.0.1:4302`、`127.0.0.1:3902`；测试/生产由 nginx 转发（与 chat2x-web 一致）。

## 技术栈

Nuxt 3 · Vue 3 · Element Plus · WindiCSS · Vite

## 目录结构

```text
zhiyi-web/
├── pages/              # 路由页面（MVP 3 页 + 登录/首页）
├── layouts/            # default（公开页）/ app（登录后全站导航）
├── components/
│   ├── layout/         # AppHeader、AppSidebar
│   ├── workspace/      # WorkspaceSwitcher（切换 + 外链主站设置）
│   ├── knowledge/      # Fact Block、Artifact、KnowledgeCard
│   └── capture/        # Capture 草稿 Review 卡片
├── composables/        # 业务 hooks（useAuth、useWorkspace、useKnowledgeList …）
├── services/           # API 层（http、knowledge、capture、auth、workspace）
├── constants/          # 领域常量（Fact 类型、生命周期枚举、角色）
├── utils/              # 纯函数（分组、格式化，无网络）
├── types/              # JSDoc 类型定义
├── middleware/         # 路由中间件（auth）
└── plugins/            # Nuxt 插件（auth、workspace-sync）
```

## 主要页面

| 路由 | 布局 | 职责 |
|------|------|------|
| `/` | default | 首页 |
| `/login` | default | 登录（跳转主站统一登录） |
| `/experience` | **app**（顶栏 + 侧栏） | 已发布经验列表 |
| `/experience/:id` | **app** | Fact Blocks + Artifacts 详情 |
| `/capture` | **app** | Agent 草稿 Review |
| `/search` | **app** | 经验搜索（语义检索 + Task Match 排序） |
| `/decision` | **app** | 决策中心（已发布 / 草稿列表） |
| `/decision/:id` | **app** | Decision 详情 |
| `/decision/drafts/:id` | **app** | Decision 草稿编辑 |
| `/rule` | **app** | 规则中心 |
| `/graph` | **app** | 经验图谱 |
| `/stats` | **app** | 数据统计（按当前工作空间） |
| `/settings` | **app** | Agent API Key 引导（签发/吊销在主站） |

占位页配置见 `constants/placeholders.js`，侧栏分组见 `constants/navigation.js`。

## 工作空间分工

| 能力 | 所在端 |
|------|--------|
| 列表 / 切换当前空间 | 智忆 Header `WorkspaceSwitcher`（主站 `/workspace/*`） |
| 创建 / 成员 / 邀请 / 改名 / 删空间 / Agent API Key | **主站** `/workspace/settings`（侧栏「工作空间」外链打开） |
| 业务数据隔离与统计 | 智忆本地 API（按当前 `workspaceId`） |
| 成员角色 | 主站整型 `1~4`，智忆归一为 `owner/admin/editor/viewer` 后控编辑权限 |

## 分层约定

| 层级 | 职责 | 示例 |
|------|------|------|
| **pages** | 路由编排、错误提示 | 调用 composable，绑定组件 |
| **composables** | 页面级状态与业务流程 | `useKnowledgeList` |
| **components** | 可复用 UI，不含 API | `FactBlockList` |
| **services** | HTTP 与后端契约 | `knowledge.service.js` |
| **constants** | 与后端对齐的枚举 | `FACT_TYPE_LABELS` |
| **utils** | 无副作用工具函数 | `groupFactsByType` |

## 相关说明

- `useWorkspace`：当前空间上下文、切换、从主站返回后同步
- 套餐 / 席位 / 配额：前端常量预留在 `constants/workspace.js`（`PLAN_TYPE_LABELS` 等），默认未启用商业化流程
- Memory API（Recall/Remember/Feedback）：由 `zhiyi-mcp` 接入，Web 不直接调用
