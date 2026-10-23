-- =====================================================================
--  Phase 4 · merchant 模块（3 张 mms_*，选做）
--  ⚠️ 本文件由 tools/extract-ddl.ps1 从 docs/04-数据库设计.md 自动生成，请勿手工修改。
--     要改表结构，先改设计文档，再重新生成。
--  目标：MySQL 8.0 / InnoDB / utf8mb4_0900_ai_ci
--  ⚠️ 含 DROP TABLE IF EXISTS，【仅供开发环境反复执行】。
--     生产脚本必须去掉 DROP、改用 CREATE TABLE IF NOT EXISTS（见 docs/04 §9.1 原则 3）。
-- =====================================================================
DROP TABLE IF EXISTS `mms_merchant`;
CREATE TABLE `mms_merchant` (
  `id`             bigint       NOT NULL                COMMENT '主键，雪花ID（1=自营商家，初始化脚本写入）',
  `merchant_no`    varchar(32)  NOT NULL                COMMENT '商家编号，如 M0001',
  `merchant_name`  varchar(64)  NOT NULL                COMMENT '商家名称',
  `merchant_type`  tinyint      NOT NULL DEFAULT 1      COMMENT '商家类型：1自营 2POP入驻 3联营',
  `contact_name`   varchar(64)  NOT NULL DEFAULT ''     COMMENT '联系人姓名',
  `contact_phone`  varchar(20)  NOT NULL DEFAULT ''     COMMENT '联系人手机号',
  `email`          varchar(64)           DEFAULT NULL   COMMENT '联系邮箱',
  `license_no`     varchar(64)           DEFAULT NULL   COMMENT '营业执照号（统一社会信用代码）',
  `license_img`    varchar(500)          DEFAULT NULL   COMMENT '营业执照图片URL',
  `legal_person`   varchar(64)           DEFAULT NULL   COMMENT '法定代表人',
  `id_card_front`  varchar(500)          DEFAULT NULL   COMMENT '法人身份证正面URL',
  `id_card_back`   varchar(500)          DEFAULT NULL   COMMENT '法人身份证反面URL',
  `company_address` varchar(255)         DEFAULT NULL   COMMENT '公司地址',
  `settle_rate`    int          NOT NULL DEFAULT 0      COMMENT '平台抽成比例（万分比）：500=5%，避免用小数',
  `bank_name`      varchar(128)          DEFAULT NULL   COMMENT '开户银行',
  `bank_account`   varchar(64)           DEFAULT NULL   COMMENT '银行账号（应加密存储）',
  `bank_holder`    varchar(64)           DEFAULT NULL   COMMENT '开户人姓名',
  `audit_status`   tinyint      NOT NULL DEFAULT 0      COMMENT '审核状态：0待审核 1审核通过 2审核驳回',
  `audit_remark`   varchar(500)          DEFAULT NULL   COMMENT '审核意见',
  `audit_time`     datetime              DEFAULT NULL   COMMENT '审核时间',
  `status`         tinyint      NOT NULL DEFAULT 1      COMMENT '状态：1正常 0停用/关店',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（即入驻申请时间）',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_no` (`merchant_no`),
  UNIQUE KEY `uk_license_no` (`license_no`, `deleted`),
  UNIQUE KEY `uk_contact_phone` (`contact_phone`, `deleted`),
  KEY `idx_audit_status_create_time` (`audit_status`, `create_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='入驻商家表';

DROP TABLE IF EXISTS `mms_shop`;
CREATE TABLE `mms_shop` (
  `id`             bigint       NOT NULL                COMMENT '主键，雪花ID',
  `merchant_id`    bigint       NOT NULL                COMMENT '商家ID，逻辑关联 mms_merchant.id',
  `shop_name`      varchar(64)  NOT NULL                COMMENT '店铺名称',
  `shop_type`      tinyint      NOT NULL DEFAULT 1      COMMENT '店铺类型：1旗舰店 2专营店 3普通店',
  `logo`           varchar(500)          DEFAULT NULL   COMMENT '店铺Logo URL',
  `banner`         varchar(500)          DEFAULT NULL   COMMENT '店铺Banner URL',
  `description`    varchar(1000)         DEFAULT NULL   COMMENT '店铺简介',
  `customer_service_phone` varchar(20)   DEFAULT NULL   COMMENT '客服电话',
  `fans_count`     int          NOT NULL DEFAULT 0      COMMENT '粉丝数',
  `product_count`  int          NOT NULL DEFAULT 0      COMMENT '在售商品数（冗余，定时刷新）',
  `sale_count`     int          NOT NULL DEFAULT 0      COMMENT '累计销量（冗余，定时刷新）',
  `score`          int          NOT NULL DEFAULT 500    COMMENT '店铺评分（万分比）：50000=5.00分，用整数避免小数',
  `sort`           int          NOT NULL DEFAULT 0      COMMENT '排序，值越小越靠前',
  `status`         tinyint      NOT NULL DEFAULT 1      COMMENT '状态：1营业中 0已关闭',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_id` (`merchant_id`, `deleted`),
  KEY `idx_status_sort` (`status`, `sort`),
  KEY `idx_shop_name` (`shop_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='店铺表';

DROP TABLE IF EXISTS `mms_settlement`;
CREATE TABLE `mms_settlement` (
  `id`                 bigint       NOT NULL                COMMENT '主键，雪花ID',
  `settlement_no`      varchar(64)  NOT NULL                COMMENT '结算单号，如 ST2025060001',
  `merchant_id`        bigint       NOT NULL                COMMENT '商家ID',
  `settle_period`      varchar(16)  NOT NULL                COMMENT '结算周期，如 2025-06（按月）',
  `period_start`       datetime     NOT NULL                COMMENT '周期开始时间',
  `period_end`         datetime     NOT NULL                COMMENT '周期结束时间',
  `order_count`        int          NOT NULL DEFAULT 0      COMMENT '结算周期内有效订单数',
  `order_amount`       bigint       NOT NULL DEFAULT 0      COMMENT '订单收款总额（单位：分）',
  `freight_amount`     bigint       NOT NULL DEFAULT 0      COMMENT '其中运费合计（单位：分），通常不参与抽成',
  `refund_amount`      bigint       NOT NULL DEFAULT 0      COMMENT '退款总额（单位：分）',
  `commission_rate`    int          NOT NULL DEFAULT 0      COMMENT '本次结算采用的平台抽成比例（万分比快照）',
  `commission_amount`  bigint       NOT NULL DEFAULT 0      COMMENT '平台抽成金额（单位：分）',
  `settle_amount`      bigint       NOT NULL DEFAULT 0      COMMENT '应结给商家金额（单位：分）= order_amount - refund_amount - commission_amount',
  `status`             tinyint      NOT NULL DEFAULT 0      COMMENT '状态：0待结算 1已结算 2已打款 3已作废',
  `settle_time`        datetime              DEFAULT NULL   COMMENT '结算时间',
  `pay_time`           datetime              DEFAULT NULL   COMMENT '打款时间',
  `pay_voucher`        varchar(500)          DEFAULT NULL   COMMENT '打款凭证URL',
  `remark`             varchar(500)          DEFAULT NULL   COMMENT '备注/差异说明',
  `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_no` (`settlement_no`),
  UNIQUE KEY `uk_merchant_id_settle_period` (`merchant_id`, `settle_period`, `deleted`),
  KEY `idx_status_settle_period` (`status`, `settle_period`),
  KEY `idx_merchant_id_create_time` (`merchant_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商家结算单表';
