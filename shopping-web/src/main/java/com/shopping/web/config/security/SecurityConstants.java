package com.shopping.web.config.security;

/**
 * 安全相关的常量与白名单。
 */
public final class SecurityConstants {

    private SecurityConstants() {
    }

    /**
     * JWT 过滤器把「令牌解析失败的错误码」放到这个 request attribute 上，
     * 供 {@link RestAuthenticationEntryPoint} 区分「过期」与「非法」。
     * <p>
     * 为什么要在过滤器里「记下来」而不是直接返回响应：
     * 若过滤器自己写响应，就会出现<b>两套 401 格式</b>（过滤器一套、EntryPoint 一套），
     * 前端得写两个分支去解析。统一交给 EntryPoint 输出，格式只有一处定义。
     */
    public static final String AUTH_ERROR_CODE_ATTR = "SHOPPING_AUTH_ERROR_CODE";

    /**
     * 免认证路径。
     * <p>
     * 逐条说明为什么可以放开：
     * <ul>
     *   <li>{@code /api/admin/auth/login|refresh} —— 登录与刷新本身就不该要求已登录；</li>
     *   <li>{@code /api/app/**} —— C 端的大部分接口（浏览商品、注册）本就允许游客访问。
     *       Phase 2 引入会员下单后会细化成「部分放开」；</li>
     *   <li>{@code /doc.html} 等 —— 接口文档页面，自带静态资源；</li>
     *   <li>{@code /actuator/**} —— 运维探活。⚠️ 生产环境要么关掉暴露、
     *       要么限制为内网访问，否则会泄漏数据库连通性等信息；</li>
     *   <li>{@code /error} —— <b>极易漏掉的一条</b>。Spring Boot 的错误转发会走
     *       {@code /error}，如果它被要求认证，那么任何 404/500 都会被改写成 401，
     *       排查问题时会被彻底带偏。</li>
     * </ul>
     */
    public static final String[] PUBLIC_ENDPOINTS = {
            // ---- 后台：登录与刷新本身不该要求已登录 ----
            "/api/admin/auth/login",
            "/api/admin/auth/refresh",
            // ---- C 端：注册/登录/刷新免认证 ----
            "/api/app/auth/register",
            "/api/app/auth/login",
            "/api/app/auth/refresh",
            // ---- C 端：商品浏览允许游客（Phase 2 加入购物车后才需要登录）----
            "/api/app/product/**",
            // 秒杀活动浏览也允许游客（和商品浏览同理）；抢购 / 结果查询仍需登录。
            // ⚠️ 刻意写具体路径而不用 /api/app/seckill/* 通配：
            //    通配会把除 /grab 之外的单段路径一并放行（例如 /result），很容易想漏。
            "/api/app/seckill/list",
            "/api/app/seckill/detail/**",
            // ---- 支付渠道回调：调用方是渠道服务器，没有我们的用户令牌 ----
            // 安全性由【验签】保证，而不是由身份认证保证。
            // 模拟收银台同理（它扮演渠道侧），接入真实渠道时这个路径会被删掉。
            "/api/pay/notify/**",
            "/api/pay/mock-gateway/**",
            // 健康检查：验收脚本与运维探活都靠它，必须公开
            "/api/app/health",
            // ---- 文档与运维 ----
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/**",
            "/favicon.ico",
            "/error"
    };

    /**
     * 允许跨域的前端来源（开发环境）。
     * <p>
     * <b>⚠️ 这里踩过一个"只在浏览器里才复现"的坑，务必看完再改：</b>
     * <p>
     * 前端的 vite 代理配置了 {@code changeOrigin: true}，它会把请求的 Host 改写成
     * 后端地址（{@code localhost:8080}）。于是浏览器发来的
     * {@code Origin: http://localhost:5174} 与后端眼中的 Host 不一致，
     * Spring 就判定这是<b>跨域请求</b>，即使前端走的是同源相对路径 {@code /api/...}。
     * <p>
     * 后果：如果 5174 不在白名单里，浏览器里<b>所有</b> POST/PUT 请求都会拿到
     * {@code 403 Invalid CORS request}，而用 curl/Postman/PowerShell 直接打接口
     * <b>完全正常</b>（因为它们不带 {@code Origin} 头）——
     * 这就是本项目真实发生过的 bug：管理后台（5173，在白名单里）一切正常，
     * 商城前台（5174，不在白名单里）连登录都登不上，而自动化验收脚本全绿。
     * <p>
     * 两个教训：
     * <ol>
     *   <li>涉及 CORS 的验收，<b>必须带上真实的 {@code Origin} 头</b>，
     *       否则等于没测；</li>
     *   <li>开发环境用<b>端口通配</b>而不是逐个列举，避免前端换个端口就整站不可用。</li>
     * </ol>
     * <p>
     * ⚠️ 生产环境不能这么放开：要把这里换成真实域名（如 {@code https://mall.example.com}），
     * 并且前端最好与 API 同源（Nginx 反向代理），从根上不需要 CORS。
     */
    public static final String[] CORS_ALLOWED_ORIGIN_PATTERNS = {
            // 开发环境：本机任意端口都放行（5173 管理后台 / 5174 商城前台 / 5175+ 预览等）
            "http://localhost:*",
            "http://127.0.0.1:*"
    };
}
