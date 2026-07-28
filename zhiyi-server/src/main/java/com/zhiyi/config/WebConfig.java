package com.zhiyi.config;

import com.zhiyi.auth.AgentAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：CORS 与 Memory API 鉴权拦截
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AgentAuthInterceptor agentAuthInterceptor;

    /**
     * 允许的跨域 Origin 模式（逗号分隔配置）。
     * 默认仅本地开发域名；生产务必改为具体前端域名，禁止使用 * 并同时开启 credentials。
     */
    @Value("${zhiyi.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*,http://*.local.example.com:*}")
    private String[] allowedOriginPatterns;

    public WebConfig(AgentAuthInterceptor agentAuthInterceptor) {
        this.agentAuthInterceptor = agentAuthInterceptor;
    }

    /**
     * 按配置放行跨域；默认仅本地，避免开源部署时任意站点携带 Cookie 调用 API
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * Memory API 统一走 Agent 鉴权（API Key 或 JWT）
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(agentAuthInterceptor)
                .addPathPatterns("/memory/**");
    }
}
