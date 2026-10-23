package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * 当前登录后台用户的信息。
 * <p>
 * {@code roles} 与 {@code perms} 是分开返回的，因为前端用途不同：
 * <ul>
 *   <li>{@code roles} —— 粗粒度，用于「这个用户是不是管理员」这类整体判断；</li>
 *   <li>{@code perms} —— 细粒度，前端用 {@code v-hasPerm="'system:user:add'"}
 *       控制按钮显隐，与后端 {@code @PreAuthorize} 用<b>同一份数据</b>。</li>
 * </ul>
 * ⚠️ 前端隐藏按钮只是<b>体验优化</b>，不是安全措施——后端必须独立校验，
 * 否则用户直接构造请求就能绕过。
 */
@Data
@Builder
@Schema(description = "当前登录用户信息")
public class AdminInfoVO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "手机号（已脱敏）")
    private String phone;

    @Schema(description = "角色编码列表，如 [SUPER_ADMIN]")
    private Set<String> roles;

    @Schema(description = "权限标识集合，如 [system:user:list, system:user:add]")
    private Set<String> perms;
}
