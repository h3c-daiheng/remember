#!/bin/sh
# 生成本地默认 Agent API Key（本地联调用；公网请吊销并重新签发）
# hash 用 MySQL SHA2 计算，与后端 ApiKeyUtil.hashPlainKey（sha256Hex(key+KEY_SALT)）一致
# 用 od -N16 读 16 字节转 hex，避免 cat|head 管道的 SIGPIPE
PLAIN_KEY="bigapp_$(od -An -tx1 -N16 /dev/urandom | tr -d ' \n')"
PREFIX=$(printf '%s' "$PLAIN_KEY" | cut -c1-16)
mysql --get-server-public-key -uroot -p"${MYSQL_ROOT_PASSWORD}" -f "${MYSQL_DATABASE}" <<SQL
DELETE FROM api_key WHERE key_name='local-default';
INSERT INTO api_key (workspace_id, key_name, key_hash, key_prefix, permission_recall, permission_remember, status)
VALUES ('1', 'local-default', SHA2(CONCAT('${PLAIN_KEY}', 'bigapp_api_key_salt'), 256), '${PREFIX}', 1, 1, 1);
SQL
echo "============================================"
echo "[seed-apikey] 本地默认 Agent API Key（请记录，仅显示一次）："
echo "$PLAIN_KEY"
echo "============================================"
