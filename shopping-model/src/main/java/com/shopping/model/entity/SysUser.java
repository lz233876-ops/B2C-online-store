package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 后台用户（{@code sys_user}）。
 * <p>
 * ⚠️ 与 C 端会员 {@code UmsUser}（{@code ums_user} 表）是<b>两套完全独立的体系</b>：
 * 后台用户属于公司内部员工，C 端会员是消费者。两者表不同、登录入口不同、
 * {@code UserDetailsService} 也不同。混在一起是常见的设计错误（会导致
 * 一个消费者账号能登进后台）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /** 登录账号，唯一（与 deleted 组成联合唯一索引） */
    private String username;

    /**
     * BCrypt 加密后的密码（60 位，字段留了 100 位余量）。
     * <p>
     * <b>绝不能用 MD5/SHA1</b>：无盐、可彩虹表反查、可暴力枚举。
     * BCrypt 自带随机盐且计算成本可调（strength），单次校验约 100ms，
     * 让离线爆破的成本高到不可接受。
     */
    private String password;

    /** 昵称/显示名 */
    private String nickname;

    /** 真实姓名 */
    private String realName;

    /** 头像 URL */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 性别：0 未知，1 男，2 女 */
    private Integer gender;

    /** 所属部门（本项目不做部门树，只留名称） */
    private String deptName;

    /** 状态：1 启用，0 禁用 */
    private Integer status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 最后登录 IP */
    private String lastLoginIp;

    /** 密码最后修改时间，用于强制改密策略 */
    private LocalDateTime pwdUpdateTime;

    /** 备注 */
    private String remark;
}
