-- =====================================================================
--  Phase 3 · promotion 模块（5 张 sms_*）
--  ⚠️ 本文件由 tools/extract-ddl.ps1 从 docs/04-数据库设计.md 自动生成，请勿手工修改。
--     要改表结构，先改设计文档，再重新生成。
--  目标：MySQL 8.0 / InnoDB / utf8mb4_0900_ai_ci
--  ⚠️ 含 DROP TABLE IF EXISTS，【仅供开发环境反复执行】。
--     生产脚本必须去掉 DROP、改用 CREATE TABLE IF NOT EXISTS（见 docs/04 §9.1 原则 3）。
-- =====================================================================
DROP TABLE IF EXISTS `sms_coupon`;
CREATE TABLE `sms_coupon` (
  `id`                   bigint       NOT NULL                COMMENT '主键，雪花ID',
  `merchant_id`          bigint       NOT NULL DEFAULT 1      COMMENT '商家ID（出资方），多商户预留：1=自营',
  `coupon_name`          varchar(64)  NOT NULL                COMMENT '优惠券名称，如「满100减20」',
  `type`                 tinyint      NOT NULL DEFAULT 1      COMMENT '券类型：1满减券 2折扣券 3无门槛券',
  `amount`               bigint       NOT NULL DEFAULT 0      COMMENT '券面额（单位：分），折扣券此列为0',
  `discount_percent`     int          NOT NULL DEFAULT 100    COMMENT '折扣百分比，仅折扣券有效：90=9折',
  `max_discount_amount`  bigint       NOT NULL DEFAULT 0      COMMENT '折扣券最高抵扣金额（单位：分），0=不限',
  `threshold_amount`     bigint       NOT NULL DEFAULT 0      COMMENT '使用门槛（单位：分），0=无门槛',
  `total_count`          int          NOT NULL DEFAULT 0      COMMENT '发行总量，0=不限量',
  `received_count`       int          NOT NULL DEFAULT 0      COMMENT '已领取数量（并发扣减字段）',
  `used_count`           int          NOT NULL DEFAULT 0      COMMENT '已使用数量',
  `per_user_limit`       int          NOT NULL DEFAULT 1      COMMENT '每人限领数量',
  `valid_type`           tinyint      NOT NULL DEFAULT 1      COMMENT '有效期类型：1固定时间区间 2领取后N天',
  `valid_days`           int          NOT NULL DEFAULT 0      COMMENT '领取后有效天数，valid_type=2时生效',
  `receive_start_time`   datetime              DEFAULT NULL   COMMENT '可领取开始时间',
  `receive_end_time`     datetime              DEFAULT NULL   COMMENT '可领取结束时间',
  `use_start_time`       datetime              DEFAULT NULL   COMMENT '可使用开始时间（valid_type=1）',
  `use_end_time`         datetime              DEFAULT NULL   COMMENT '可使用结束时间（valid_type=1）',
  `scope_type`           tinyint      NOT NULL DEFAULT 1      COMMENT '适用范围：1全场 2指定类目 3指定商品',
  `scope_ids`            json                  DEFAULT NULL   COMMENT '适用范围ID数组，如 [101,102]',
  `get_way`              tinyint      NOT NULL DEFAULT 1      COMMENT '获取方式：1主动领取 2新人注册赠送 3后台发放 4满赠',
  `status`               tinyint      NOT NULL DEFAULT 1      COMMENT '状态：1启用 0停用',
  `version`              int          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  `create_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_status_receive_time` (`status`, `receive_start_time`, `receive_end_time`),
  KEY `idx_merchant_id_status` (`merchant_id`, `status`),
  KEY `idx_get_way` (`get_way`),
  KEY `idx_use_end_time` (`use_end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠券模板表';

DROP TABLE IF EXISTS `sms_coupon_record`;
CREATE TABLE `sms_coupon_record` (
  `id`               bigint       NOT NULL                COMMENT '主键，雪花ID',
  `coupon_id`        bigint       NOT NULL                COMMENT '优惠券模板ID，逻辑关联 sms_coupon.id',
  `user_id`          bigint       NOT NULL                COMMENT '会员ID，逻辑关联 ums_user.id',
  `receive_seq`      int          NOT NULL DEFAULT 1      COMMENT '该用户第几次领取本券（从1开始，用于限领唯一索引）',
  `coupon_name`      varchar(64)  NOT NULL DEFAULT ''     COMMENT '券名称快照',
  `type`             tinyint      NOT NULL DEFAULT 1      COMMENT '券类型快照：1满减 2折扣 3无门槛',
  `amount`           bigint       NOT NULL DEFAULT 0      COMMENT '券面额快照（单位：分）',
  `discount_percent` int          NOT NULL DEFAULT 100    COMMENT '折扣百分比快照',
  `threshold_amount` bigint       NOT NULL DEFAULT 0      COMMENT '使用门槛快照（单位：分）',
  `status`           tinyint      NOT NULL DEFAULT 0      COMMENT '使用状态：0未使用 1已使用 2已过期 3已冻结（下单锁定中）',
  `order_id`         bigint                DEFAULT NULL   COMMENT '使用的订单ID',
  `order_no`         varchar(64)           DEFAULT NULL   COMMENT '使用的订单号',
  `use_time`         datetime              DEFAULT NULL   COMMENT '使用时间',
  `receive_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
  `expire_time`      datetime     NOT NULL                COMMENT '过期时间（按模板规则算好写入，避免每次查询现算）',
  `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coupon_id_user_id_receive_seq` (`coupon_id`, `user_id`, `receive_seq`),
  KEY `idx_user_id_status_expire_time` (`user_id`, `status`, `expire_time`),
  KEY `idx_coupon_id_user_id` (`coupon_id`, `user_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_status_expire_time` (`status`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='领券/用券记录表';

DROP TABLE IF EXISTS `sms_seckill_activity`;
CREATE TABLE `sms_seckill_activity` (
  `id`             bigint       NOT NULL                COMMENT '主键，雪花ID',
  `merchant_id`    bigint       NOT NULL DEFAULT 1      COMMENT '商家ID，多商户预留：1=自营',
  `activity_name`  varchar(64)  NOT NULL                COMMENT '活动名称，如「618 数码专场」',
  `start_time`     datetime     NOT NULL                COMMENT '活动开始时间',
  `end_time`       datetime     NOT NULL                COMMENT '活动结束时间',
  `status`         tinyint      NOT NULL DEFAULT 0      COMMENT '状态：0未开始 1进行中 2已结束 3已下线',
  `description`    varchar(500)          DEFAULT NULL   COMMENT '活动说明',
  `banner`         varchar(500)          DEFAULT NULL   COMMENT '活动Banner图',
  `sort`           int          NOT NULL DEFAULT 0      COMMENT '排序，值越小越靠前',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_status_start_time` (`status`, `start_time`, `end_time`),
  KEY `idx_merchant_id_status` (`merchant_id`, `status`),
  KEY `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='秒杀活动表';

DROP TABLE IF EXISTS `sms_seckill_sku`;
CREATE TABLE `sms_seckill_sku` (
  `id`             bigint   NOT NULL                COMMENT '主键，雪花ID',
  `merchant_id`    bigint   NOT NULL DEFAULT 1      COMMENT '商家ID，多商户预留：1=自营',
  `activity_id`    bigint   NOT NULL                COMMENT '秒杀活动ID，逻辑关联 sms_seckill_activity.id',
  `spu_id`         bigint   NOT NULL                COMMENT 'SPU ID',
  `sku_id`         bigint   NOT NULL                COMMENT 'SKU ID，逻辑关联 pms_sku.id',
  `seckill_price`  bigint   NOT NULL DEFAULT 0      COMMENT '秒杀价（单位：分），必须低于原价',
  `original_price` bigint   NOT NULL DEFAULT 0      COMMENT '原价（单位：分），用于展示划线价',
  `seckill_stock`  int      NOT NULL DEFAULT 0      COMMENT '秒杀总库存（投放量）',
  `seckill_sold`   int      NOT NULL DEFAULT 0      COMMENT '已售数量（并发扣减字段）',
  `limit_per_user` int      NOT NULL DEFAULT 1      COMMENT '每人限购数量（本项目实现为限购1件）',
  `sort`           int      NOT NULL DEFAULT 0      COMMENT '场内排序，值越小越靠前',
  `status`         tinyint  NOT NULL DEFAULT 1      COMMENT '状态：1启用 0禁用',
  `version`        int      NOT NULL DEFAULT 0      COMMENT '乐观锁版本号（备用方案，见§4.3对比）',
  `create_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        tinyint  NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_id_sku_id` (`activity_id`, `sku_id`),
  KEY `idx_sku_id` (`sku_id`),
  KEY `idx_activity_id_status_sort` (`activity_id`, `status`, `sort`),
  KEY `idx_merchant_id` (`merchant_id`),
  CONSTRAINT `chk_seckill_sold` CHECK (`seckill_sold` >= 0 AND `seckill_sold` <= `seckill_stock`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='秒杀商品表（含秒杀库存）';

DROP TABLE IF EXISTS `sms_seckill_record`;
CREATE TABLE `sms_seckill_record` (
  `id`             bigint       NOT NULL                COMMENT '主键，雪花ID',
  `merchant_id`    bigint       NOT NULL DEFAULT 1      COMMENT '商家ID，多商户预留：1=自营',
  `activity_id`    bigint       NOT NULL                COMMENT '秒杀活动ID',
  `seckill_sku_id` bigint       NOT NULL                COMMENT '秒杀商品ID，逻辑关联 sms_seckill_sku.id',
  `sku_id`         bigint       NOT NULL                COMMENT 'SKU ID（冗余，便于直接查商品）',
  `user_id`        bigint       NOT NULL                COMMENT '抢购会员ID',
  `quantity`       int          NOT NULL DEFAULT 1      COMMENT '抢购数量（本项目恒为1）',
  `seckill_price`  bigint       NOT NULL DEFAULT 0      COMMENT '秒杀成交价（单位：分，快照）',
  `order_id`       bigint                DEFAULT NULL   COMMENT '生成的订单ID（异步创建，可能短暂为空）',
  `order_no`       varchar(64)           DEFAULT NULL   COMMENT '生成的订单号',
  `status`         tinyint      NOT NULL DEFAULT 0      COMMENT '状态：0已抢到待支付 1已支付 2已取消/超时 3已退款',
  `request_id`     varchar(64)           DEFAULT NULL   COMMENT '客户端请求唯一ID（接口级幂等，防按钮重复提交）',
  `ip`             varchar(64)           DEFAULT NULL   COMMENT '抢购IP（风控用）',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（即抢到时间）',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_id_user_id` (`activity_id`, `user_id`),
  UNIQUE KEY `uk_request_id` (`request_id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id_create_time` (`user_id`, `create_time`),
  KEY `idx_seckill_sku_id` (`seckill_sku_id`),
  KEY `idx_status_create_time` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='秒杀成功记录表';
