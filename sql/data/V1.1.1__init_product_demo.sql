-- =====================================================================
--  初始化数据 · 演示数据 · C 端会员 + 演示数据 · 自营商品（SPU/SKU）
--  ⚠️ 本文件由 tools/extract-ddl.ps1 从 docs/04-数据库设计.md 自动生成，请勿手工修改。
--     要改表结构，先改设计文档，再重新生成。
--  目标：MySQL 8.0 / InnoDB / utf8mb4_0900_ai_ci
-- =====================================================================
-- ============================================================
-- 8.6 演示用会员账号
-- 🔑 账号 testuser / 密码 123456
-- ⚠️ 这里的哈希必须是【123456】的哈希，不能复用管理员的（admin123）——
--    两者不可互换，复用会导致「按文档里的密码登不进去」。
-- ============================================================

INSERT INTO `ums_user`
  (`id`, `username`, `password`, `phone`, `nickname`, `gender`,
   `member_level_id`, `growth`, `points`, `total_points`, `status`, `register_source`,
   `create_time`, `update_time`, `deleted`)
VALUES
  (1, 'testuser', '$2a$10$bTX30qCPpCQoWZAKui/xIu3c4B584Oe/tMvLaC/ZuAscv5mIHuNx2',
   '13900000001', '测试用户', 1, 1, 0, 0, 0, 1, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- ============================================================
-- 8.7 演示商品：让 SPU/SKU/属性的关系具象化
-- 文件建议：sql/data/V1.0.6__init_product_demo.sql
-- 结构：iPhone 15（SPU） + 颜色(黑/白) × 内存(128G/256G) = 4 个 SKU
-- ============================================================

-- 类目
INSERT INTO `pms_category` (`id`, `parent_id`, `name`, `level`, `sort`, `is_show`, `status`,
  `create_time`, `update_time`, `deleted`) VALUES
  (1, 0,    '手机数码', 1, 1, 1, 1, NOW(), NOW(), 0),
  (2, 1,    '手机通讯', 2, 1, 1, 1, NOW(), NOW(), 0),
  (3, 2,    '智能手机', 3, 1, 1, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- 品牌
INSERT INTO `pms_brand` (`id`, `name`, `name_cn`, `first_letter`, `sort`, `status`,
  `create_time`, `update_time`, `deleted`) VALUES
  (1, 'Apple', '苹果', 'A', 1, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- 属性：颜色、内存（规格属性 type=1）；上市年份（参数属性 type=2）
INSERT INTO `pms_attribute` (`id`, `category_id`, `name`, `type`, `input_type`, `is_required`,
  `searchable`, `sort`, `status`, `create_time`, `update_time`, `deleted`) VALUES
  (1, 3, '颜色',     1, 1, 1, 1, 1, 1, NOW(), NOW(), 0),
  (2, 3, '机身内存', 1, 1, 1, 1, 2, 1, NOW(), NOW(), 0),
  (3, 3, '上市年份', 2, 3, 0, 0, 3, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- 属性值
INSERT INTO `pms_attribute_value` (`id`, `attribute_id`, `value`, `sort`, `status`,
  `create_time`, `update_time`, `deleted`) VALUES
  (1, 1, '黑色',   1, 1, NOW(), NOW(), 0),
  (2, 1, '白色',   2, 1, NOW(), NOW(), 0),
  (3, 2, '128GB',  1, 1, NOW(), NOW(), 0),
  (4, 2, '256GB',  2, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- SPU（merchant_id = 1 自营）
INSERT INTO `pms_spu` (`id`, `merchant_id`, `category_id`, `brand_id`, `spu_name`, `sub_title`,
  `unit`, `main_image`, `price`, `original_price`, `stock`, `sale_count`, `publish_status`, `status`,
  `create_time`, `update_time`, `deleted`) VALUES
  (1, 1, 3, 1, 'Apple iPhone 15', '灵动岛设计，A16 仿生芯片', '台',
   'https://cdn.example.com/iphone15-main.jpg', 599900, 699900, 400, 0, 2, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- SPU 用了哪些属性（规格属性值在 SKU 上，参数属性值在这里）
INSERT INTO `pms_spu_attribute` (`id`, `spu_id`, `attribute_id`, `attribute_name`, `type`, `value_json`, `sort`,
  `create_time`, `update_time`, `deleted`) VALUES
  (1, 1, 1, '颜色',     1, NULL,                          1, NOW(), NOW(), 0),
  (2, 1, 2, '机身内存', 1, NULL,                          2, NOW(), NOW(), 0),
  (3, 1, 3, '上市年份', 2, JSON_ARRAY('2023年', '全国联保'), 3, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- SKU（4 个组合：黑/白 × 128G/256G）
INSERT INTO `pms_sku` (`id`, `merchant_id`, `spu_id`, `sku_code`, `sku_name`, `spec_desc`, `spec_json`,
  `price`, `original_price`, `cost_price`, `stock`, `status`, `create_time`, `update_time`, `deleted`) VALUES
  (1, 1, 1, 'IP15-BLK-128', 'Apple iPhone 15 128GB 黑色', '128GB / 黑色',
   JSON_ARRAY(JSON_OBJECT('attributeId',1,'attributeName','颜色','valueId',1,'valueName','黑色'),
              JSON_OBJECT('attributeId',2,'attributeName','机身内存','valueId',3,'valueName','128GB')),
   599900, 699900, 520000, 100, 1, NOW(), NOW(), 0),
  (2, 1, 1, 'IP15-BLK-256', 'Apple iPhone 15 256GB 黑色', '256GB / 黑色',
   JSON_ARRAY(JSON_OBJECT('attributeId',1,'attributeName','颜色','valueId',1,'valueName','黑色'),
              JSON_OBJECT('attributeId',2,'attributeName','机身内存','valueId',4,'valueName','256GB')),
   679900, 779900, 590000, 100, 1, NOW(), NOW(), 0),
  (3, 1, 1, 'IP15-WHT-128', 'Apple iPhone 15 128GB 白色', '128GB / 白色',
   JSON_ARRAY(JSON_OBJECT('attributeId',1,'attributeName','颜色','valueId',2,'valueName','白色'),
              JSON_OBJECT('attributeId',2,'attributeName','机身内存','valueId',3,'valueName','128GB')),
   599900, 699900, 520000, 100, 1, NOW(), NOW(), 0),
  (4, 1, 1, 'IP15-WHT-256', 'Apple iPhone 15 256GB 白色', '256GB / 白色',
   JSON_ARRAY(JSON_OBJECT('attributeId',1,'attributeName','颜色','valueId',2,'valueName','白色'),
              JSON_OBJECT('attributeId',2,'attributeName','机身内存','valueId',4,'valueName','256GB')),
   679900, 779900, 590000, 100, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- SKU-属性值组合（每个 SKU 2 行，4 个 SKU 共 8 行）
INSERT INTO `pms_sku_attribute_value`
  (`id`, `sku_id`, `spu_id`, `attribute_id`, `attribute_name`, `attribute_value_id`, `attribute_value`,
   `sort`, `create_time`, `update_time`, `deleted`) VALUES
  (1, 1, 1, 1, '颜色',     1, '黑色',  1, NOW(), NOW(), 0),
  (2, 1, 1, 2, '机身内存', 3, '128GB', 2, NOW(), NOW(), 0),
  (3, 2, 1, 1, '颜色',     1, '黑色',  1, NOW(), NOW(), 0),
  (4, 2, 1, 2, '机身内存', 4, '256GB', 2, NOW(), NOW(), 0),
  (5, 3, 1, 1, '颜色',     2, '白色',  1, NOW(), NOW(), 0),
  (6, 3, 1, 2, '机身内存', 3, '128GB', 2, NOW(), NOW(), 0),
  (7, 4, 1, 1, '颜色',     2, '白色',  1, NOW(), NOW(), 0),
  (8, 4, 1, 2, '机身内存', 4, '256GB', 2, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- 库存（每个 SKU 一行，warehouse_id = 1 单仓）
-- 注意：available_stock 是生成列，**不能出现在 INSERT 列表里**！
-- ⚠️ total_in 必须等于期初 stock，否则「total_in - total_out = stock」这条对账口径
--    从第一行数据起就不成立（踩过：只写 stock 不写 total_in，导致对账恒等式失败）。
INSERT INTO `wms_stock`
  (`id`, `merchant_id`, `sku_id`, `warehouse_id`, `stock`, `locked_stock`, `warn_stock`,
   `total_in`, `total_out`, `create_time`, `update_time`, `deleted`) VALUES
  (1, 1, 1, 1, 100, 0, 10, 100, 0, NOW(), NOW(), 0),
  (2, 1, 2, 1, 100, 0, 10, 100, 0, NOW(), NOW(), 0),
  (3, 1, 3, 1, 100, 0, 10, 100, 0, NOW(), NOW(), 0),
  (4, 1, 4, 1, 100, 0, 10, 100, 0, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- 期初入库流水（每个 SKU 一条）
-- ⚠️ 播种库存时必须【同时】写期初入库流水，否则对账恒等式
--    SUM(change_num) == available_stock 从第一行数据起就不成立
--    （踩过两次：先是漏写 total_in，再是漏写这条期初流水）。
-- biz_no 用 IN_{wms_stock.id}，与 StockService.initStock 的命名保持一致。
INSERT INTO `wms_stock_log`
  (`id`, `merchant_id`, `sku_id`, `warehouse_id`, `biz_type`, `biz_no`, `order_no`,
   `change_num`, `before_stock`, `after_stock`, `operator_type`, `remark`,
   `create_time`, `update_time`, `deleted`) VALUES
  (1, 1, 1, 1, 7, 'IN_1', NULL, 100, 0, 100, 3, '期初入库', NOW(), NOW(), 0),
  (2, 1, 2, 1, 7, 'IN_2', NULL, 100, 0, 100, 3, '期初入库', NOW(), NOW(), 0),
  (3, 1, 3, 1, 7, 'IN_3', NULL, 100, 0, 100, 3, '期初入库', NOW(), NOW(), 0),
  (4, 1, 4, 1, 7, 'IN_4', NULL, 100, 0, 100, 3, '期初入库', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- 图片
INSERT INTO `pms_product_image` (`id`, `spu_id`, `sku_id`, `url`, `type`, `sort`,
  `create_time`, `update_time`, `deleted`) VALUES
  (1, 1, 0, 'https://cdn.example.com/iphone15-main.jpg', 1, 0, NOW(), NOW(), 0),
  (2, 1, 0, 'https://cdn.example.com/iphone15-b1.jpg',   2, 1, NOW(), NOW(), 0),
  (3, 1, 0, 'https://cdn.example.com/iphone15-b2.jpg',   2, 2, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `update_time` = NOW();
