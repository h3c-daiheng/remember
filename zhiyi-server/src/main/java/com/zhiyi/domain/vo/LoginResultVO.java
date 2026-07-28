package com.zhiyi.domain.vo;

import lombok.Data;

/**
 * 登录接口响应，包含访问令牌与用户信息
 */
@Data
public class LoginResultVO {

    /** 访问令牌，后续请求需携带 */
    private String token;

    /** 当前登录用户信息 */
    private LoginUserVO user;
}
