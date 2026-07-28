package com.zhiyi.modelgateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Model Gateway 配置属性注册
 */
@Configuration
@EnableConfigurationProperties(ModelGatewayProperties.class)
public class ModelGatewayConfiguration {
}
