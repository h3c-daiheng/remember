package com.zhiyi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Zhiyi 后端服务启动类
 */
@SpringBootApplication
@MapperScan({"com.zhiyi.dao", "com.zhiyi.memory.dao"})
public class ZhiyiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhiyiApplication.class, args);
    }
}
