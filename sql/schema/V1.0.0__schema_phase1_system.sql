-- =====================================================================
--  Phase 1 · system 模块（9 张 sys_* + sys_file）
--  ⚠️ 本文件由 tools/extract-ddl.ps1 从 docs/04-数据库设计.md 自动生成，请勿手工修改。
--     要改表结构，先改设计文档，再重新生成。
--  目标：MySQL 8.0 / InnoDB / utf8mb4_0900_ai_ci
--  ⚠️ 含 DROP TABLE IF EXISTS，【仅供开发环境反复执行】。
--     生产脚本必须去掉 DROP、改用 CREATE TABLE IF NOT EXISTS（见 docs/04 §9.1 原则 3）。
-- =====================================================================
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`              bigint       NOT NULL                COMMENT '主键，雪花ID',
  `username`        varchar(64)  NOT NULL                COMMENT '登录账号',
  `password`        varchar(100) NOT NULL                COMMENT 'BCrypt 加密后的密码（60位，留余量）',
  `nickname`        varchar(64)  NOT NULL DEFAULT ''     COMMENT '昵称/显示名',
  `real_name`       varchar(64)           DEFAULT NULL   COMMENT '真实姓名',
  `avatar`          varchar(500)          DEFAULT NULL   COMMENT '头像 URL',
  `phone`           varchar(20)           DEFAULT NULL   COMMENT '手机号',
  `email`           varchar(64)           DEFAULT NULL   COMMENT '邮箱',
  `gender`          tinyint      NOT NULL DEFAULT 0      COMMENT '性别：0未知 1男 2女',
  `dept_name`       varchar(64)           DEFAULT NULL   COMMENT '所属部门（本项目不做部门树，只留名称）',
  `status`          tinyint      NOT NULL DEFAULT 1      COMMENT '状态：1启用 0禁用',
  `last_login_time` datetime              DEFAULT NULL   COMMENT '最后登录时间',
  `last_login_ip`   varchar(64)           DEFAULT NULL   COMMENT '最后登录IP',
  `pwd_update_time` datetime              DEFAULT NULL   COMMENT '密码最后修改时间，用于强制改密策略',
  `remark`          varchar(255)          DEFAULT NULL   COMMENT '备注',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`, `deleted`),
  KEY `idx_phone` (`phone`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台用户表';

DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id`          bigint      NOT NULL                COMMENT '主键，雪花ID',
  `role_name`   varchar(64) NOT NULL                COMMENT '角色名称，如「超级管理员」',
  `role_code`   varchar(64) NOT NULL                COMMENT '角色编码，如 SUPER_ADMIN / OPERATOR',
  `data_scope`  tinyint     NOT NULL DEFAULT 1      COMMENT '数据范围：1全部 2本商户 3仅本人',
  `sort`        int         NOT NULL DEFAULT 0      COMMENT '排序，值越小越靠前',
  `status`      tinyint     NOT NULL DEFAULT 1      COMMENT '状态：1启用 0禁用',
  `remark`      varchar(255)         DEFAULT NULL   COMMENT '备注',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     tinyint     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`, `deleted`),
  KEY `idx_status_sort` (`status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台角色表';

DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `id`          bigint   NOT NULL                COMMENT '主键，雪花ID',
  `user_id`     bigint   NOT NULL                COMMENT '用户ID，逻辑关联 sys_user.id',
  `role_id`     bigint   NOT NULL                COMMENT '角色ID，逻辑关联 sys_role.id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     tinyint  NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id_role_id` (`user_id`, `role_id`, `deleted`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台用户-角色关联表';

DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
  `id`          bigint       NOT NULL                COMMENT '主键，雪花ID',
  `parent_id`   bigint       NOT NULL DEFAULT 0      COMMENT '父菜单ID，0表示根节点',
  `menu_name`   varchar(64)  NOT NULL                COMMENT '菜单名称',
  `menu_type`   tinyint      NOT NULL DEFAULT 1      COMMENT '类型：1目录 2菜单 3按钮',
  `path`        varchar(200)          DEFAULT NULL   COMMENT '前端路由路径，如 /system/user',
  `component`   varchar(200)          DEFAULT NULL   COMMENT '前端组件路径，如 system/user/index',
  `perms`       varchar(128)          DEFAULT NULL   COMMENT '权限标识，如 system:user:add',
  `icon`        varchar(64)           DEFAULT NULL   COMMENT '菜单图标',
  `sort`        int          NOT NULL DEFAULT 0      COMMENT '同级排序，值越小越靠前',
  `visible`     tinyint      NOT NULL DEFAULT 1      COMMENT '是否在菜单中显示：1显示 0隐藏',
  `keep_alive`  tinyint      NOT NULL DEFAULT 1      COMMENT '是否缓存页面：1是 0否',
  `status`      tinyint      NOT NULL DEFAULT 1      COMMENT '状态：1启用 0禁用',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perms` (`perms`, `deleted`),
  KEY `idx_parent_id_sort` (`parent_id`, `sort`),
  KEY `idx_menu_type` (`menu_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台菜单与权限点表';

DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu` (
  `id`          bigint   NOT NULL                COMMENT '主键，雪花ID',
  `role_id`     bigint   NOT NULL                COMMENT '角色ID',
  `menu_id`     bigint   NOT NULL                COMMENT '菜单/权限点ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     tinyint  NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_id_menu_id` (`role_id`, `menu_id`, `deleted`),
  KEY `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-菜单权限关联表';

DROP TABLE IF EXISTS `sys_dict_type`;
CREATE TABLE `sys_dict_type` (
  `id`          bigint      NOT NULL                COMMENT '主键，雪花ID',
  `dict_name`   varchar(64) NOT NULL                COMMENT '字典名称，如「订单状态」',
  `dict_type`   varchar(64) NOT NULL                COMMENT '字典类型编码，如 order_status',
  `status`      tinyint     NOT NULL DEFAULT 1      COMMENT '状态：1启用 0禁用',
  `remark`      varchar(255)         DEFAULT NULL   COMMENT '备注',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     tinyint     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type` (`dict_type`, `deleted`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典类型表';

DROP TABLE IF EXISTS `sys_dict_data`;
CREATE TABLE `sys_dict_data` (
  `id`          bigint       NOT NULL                COMMENT '主键，雪花ID',
  `dict_type`   varchar(64)  NOT NULL                COMMENT '字典类型编码，逻辑关联 sys_dict_type.dict_type',
  `dict_label`  varchar(64)  NOT NULL                COMMENT '字典标签（展示值），如「待支付」',
  `dict_value`  varchar(64)  NOT NULL                COMMENT '字典键值（存储值），如 0',
  `sort`        int          NOT NULL DEFAULT 0      COMMENT '排序，值越小越靠前',
  `css_class`   varchar(64)           DEFAULT NULL   COMMENT '前端标签样式，如 success/warning/danger',
  `is_default`  tinyint      NOT NULL DEFAULT 0      COMMENT '是否默认选中：1是 0否',
  `status`      tinyint      NOT NULL DEFAULT 1      COMMENT '状态：1启用 0禁用',
  `remark`      varchar(255)          DEFAULT NULL   COMMENT '备注',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type_dict_value` (`dict_type`, `dict_value`, `deleted`),
  KEY `idx_dict_type_sort` (`dict_type`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典数据表';

DROP TABLE IF EXISTS `sys_operation_log`;
CREATE TABLE `sys_operation_log` (
  `id`             bigint        NOT NULL                COMMENT '主键，雪花ID',
  `user_id`        bigint                 DEFAULT NULL   COMMENT '操作人ID（未登录为NULL）',
  `username`       varchar(64)            DEFAULT NULL   COMMENT '操作人账号（冗余，防用户被删后无法追溯）',
  `module`         varchar(64)   NOT NULL DEFAULT ''     COMMENT '所属模块，如 商品管理',
  `operation`      varchar(128)  NOT NULL DEFAULT ''     COMMENT '操作描述，如 新增商品',
  `business_type`  tinyint       NOT NULL DEFAULT 0      COMMENT '业务类型：0其它 1新增 2修改 3删除 4导出 5导入',
  `method`         varchar(255)           DEFAULT NULL   COMMENT '执行的方法签名，如 com.shopping.service.product.impl.SpuServiceImpl.save',
  `request_method` varchar(16)            DEFAULT NULL   COMMENT 'HTTP方法：GET/POST/PUT/DELETE',
  `request_url`    varchar(500)           DEFAULT NULL   COMMENT '请求URL',
  `request_param`  text                   DEFAULT NULL   COMMENT '请求参数（敏感字段需脱敏）',
  `response_result` text                  DEFAULT NULL   COMMENT '响应结果（截断至2000字符）',
  `status`         tinyint       NOT NULL DEFAULT 1      COMMENT '操作结果：1成功 0失败',
  `error_msg`      varchar(2000)          DEFAULT NULL   COMMENT '错误信息（异常堆栈首行）',
  `ip`             varchar(64)            DEFAULT NULL   COMMENT '操作IP',
  `ip_region`      varchar(128)           DEFAULT NULL   COMMENT 'IP归属地（离线库解析，可为空）',
  `user_agent`     varchar(500)           DEFAULT NULL   COMMENT '浏览器 UA',
  `cost_time`      bigint        NOT NULL DEFAULT 0      COMMENT '耗时（毫秒）',
  `trace_id`       varchar(64)            DEFAULT NULL   COMMENT '链路追踪ID，与日志文件中的traceId对应',
  `create_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（即操作时间）',
  `update_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        tinyint       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_module_business_type` (`module`, `business_type`),
  KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台操作日志表';

DROP TABLE IF EXISTS `sys_file`;
CREATE TABLE `sys_file` (
  `id`            bigint       NOT NULL                COMMENT '主键，雪花ID',
  `file_name`     varchar(255) NOT NULL                COMMENT '原始文件名',
  `file_key`      varchar(500) NOT NULL                COMMENT '存储唯一键（本地相对路径或OSS objectKey）',
  `file_url`      varchar(500) NOT NULL                COMMENT '可访问URL',
  `file_type`     varchar(64)           DEFAULT NULL   COMMENT 'MIME类型，如 image/jpeg',
  `file_suffix`   varchar(16)           DEFAULT NULL   COMMENT '扩展名，如 jpg',
  `file_size`     bigint       NOT NULL DEFAULT 0      COMMENT '文件大小（字节）',
  `biz_type`      varchar(64)           DEFAULT NULL   COMMENT '业务类型：product/avatar/aftersale/refund_proof',
  `biz_id`        bigint                DEFAULT NULL   COMMENT '关联业务ID（如SPU ID）',
  `storage_type`  tinyint      NOT NULL DEFAULT 1      COMMENT '存储方式：1本地 2阿里云OSS 3MinIO',
  `uploader_type` tinyint      NOT NULL DEFAULT 1      COMMENT '上传者类型：1后台用户 2C端会员',
  `uploader_id`   bigint                DEFAULT NULL   COMMENT '上传者ID',
  `md5`           varchar(32)           DEFAULT NULL   COMMENT '文件MD5，用于秒传与去重',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_biz_type_biz_id` (`biz_type`, `biz_id`),
  KEY `idx_uploader_type_uploader_id` (`uploader_type`, `uploader_id`),
  KEY `idx_md5` (`md5`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件记录表';
