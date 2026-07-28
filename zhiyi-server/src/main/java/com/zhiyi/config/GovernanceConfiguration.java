package com.zhiyi.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 记忆治理配置与定时任务注册
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(GovernanceProperties.class)
public class GovernanceConfiguration {
}
