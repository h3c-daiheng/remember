package com.zhiyi.controller;

import com.zhiyi.auth.LoginContext;
import com.zhiyi.common.Result;
import com.zhiyi.domain.vo.LoginUserVO;
import com.zhiyi.service.AuthService;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 认证接口：本地账号密码登录、获取当前用户、退出（不依赖 gonline）
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 本地账号密码登录，返回 JWT 与用户信息
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req) {
        return Result.success(authService.login(req.getUsername(), req.getPassword()));
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public Result<LoginUserVO> currentUser(HttpServletRequest request) {
        return Result.success(LoginContext.requireLoginUser(request));
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        authService.logout(LoginContext.resolveToken(request));
        return Result.success(null);
    }

    /** 本地登录入参 */
    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
