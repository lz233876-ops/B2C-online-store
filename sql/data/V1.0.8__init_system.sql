-- =====================================================================
--  初始化数据 · 初始化 · 默认管理员与角色 + 初始化 · 菜单与权限点 + 初始化 · 字典数据
--  ⚠️ 本文件由 tools/extract-ddl.ps1 从 docs/04-数据库设计.md 自动生成，请勿手工修改。
--     要改表结构，先改设计文档，再重新生成。
--  目标：MySQL 8.0 / InnoDB / utf8mb4_0900_ai_ci
-- =====================================================================
-- ============================================================
-- 8.1 默认管理员、角色、用户-角色关联
-- 文件建议：sql/data/V1.0.0__init_system.sql
-- ============================================================

-- 后台超级管理员（id 固定为 1）
-- 🔑 账号 admin / 密码 admin123  —— 已实测可登录（BCrypt.checkpw("admin123", 下方哈希) == true）
-- ⚠️ 生产环境务必修改；公开仓库里放一个可用的默认密码是常见做法，但必须显式告知使用者
INSERT INTO `sys_user`
  (`id`, `username`, `password`, `nickname`, `real_name`, `phone`, `status`,
   `create_time`, `update_time`, `deleted`)
VALUES
  (1, 'admin',
   '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
   '超级管理员', '系统管理员', '13800000000', 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- 角色：超管 / 运营 / 客服 / 商家管理员（Phase 4 用）
INSERT INTO `sys_role`
  (`id`, `role_name`, `role_code`, `data_scope`, `sort`, `status`, `remark`,
   `create_time`, `update_time`, `deleted`)
VALUES
  (1, '超级管理员', 'SUPER_ADMIN', 1, 1, 1, '拥有全部权限，不可删除', NOW(), NOW(), 0),
  (2, '运营专员',   'OPERATOR',    1, 2, 1, '商品、活动、订单管理',   NOW(), NOW(), 0),
  (3, '客服专员',   'CS',          1, 3, 1, '订单查询、售后审核',     NOW(), NOW(), 0),
  (4, '商家管理员', 'MERCHANT_ADMIN', 2, 4, 1, 'Phase 4 多商户，仅本商户数据', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- 管理员绑定超管角色
INSERT INTO `sys_user_role` (`id`, `user_id`, `role_id`, `create_time`, `update_time`, `deleted`)
VALUES (1, 1, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- ============================================================
-- 8.2 菜单/权限点（节选：目录 + 菜单 + 按钮三级）
-- 文件建议：sql/data/V1.0.1__init_menu.sql
-- 约定：menu_type 1=目录 2=菜单 3=按钮；parent_id=0 为根
-- ============================================================

INSERT INTO `sys_menu`
  (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`,
   `icon`, `sort`, `visible`, `keep_alive`, `status`, `create_time`, `update_time`, `deleted`)
VALUES
  -- ---------- 一级目录 ----------
  (100, 0, '系统管理', 1, '/system',  NULL,                  NULL, 'setting', 10, 1, 1, 1, NOW(), NOW(), 0),
  (200, 0, '商品管理', 1, '/product', NULL,                  NULL, 'goods',   20, 1, 1, 1, NOW(), NOW(), 0),
  (300, 0, '交易管理', 1, '/trade',   NULL,                  NULL, 'order',   30, 1, 1, 1, NOW(), NOW(), 0),
  (400, 0, '营销管理', 1, '/promotion', NULL,                NULL, 'gift',    40, 1, 1, 1, NOW(), NOW(), 0),
  (500, 0, '会员管理', 1, '/ums',     NULL,                  NULL, 'user',    50, 1, 1, 1, NOW(), NOW(), 0),

  -- ---------- 二级菜单：系统管理 ----------
  (101, 100, '用户管理', 2, 'user',   'system/user/index',    'system:user:list',   'peoples', 1, 1, 1, 1, NOW(), NOW(), 0),
  (102, 100, '角色管理', 2, 'role',   'system/role/index',    'system:role:list',   'role',    2, 1, 1, 1, NOW(), NOW(), 0),
  (103, 100, '菜单管理', 2, 'menu',   'system/menu/index',    'system:menu:list',   'tree',    3, 1, 1, 1, NOW(), NOW(), 0),
  (104, 100, '字典管理', 2, 'dict',   'system/dict/index',    'system:dict:list',   'dict',    4, 1, 1, 1, NOW(), NOW(), 0),
  (105, 100, '操作日志', 2, 'log',    'system/log/index',     'system:log:list',    'log',     5, 1, 1, 1, NOW(), NOW(), 0),
  (106, 100, '文件管理', 2, 'file',   'system/file/index',    'system:file:list',   'upload',  6, 1, 1, 1, NOW(), NOW(), 0),

  -- ---------- 二级菜单：商品管理 ----------
  (201, 200, '商品列表', 2, 'spu',    'product/spu/index',    'product:spu:list',   'list',    1, 1, 1, 1, NOW(), NOW(), 0),
  (202, 200, '类目管理', 2, 'category','product/category/index','product:category:list','tree', 2, 1, 1, 1, NOW(), NOW(), 0),
  (203, 200, '品牌管理', 2, 'brand',  'product/brand/index',  'product:brand:list', 'star',    3, 1, 1, 1, NOW(), NOW(), 0),
  (204, 200, '属性管理', 2, 'attribute','product/attribute/index','product:attribute:list','edit',4,1,1,1,NOW(),NOW(),0),

  -- ---------- 二级菜单：交易管理 ----------
  (301, 300, '订单列表', 2, 'order',  'trade/order/index',    'trade:order:list',   'form',    1, 1, 1, 1, NOW(), NOW(), 0),
  (302, 300, '售后管理', 2, 'aftersale','trade/aftersale/index','trade:aftersale:list','refund',2,1,1,1,NOW(),NOW(),0),
  (303, 300, '库存管理', 2, 'stock',  'trade/stock/index',    'trade:stock:list',   'shopping',3,1,1,1,NOW(),NOW(),0),

  -- ---------- 二级菜单：营销管理 ----------
  (401, 400, '优惠券',   2, 'coupon', 'promotion/coupon/index',  'promotion:coupon:list',  'ticket', 1, 1, 1, 1, NOW(), NOW(), 0),
  (402, 400, '秒杀活动', 2, 'seckill','promotion/seckill/index', 'promotion:seckill:list', 'time',   2, 1, 1, 1, NOW(), NOW(), 0),

  -- ---------- 二级菜单：会员管理 ----------
  (501, 500, '会员列表', 2, 'user',   'ums/user/index',       'ums:user:list',      'people',  1, 1, 1, 1, NOW(), NOW(), 0),
  (502, 500, '会员等级', 2, 'level',  'ums/level/index',      'ums:level:list',     'star',    2, 1, 1, 1, NOW(), NOW(), 0),
  (503, 500, '登录日志', 2, 'loginlog','ums/loginlog/index',  'ums:loginlog:list',  'logininfor', 3, 1, 1, 1, NOW(), NOW(), 0),

  -- ---------- 按钮：用户管理（menu_type=3，只有 perms） ----------
  (1011, 101, '用户新增', 3, NULL, NULL, 'system:user:add',    NULL, 1, 0, 0, 1, NOW(), NOW(), 0),
  (1012, 101, '用户修改', 3, NULL, NULL, 'system:user:edit',   NULL, 2, 0, 0, 1, NOW(), NOW(), 0),
  (1013, 101, '用户删除', 3, NULL, NULL, 'system:user:delete', NULL, 3, 0, 0, 1, NOW(), NOW(), 0),
  (1014, 101, '重置密码', 3, NULL, NULL, 'system:user:resetPwd',NULL,4, 0, 0, 1, NOW(), NOW(), 0),
  (1015, 101, '分配角色', 3, NULL, NULL, 'system:user:assignRole',NULL,5,0,0,1, NOW(), NOW(), 0),

  -- ---------- 按钮：商品管理 ----------
  (2011, 201, '商品新增', 3, NULL, NULL, 'product:spu:add',    NULL, 1, 0, 0, 1, NOW(), NOW(), 0),
  (2012, 201, '商品修改', 3, NULL, NULL, 'product:spu:edit',   NULL, 2, 0, 0, 1, NOW(), NOW(), 0),
  (2013, 201, '商品删除', 3, NULL, NULL, 'product:spu:delete', NULL, 3, 0, 0, 1, NOW(), NOW(), 0),
  (2014, 201, '上下架',   3, NULL, NULL, 'product:spu:publish',NULL, 4, 0, 0, 1, NOW(), NOW(), 0),

  -- ---------- 按钮：订单管理 ----------
  (3011, 301, '订单发货', 3, NULL, NULL, 'trade:order:deliver',NULL, 1, 0, 0, 1, NOW(), NOW(), 0),
  (3012, 301, '订单关闭', 3, NULL, NULL, 'trade:order:close',  NULL, 2, 0, 0, 1, NOW(), NOW(), 0),
  (3013, 301, '订单导出', 3, NULL, NULL, 'trade:order:export', NULL, 3, 0, 0, 1, NOW(), NOW(), 0),

  -- ---------- 按钮：秒杀 ----------
  (4021, 402, '活动新增', 3, NULL, NULL, 'promotion:seckill:add',   NULL, 1, 0, 0, 1, NOW(), NOW(), 0),
  (4022, 402, '活动修改', 3, NULL, NULL, 'promotion:seckill:edit',  NULL, 2, 0, 0, 1, NOW(), NOW(), 0),
  (4023, 402, '上下线',   3, NULL, NULL, 'promotion:seckill:status',NULL, 3, 0, 0, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- 给「超级管理员」授权全部菜单（用 SELECT 动态生成，避免手写几百行）
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `update_time`, `deleted`)
SELECT `id` * 10 + 1, 1, `id`, NOW(), NOW(), 0
FROM `sys_menu`
WHERE `deleted` = 0
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- 给「运营专员」授权商品 + 营销 + 交易（id 段 200~499）
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `update_time`, `deleted`)
SELECT `id` * 10 + 2, 2, `id`, NOW(), NOW(), 0
FROM `sys_menu`
WHERE `deleted` = 0 AND LEFT(`id`, 1) IN ('2', '3', '4')
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- 给「客服专员」授权交易管理（id 段 300~399）
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`, `update_time`, `deleted`)
SELECT `id` * 10 + 3, 3, `id`, NOW(), NOW(), 0
FROM `sys_menu`
WHERE `deleted` = 0 AND LEFT(`id`, 1) = '3'
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- ============================================================
-- 8.5 字典类型与字典数据
-- 文件建议：sql/data/V1.0.4__init_dict.sql
-- 字典的作用：把「状态码 -> 中文标签 / 前端样式」的映射从代码挪到数据库，
--            运营改颜色、改文案不需要发版。
-- ============================================================

-- ---------- 字典类型 ----------
INSERT INTO `sys_dict_type`
  (`id`, `dict_name`, `dict_type`, `status`, `remark`, `create_time`, `update_time`, `deleted`)
VALUES
  (1, '订单状态',     'order_status',     1, 'oms_order.status',        NOW(), NOW(), 0),
  (2, '支付方式',     'pay_type',         1, 'oms_order.pay_type',      NOW(), NOW(), 0),
  (3, '支付状态',     'payment_status',   1, 'pay_payment.status',      NOW(), NOW(), 0),
  (4, '订单来源',     'order_source',     1, 'oms_order.source_type',   NOW(), NOW(), 0),
  (5, '订单类型',     'order_type',       1, 'oms_order.order_type',    NOW(), NOW(), 0),
  (6, '售后类型',     'aftersale_type',   1, 'oms_aftersale.type',      NOW(), NOW(), 0),
  (7, '售后状态',     'aftersale_status', 1, 'oms_aftersale.status',    NOW(), NOW(), 0),
  (8, '库存流水类型', 'stock_biz_type',   1, 'wms_stock_log.biz_type',  NOW(), NOW(), 0),
  (9, '优惠券类型',   'coupon_type',      1, 'sms_coupon.type',         NOW(), NOW(), 0),
  (10,'券使用状态',   'coupon_status',    1, 'sms_coupon_record.status',NOW(), NOW(), 0),
  (11,'秒杀状态',     'seckill_status',   1, 'sms_seckill_activity.status', NOW(), NOW(), 0),
  (12,'商品上下架',   'spu_status',       1, 'pms_spu.status',          NOW(), NOW(), 0),
  (13,'商品发布状态', 'spu_publish_status',1,'pms_spu.publish_status',  NOW(), NOW(), 0),
  (14,'退款状态',     'refund_status',    1, 'pay_refund.status',       NOW(), NOW(), 0),
  (15,'商家审核状态', 'merchant_audit_status', 1, 'mms_merchant.audit_status', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- ---------- 字典数据（css_class 供 Element Plus 的 el-tag 直接用） ----------
INSERT INTO `sys_dict_data`
  (`id`, `dict_type`, `dict_label`, `dict_value`, `sort`, `css_class`, `is_default`, `status`,
   `remark`, `create_time`, `update_time`, `deleted`)
VALUES
  -- 订单状态（与 §5.1 严格一致）
  (101, 'order_status', '待支付', '0', 1, 'warning',  1, 1, NULL, NOW(), NOW(), 0),
  (102, 'order_status', '待发货', '1', 2, 'primary',  0, 1, NULL, NOW(), NOW(), 0),
  (103, 'order_status', '待收货', '2', 3, 'primary',  0, 1, NULL, NOW(), NOW(), 0),
  (104, 'order_status', '已完成', '3', 4, 'success',  0, 1, NULL, NOW(), NOW(), 0),
  (105, 'order_status', '已关闭', '4', 5, 'info',     0, 1, '超时未支付自动关闭', NOW(), NOW(), 0),
  (106, 'order_status', '已取消', '5', 6, 'info',     0, 1, '用户或客服主动取消', NOW(), NOW(), 0),

  -- 支付方式
  (201, 'pay_type', '未支付',   '0', 1, 'info',    1, 1, NULL, NOW(), NOW(), 0),
  (202, 'pay_type', '模拟支付宝','1', 2, 'primary', 0, 1, NULL, NOW(), NOW(), 0),
  (203, 'pay_type', '模拟微信', '2', 3, 'success', 0, 1, NULL, NOW(), NOW(), 0),
  (204, 'pay_type', '余额支付', '3', 4, 'warning', 0, 1, NULL, NOW(), NOW(), 0),

  -- 支付状态
  (301, 'payment_status', '待支付',   '0', 1, 'warning', 1, 1, NULL, NOW(), NOW(), 0),
  (302, 'payment_status', '支付成功', '1', 2, 'success', 0, 1, NULL, NOW(), NOW(), 0),
  (303, 'payment_status', '支付失败', '2', 3, 'danger',  0, 1, NULL, NOW(), NOW(), 0),
  (304, 'payment_status', '已关闭',   '3', 4, 'info',    0, 1, NULL, NOW(), NOW(), 0),
  (305, 'payment_status', '已全额退款','4', 5, 'info',   0, 1, NULL, NOW(), NOW(), 0),
  (306, 'payment_status', '部分退款', '5', 6, 'warning', 0, 1, NULL, NOW(), NOW(), 0),

  -- 订单来源
  (401, 'order_source', 'PC',   '1', 1, NULL, 1, 1, NULL, NOW(), NOW(), 0),
  (402, 'order_source', 'H5',   '2', 2, NULL, 0, 1, NULL, NOW(), NOW(), 0),
  (403, 'order_source', '小程序','3', 3, NULL, 0, 1, NULL, NOW(), NOW(), 0),
  (404, 'order_source', 'App',  '4', 4, NULL, 0, 1, NULL, NOW(), NOW(), 0),
  (405, 'order_source', '后台代下单','5', 5, NULL, 0, 1, NULL, NOW(), NOW(), 0),

  -- 订单类型
  (501, 'order_type', '普通订单', '1', 1, 'primary', 1, 1, NULL, NOW(), NOW(), 0),
  (502, 'order_type', '秒杀订单', '2', 2, 'danger',  0, 1, NULL, NOW(), NOW(), 0),
  (503, 'order_type', '积分兑换', '3', 3, 'warning', 0, 1, NULL, NOW(), NOW(), 0),

  -- 售后类型
  (601, 'aftersale_type', '仅退款',   '1', 1, NULL, 1, 1, NULL, NOW(), NOW(), 0),
  (602, 'aftersale_type', '退货退款', '2', 2, NULL, 0, 1, NULL, NOW(), NOW(), 0),
  (603, 'aftersale_type', '换货',     '3', 3, NULL, 0, 1, NULL, NOW(), NOW(), 0),

  -- 售后状态
  (701, 'aftersale_status', '待审核',   '0', 1, 'warning', 1, 1, NULL, NOW(), NOW(), 0),
  (702, 'aftersale_status', '已同意',   '1', 2, 'primary', 0, 1, NULL, NOW(), NOW(), 0),
  (703, 'aftersale_status', '已拒绝',   '2', 3, 'danger',  0, 1, NULL, NOW(), NOW(), 0),
  (704, 'aftersale_status', '退货中',   '3', 4, 'primary', 0, 1, NULL, NOW(), NOW(), 0),
  (705, 'aftersale_status', '已收货',   '4', 5, 'primary', 0, 1, NULL, NOW(), NOW(), 0),
  (706, 'aftersale_status', '退款完成', '5', 6, 'success', 0, 1, NULL, NOW(), NOW(), 0),
  (707, 'aftersale_status', '已关闭',   '6', 7, 'info',    0, 1, NULL, NOW(), NOW(), 0),

  -- 库存流水类型（与 §3.4.3 注释严格一致）
  (801, 'stock_biz_type', '下单锁定', '1', 1, 'warning', 1, 1, NULL, NOW(), NOW(), 0),
  (802, 'stock_biz_type', '支付扣减', '2', 2, 'primary', 0, 1, NULL, NOW(), NOW(), 0),
  (803, 'stock_biz_type', '取消释放', '3', 3, 'info',    0, 1, NULL, NOW(), NOW(), 0),
  (804, 'stock_biz_type', '退款回补', '4', 4, 'success', 0, 1, NULL, NOW(), NOW(), 0),
  (805, 'stock_biz_type', '后台调整', '5', 5, 'danger',  0, 1, '仅管理员可操作，需记录原因', NOW(), NOW(), 0),
  (806, 'stock_biz_type', '秒杀扣减', '6', 6, 'danger',  0, 1, NULL, NOW(), NOW(), 0),
  (807, 'stock_biz_type', '入库',     '7', 7, 'success', 0, 1, NULL, NOW(), NOW(), 0),

  -- 优惠券类型
  (901, 'coupon_type', '满减券',  '1', 1, 'danger',  1, 1, NULL, NOW(), NOW(), 0),
  (902, 'coupon_type', '折扣券',  '2', 2, 'warning', 0, 1, NULL, NOW(), NOW(), 0),
  (903, 'coupon_type', '无门槛券','3', 3, 'success', 0, 1, NULL, NOW(), NOW(), 0),

  -- 券使用状态
  (1001, 'coupon_status', '未使用', '0', 1, 'success', 1, 1, NULL, NOW(), NOW(), 0),
  (1002, 'coupon_status', '已使用', '1', 2, 'info',    0, 1, NULL, NOW(), NOW(), 0),
  (1003, 'coupon_status', '已过期', '2', 3, 'danger',  0, 1, NULL, NOW(), NOW(), 0),
  (1004, 'coupon_status', '已冻结', '3', 4, 'warning', 0, 1, '下单锁定中', NOW(), NOW(), 0),

  -- 秒杀活动状态
  (1101, 'seckill_status', '未开始', '0', 1, 'info',    1, 1, NULL, NOW(), NOW(), 0),
  (1102, 'seckill_status', '进行中', '1', 2, 'danger',  0, 1, NULL, NOW(), NOW(), 0),
  (1103, 'seckill_status', '已结束', '2', 3, 'info',    0, 1, NULL, NOW(), NOW(), 0),
  (1104, 'seckill_status', '已下线', '3', 4, 'info',    0, 1, NULL, NOW(), NOW(), 0),

  -- 商品上下架
  (1201, 'spu_status', '下架', '0', 1, 'info',    1, 1, NULL, NOW(), NOW(), 0),
  (1202, 'spu_status', '上架', '1', 2, 'success', 0, 1, NULL, NOW(), NOW(), 0),

  -- 商品发布状态
  (1301, 'spu_publish_status', '草稿',     '0', 1, 'info',    1, 1, NULL, NOW(), NOW(), 0),
  (1302, 'spu_publish_status', '待审核',   '1', 2, 'warning', 0, 1, NULL, NOW(), NOW(), 0),
  (1303, 'spu_publish_status', '审核通过', '2', 3, 'success', 0, 1, NULL, NOW(), NOW(), 0),
  (1304, 'spu_publish_status', '审核驳回', '3', 4, 'danger',  0, 1, NULL, NOW(), NOW(), 0),

  -- 退款状态
  (1401, 'refund_status', '待退款',   '0', 1, 'warning', 1, 1, NULL, NOW(), NOW(), 0),
  (1402, 'refund_status', '退款中',   '1', 2, 'primary', 0, 1, NULL, NOW(), NOW(), 0),
  (1403, 'refund_status', '退款成功', '2', 3, 'success', 0, 1, NULL, NOW(), NOW(), 0),
  (1404, 'refund_status', '退款失败', '3', 4, 'danger',  0, 1, NULL, NOW(), NOW(), 0),

  -- 商家审核状态
  (1501, 'merchant_audit_status', '待审核',   '0', 1, 'warning', 1, 1, NULL, NOW(), NOW(), 0),
  (1502, 'merchant_audit_status', '审核通过', '1', 2, 'success', 0, 1, NULL, NOW(), NOW(), 0),
  (1503, 'merchant_audit_status', '审核驳回', '2', 3, 'danger',  0, 1, NULL, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `update_time` = NOW();
