package com.shopping.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置（Knife4j，基于 springdoc-openapi）。
 * <p>
 * 按「端」分组而不是把所有接口堆在一起：C 端（会员侧）与后台管理端的
 * 使用者和鉴权方式都不同，分开更清晰，面试演示时也更好讲。
 * <p>
 * 访问地址：{@code http://localhost:8080/doc.html}
 */
@Configuration
public class Knife4jConfig {

    private static final String SECURITY_SCHEME_NAME = "Bearer";

    @Bean
    public OpenAPI shoppingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("shopping-mall 接口文档")
                        .description("B2C 自营商城（含秒杀）— 单体多模块架构")
                        .version("v1.0.0")
                        .contact(new Contact().name("shopping-mall"))
                        .license(new License().name("For learning & job hunting")))
                // 全局声明：所有接口都可以带 Authorization 头
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .description("登录后拿到的 accessToken，填在这里即可调试受保护接口")));
    }

    /** C 端（会员侧）：/api/app/** */
    @Bean
    public GroupedOpenApi appApi() {
        return GroupedOpenApi.builder()
                .group("1-C端(会员)")
                .pathsToMatch("/api/app/**")
                .build();
    }

    /** 后台管理端：/api/admin/** */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("2-管理端(后台)")
                .pathsToMatch("/api/admin/**")
                .build();
    }
}
