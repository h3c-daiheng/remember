# 智忆 Quickstart

> 5 分钟把智忆跑起来,体验「提交经验 -> Review 发布 -> Recall 召回」完整闭环。
>
> 详细说明见 [USAGE.md](./USAGE.md),各模块开发见各 `README.md`。

---

## 前置条件

- **Docker**(带 compose 插件)
- **一个 OpenAI 兼容 API**(url + key)-- 用于 AI 召回与文档抽取。没有也能跑,只是 AI 功能不可用。

---

## 1. 配置环境变量

在项目根目录生成 `.env`(随机密钥自动生成,只需填你的 LLM 配置):

```bash
cat > .env <<EOF
MYSQL_ROOT_PASSWORD=$(openssl rand -hex 16)
JWT_SECRET=$(openssl rand -hex 32)
MODEL_GATEWAY_APP_TOKEN=$(openssl rand -hex 16)
OPENAI_API_BASE=https://ark.cn-beijing.volces.com/api/coding/v3
OPENAI_API_KEY=sk-替换为你的key
OPENAI_CHAT_MODEL=glm-5.2
OPENAI_EMBEDDING_MODEL=doubao-embedding-text
EOF
```

> 把 `OPENAI_API_KEY` 改成你的真实 key;`OPENAI_API_BASE` / 模型名按你的 OpenAI 兼容端点改(示例是火山方舟)。embedding 模型务必用**文本**模型,不要用 vision 模型,否则召回质量差。

---

## 2. 一键启动

```bash
docker compose up -d --build
```

首启约 2~5 分钟(maven 打包后端 + npm 构建前端)。完成后:

```bash
docker compose ps          # 5 个容器都 Up
docker compose logs -f server  # 看到 "Started ZhiyiApplication" 即就绪
```

启动的 5 个服务:

| 服务 | 作用 |
|------|------|
| `mysql` | 数据库(自动建表 + 种子数据) |
| `zhiyi-server` | 后端(本地登录 + Memory API) |
| `zhiyi-web` | 前端(Nuxt SSR) |
| `ai-gateway` | AI 网关(转发到你的 OpenAI 兼容 API) |
| `nginx` | 同域反代(80 端口统一入口) |

---

## 3. 访问与登录

浏览器打开 **`http://localhost`**(部署在服务器则用公网 IP)。

- 点右上角登录,或在地址栏进 `/login`
- 账号 `admin` / 密码 `123456`
- 登录后进入经验页面

> ⚠️ `admin/123456` 是种子账号,公网部署务必改密:
> ```bash
> docker exec -e MYSQL_PWD=$(grep MYSQL_ROOT_PASSWORD .env|cut -d= -f2) zhiyi-mysql \
>   mysql -h127.0.0.1 -uroot -D zhiyi -e "UPDATE sys_user SET password=SHA2(CONCAT('新密码','chat2x_salt'),256) WHERE username='admin';"
> ```

---

## 4. 体验核心闭环

### 4.1 提交一条经验(Agent API Key)

部署时已自动生成一个 Agent API Key,取出来:

```bash
APIKEY=$(docker logs zhiyi-mysql 2>&1 | grep -oE 'bigapp_[a-f0-9]{32}' | head -1)
echo "API Key: $APIKEY"
```

提交一条踩坑经验(进 Capture 草稿):

```bash
curl -X POST http://localhost/api/memory/submit \
  -H "Authorization: Bearer $APIKEY" \
  -H "Content-Type: application/json" \
  -d '{
    "knowledgeType": "experience",
    "repository": "my-project",
    "module": "auth",
    "payload": {
      "task": "实现登录鉴权",
      "observation": "JWT 过期后前端不跳转,用户看到一堆 401",
      "decision": "在响应拦截器里拦截 401,清 token 跳登录页",
      "action": "axios.interceptors.response 加 401 处理",
      "outcome": "过期自动跳登录,验证通过"
    }
  }'
```

返回 `draftId`、`status: "pending_review"` -- 经验进了草稿池。

### 4.2 Review 发布(Web 端)

1. 浏览器登录后访问 **`/capture`**
2. 看到刚提交的草稿,点确认发布
3. 发布后成为已发布经验,可被召回

### 4.3 召回经验

```bash
curl -X POST http://localhost/api/memory/recall \
  -H "Authorization: Bearer $APIKEY" \
  -H "Content-Type: application/json" \
  -d '{"task": "登录过期怎么处理"}'
```

返回 `items`(命中的经验)+ `promptBlock`(可直接注入 Agent 上下文)。刚才发布的那条应被召回。

> 闭环完成: **提交 -> Review -> 召回 -> 越用越准**(配合 `memory_feedback` 反馈)。

---

## 5. 接入 Agent(Cursor / Claude Code)

zhiyi-mcp 让 Agent 调用智忆的 recall/submit。先构建(烘焙后端地址):

```bash
# 把 .env.production 的 BIGAPP_API_BASE 改成你的访问地址
sed -i 's|^BIGAPP_API_BASE=.*|BIGAPP_API_BASE=http://localhost/api|' zhiyi-mcp/.env.production
cd zhiyi-mcp && npm install && npm run build:production && cd ..
```

Cursor 的 `.cursor/mcp.json` 或 Claude Code 全局配置:

```json
{
  "mcpServers": {
    "zhiyi": {
      "command": "node",
      "args": ["/绝对路径/zhiyi-mcp/dist/index.js"],
      "env": {
        "BIGAPP_API_KEY": "上面取到的 bigapp_..."
      }
    }
  }
}
```

配好后,Agent 就能在干活前 `memory_recall` 读经验、干完后 `memory_submit` 沉淀经验。

---

## 6. 常用命令

```bash
docker compose up -d --build   # 启动 / 重建
docker compose logs -f server  # 后端日志
docker compose logs -f ai-gateway  # AI 网关日志(看 LLM 调用)
docker compose down            # 停止
docker compose down -v         # 停止并清库(重来会自动重新初始化)
```

---

## 下一步

- 完整功能与配置参考:[USAGE.md](./USAGE.md)
- MCP 工具参数细节:[zhiyi-mcp/README.md](./zhiyi-mcp/README.md)
- 后端配置项:[zhiyi-server/README.md](./zhiyi-server/README.md)

## 常见问题

| 现象 | 处理 |
|------|------|
| `docker compose up` 后公网打不开 | 云服务器安全组放行 80 端口 |
| Recall 报 502 `未配置 OPENAI_API_BASE` | `.env` 没填 `OPENAI_API_KEY`,填后 `docker compose up -d ai-gateway` |
| 召回结果不相关 | embedding 模型用了 vision 版,换成文本 embedding 模型 |
| 忘记 Agent API Key | `docker logs zhiyi-mysql 2>&1 \| grep -A2 seed-apikey` |
| 改了后端代码 | `docker compose up -d --build server` 重建后端镜像 |
