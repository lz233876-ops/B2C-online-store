-- =====================================================================
--  Phase 1 · user + auth 模块（4 张 ums_*）
--  ⚠️ 本文件由 tools/extract-ddl.ps1 从 docs/04-数据库设计.md 自动生成，请勿手工修改。
--     要改表结构，先改设计文档，再重新生成。
--  目标：MySQL 8.0 / InnoDB / utf8mb4_0900_ai_ci
--  ⚠️ 含 DROP TABLE IF EXISTS，【仅供开发环境反复执行】。
--     生产脚本必须去掉 DROP、改用 CREATE TABLE IF NOT EXISTS（见 docs/04 §9.1 原则 3）。
-- =====================================================================
DROP TABLE IF EXISTS `ums_user`;
CREATE TABLE `ums_user` (
  `id`              bigint       NOT NULL                COMMENT '主键，雪花ID',
  `username`        varchar(64)           DEFAULT NULL   COMMENT '会员昵称（可空，允许后设置）',
  `password`        varchar(100)          DEFAULT NULL   COMMENT 'BCrypt密码，验证码登录用户为NULL',
  `phone`           varchar(20)  NOT NULL                COMMENT '手机号，登录主标识',
  `email`           varchar(64)           DEFAULT NULL   COMMENT '邮箱',
  `nickname`        varchar(64)           DEFAULT NULL   COMMENT '展示昵称',
  `avatar`          varchar(500)          DEFAULT NULL   COMMENT '头像URL',
  `gender`          tinyint      NOT NULL DEFAULT 0      COMMENT '性别：0未知 1男 2女',
  `birthday`        date                  DEFAULT NULL   COMMENT '生日',
  `member_level_id` bigint       NOT NULL DEFAULT 1      COMMENT '会员等级ID，逻辑关联 ums_member_level.id',
  `growth`          int          NOT NULL DEFAULT 0      COMMENT '成长值，升级依据',
  `points`          int          NOT NULL DEFAULT 0      COMMENT '可用积分',
  `total_points`    int          NOT NULL DEFAULT 0      COMMENT '历史累计积分（只增不减，用于等级判定）',
  `status`          tinyint      NOT NULL DEFAULT 1      COMMENT '状态：1正常 0禁用',
  `register_source` tinyint      NOT NULL DEFAULT 1      COMMENT '注册来源：1PC 2H5 3小程序 4App',
  `last_login_time` datetime              DEFAULT NULL   COMMENT '最后登录时间',
  `last_login_ip`   varchar(64)           DEFAULT NULL   COMMENT '最后登录IP',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（即注册时间）',
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`, `deleted`),
  UNIQUE KEY `uk_username` (`username`, `deleted`),
  KEY `idx_member_level_id` (`member_level_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='C端会员表';

DROP TABLE IF EXISTS `ums_user_address`;
CREATE TABLE `ums_user_address` (
  `id`               bigint       NOT NULL                COMMENT '主键，雪花ID',
  `user_id`          bigint       NOT NULL                COMMENT '会员ID，逻辑关联 ums_user.id',
  `receiver_name`    varchar(64)  NOT NULL                COMMENT '收货人姓名',
  `receiver_phone`   varchar(20)  NOT NULL                COMMENT '收货人手机号',
  `province`         varchar(64)  NOT NULL DEFAULT ''     COMMENT '省',
  `city`             varchar(64)  NOT NULL DEFAULT ''     COMMENT '市',
  `district`         varchar(64)  NOT NULL DEFAULT ''     COMMENT '区/县',
  `detail_address`   varchar(255) NOT NULL                COMMENT '详细地址（街道门牌）',
  `postal_code`      varchar(16)           DEFAULT NULL   COMMENT '邮政编码',
  `default_status`   tinyint      NOT NULL DEFAULT 0      COMMENT '是否默认：1是 0否',
  `tag`              varchar(32)           DEFAULT NULL   COMMENT '标签：家/公司/学校',
  `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id_default_status` (`user_id`, `default_status`, `deleted`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_receiver_phone` (`receiver_phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员收货地址表';

DROP TABLE IF EXISTS `ums_member_level`;
CREATE TABLE `ums_member_level` (
  `id`               bigint      NOT NULL                COMMENT '主键，雪花ID',
  `level_name`       varchar(64) NOT NULL                COMMENT '等级名称，如「黄金会员」',
  `level_value`      int         NOT NULL                COMMENT '等级值，数字越大等级越高',
  `growth_threshold` int         NOT NULL DEFAULT 0      COMMENT '升级所需成长值门槛（累计值）',
  `discount_percent` int         NOT NULL DEFAULT 100    COMMENT '折扣百分比：100=不打折 95=9.5折 90=9折',
  `icon`             varchar(500)         DEFAULT NULL   COMMENT '等级图标URL',
  `description`      varchar(255)         DEFAULT NULL   COMMENT '等级权益描述',
  `status`           tinyint     NOT NULL DEFAULT 1      COMMENT '状态：1启用 0禁用',
  `create_time`      datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          tinyint     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_level_value` (`level_value`, `deleted`),
  KEY `idx_growth_threshold` (`growth_threshold`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员等级表';

DROP TABLE IF EXISTS `ums_login_log`;
CREATE TABLE `ums_login_log` (
  `id`          bigint       NOT NULL                COMMENT '主键，雪花ID',
  `user_id`     bigint                DEFAULT NULL   COMMENT '用户ID（登录失败时为NULL）',
  `username`    varchar(64)           DEFAULT NULL   COMMENT '登录账号/手机号（冗余，失败时也要记）',
  `user_type`   tinyint      NOT NULL DEFAULT 2      COMMENT '用户类型：1后台用户 2C端会员',
  `login_type`  tinyint      NOT NULL DEFAULT 1      COMMENT '登录方式：1密码 2验证码 3刷新Token',
  `ip`          varchar(64)           DEFAULT NULL   COMMENT '登录IP',
  `ip_region`   varchar(128)          DEFAULT NULL   COMMENT 'IP归属地',
  `user_agent`  varchar(500)          DEFAULT NULL   COMMENT '浏览器/客户端UA',
  `status`      tinyint      NOT NULL DEFAULT 1      COMMENT '登录结果：1成功 0失败',
  `msg`         varchar(255)          DEFAULT NULL   COMMENT '结果描述，如「密码错误」「账号已锁定」',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（即登录时间）',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_username_status` (`username`, `status`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_ip` (`ip`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志表';
