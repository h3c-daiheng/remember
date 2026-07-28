package com.zhiyi.util;

import cn.hutool.crypto.digest.DigestUtil;

/**
 * 密码工具类，使用固定盐值做 SHA256 哈希
 */
public final class PasswordUtil {

    /** 密码哈希盐值，需与初始化 SQL 保持一致 */
    private static final String PASSWORD_SALT = "chat2x_salt";

    private PasswordUtil() {
    }

    /**
     * 将明文密码转为存储用的哈希值
     */
    public static String encode(String rawPassword) {
        return DigestUtil.sha256Hex(rawPassword + PASSWORD_SALT);
    }

    /**
     * 校验明文密码是否与存储哈希一致
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return encode(rawPassword).equals(encodedPassword);
    }
}
