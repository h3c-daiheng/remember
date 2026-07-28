package com.zhiyi.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI Review 配置属性注册
 */
@Configuration
@EnableConfigurationProperties(AiReviewProperties.class)
public class AiReviewConfiguration {
}
