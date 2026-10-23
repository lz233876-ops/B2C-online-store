-- =====================================================================
--  Phase 2 · cart + inventory 模块（oms_cart_item / wms_*）
--  ⚠️ 本文件由 tools/extract-ddl.ps1 从 docs/04-数据库设计.md 自动生成，请勿手工修改。
--     要改表结构，先改设计文档，再重新生成。
--  目标：MySQL 8.0 / InnoDB / utf8mb4_0900_ai_ci
--  ⚠️ 含 DROP TABLE IF EXISTS，【仅供开发环境反复执行】。
--     生产脚本必须去掉 DROP、改用 CREATE TABLE IF NOT EXISTS（见 docs/04 §9.1 原则 3）。
-- =====================================================================
DROP TABLE IF EXISTS `oms_cart_item`;
CREATE TABLE `oms_cart_item` (
  `id`          bigint       NOT NULL                COMMENT '主键，雪花ID',
  `merchant_id` bigint       NOT NULL DEFAULT 1      COMMENT '商家ID，多商户预留：1=自营',
  `user_id`     bigint       NOT NULL                COMMENT '会员ID，逻辑关联 ums_user.id',
  `spu_id`      bigint       NOT NULL                COMMENT 'SPU ID（冗余，便于按商品分组展示）',
  `sku_id`      bigint       NOT NULL                COMMENT 'SKU ID，逻辑关联 pms_sku.id',
  `quantity`    int          NOT NULL DEFAULT 1      COMMENT '购买数量',
  `selected`    tinyint      NOT NULL DEFAULT 1      COMMENT '是否勾选结算：1勾选 0未勾选',
  `price`       bigint       NOT NULL DEFAULT 0      COMMENT '加入购物车时的单价快照（单位：分）',
  `sku_name`    varchar(200) NOT NULL DEFAULT ''     COMMENT '商品名称快照（防商品改名/删除后购物车展示异常）',
  `sku_image`   varchar(500)          DEFAULT NULL   COMMENT '商品图片快照',
  `spec_desc`   varchar(255)          DEFAULT NULL   COMMENT '规格摘要快照，如「128GB / 黑色」',
  `spu_status`  tinyint      NOT NULL DEFAULT 1      COMMENT '加购时SPU状态快照：1上架 0下架（用于提示「商品已下架」）',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id_sku_id` (`user_id`, `sku_id`, `deleted`),
  KEY `idx_user_id_selected` (`user_id`, `selected`),
  KEY `idx_sku_id` (`sku_id`),
  KEY `idx_merchant_id` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购物车项表';

DROP TABLE IF EXISTS `wms_stock`;
CREATE TABLE `wms_stock` (
  `id`              bigint   NOT NULL                COMMENT '主键，雪花ID',
  `merchant_id`     bigint   NOT NULL DEFAULT 1      COMMENT '商家ID，多商户预留：1=自营',
  `sku_id`          bigint   NOT NULL                COMMENT 'SKU ID，逻辑关联 pms_sku.id',
  `warehouse_id`    bigint   NOT NULL DEFAULT 1      COMMENT '仓库ID，Phase1-3 固定为1（单仓）',
  `stock`           int      NOT NULL DEFAULT 0      COMMENT '实际总库存（含已锁定部分）',
  `locked_stock`    int      NOT NULL DEFAULT 0      COMMENT '下单已锁定库存（未支付）',
  `available_stock` int      GENERATED ALWAYS AS (`stock` - `locked_stock`) STORED
                                                     COMMENT '可售库存=stock-locked_stock（生成列，只读）',
  `warn_stock`      int      NOT NULL DEFAULT 0      COMMENT '库存预警阈值，低于此值告警',
  `total_in`        int      NOT NULL DEFAULT 0      COMMENT '累计入库数量（对账用）',
  `total_out`       int      NOT NULL DEFAULT 0      COMMENT '累计出库数量（对账用）',
  `version`         int      NOT NULL DEFAULT 0      COMMENT '乐观锁版本号（备用方案，见§4.3对比）',
  `create_time`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         tinyint  NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_id_warehouse_id` (`sku_id`, `warehouse_id`),
  KEY `idx_merchant_id` (`merchant_id`),
  KEY `idx_available_stock` (`available_stock`),
  CONSTRAINT `chk_stock_non_negative` CHECK (`stock` >= 0 AND `locked_stock` >= 0 AND `locked_stock` <= `stock`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品库存表';

DROP TABLE IF EXISTS `wms_stock_log`;
CREATE TABLE `wms_stock_log` (
  `id`            bigint       NOT NULL                COMMENT '主键，雪花ID',
  `merchant_id`   bigint       NOT NULL DEFAULT 1      COMMENT '商家ID，多商户预留：1=自营',
  `sku_id`        bigint       NOT NULL                COMMENT 'SKU ID，逻辑关联 pms_sku.id',
  `warehouse_id`  bigint       NOT NULL DEFAULT 1      COMMENT '仓库ID',
  `biz_type`      tinyint      NOT NULL                COMMENT '业务类型：1下单锁定 2支付扣减 3取消释放 4退款回补 5后台调整 6秒杀扣减 7入库',
  `biz_no`        varchar(64)  NOT NULL                COMMENT '业务幂等号（见下方说明），(biz_type,biz_no)全局唯一',
  `order_no`      varchar(64)           DEFAULT NULL   COMMENT '关联订单号（便于按订单排查）',
  `change_num`    int          NOT NULL                COMMENT '变动数量（带符号）= Δ可售库存：锁定- 扣减0 释放+ 回补+',
  `before_stock`  int          NOT NULL DEFAULT 0      COMMENT '变动前可售库存',
  `after_stock`   int          NOT NULL DEFAULT 0      COMMENT '变动后可售库存',
  `operator_type` tinyint      NOT NULL DEFAULT 3      COMMENT '操作人类型：1用户 2管理员 3系统',
  `operator_id`   bigint                DEFAULT NULL   COMMENT '操作人ID',
  `remark`        varchar(255)          DEFAULT NULL   COMMENT '备注，如「超时关单释放」',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除（流水表恒为0，见说明）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_type_biz_no` (`biz_type`, `biz_no`),
  KEY `idx_sku_id_create_time` (`sku_id`, `create_time`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存流水表（幂等核心）';
