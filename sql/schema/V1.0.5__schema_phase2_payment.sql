-- =====================================================================
--  Phase 2 · payment 模块 + 基础设施（3 张 pay_* + sys_message）
--  ⚠️ 本文件由 tools/extract-ddl.ps1 从 docs/04-数据库设计.md 自动生成，请勿手工修改。
--     要改表结构，先改设计文档，再重新生成。
--  目标：MySQL 8.0 / InnoDB / utf8mb4_0900_ai_ci
--  ⚠️ 含 DROP TABLE IF EXISTS，【仅供开发环境反复执行】。
--     生产脚本必须去掉 DROP、改用 CREATE TABLE IF NOT EXISTS（见 docs/04 §9.1 原则 3）。
-- =====================================================================
DROP TABLE IF EXISTS `pay_payment`;
CREATE TABLE `pay_payment` (
  `id`              bigint       NOT NULL                COMMENT '主键，雪花ID',
  `merchant_id`     bigint       NOT NULL DEFAULT 1      COMMENT '商家ID，多商户预留：1=自营（Phase4分账依据）',
  `payment_no`      varchar(64)  NOT NULL                COMMENT '支付单号（本系统生成），如 PAY20250601120000001',
  `order_id`        bigint       NOT NULL                COMMENT '订单ID，逻辑关联 oms_order.id',
  `order_no`        varchar(64)  NOT NULL                COMMENT '订单号（冗余）',
  `user_id`         bigint       NOT NULL                COMMENT '付款会员ID',
  `amount`          bigint       NOT NULL DEFAULT 0      COMMENT '应付金额（单位：分），下单时从 oms_order.pay_amount 复制',
  `paid_amount`     bigint       NOT NULL DEFAULT 0      COMMENT '实收金额（单位：分），支付成功后由回调写入',
  `channel`         tinyint      NOT NULL DEFAULT 1      COMMENT '支付渠道：1模拟支付宝 2模拟微信 3余额支付',
  `status`          tinyint      NOT NULL DEFAULT 0      COMMENT '支付状态：0待支付 1支付成功 2支付失败 3已关闭 4已全额退款 5部分退款',
  `trade_no`        varchar(64)           DEFAULT NULL   COMMENT '第三方交易号（渠道返回，对账唯一依据）',
  `buyer_account`   varchar(128)          DEFAULT NULL   COMMENT '渠道侧付款账号（脱敏存储）',
  `subject`         varchar(255)          DEFAULT NULL   COMMENT '支付标题（传给渠道展示，如「订单 SO2025...」）',
  `notify_data`     text                  DEFAULT NULL   COMMENT '最近一次成功回调的原始报文（截断至4000字符）',
  `error_msg`       varchar(500)          DEFAULT NULL   COMMENT '失败原因',
  `expire_time`     datetime              DEFAULT NULL   COMMENT '支付单过期时间（超时后不可再支付）',
  `pay_time`        datetime              DEFAULT NULL   COMMENT '支付成功时间',
  `close_time`      datetime              DEFAULT NULL   COMMENT '关闭时间',
  `version`         int          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号，防止并发回调重复处理',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  UNIQUE KEY `uk_order_id_channel_status` (`order_id`, `channel`, `status`, `deleted`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_trade_no` (`trade_no`),
  KEY `idx_user_id_create_time` (`user_id`, `create_time`),
  KEY `idx_status_expire_time` (`status`, `expire_time`),
  KEY `idx_merchant_id_create_time` (`merchant_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付单表';

DROP TABLE IF EXISTS `pay_refund`;
CREATE TABLE `pay_refund` (
  `id`                bigint       NOT NULL                COMMENT '主键，雪花ID',
  `merchant_id`       bigint       NOT NULL DEFAULT 1      COMMENT '商家ID，多商户预留：1=自营',
  `refund_no`         varchar(64)  NOT NULL                COMMENT '退款单号（本系统生成），如 RF20250601120000001',
  `payment_no`        varchar(64)  NOT NULL                COMMENT '原支付单号，逻辑关联 pay_payment.payment_no',
  `order_id`          bigint       NOT NULL                COMMENT '订单ID',
  `order_no`          varchar(64)  NOT NULL                COMMENT '订单号（冗余）',
  `aftersale_id`      bigint                DEFAULT NULL   COMMENT '关联售后单ID（后台主动退款时为空）',
  `user_id`           bigint       NOT NULL                COMMENT '收款会员ID',
  `channel`           tinyint      NOT NULL DEFAULT 1      COMMENT '退款渠道：1模拟支付宝 2模拟微信 3余额',
  `refund_amount`     bigint       NOT NULL DEFAULT 0      COMMENT '退款金额（单位：分）',
  `refund_type`       tinyint      NOT NULL DEFAULT 1      COMMENT '退款类型：1全额 2部分',
  `status`            tinyint      NOT NULL DEFAULT 0      COMMENT '退款状态：0待退款 1退款中 2退款成功 3退款失败',
  `channel_refund_no` varchar(64)           DEFAULT NULL   COMMENT '渠道退款单号（渠道返回）',
  `reason`            varchar(255)          DEFAULT NULL   COMMENT '退款原因',
  `error_msg`         varchar(500)          DEFAULT NULL   COMMENT '失败原因',
  `retry_count`       int          NOT NULL DEFAULT 0      COMMENT '重试次数（定时任务补单用）',
  `next_retry_time`   datetime              DEFAULT NULL   COMMENT '下次重试时间（指数退避）',
  `refund_time`       datetime              DEFAULT NULL   COMMENT '退款成功时间',
  `version`           int          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`           tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_no` (`refund_no`),
  UNIQUE KEY `uk_aftersale_id` (`aftersale_id`, `deleted`),
  KEY `idx_payment_no` (`payment_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_status_next_retry_time` (`status`, `next_retry_time`),
  KEY `idx_merchant_id_create_time` (`merchant_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退款单表';

DROP TABLE IF EXISTS `pay_notify_log`;
CREATE TABLE `pay_notify_log` (
  `id`                 bigint       NOT NULL                COMMENT '主键，雪花ID',
  `channel_notify_id`  varchar(128)          DEFAULT NULL   COMMENT '渠道回调唯一ID（幂等键），由模拟支付网关生成',
  `channel`            tinyint      NOT NULL DEFAULT 1      COMMENT '渠道：1模拟支付宝 2模拟微信 3余额',
  `notify_type`        tinyint      NOT NULL DEFAULT 1      COMMENT '回调类型：1支付回调 2退款回调',
  `payment_no`         varchar(64)           DEFAULT NULL   COMMENT '本系统支付单号（从报文解析）',
  `order_no`           varchar(64)           DEFAULT NULL   COMMENT '订单号（从报文解析，便于排查）',
  `trade_no`           varchar(64)           DEFAULT NULL   COMMENT '渠道交易号',
  `request_body`       text                  DEFAULT NULL   COMMENT '回调原始报文（完整保留，排障依据）',
  `request_headers`    text                  DEFAULT NULL   COMMENT '回调请求头（含签名相关字段）',
  `sign`               varchar(512)          DEFAULT NULL   COMMENT '渠道签名原文',
  `verify_status`      tinyint      NOT NULL DEFAULT 0      COMMENT '验签结果：1通过 0失败',
  `handle_status`      tinyint      NOT NULL DEFAULT 0      COMMENT '处理结果：0待处理 1处理成功 2处理失败 3重复回调已忽略',
  `handle_msg`         varchar(500)          DEFAULT NULL   COMMENT '处理结果描述',
  `handle_time`        datetime              DEFAULT NULL   COMMENT '处理完成时间',
  `cost_time`          bigint       NOT NULL DEFAULT 0      COMMENT '处理耗时（毫秒）',
  `trace_id`           varchar(64)           DEFAULT NULL   COMMENT '链路追踪ID',
  `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（即收到回调时间）',
  `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除（恒为0）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_channel_notify_id` (`channel`, `channel_notify_id`),
  KEY `idx_payment_no` (`payment_no`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_handle_status_create_time` (`handle_status`, `create_time`),
  KEY `idx_trade_no` (`trade_no`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付回调日志表';

DROP TABLE IF EXISTS `sys_message`;
CREATE TABLE `sys_message` (
  `id`               bigint       NOT NULL                COMMENT '主键，雪花ID',
  `message_no`       varchar(64)  NOT NULL                COMMENT '消息唯一编号（幂等键）',
  `biz_type`         varchar(64)  NOT NULL                COMMENT '业务类型：ORDER_CREATE/ORDER_CANCEL/STOCK_UNLOCK/REFUND_APPLY/SECKILL_ORDER',
  `biz_no`           varchar(64)  NOT NULL                COMMENT '业务单号（如订单号、支付单号）',
  `topic`            varchar(128) NOT NULL                COMMENT 'RocketMQ Topic',
  `tag`              varchar(64)           DEFAULT NULL   COMMENT 'RocketMQ Tag（用于消费端过滤）',
  `msg_key`          varchar(128)          DEFAULT NULL   COMMENT '消息业务KEY（RocketMQ msgKey，便于控制台按KEY查询）',
  `payload`          json         NOT NULL                COMMENT '消息体（JSON）',
  `status`           tinyint      NOT NULL DEFAULT 0      COMMENT '状态：0待发送 1已发送 2消费成功 3发送失败 4消费失败',
  `retry_count`      int          NOT NULL DEFAULT 0      COMMENT '重试次数',
  `next_retry_time`  datetime              DEFAULT NULL   COMMENT '下次重试时间（指数退避）',
  `error_msg`        varchar(1000)         DEFAULT NULL   COMMENT '最后一次错误信息',
  `send_time`        datetime              DEFAULT NULL   COMMENT '发送成功时间',
  `consume_time`     datetime              DEFAULT NULL   COMMENT '消费成功时间',
  `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_no` (`message_no`),
  KEY `idx_status_next_retry_time` (`status`, `next_retry_time`),
  KEY `idx_biz_type_biz_no` (`biz_type`, `biz_no`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='本地消息表（最终一致性）';
