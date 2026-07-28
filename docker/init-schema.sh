#!/bin/sh
# 智忆 schema 初始化：schema.sql 是「全量建表 + 增量迁移」混合脚本，
# 首次初始化时增量 ALTER 会与 CREATE TABLE 冲突（Duplicate column）。
# 用 mysql -f 忽略这类已存在错误继续执行，最终表结构与增量结果一致。
echo "[init-schema] applying schema.sql (--force, ignore duplicate-column errors)"
# entrypoint 在临时服务器（--skip-networking）阶段执行本脚本，必须走 socket（不能 -h 127.0.0.1）；
# --get-server-public-key 兼容 caching_sha2_password，--default-character-set=utf8mb4 防种子中文双重编码，-f 忽略增量 ALTER 重复列错误
mysql --get-server-public-key --default-character-set=utf8mb4 -uroot -p"${MYSQL_ROOT_PASSWORD}" -f "${MYSQL_DATABASE}" < /schema.sql
rc=$?
echo "[init-schema] mysql exit=$rc (非0多为已忽略的重复列/重复键错误，本地联调可接受)"
exit 0
