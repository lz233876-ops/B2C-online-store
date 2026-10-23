-- =====================================================================
--  verify-constraints.sql — 数据库级防线行为验证
--
--  用法（必须加 --force，因为本脚本【故意】触发错误）：
--    mysql -uroot -p --default-character-set=utf8mb4 --force shopping_mall < sql/verify-constraints.sql
--
--  为什么要有这个脚本：
--    本项目对外宣称「防超卖 / 防重复下单 / 防重复回调靠数据库约束兜底，不靠应用层 if」。
--    这是一个【可证伪的断言】——所以必须能被证明。
--    本脚本逐条触发违规写入，验证数据库确实拒绝了它们。
--
--  全部测试在事务内进行，最后 ROLLBACK，不会污染数据。
--  预期结果：每次「故意违规」都应报错，且能看到对应的错误码。
-- =====================================================================

SELECT '============================================================' AS `说明`;
SELECT '一、幂等唯一索引：重复写入必须被拒绝（预期 ERROR 1062）' AS `说明`;
SELECT '============================================================' AS `说明`;

START TRANSACTION;

-- 1.1 库存流水幂等：(biz_type, biz_no) 唯一。同一个业务动作重放必须撞唯一键。
--     这是「超时关单回滚库存重试多次，但库存只回滚一次」的数据库级保障。
INSERT INTO `wms_stock_log`
  (`id`, `merchant_id`, `sku_id`, `warehouse_id`, `biz_type`, `biz_no`, `change_num`, `remark`)
VALUES
  (9999990001, 1, 1, 1, 3, 'VERIFY-CONSTRAINT-ORDER-001_CANCEL', 1, '自检第一条，应成功');
SELECT '  ↑ 第一条流水：必须成功' AS `预期`;

INSERT INTO `wms_stock_log`
  (`id`, `merchant_id`, `sku_id`, `warehouse_id`, `biz_type`, `biz_no`, `change_num`, `remark`)
VALUES
  (9999990002, 1, 1, 1, 3, 'VERIFY-CONSTRAINT-ORDER-001_CANCEL', 1, '自检第二条：重复，应被唯一索引拒绝');
SELECT '  ↑ 第二条重复流水：必须 ERROR 1062，否则幂等失效' AS `预期`;

-- 1.2 秒杀一人一单：(activity_id, user_id) 唯一。
--     这是「同一个用户重复提交秒杀请求，只会成功一次」的数据库级保障。
INSERT INTO `sms_seckill_record`
  (`id`, `activity_id`, `seckill_sku_id`, `sku_id`, `user_id`, `seckill_price`, `status`, `request_id`)
VALUES
  (9999990003, 999999, 999999, 1, 88888888, 1, 1, 'VERIFY-REQ-001');
SELECT '  ↑ 第一个秒杀记录：必须成功' AS `预期`;

-- ↓↓↓ 以下这条必须报 ERROR 1062 ↓↓↓
INSERT INTO `sms_seckill_record`
  (`id`, `activity_id`, `seckill_sku_id`, `sku_id`, `user_id`, `seckill_price`, `status`, `request_id`)
VALUES
  (9999990004, 999999, 999999, 1, 88888888, 1, 1, 'VERIFY-REQ-002');
SELECT '  ↑ 同一用户第二单：必须 ERROR 1062，否则会一人下两单' AS `预期`;

-- 1.3 支付回调去重：(channel, channel_notify_id) 唯一，且【不含 deleted】。
--     这是「渠道重复推送 25 次，订单状态只变一次」的数据库级保障。
INSERT INTO `pay_notify_log` (`id`, `channel`, `channel_notify_id`)
VALUES (9999990005, 1, 'VERIFY-NOTIFY-001');
SELECT '  ↑ 第一次回调：必须成功' AS `预期`;

-- ↓↓↓ 以下这条必须报 ERROR 1062 ↓↓↓
INSERT INTO `pay_notify_log` (`id`, `channel`, `channel_notify_id`)
VALUES (9999990006, 1, 'VERIFY-NOTIFY-001');
SELECT '  ↑ 重复回调：必须 ERROR 1062，否则会重复发货' AS `预期`;

SELECT '============================================================' AS `说明`;
SELECT '二、防超卖：条件更新 + CHECK 约束（本项目的核心断言）' AS `说明`;
SELECT '============================================================' AS `说明`;

SET @sku := (SELECT `sku_id` FROM `wms_stock` ORDER BY `sku_id` LIMIT 1);
SELECT @sku AS `测试用 SKU`, (SELECT `stock` FROM `wms_stock` WHERE `sku_id` = @sku) AS `当前库存`;

-- 2.1 条件更新：超量扣减时 affected = 0，【静默失败】而不是报错。
--     这就是防超卖的常规路径：应用层看到 0 就知道库存不足，抛业务异常。
UPDATE `wms_stock` SET `stock` = `stock` - 99999 WHERE `sku_id` = @sku AND `stock` >= 99999;
SELECT ROW_COUNT() AS `超量扣减 affected（必须 = 0）`;

-- 2.2 正常扣减：affected = 1
UPDATE `wms_stock` SET `stock` = `stock` - 1 WHERE `sku_id` = @sku AND `stock` >= 1;
SELECT ROW_COUNT() AS `正常扣减 affected（必须 = 1）`;

-- 2.3 CHECK 约束兜底：如果有人绕过条件更新、直接写负数，数据库必须拒绝。
--     这是「最后一道防线」——即使应用层代码写错了，也不会出现负库存。
UPDATE `wms_stock` SET `stock` = -1 WHERE `sku_id` = @sku;
SELECT '  ↑ 直接写负库存：必须被 CHECK 约束拒绝（预期 ERROR 3819）' AS `预期`;

SELECT '============================================================' AS `说明`;
SELECT '三、回滚（本脚本不改动任何数据）' AS `说明`;
SELECT '============================================================' AS `说明`;
ROLLBACK;

SELECT (SELECT `stock` FROM `wms_stock` WHERE `sku_id` = @sku) AS `回滚后库存（应与测试前一致）`;
SELECT COUNT(*) AS `残留测试流水（必须 = 0）` FROM `wms_stock_log` WHERE `biz_no` LIKE 'VERIFY-%';
SELECT COUNT(*) AS `残留测试秒杀记录（必须 = 0）` FROM `sms_seckill_record` WHERE `request_id` LIKE 'VERIFY-%';
SELECT COUNT(*) AS `残留测试回调日志（必须 = 0）` FROM `pay_notify_log` WHERE `channel_notify_id` LIKE 'VERIFY-%';
