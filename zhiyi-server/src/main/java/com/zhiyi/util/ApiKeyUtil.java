package com.zhiyi.util;

import cn.hutool.crypto.digest.DigestUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.UUID;

/**
 * Agent API Key 工具：生成明文密钥、计算存储哈希
 */
public final class ApiKeyUtil {

    /** 明文密钥前缀，便于与 JWT 区分 */
    public static final String KEY_PREFIX = "bigapp_";

    /** 密钥哈希盐值 */
    private static final String KEY_SALT = "bigapp_api_key_salt";

    /** 存储到数据库的前缀长度，用于快速检索 */
    public static final int STORED_PREFIX_LENGTH = 16;

    private ApiKeyUtil() {
    }

    /**
     * 生成新的明文 API Key，格式 bigapp_{32位随机字符}
     */
    public static String generatePlainKey() {
        return KEY_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 提取用于展示与检索的密钥前缀
     */
    public static String extractStoredPrefix(String plainKey) {
        if (StringUtils.isBlank(plainKey)) {
            return "";
        }
        if (plainKey.length() <= STORED_PREFIX_LENGTH) {
            return plainKey;
        }
        return plainKey.substring(0, STORED_PREFIX_LENGTH);
    }

    /**
     * 判断 Authorization 令牌是否为 Agent API Key（非 JWT）
     */
    public static boolean isApiKeyToken(String token) {
        return StringUtils.isNotBlank(token) && token.startsWith(KEY_PREFIX);
    }

    /**
     * 将明文密钥转为存储哈希
     */
    public static String hashPlainKey(String plainKey) {
        return DigestUtil.sha256Hex(plainKey + KEY_SALT);
    }

    /**
     * 校验明文密钥是否与存储哈希一致
     */
    public static boolean matches(String plainKey, String storedHash) {
        return hashPlainKey(plainKey).equals(storedHash);
    }
}
