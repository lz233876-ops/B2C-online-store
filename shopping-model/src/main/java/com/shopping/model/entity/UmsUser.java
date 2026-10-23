package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * C 端会员（{@code ums_user}）—— <b>与后台用户 {@code sys_user} 是两套完全独立的体系</b>。
 * <p>
 * 后台用户是公司员工，会员是消费者。两者表不同、登录入口不同、{@code UserDetailsService}
 * 不同、{@code AuthenticationManager} 也不同。混用会导致「一个消费者账号能登进后台」，
 * 这是很严重的设计错误。
 * <p>
 * <b>登录主标识是手机号 {@code phone}，不是 {@code username}。</b>
 * {@code username} 在本表里其实是「可后设置的会员名」，允许为空；
 * 而 {@code phone} 是 NOT NULL 且有唯一索引 {@code uk_phone(phone, deleted)}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ums_user")
public class UmsUser extends BaseEntity {

    /** 会员名（可空，允许注册后再设置） */
    private String username;

    /**
     * BCrypt 密码。
     * <p>允许为 NULL —— 验证码登录的会员没有密码。
     * 所以校验密码前必须先判空，否则 {@code PasswordEncoder.matches(x, null)} 会抛异常。
     */
    private String password;

    /** 手机号，<b>登录主标识</b>，唯一 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 展示昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 性别：0 未知，1 男，2 女 */
    private Integer gender;

    /** 生日 */
    private LocalDate birthday;

    /** 会员等级 ID，逻辑关联 {@code ums_member_level.id} */
    private Long memberLevelId;

    /** 成长值，升级依据 */
    private Integer growth;

    /** 可用积分 */
    private Integer points;

    /** 历史累计积分（只增不减，用于等级判定） */
    private Integer totalPoints;

    /** 状态：1 正常，0 禁用 */
    private Integer status;

    /** 注册来源：1 PC，2 H5，3 小程序，4 App */
    private Integer registerSource;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 最后登录 IP */
    private String lastLoginIp;
}
