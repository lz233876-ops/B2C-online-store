package com.shopping.web.config;

import com.shopping.service.auth.UmsUserDetailsService;
import com.shopping.service.auth.UserDetailsServiceImpl;
import com.shopping.web.config.security.AppJwtAuthenticationFilter;
import com.shopping.web.config.security.JwtAuthenticationFilter;
import com.shopping.web.config.security.RestAccessDeniedHandler;
import com.shopping.web.config.security.RestAuthenticationEntryPoint;
import com.shopping.web.config.security.SecurityConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置。
 * <p>
 * Spring Security 6 与网上大多数教程（5.x）<b>写法完全不同</b>：
 * <ul>
 *   <li>{@code WebSecurityConfigurerAdapter} 已被<b>删除</b>，改为声明 {@code SecurityFilterChain} Bean；</li>
 *   <li>{@code antMatchers} / {@code mvcMatchers} 已弃用，统一为 {@code requestMatchers}；</li>
 *   <li>配置全部走 Lambda DSL，旧的方法链写法编译不过。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
// 开启方法级鉴权，否则 @PreAuthorize 完全不生效（这是最常见「配了没效果」的原因）
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AppJwtAuthenticationFilter appJwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final UserDetailsServiceImpl userDetailsService;
    private final UmsUserDetailsService umsUserDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 前后端分离 + 用 JWT 放在 Header，不用 Cookie，因此不存在 CSRF 攻击面。
                // ⚠️ 如果哪天改成把 token 放 Cookie，必须把这个 disable 去掉。
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 这三项都是「给服务端渲染/表单登录用的」，纯 API 服务不需要，
                // 关掉可以避免它们生成多余的过滤器和默认登录页
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // 完全无状态：不创建 HttpSession，登录态只存在于 JWT 里。
                // 好处是可以水平扩容任意实例，不需要 session 共享。
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry
                        // 放行浏览器的预检请求，否则跨域调试时会莫名其妙 401
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(SecurityConstants.PUBLIC_ENDPOINTS).permitAll()
                        // 管理端必须登录；细粒度权限由各方法上的 @PreAuthorize 控制
                        .requestMatchers("/api/admin/**").authenticated()
                        // C 端：白名单之外的（购物车/地址/订单）都要求会员登录
                        .requestMatchers("/api/app/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)   // 401
                        .accessDeniedHandler(accessDeniedHandler))            // 403
                // 两个 JWT 过滤器都插在 UsernamePasswordAuthenticationFilter 之前，
                // 早于 AuthorizationFilter 完成上下文装配。
                // 它们各自用 shouldNotFilter 限定只处理自己那一端的路径，互不干扰。
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(appJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 密码编码器。
     * <p>
     * 为什么是 <b>BCrypt</b> 而不是 MD5/SHA：
     * <ol>
     *   <li>自带<b>随机盐</b>，同样的密码每次加密结果都不同 → 彩虹表失效；</li>
     *   <li>计算成本<b>可调</b>（strength，默认 10 约 100ms）→ 离线爆破成本极高；</li>
     *   <li>盐直接编码在结果字符串里（{@code $2a$10$...}），不需要额外存盐字段。</li>
     * </ol>
     * 代价是单次校验约 100ms，所以<b>登录接口本身就是一个需要限流的点</b>——
     * 攻击者可以用大量登录请求打满 CPU。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 后台用户的认证提供者（查 {@code sys_user}）。
     * <p>
     * {@code hideUserNotFoundExceptions} 默认为 true，这里显式写出来是为了
     * <b>表明这是有意为之</b>：把 {@code UsernameNotFoundException} 转成
     * {@code BadCredentialsException}，避免攻击者通过错误提示枚举出哪些账号存在。
     */
    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    /**
     * C 端会员的认证提供者（查 {@code ums_user}）。
     * <p>与后台那个是<b>两个独立的 Provider</b>——这正是「会员账号登不进后台」的机制保证。
     */
    @Bean
    public DaoAuthenticationProvider appDaoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(umsUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    /**
     * 后台认证管理器。
     * <p>
     * 直接 new 一个 {@code ProviderManager} 而不是从 {@code AuthenticationConfiguration} 取，
     * 是为了<b>精确控制这条链上只有后台的 Provider</b>。
     * <p>
     * <b>{@code @Primary} 是必须的，原因有点绕但很关键：</b>
     * Spring Security 自己的 {@code HttpSecurityConfiguration} 在构建 {@code HttpSecurity} 时
     * 会去容器里找<b>唯一</b>一个 {@code AuthenticationManager}；有两个且没有 primary 时，
     * 启动直接失败：
     * <pre>Found 2 beans for type AuthenticationManager, but none marked as primary</pre>
     * 标 {@code @Primary} 表示「框架内部需要默认值时用它」。
     * <p>
     * <b>这不影响两条链的隔离</b>：{@code AuthServiceImpl} 与 {@code AppAuthServiceImpl}
     * 都<b>显式</b>用 {@code @Qualifier} 指定自己那一个，从不依赖 primary。
     * 换句话说，primary 只服务于框架内部，业务代码里没有任何一处「隐式选择认证链」。
     */
    @Bean
    @Primary
    public AuthenticationManager adminAuthenticationManager() {
        return new ProviderManager(daoAuthenticationProvider());
    }

    /**
     * C 端会员认证管理器。
     * <p>
     * <b>两个 AuthenticationManager 必须各自命名并被显式注入</b>
     * （{@code @Qualifier}）。如果哪天有人图省事把两者合并成一个，
     * 就会出现「会员手机号拿去 sys_user 表比对」——轻则登不上，
     * 重则跨体系登录。这是本项目认证设计里最不能碰的一条线。
     */
    @Bean
    public AuthenticationManager appAuthenticationManager() {
        return new ProviderManager(appDaoAuthenticationProvider());
    }

    /**
     * 跨域配置。
     * <p>
     * ⚠️ {@code setAllowCredentials(true)} 与 {@code allowedOrigins("*")} <b>不能同时使用</b>，
     * 浏览器会直接拒绝。
     * <p>
     * 这里用 {@code setAllowedOriginPatterns}（支持 {@code http://localhost:*} 这种端口通配）
     * 而不是枚举端口 —— 原因见 {@link SecurityConstants#CORS_ALLOWED_ORIGIN_PATTERNS} 的注释：
     * 枚举端口时，前端换一个端口就会整站 403，而且用脚本直接打接口完全测不出来。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(
                Arrays.asList(SecurityConstants.CORS_ALLOWED_ORIGIN_PATTERNS));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
