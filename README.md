# 智忆 (remember)

> Agent 经验沉淀与召回系统：**提交经验 → Review 发布 → Recall 召回 → 越用越准**。

智忆为 AI Agent（Cursor / Claude Code 等）提供长期记忆能力——干活前 `recall` 读取历史经验，干完后 `submit` 沉淀新经验；经验经人工 Review 发布后即可被召回，配合 `memory_feedback` 反馈持续提升相关性。

## 架构

```
浏览器 ──:80──> nginx ──┬── /api  -> zhiyi-server:4302   (Java/Spring Boot: 登录 + Memory API)
                        └── /     -> zhiyi-web:4301       (Nuxt 3 SSR)
zhiyi-server ──> ai-gateway:4502 ──> OpenAI 兼容 API      (召回 / 文档抽取)
zhiyi-server ──> mysql:3306                              (经验 / 草稿 / 工作空间 / 用户)
Agent ──MCP──> zhiyi-mcp ──/api──> zhiyi-server
```

| 服务 | 目录 | 技术栈 | 端口 |
|------|------|--------|------|
| zhiyi-server | `zhiyi-server/` | Spring Boot 2.7 + MyBatis-Plus (JDK 8) | 4302 |
| zhiyi-web | `zhiyi-web/` | Nuxt 3 (SSR) | 4301 |
| ai-gateway | `ai-gateway/` | Node (零依赖, 转发 OpenAI 兼容 API) | 4502 |
| zhiyi-mcp | `zhiyi-mcp/` | TypeScript (MCP Server) | - |
| mysql | - | MySQL 8.0 | 3306 |
| nginx | `nginx/` | nginx:alpine (同域反代) | 80 |

## 目录结构

```
.
├── build.sh              # 全流程编译脚本 (本地构建 + docker 镜像 + 离线包)
├── docker-compose.yml    # 本地联调编排 (build: 版)
├── .env.example          # 环境变量模板 (复制为 .env)
├── .mcp.json.example     # MCP 客户端配置模板
├── QUICKSTART.md         # 5 分钟上手
├── USAGE.md              # 完整功能与配置说明
├── docker/               # 数据库初始化脚本 (init-schema.sh / seed-apikey.sh)
├── nginx/                # nginx 反代配置
├── zhiyi-server/         # 后端 (Maven, 见其 README)
├── zhiyi-web/            # 前端 (Nuxt, 见其 README)
├── zhiyi-mcp/            # MCP Server (见其 README)
└── ai-gateway/           # AI 网关
```

## 快速开始

**前置条件**：Docker（带 compose 插件）+ 一个 OpenAI 兼容 API（可选，没有则 AI 功能不可用）。

```bash
# 1. 生成 .env (随机密钥; 仅需填 OPENAI_API_KEY)
cp .env.example .env
# 编辑 .env, 把 OPENAI_API_KEY 改成你的真实 key

# 2. 一键启动 (首启约 2~5 分钟: 容器内 maven 打包 + npm 构建)
docker compose up -d --build

# 3. 访问 http://localhost, 登录 admin / 123456
```

详细闭环体验（提交经验 → Review → 召回）见 [QUICKSTART.md](./QUICKSTART.md)。

## 编译脚本 `build.sh`

全流程编译脚本，覆盖本地构建、docker 镜像、离线部署包。

```bash
./build.sh                     # 构建全部本地产物 (server jar + web .output + mcp dist)
./build.sh all --docker        # 本地产物 + docker 镜像
./build.sh server|web|mcp      # 单独构建某组件
./build.sh mcp --env test      # 指定 MCP 烘焙环境 (production/test/development)
./build.sh docker              # 构建 docker 镜像 (默认 linux/amd64)
./build.sh docker --arch arm64 # 构建 arm64 镜像 (需 QEMU)
./build.sh offline             # 打离线部署包 (需先 docker + mcp)
./build.sh clean               # 清理构建产物
```

**离线部署包**：`./build.sh offline` 生成 `zhiyi-offline-<arch>-<时间戳>.tar.gz`，含 5 个镜像归档 + image 版 compose + 启停脚本 + 配置。拷到目标机 `tar xzf` 后 `./start.sh` 一键部署，全程不联网。

> 本地构建工具链：`mvn`(Maven 3.x) + `node>=18`/`npm`。脚本会在缺失时给出安装提示。

## 接入 Agent

构建 MCP（烘焙后端地址）并配置到 Cursor / Claude Code：

```bash
# 按你的访问地址改 zhiyi-mcp/.env.production 的 BIGAPP_API_BASE
cd zhiyi-mcp && npm install && npm run build:production && cd ..
```

参考 [.mcp.json.example](./.mcp.json.example) 配置 `BIGAPP_API_KEY`（部署后从 mysql 日志取）。

## 文档

- [QUICKSTART.md](./QUICKSTART.md) — 5 分钟上手与核心闭环
- [USAGE.md](./USAGE.md) — 完整功能与配置参考
- [zhiyi-server/README.md](./zhiyi-server/README.md) — 后端配置项
- [zhiyi-mcp/README.md](./zhiyi-mcp/README.md) — MCP 工具参数细节

## 安全注意

- 仓库内配置均为占位符；部署前务必替换数据库密码、JWT 密钥、OpenAI Key、模型网关 Token。
- `admin/123456` 为种子账号，公网部署务必改密。
- 真实 `.env` / `.mcp.json` 已被 `.gitignore` 忽略，勿提交含密钥的配置。
