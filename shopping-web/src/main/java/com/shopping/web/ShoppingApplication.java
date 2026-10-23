package com.shopping.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用启动类。
 * <p>
 * 注意 {@code scanBasePackages = "com.shopping"}：启动类在 {@code com.shopping.web} 下，
 * 默认只能扫到本模块。而 Service 在 {@code com.shopping.service}，
 * 因此必须把扫描根提升到 {@code com.shopping}，否则会报找不到 Bean。
 * <p>
 * 这个「多模块 + 启动类在子模块」的坑，是单体多模块项目最常见的启动失败原因。
 */
@SpringBootApplication(scanBasePackages = "com.shopping")
@MapperScan("com.shopping.dao.mapper")
@EnableScheduling
public class ShoppingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoppingApplication.class, args);
    }
}
