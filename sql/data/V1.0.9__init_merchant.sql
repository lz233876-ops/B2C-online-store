-- =====================================================================
--  初始化数据 · 初始化 · 默认商家 merchant_id=1
--  ⚠️ 本文件由 tools/extract-ddl.ps1 从 docs/04-数据库设计.md 自动生成，请勿手工修改。
--     要改表结构，先改设计文档，再重新生成。
--  目标：MySQL 8.0 / InnoDB / utf8mb4_0900_ai_ci
-- =====================================================================
-- ============================================================
-- 8.3 默认自营商家（id=1）与店铺
-- 必须在任何商品/订单数据之前执行！
-- 文件建议：sql/data/V1.0.2__init_merchant.sql
-- 说明：Phase 1-3 是纯自营 B2C，但所有商品/订单/库存表的
--       merchant_id 默认值为 1，所以这条基础数据必须先建好。
-- ============================================================

INSERT INTO `mms_merchant`
  (`id`, `merchant_no`, `merchant_name`, `merchant_type`, `contact_name`, `contact_phone`,
   `email`, `license_no`, `settle_rate`, `audit_status`, `status`,
   `create_time`, `update_time`, `deleted`)
VALUES
  (1, 'M0001', '自营商城', 1, '平台运营', '13800000000',
   'admin@shopping.com', '91310000MA1FL0000X', 0, 1, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `update_time` = NOW();

INSERT INTO `mms_shop`
  (`id`, `merchant_id`, `shop_name`, `shop_type`, `description`,
   `fans_count`, `product_count`, `sale_count`, `score`, `sort`, `status`,
   `create_time`, `update_time`, `deleted`)
VALUES
  (1, 1, '自营旗舰店', 1, '平台自营，品质保证，全场包邮', 0, 0, 0, 50000, 1, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `update_time` = NOW();
