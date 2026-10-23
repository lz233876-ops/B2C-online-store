-- =====================================================================
--  verify.sql — 建库后自检
--
--  用法（在项目根目录）：
--    mysql -uroot -p --default-character-set=utf8mb4 shopping_mall < sql/verify.sql
--
--  设计说明：
--    每一项都输出「检查项 / 实际值 / 期望值 / 结论」，一眼能看出哪里不对。
--    PASS 不代表业务正确，但 FAIL 一定是建库有问题。
-- =====================================================================

SELECT '========== 一、结构检查 ==========' AS `自检`;

SELECT `检查项`, `实际值`, `期望值`, IF(`实际值` = `期望值`, 'PASS', 'FAIL') AS `结论`
FROM (
    SELECT '表数量' AS `检查项`,
           (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()) AS `实际值`,
           '41' AS `期望值`
    UNION ALL
    SELECT '非 InnoDB 表数',
           (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND engine <> 'InnoDB'), '0'
    UNION ALL
    SELECT '非 utf8mb4_0900_ai_ci 表数',
           (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_collation <> 'utf8mb4_0900_ai_ci'), '0'
    UNION ALL
    SELECT '空表注释数',
           (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND (table_comment IS NULL OR table_comment = '')), '0'
    UNION ALL
    SELECT '空字段注释数',
           (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND (column_comment IS NULL OR column_comment = '')), '0'
    UNION ALL
    SELECT '物理外键数',
           (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND constraint_type = 'FOREIGN KEY'), '0'
    UNION ALL
    SELECT '缺公共字段的表数',
           (SELECT COUNT(*) FROM (
                SELECT table_name, COUNT(DISTINCT column_name) AS n
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND column_name IN ('id', 'create_time', 'update_time', 'deleted')
                GROUP BY table_name HAVING n < 4) x), '0'
    UNION ALL
    SELECT '缺 merchant_id 的核心表数',
           (SELECT COUNT(*) FROM (
                SELECT 'pms_spu' AS t UNION ALL SELECT 'pms_sku' UNION ALL SELECT 'oms_order'
                UNION ALL SELECT 'oms_order_item' UNION ALL SELECT 'wms_stock' UNION ALL SELECT 'sms_seckill_activity') core
            WHERE NOT EXISTS (
                SELECT 1 FROM information_schema.columns c
                WHERE c.table_schema = DATABASE() AND c.table_name = core.t AND c.column_name = 'merchant_id')), '0'
) t;

SELECT '========== 二、初始化数据 ==========' AS `自检`;

SELECT `检查项`, `实际值`, `期望值`, IF(`实际值` = `期望值`, 'PASS', 'WARN') AS `结论`
FROM (
    SELECT '后台管理员' AS `检查项`, (SELECT COUNT(*) FROM sys_user) AS `实际值`, '1' AS `期望值`
    UNION ALL SELECT '角色',        (SELECT COUNT(*) FROM sys_role),                  '4'
    UNION ALL SELECT '菜单/权限点', (SELECT COUNT(*) FROM sys_menu),                  '38'
    UNION ALL SELECT '角色-菜单授权', (SELECT COUNT(*) FROM sys_role_menu),           '67'
    UNION ALL SELECT '字典项',      (SELECT COUNT(*) FROM sys_dict_data),             '65'
    UNION ALL SELECT '默认商家',    (SELECT COUNT(*) FROM mms_merchant WHERE id = 1), '1'
    UNION ALL SELECT '默认店铺',    (SELECT COUNT(*) FROM mms_shop),                  '1'
    UNION ALL SELECT '会员等级',    (SELECT COUNT(*) FROM ums_member_level),          '5'
) t;

SELECT '========== 三、演示数据（V1.1.1，可选）==========' AS `自检`;

SELECT `检查项`, `实际值`
FROM (
    SELECT 'SPU' AS `检查项`, (SELECT COUNT(*) FROM pms_spu) AS `实际值`
    UNION ALL SELECT 'SKU',              (SELECT COUNT(*) FROM pms_sku)
    UNION ALL SELECT 'SKU 规格组合',     (SELECT COUNT(*) FROM pms_sku_attribute_value)
    UNION ALL SELECT '库存行',           (SELECT COUNT(*) FROM wms_stock)
    UNION ALL SELECT '库存合计(生成列)', (SELECT IFNULL(SUM(available_stock), 0) FROM wms_stock)
    UNION ALL SELECT '演示会员',         (SELECT COUNT(*) FROM ums_user)
) t;

-- 生成列自检：available_stock 必须等于 stock - locked_stock，由数据库保证，不靠应用层算
SELECT '生成列 available_stock 与 stock - locked_stock 不一致的行数' AS `检查项`,
       COUNT(*) AS `实际值`,
       IF(COUNT(*) = 0, 'PASS', 'FAIL') AS `结论`
FROM wms_stock
WHERE available_stock <> stock - locked_stock;

-- 幂等防线自检：这三条唯一索引是「防超卖 / 防重复下单 / 防重复回调」的数据库级保障
SELECT '========== 四、幂等唯一索引 ==========' AS `自检`;

SELECT t.`表`, t.`索引`, IF(s.`存在` = 1, 'PASS', 'FAIL') AS `结论`
FROM (
    SELECT 'wms_stock_log' AS `表`, 'uk_biz_type_biz_no' AS `索引`
    UNION ALL SELECT 'sms_seckill_record', 'uk_activity_id_user_id'
    UNION ALL SELECT 'pay_notify_log',     'uk_channel_channel_notify_id'
) t
LEFT JOIN (
    SELECT DISTINCT table_name AS `表名`, index_name AS `索引名`, 1 AS `存在`
    FROM information_schema.statistics WHERE table_schema = DATABASE()
) s ON s.`表名` = t.`表` AND s.`索引名` = t.`索引`;

-- 如果上面的索引名与实际不符，这里把所有唯一索引列出来供核对，避免"自检通过但索引名写错了"
SELECT table_name AS `表`, index_name AS `唯一索引`, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS `列`
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND non_unique = 0
GROUP BY table_name, index_name
ORDER BY table_name, index_name;
