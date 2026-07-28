# 简易 ai-gateway：转发到 OpenAI 兼容 API（无依赖，Node 18+ 内置 fetch）
FROM node:20-alpine
WORKDIR /app
COPY package.json server.js ./
EXPOSE 4502
CMD ["node", "server.js"]
