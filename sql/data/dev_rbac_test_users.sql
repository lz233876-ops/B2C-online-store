-- =====================================================================
--  dev_rbac_test_users.sql —— 仅供本地验证 RBAC 的测试账号
--
--  ⚠️ 刻意【不】纳入 sql/init.sql，原因：
--      init.sql 是由 tools/extract-ddl.ps1 从 docs/04 生成的，
--      任何手工加进去的 SOURCE 行在下次重新生成时都会丢失。
--      所以「文档里没有的、只用于本地验证」的数据单独放一个文件。
--
--  用法（在 init.sql 之后执行）：
--    mysql -uroot -p --default-character-set=utf8mb4 shopping_mall < sql/data/dev_rbac_test_users.sql
--
--  用途：超级管理员能访问一切，所以只用 admin 账号无法验证「无权限返回 403」。
--        这里造两个不同权限档位的账号，用来验证权限确实是按角色生效的。
--
--  幂等：重复执行不会报错，也不会产生重复数据。
-- =====================================================================

USE `shopping_mall`;

-- ---------------------------------------------------------------
-- 账号 1：operator / admin123 —— 绑定 OPERATOR 角色（22 个权限，但【没有】任何 system:* ）
-- 用途：访问 /api/admin/system/user/page 应返回 403
-- ---------------------------------------------------------------
INSERT INTO `sys_user`
    (`id`, `username`, `password`, `nickname`, `real_name`, `phone`, `email`,
     `gender`, `dept_name`, `status`, `remark`, `create_time`, `update_time`, `deleted`)
VALUES
    (1001, 'operator', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
     '运营专员', '测试运营', '13900001111', 'operator@example.com',
     0, '运营部', 1, 'RBAC 验证用账号：不应有 system:* 权限',
     NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    `password` = VALUES(`password`), `status` = VALUES(`status`), `deleted` = 0, `update_time` = NOW();

INSERT INTO `sys_user_role` (`id`, `user_id`, `role_id`, `create_time`, `update_time`, `deleted`)
VALUES (1001, 1001, 2, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `deleted` = 0, `update_time` = NOW();

-- ---------------------------------------------------------------
-- 账号 2：merchant / admin123 —— 绑定 MERCHANT_ADMIN 角色（0 个菜单权限）
-- 用途：验证「一个权限都没有的角色」也能正常登录，但访问任何受保护接口都 403。
--       这个场景容易被忽略：很多实现在「用户没有任何角色」时会抛空指针。
-- ---------------------------------------------------------------
INSERT INTO `sys_user`
    (`id`, `username`, `password`, `nickname`, `real_name`, `phone`, `email`,
     `gender`, `dept_name`, `status`, `remark`, `create_time`, `update_time`, `deleted`)
VALUES
    (1002, 'merchant', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
     '商家管理员', '测试商家', '13900002222', 'merchant@example.com',
     0, '商家', 1, 'RBAC 验证用账号：零权限',
     NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    `password` = VALUES(`password`), `status` = VALUES(`status`), `deleted` = 0, `update_time` = NOW();

INSERT INTO `sys_user_role` (`id`, `user_id`, `role_id`, `create_time`, `update_time`, `deleted`)
VALUES (1002, 1002, 4, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `deleted` = 0, `update_time` = NOW();

-- ---------------------------------------------------------------
-- 账号 3：disabled / admin123 —— 已禁用账号
-- 用途：验证 status=0 的账号登录时被 DisabledException 拦下，返回 1002 而不是 1001。
--       这验证了 LoginUser.isEnabled() + Spring Security 的联动是否生效。
-- ---------------------------------------------------------------
INSERT INTO `sys_user`
    (`id`, `username`, `password`, `nickname`, `status`, `remark`,
     `create_time`, `update_time`, `deleted`)
VALUES
    (1003, 'disabled', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
     '已禁用账号', 0, 'RBAC 验证用账号：应登录失败',
     NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    `password` = VALUES(`password`), `status` = VALUES(`status`), `deleted` = 0, `update_time` = NOW();

-- ---------------------------------------------------------------
-- 自检
-- ---------------------------------------------------------------
SELECT u.id, u.username, u.nickname, u.status, r.role_code,
       (SELECT COUNT(*) FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.deleted = 0) AS menu_granted
FROM sys_user u
         LEFT JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = 0
         LEFT JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0
WHERE u.id IN (1001, 1002, 1003)
ORDER BY u.id;
