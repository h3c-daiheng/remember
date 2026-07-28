# zhiyi-server

**智忆**（产品代码 `zhiyi`，编号 `4300`）后端服务，基于 Spring Boot 2.7 + MyBatis-Plus。

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 1.8 | 语法约束 |
| Spring Boot | 2.7.18 | Web、校验 |
| MyBatis-Plus | 3.5.7 | ORM、逻辑删除 |
| PageHelper | 2.1.0 | 列表分页 |
| Hutool | 5.8.26 | 工具库 |
| MySQL Connector | 8.0.33 | 数据库驱动 |

## 启动

启动前请按环境修改 `src/main/resources/application-*.yml` 中的占位配置：

- 数据库：`spring.datasource.url` / `username` / `password`（默认密码为 `changeme`）
- OAuth：`zhiyi.auth.gonline.client-id` / `client-secret`
- JWT：`zhiyi.auth.jwt-secret`（**长度须 ≥32**，部署前换成随机密钥）
- 模型网关：`zhiyi.model-gateway.base-url` / `app-token`
- CORS：`zhiyi.cors.allowed-origin-patterns`（生产改为真实前端域名，**禁止使用 `*`**）

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

| Profile | 端口 | 说明 |
|---------|------|------|
| `dev` | 4302 | 本地开发（默认） |
| `test` | 4312 | 测试环境 |
| `pro` | 4322 | 生产环境 |

主类 `com.zhiyi.ZhiyiApplication`。

## 数据库

执行 `sql/schema.sql` 初始化 `sys_user` 表及测试账号（admin/alice/bob，密码 `123456`）。

> 种子账号仅供本地联调，**禁止用于生产**；公开部署前请删除或改密。

## 开源 / 部署安全注意

- 仓库内配置均为占位符，部署前必须替换数据库密码、OAuth、JWT、模型网关 Token
- 打包或拷贝源码前请删除本地 `logs/`、`.idea/`、前端 `.nuxt/`、MCP 旧 `dist/`，避免带出运行日志与烘焙域名
- 若仓库历史曾含真实密钥，开源前请轮换全部相关凭据

## 保留能力

- 统一 API 响应（`Result`、`PageResult`）
- 全局异常处理
- JWT 登录鉴权（`/auth/*`）
- 可配置 CORS（默认仅本地 Origin）

业务模块在此脚手架之上扩展 controller、service、dao 即可。
