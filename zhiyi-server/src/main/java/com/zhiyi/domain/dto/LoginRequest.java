package com.zhiyi.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 用户登录请求参数
 */
@Data
public class LoginRequest {

    /** 登录账号 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 登录密码 */
    @NotBlank(message = "密码不能为空")
    private String password;
}
