#!/usr/bin/env bash
###############################################################################
# 智忆 (zhiyi) 全流程编译脚本
#
# 用法:  ./build.sh [命令] [选项]
#
# 命令:
#   all       构建全部组件产物 (server jar + web .output + mcp dist)  [默认]
#   server    仅构建后端 jar     (zhiyi-server/target/*.jar)
#   web       仅构建前端产物     (zhiyi-web/.output)
#   mcp       仅构建 MCP 产物    (zhiyi-mcp/dist)
#   gateway   ai-gateway 无构建步骤, 仅提示
#   docker    构建 docker 镜像   (linux/<arch>)
#   offline   打离线部署包       (依赖: 镜像已构建 + mcp dist 已构建)
#   clean     清理所有构建产物
#
# 选项:
#   --docker     all 时顺带构建 docker 镜像
#   --arch ARCH  目标架构 (默认 amd64); 影响 docker buildx 与离线镜像
#   --env ENV    MCP 烘焙环境 (默认 production; 可选 test/development)
#   -h, --help   显示帮助
#
# 示例:
#   ./build.sh                     # 构建全部本地产物
#   ./build.sh all --docker        # 本地产物 + docker 镜像
#   ./build.sh mcp --env test      # 以 test 环境烘焙 MCP
#   ./build.sh docker --arch arm64 # 构建 arm64 镜像 (需 QEMU: docker run --privileged --rm tonistiigi/binfmt --install all)
#   ./build.sh offline             # 打离线包 (需先 docker + mcp)
###############################################################################
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ARCH="${ARCH:-amd64}"
MCP_ENV="production"
WITH_DOCKER=0
CMD="all"

# ---------- 颜色 ----------
if [[ -t 1 ]]; then
  C_R='\033[0;31m'; C_G='\033[0;32m'; C_Y='\033[0;33m'; C_B='\033[0;34m'; C_N='\033[0m'
else
  C_R=''; C_G=''; C_Y=''; C_B=''; C_N=''
fi
log()  { printf "${C_B}[$(date +%H:%M:%S)]${C_N} %s\n" "$*"; }
ok()   { printf "${C_G}[OK]${C_N} %s\n" "$*"; }
warn() { printf "${C_Y}[WARN]${C_N} %s\n" "$*" >&2; }
die()  { printf "${C_R}[ERR]${C_N} %s\n" "$*" >&2; exit 1; }
step() { printf "\n${C_Y}==== %s ====${C_N}\n" "$*"; }

# ---------- 工具链检查 ----------
need() { command -v "$1" >/dev/null 2>&1 || die "缺少依赖: $1 (请先安装)"; }

check_tools() {
  local missing=()
  command -v mvn  >/dev/null 2>&1 || missing+=(mvn)
  command -v npm  >/dev/null 2>&1 || missing+=(npm)
  command -v node >/dev/null 2>&1 || missing+=(node)
  if [[ ${#missing[@]} -gt 0 ]]; then
    warn "缺少本地构建工具: ${missing[*]}"
    warn "  Ubuntu/Debian: apt-get install -y maven nodejs npm"
    warn "  CentOS/RHEL  : yum install -y maven nodejs"
    warn "  或用 nvm 安装 node>=18, maven 官网下载"
    die "请安装缺失工具后重试"
  fi
}

check_docker() {
  command -v docker >/dev/null 2>&1 || die "缺少 docker (构建镜像/离线包必需)"
  docker info >/dev/null 2>&1 || die "docker 守护进程未运行"
}

# 计时
SECS_START=0
timer_start() { SECS_START=$SECONDS; }
timer_show()  { printf "${C_G}总耗时 $((SECONDS - SECS_START))s${C_N}\n"; }

# ---------- 各组件构建 ----------
build_server() {
  step "构建 zhiyi-server (Maven -> jar)"
  need mvn
  ( cd zhiyi-server && mvn -B clean package -DskipTests )
  local jar="zhiyi-server/target/zhiyi-server-1.0.0-SNAPSHOT.jar"
  [[ -f "$jar" ]] || die "后端 jar 未生成: $jar"
  ok "后端 jar: $jar ($(du -h "$jar" | cut -f1))"
}

build_web() {
  step "构建 zhiyi-web (Nuxt -> .output)"
  need npm
  ( cd zhiyi-web && npm install --no-audit --no-fund && npm run build )
  [[ -d zhiyi-web/.output ]] || die "前端 .output 未生成"
  ok "前端产物: zhiyi-web/.output"
}

build_mcp() {
  step "构建 zhiyi-mcp (tsc -> dist, 环境: $MCP_ENV)"
  need npm
  local script
  case "$MCP_ENV" in
    production)  script="build:production" ;;
    test)        script="build:test" ;;
    development) script="build" ;;
    *) die "未知 MCP 环境: $MCP_ENV" ;;
  esac
  ( cd zhiyi-mcp && npm install --no-audit --no-fund && npm run "$script" )
  [[ -f zhiyi-mcp/dist/index.js ]] || die "MCP dist/index.js 未生成"
  ok "MCP 产物: zhiyi-mcp/dist (烘焙 $MCP_ENV)"
}

build_gateway() {
  step "ai-gateway"
  log "零依赖 Node 脚本, 无构建步骤; 运行: node ai-gateway/server.js"
  ok "无需构建"
}

build_all_local() {
  check_tools
  timer_start
  build_server
  build_web
  build_mcp
  build_gateway
  printf "\n"; ok "全部本地产物构建完成"; timer_show
}

# ---------- docker 镜像 ----------
# 单镜像构建: 优先 buildx, 回退 docker build
build_image() {
  local tag=$1 df=$2 ctx=$3
  if docker buildx version >/dev/null 2>&1; then
    docker buildx build --platform "linux/$ARCH" -t "$tag" -f "$df" "$ctx" --load
  else
    warn "无 buildx, 回退 docker build (跨架构需 QEMU)"
    docker build --platform "linux/$ARCH" -t "$tag" -f "$df" "$ctx"
  fi
}

build_docker() {
  step "构建 docker 镜像 (linux/$ARCH)"
  check_docker
  timer_start
  log "构建 zhiyi-server:latest"
  build_image zhiyi-server:latest zhiyi-server/Dockerfile.dev zhiyi-server
  log "构建 zhiyi-web:latest"
  build_image zhiyi-web:latest zhiyi-web/Dockerfile zhiyi-web
  log "构建 zhiyi-ai-gateway:latest"
  build_image zhiyi-ai-gateway:latest ai-gateway/Dockerfile ai-gateway
  log "拉取基础镜像 mysql:8.0 / nginx:alpine"
  docker pull --platform "linux/$ARCH" mysql:8.0
  docker pull --platform "linux/$ARCH" nginx:alpine
  ok "docker 镜像就绪 (linux/$ARCH)"; timer_show
}

# ---------- 离线部署包 ----------
build_offline() {
  step "打离线部署包 (arch=$ARCH)"
  check_docker
  command -v openssl >/dev/null 2>&1 || die "离线包 init-env.sh 需要 openssl"
  local stamp off stage pkg_name
  stamp="$(date +%Y%m%d-%H%M%S)" 2>/dev/null || stamp="manual"
  pkg_name="zhiyi-offline-${ARCH}-${stamp}.tar.gz"

  # 前置检查: 镜像 + mcp dist
  for img in zhiyi-server:latest zhiyi-web:latest zhiyi-ai-gateway:latest mysql:8.0 nginx:alpine; do
    docker image inspect "$img" >/dev/null 2>&1 \
      || die "镜像不存在: $img (请先 ./build.sh docker --arch $ARCH)"
  done
  [[ -f zhiyi-mcp/dist/index.js ]] \
    || die "MCP dist 不存在 (请先 ./build.sh mcp)"

  stage="$(mktemp -d)"
  off="$stage/zhiyi-offline"
  mkdir -p "$off/nginx" "$off/sql" "$off/docker"

  log "归档镜像 -> images.tar.gz"
  docker save zhiyi-server:latest zhiyi-web:latest zhiyi-ai-gateway:latest mysql:8.0 nginx:alpine \
    | gzip > "$off/images.tar.gz"

  log "拷入配置与脚本"
  cp .env.example "$off/.env.example"
  cp nginx/zhiyi.conf "$off/nginx/zhiyi.conf"
  cp docker/init-schema.sh "$off/docker/init-schema.sh"
  cp docker/seed-apikey.sh "$off/docker/seed-apikey.sh"
  cp zhiyi-server/sql/schema.sql "$off/sql/schema.sql"

  log "拷入 zhiyi-mcp (含预构建 dist + node_modules)"
  mkdir -p "$off/zhiyi-mcp"
  cp -r zhiyi-mcp/dist "$off/zhiyi-mcp/dist"
  cp -r zhiyi-mcp/node_modules "$off/zhiyi-mcp/node_modules"
  cp zhiyi-mcp/package.json zhiyi-mcp/package-lock.json zhiyi-mcp/tsconfig.json zhiyi-mcp/README.md \
     "$off/zhiyi-mcp/" 2>/dev/null || true
  cp -r zhiyi-mcp/src "$off/zhiyi-mcp/src" 2>/dev/null || true
  cp zhiyi-mcp/.env.production zhiyi-mcp/.env.example "$off/zhiyi-mcp/" 2>/dev/null || true

  log "生成 image: 版 docker-compose.yml"
  gen_offline_compose > "$off/docker-compose.yml"

  log "生成 start.sh / stop.sh / init-env.sh"
  gen_start_sh   > "$off/start.sh";    chmod +x "$off/start.sh"
  gen_stop_sh    > "$off/stop.sh";     chmod +x "$off/stop.sh"
  gen_initenv_sh > "$off/init-env.sh"; chmod +x "$off/init-env.sh"

  log "生成离线包 README"
  gen_offline_readme > "$off/README.md"

  log "打包 -> $pkg_name"
  tar czf "$pkg_name" -C "$stage" zhiyi-offline
  rm -rf "$stage"
  ok "离线包: $pkg_name ($(du -h "$pkg_name" | cut -f1))"
  log "部署: tar xzf $pkg_name && cd zhiyi-offline && ./start.sh"
}

gen_offline_compose() { cat <<'YAML'
# 智忆离线部署编排 (image: 版; 镜像由 start.sh 从 images.tar.gz 加载)
services:
  mysql:
    image: mysql:8.0
    container_name: zhiyi-mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: zhiyi
      TZ: Asia/Shanghai
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
    ports:
      - "127.0.0.1:3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./docker/init-schema.sh:/docker-entrypoint-initdb.d/01-init-schema.sh:ro
      - ./docker/seed-apikey.sh:/docker-entrypoint-initdb.d/02-seed-apikey.sh:ro
      - ./sql/schema.sql:/schema.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -uroot -p$$MYSQL_ROOT_PASSWORD"]
      interval: 5s
      timeout: 5s
      retries: 20

  server:
    image: zhiyi-server:latest
    container_name: zhiyi-server
    depends_on:
      mysql:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SPRING_DATASOURCE_URL: "jdbc:mysql://mysql:3306/zhiyi?allowMultiQueries=true&useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=GMT%2B8"
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      ZHIYI_AUTH_JWT_SECRET: ${JWT_SECRET}
      ZHIYI_MODEL_GATEWAY_MODE: remote
      ZHIYI_MODEL_GATEWAY_BASE_URL: http://ai-gateway:4502
      ZHIYI_MODEL_GATEWAY_APP_TOKEN: ${MODEL_GATEWAY_APP_TOKEN}
    ports:
      - "127.0.0.1:4302:4302"

  web:
    image: zhiyi-web:latest
    container_name: zhiyi-web
    depends_on:
      - server
    environment:
      PORT: "4301"
      NUXT_GONLINE_API_SERVER_TARGET: "http://server:4302"
      NUXT_PUBLIC_GONLINE_WEB_ORIGIN: "http://localhost"
      NUXT_PUBLIC_COOKIE_DOMAIN: ""
    ports:
      - "127.0.0.1:4301:4301"

  ai-gateway:
    image: zhiyi-ai-gateway:latest
    container_name: zhiyi-ai-gateway
    environment:
      PORT: "4502"
      OPENAI_API_BASE: ${OPENAI_API_BASE:-}
      OPENAI_API_KEY: ${OPENAI_API_KEY:-}
      OPENAI_CHAT_MODEL: ${OPENAI_CHAT_MODEL:-gpt-4o-mini}
      OPENAI_EMBEDDING_MODEL: ${OPENAI_EMBEDDING_MODEL:-text-embedding-3-small}
      APP_TOKEN: ${MODEL_GATEWAY_APP_TOKEN:-}
    ports:
      - "127.0.0.1:4502:4502"

  nginx:
    image: nginx:alpine
    container_name: zhiyi-nginx
    depends_on:
      - web
      - server
    ports:
      - "80:80"
    volumes:
      - ./nginx/zhiyi.conf:/etc/nginx/conf.d/default.conf:ro

volumes:
  mysql_data:
YAML
}

gen_start_sh() { cat <<'SH'
#!/usr/bin/env bash
# 智忆离线部署一键启动 (不联网)
set -e
cd "$(dirname "$0")"
echo "[1/3] 加载镜像..."
docker load < images.tar.gz
echo "[2/3] 生成 .env (首次)..."
[ -f .env ] || ./init-env.sh
echo "[3/3] 启动容器..."
docker compose up -d
echo "完成。访问 http://<本机IP>  账号 admin/123456"
echo "查看日志: docker compose logs -f server"
SH
}

gen_stop_sh() { cat <<'SH'
#!/usr/bin/env bash
cd "$(dirname "$0")"
docker compose down "$@"
SH
}

gen_initenv_sh() { cat <<'SH'
#!/usr/bin/env bash
# 首次生成 .env (随机密钥; OPENAI_* 留空待填)
cat > .env <<EOF
MYSQL_ROOT_PASSWORD=$(openssl rand -hex 16)
JWT_SECRET=$(openssl rand -hex 32)
MODEL_GATEWAY_APP_TOKEN=$(openssl rand -hex 16)
OPENAI_API_BASE=
OPENAI_API_KEY=
OPENAI_CHAT_MODEL=glm-5.2
OPENAI_EMBEDDING_MODEL=doubao-embedding-text
EOF
echo "已生成 .env, 请编辑填写 OPENAI_API_KEY (AI 功能可留空)"
SH
}

gen_offline_readme() { cat <<'MD'
# 智忆离线部署包

## 部署
1. 拷贝本目录到目标机 (需已安装 docker + compose 插件)
2. `./start.sh`  (自动 load 镜像 + 生成 .env + 启动; 全程不联网)
3. 访问 http://<本机IP>  账号 admin/123456 (公网部署务必改密)

## 填写 AI 配置
编辑 `.env` 填入 OPENAI_API_KEY / OPENAI_API_BASE (OpenAI 兼容端点), 然后:
    docker compose up -d ai-gateway

## 常用命令
- `./stop.sh`                       停止
- `docker compose ps`               查看容器
- `docker compose logs -f server`   后端日志
- `docker compose down -v`          停止并清库 (重来会重新初始化)

## 取 Agent API Key
    docker logs zhiyi-mysql 2>&1 | grep -oE 'bigapp_[a-f0-9]{32}' | head -1
MD
}

# ---------- clean ----------
do_clean() {
  step "清理构建产物"
  rm -rf zhiyi-server/target
  rm -rf zhiyi-web/.output zhiyi-web/.nuxt
  rm -rf zhiyi-mcp/dist zhiyi-mcp/src/config.generated.ts
  rm -f images.tar.gz zhiyi-offline-*.tar.gz
  ok "已清理 (node_modules 保留; 如需删除: rm -rf zhiyi-web/node_modules zhiyi-mcp/node_modules)"
}

# ---------- 参数解析 ----------
show_help() { sed -n '2,40p' "$0"; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    all|server|web|mcp|gateway|docker|offline|clean) CMD="$1";;
    --docker) WITH_DOCKER=1;;
    --arch) ARCH="$2"; shift;;
    --env)  MCP_ENV="$2"; shift;;
    -h|--help) show_help; exit 0;;
    *) die "未知参数: $1 (用 -h 查看帮助)";;
  esac
  shift
done

case "$CMD" in
  all)
    build_all_local
    [[ "$WITH_DOCKER" -eq 1 ]] && build_docker
    ;;
  server)   check_tools; build_server;;
  web)      check_tools; build_web;;
  mcp)      check_tools; build_mcp;;
  gateway)  build_gateway;;
  docker)   build_docker;;
  offline)  build_offline;;
  clean)    do_clean;;
  *) die "未知命令: $CMD";;
esac
