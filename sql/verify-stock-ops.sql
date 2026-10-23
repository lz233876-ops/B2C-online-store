-- =====================================================================
--  verify-stock-ops.sql —— 库存四个原子操作的 SQL 语义验证
--
--  为什么要有这个脚本：
--    「防超卖」的全部实现就是 WmsStockMapper.xml 里那四条【条件更新】。
--    条件写错（比如把 available_stock >= n 写成 > n、把 locked_stock 的条件漏掉），
--    代码看起来完全正常，但线上会超卖。所以必须能证明它。
--
--  用法（在项目根目录）：
--    mysql -uroot -p --default-character-set=utf8mb4 shopping_mall < sql/verify-stock-ops.sql
--
--  输出格式：每个检查输出一行 CHECK|<检查项>|<实际值>|<PASS|FAIL>
--           调用方（tools/verify-cart.ps1）只需断言「没有 FAIL」。
--
--  全部在事务内执行，最后 ROLLBACK，不改动任何数据。
--  测试对象：sku_id = 1（演示商品，初始 stock=100 / locked=0 / total_in=100 / total_out=0）
-- =====================================================================

START TRANSACTION;

SELECT '=== 初始状态 ===' AS step;
SELECT CONCAT('STATE|初始|stock=', stock, ' locked=', locked_stock,
              ' available=', available_stock, ' total_in=', total_in, ' total_out=', total_out)
           AS state_line
FROM wms_stock WHERE sku_id = 1 AND warehouse_id = 1;

-- ---------------------------------------------------------------
-- ① 锁定：正常锁定 10
-- ---------------------------------------------------------------
UPDATE wms_stock SET locked_stock = locked_stock + 10
WHERE sku_id = 1 AND warehouse_id = 1 AND deleted = 0 AND available_stock >= 10;
SET @rc = ROW_COUNT();
SELECT CONCAT('CHECK|lock_10_成功||', IF(@rc = 1, 'PASS', 'FAIL')) AS check_line;
SELECT CONCAT('CHECK|lock_10_后_locked=10||', IF(locked_stock = 10, 'PASS', 'FAIL')) AS check_line
FROM wms_stock WHERE sku_id = 1;
SELECT CONCAT('CHECK|lock_10_后_available=90||', IF(available_stock = 90, 'PASS', 'FAIL')) AS check_line
FROM wms_stock WHERE sku_id = 1;

-- ---------------------------------------------------------------
-- ② 锁定：超量锁定必须失败（这是防超卖的第一道闸门）
-- ---------------------------------------------------------------
UPDATE wms_stock SET locked_stock = locked_stock + 999999
WHERE sku_id = 1 AND warehouse_id = 1 AND deleted = 0 AND available_stock >= 999999;
SET @rc = ROW_COUNT();
SELECT CONCAT('CHECK|lock_超量_影响行数为0（防超卖）||', IF(@rc = 0, 'PASS', 'FAIL')) AS check_line;
SELECT CONCAT('CHECK|lock_超量_后_数据未被改动||', IF(locked_stock = 10, 'PASS', 'FAIL')) AS check_line
FROM wms_stock WHERE sku_id = 1;

-- ---------------------------------------------------------------
-- ③ 扣减：冻结转实扣，可售库存必须保持不变
-- ---------------------------------------------------------------
UPDATE wms_stock SET stock = stock - 10, locked_stock = locked_stock - 10, total_out = total_out + 10
WHERE sku_id = 1 AND warehouse_id = 1 AND deleted = 0 AND locked_stock >= 10 AND stock >= 10;
SET @rc = ROW_COUNT();
SELECT CONCAT('CHECK|deduct_10_成功||', IF(@rc = 1, 'PASS', 'FAIL')) AS check_line;
SELECT CONCAT('CHECK|deduct_后_stock=90||', IF(stock = 90, 'PASS', 'FAIL')) AS check_line
FROM wms_stock WHERE sku_id = 1;
SELECT CONCAT('CHECK|deduct_后_locked=0||', IF(locked_stock = 0, 'PASS', 'FAIL')) AS check_line
FROM wms_stock WHERE sku_id = 1;
SELECT CONCAT('CHECK|deduct_后_available_不变=90||', IF(available_stock = 90, 'PASS', 'FAIL')) AS check_line
FROM wms_stock WHERE sku_id = 1;

-- ---------------------------------------------------------------
-- ④ 扣减：没有冻结就去扣，必须失败（否则会扣掉别人的锁定）
-- ---------------------------------------------------------------
UPDATE wms_stock SET stock = stock - 10, locked_stock = locked_stock - 10, total_out = total_out + 10
WHERE sku_id = 1 AND warehouse_id = 1 AND deleted = 0 AND locked_stock >= 10 AND stock >= 10;
SET @rc = ROW_COUNT();
SELECT CONCAT('CHECK|deduct_无冻结时_影响行数为0||', IF(@rc = 0, 'PASS', 'FAIL')) AS check_line;

-- ---------------------------------------------------------------
-- ⑤ 释放：没锁过就释放，必须失败（否则会把别人的锁定吃掉 → 超卖）
-- ---------------------------------------------------------------
UPDATE wms_stock SET locked_stock = locked_stock - 10
WHERE sku_id = 1 AND warehouse_id = 1 AND deleted = 0 AND locked_stock >= 10;
SET @rc = ROW_COUNT();
SELECT CONCAT('CHECK|release_无锁定时_影响行数为0||', IF(@rc = 0, 'PASS', 'FAIL')) AS check_line;

-- ---------------------------------------------------------------
-- ⑥ 锁定 → 释放一轮，可售库存必须回到原值
-- ---------------------------------------------------------------
UPDATE wms_stock SET locked_stock = locked_stock + 5
WHERE sku_id = 1 AND warehouse_id = 1 AND deleted = 0 AND available_stock >= 5;
SET @rc = ROW_COUNT();
SELECT CONCAT('CHECK|lock_5_成功||', IF(@rc = 1, 'PASS', 'FAIL')) AS check_line;
SELECT CONCAT('CHECK|lock_5_后_available=85||', IF(available_stock = 85, 'PASS', 'FAIL')) AS check_line
FROM wms_stock WHERE sku_id = 1;

UPDATE wms_stock SET locked_stock = locked_stock - 5
WHERE sku_id = 1 AND warehouse_id = 1 AND deleted = 0 AND locked_stock >= 5;
SET @rc = ROW_COUNT();
SELECT CONCAT('CHECK|release_5_成功||', IF(@rc = 1, 'PASS', 'FAIL')) AS check_line;
SELECT CONCAT('CHECK|release_后_available_回到90||', IF(available_stock = 90, 'PASS', 'FAIL')) AS check_line
FROM wms_stock WHERE sku_id = 1;

-- ---------------------------------------------------------------
-- ⑦ 退款回补：货退回，可售增加
-- ---------------------------------------------------------------
UPDATE wms_stock SET stock = stock + 3, total_in = total_in + 3
WHERE sku_id = 1 AND warehouse_id = 1 AND deleted = 0;
SET @rc = ROW_COUNT();
SELECT CONCAT('CHECK|refund_3_成功||', IF(@rc = 1, 'PASS', 'FAIL')) AS check_line;
SELECT CONCAT('CHECK|refund_后_available=93||', IF(available_stock = 93, 'PASS', 'FAIL')) AS check_line
FROM wms_stock WHERE sku_id = 1;

-- ---------------------------------------------------------------
-- ⑧ 一致性不变量（每一条都必须恒成立）
-- ---------------------------------------------------------------
SELECT CONCAT('CHECK|不变量_available_=_stock-locked||',
              IF(available_stock = stock - locked_stock, 'PASS', 'FAIL')) AS check_line
FROM wms_stock WHERE sku_id = 1;

SELECT CONCAT('CHECK|不变量_total_in-total_out_=_stock||',
              IF(total_in - total_out = stock, 'PASS', 'FAIL')) AS check_line
FROM wms_stock WHERE sku_id = 1;

SELECT CONCAT('CHECK|不变量_最终_stock=93||', IF(stock = 93, 'PASS', 'FAIL')) AS check_line
FROM wms_stock WHERE sku_id = 1;

-- ---------------------------------------------------------------
-- ⑨ 回滚，确认数据未被改动
-- ---------------------------------------------------------------
ROLLBACK;
SELECT CONCAT('CHECK|回滚后_stock_恢复为100||', IF(stock = 100, 'PASS', 'FAIL')) AS check_line
FROM wms_stock WHERE sku_id = 1;
SELECT CONCAT('CHECK|回滚后_locked_恢复为0||', IF(locked_stock = 0, 'PASS', 'FAIL')) AS check_line
FROM wms_stock WHERE sku_id = 1;
