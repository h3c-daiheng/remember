package com.zhiyi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务配置：usage_daily 写入等后台任务
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 日用量累加专用线程池，避免阻塞 Memory API 主流程
     */
    @Bean(name = "usageDailyExecutor")
    public Executor usageDailyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("usage-daily-");
        executor.initialize();
        return executor;
    }

    /**
     * 经验关系自动建边线程池
     */
    @Bean(name = "relationExecutor")
    public Executor relationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("relation-");
        executor.initialize();
        return executor;
    }

    /**
     * Capture AI Review 异步审核线程池，避免阻塞 Submit 主流程
     */
    @Bean(name = "aiReviewExecutor")
    public Executor aiReviewExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ai-review-");
        executor.initialize();
        return executor;
    }
}
