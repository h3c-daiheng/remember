# zhiyi-web 本地联调镜像（多阶段：build -> 运行 Nitro SSR .output）
FROM node:20-alpine AS builder
WORKDIR /app
COPY . .
RUN npm install --no-audit --no-fund
RUN npm run build

FROM node:20-alpine
WORKDIR /app
COPY --from=builder /app/.output ./.output
EXPOSE 4301
CMD ["node", ".output/server/index.mjs"]
