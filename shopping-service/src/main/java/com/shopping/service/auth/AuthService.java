package com.shopping.service.auth;

import com.shopping.model.dto.LoginDTO;
import com.shopping.model.vo.AdminInfoVO;
import com.shopping.model.vo.LoginVO;
import com.shopping.model.vo.MenuTreeVO;

import java.util.List;

/**
 * 后台认证服务。
 */
public interface AuthService {

    /**
     * 账号密码登录。
     *
     * @param dto      登录入参
     * @param clientIp 客户端 IP，仅用于记录登录审计信息。
     *                 <b>刻意由 Controller 传入而不是在 Service 里取 {@code HttpServletRequest}</b>——
     *                 否则 service 模块就要依赖 web 模块，违反架构分层（会被 Enforcer 拦下）。
     * @return 令牌对
     */
    LoginVO login(LoginDTO dto, String clientIp);

    /**
     * 用 refreshToken 换取新的令牌对。
     * <p>
     * <b>刷新即轮换（rotation）</b>：旧的 refreshToken 在本次调用后立即失效。
     * 这样做能发现「refreshToken 被盗用」——攻击者用旧的刷新时，
     * 合法用户的 token 已被轮换掉，会立刻暴露。
     *
     * @param refreshToken 刷新令牌
     */
    LoginVO refresh(String refreshToken);

    /**
     * 登出。
     * <p>
     * 两件事：① 把当前 accessToken 的 jti 加入黑名单（它无法被主动失效，
     * 只能靠黑名单补齐剩余有效期）；② 删除该用户在 Redis 里的全部 refreshToken。
     *
     * @param accessToken 当前请求携带的 accessToken，可为 null
     */
    void logout(String accessToken);

    /** 当前登录用户的信息（含角色与权限标识） */
    AdminInfoVO getCurrentUserInfo();

    /** 当前登录用户可见的动态路由树 */
    List<MenuTreeVO> getCurrentUserMenuTree();
}
