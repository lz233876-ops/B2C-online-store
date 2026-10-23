-- =====================================================================
--  dev_seckill_demo.sql —— 秒杀演示数据（开发/验收用，手工维护）
--
--  为什么单独一个文件，而不是放进 docs/04 §8 的种子：
--    秒杀活动有【时间窗口】，必须在「导入数据的那一刻」才开始，
--    写死在文档里会导致「第二天打开项目，活动早就结束了」。
--    所以这里用 NOW() 相对时间，并且每次导入都会刷新时间窗口（幂等）。
--
--  用法（项目根目录）：
--    cmd /c "mysql -uroot -p --default-character-set=utf8mb4 shopping_mall < sql\data\dev_seckill_demo.sql"
--
--  导入后：活动「演示秒杀专场」立即开始，30 天后结束；
--          SKU1 秒杀价 2999.00（原价 5999.00，5.0 折），投放 100 件。
-- =====================================================================

-- 秒杀活动：从现在开始，30 天后结束
INSERT INTO `sms_seckill_activity`
  (`id`, `merchant_id`, `activity_name`, `start_time`, `end_time`, `status`,
   `description`, `sort`, `create_time`, `update_time`, `deleted`)
VALUES
  (9001, 1, '演示秒杀专场（iPhone 半价）',
   DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 30 DAY), 1,
   '用于开发调试与压测的演示活动。库存与活动时间每次导入都会刷新。', 0, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  `activity_name` = VALUES(`activity_name`),
  `start_time`    = VALUES(`start_time`),
  `end_time`      = VALUES(`end_time`),
  `status`        = 1,
  `update_time`   = NOW();

-- 秒杀商品：拿演示 SKU1 做标的
--   ⚠️ seckill_price 必须低于 original_price（业务校验），这里 2999.00 vs 5999.00
--   ⚠️ seckill_stock 应当 <= 真实可售库存，否则秒杀建单时锁真实库存会失败
INSERT INTO `sms_seckill_sku`
  (`id`, `merchant_id`, `activity_id`, `spu_id`, `sku_id`, `seckill_price`, `original_price`,
   `seckill_stock`, `seckill_sold`, `limit_per_user`, `sort`, `status`, `version`,
   `create_time`, `update_time`, `deleted`)
VALUES
  (90011, 1, 9001, 1, 1, 299900, 599900, 100, 0, 1, 0, 1, 0, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  `activity_id`    = 9001,
  `sku_id`         = 1,
  `seckill_price`  = 299900,
  `original_price` = 599900,
  -- 重置投放量与已售：每次导入都是一场新活动
  `seckill_stock`  = 100,
  `seckill_sold`   = 0,
  `limit_per_user` = 1,
  `status`         = 1,
  `update_time`    = NOW();

-- 自检
SELECT '=== 秒杀演示数据 ===' AS step;
SELECT a.id AS activity_id, a.activity_name, a.start_time, a.end_time, a.status,
       s.id AS seckill_sku_id, s.seckill_price, s.seckill_stock, s.seckill_sold
FROM sms_seckill_activity a
JOIN sms_seckill_sku s ON s.activity_id = a.id
WHERE a.id = 9001 AND a.deleted = 0 AND s.deleted = 0;
