-- =====================================================================
--  本地开发环境一键初始化（⚠️ 会 DROP DATABASE，仅供开发环境！）
--  ⚠️ 本文件由 tools/extract-ddl.ps1 从 docs/04-数据库设计.md 自动生成，请勿手工修改。
--     要改表结构，先改设计文档，再重新生成。
--  目标：MySQL 8.0 / InnoDB / utf8mb4_0900_ai_ci
-- =====================================================================
-- 用法（必须在项目根目录执行，因为 SOURCE 的路径相对客户端工作目录）：
--   mysql -uroot -p --default-character-set=utf8mb4 < sql/init.sql

DROP DATABASE IF EXISTS `shopping_mall`;
CREATE DATABASE `shopping_mall`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE `shopping_mall`;

SOURCE sql/schema/V1.0.0__schema_phase1_system.sql;
SOURCE sql/schema/V1.0.1__schema_phase1_user.sql;
SOURCE sql/schema/V1.0.2__schema_phase1_product.sql;
SOURCE sql/schema/V1.0.3__schema_phase2_cart_stock.sql;
SOURCE sql/schema/V1.0.4__schema_phase2_trade.sql;
SOURCE sql/schema/V1.0.5__schema_phase2_payment.sql;
SOURCE sql/schema/V1.0.6__schema_phase3_promotion.sql;
SOURCE sql/schema/V1.0.7__schema_phase4_merchant.sql;

-- 数据顺序不能乱：商家 -> 权限 -> 等级 -> 演示数据
SOURCE sql/data/V1.0.8__init_system.sql;
SOURCE sql/data/V1.0.9__init_merchant.sql;
SOURCE sql/data/V1.1.0__init_member_level.sql;
-- SOURCE sql/data/V1.1.1__init_product_demo.sql;  -- 演示数据，按需开启
