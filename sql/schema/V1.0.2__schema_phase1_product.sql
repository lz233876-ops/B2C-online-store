-- =====================================================================
--  Phase 1 · product 模块（9 张 pms_*）
--  ⚠️ 本文件由 tools/extract-ddl.ps1 从 docs/04-数据库设计.md 自动生成，请勿手工修改。
--     要改表结构，先改设计文档，再重新生成。
--  目标：MySQL 8.0 / InnoDB / utf8mb4_0900_ai_ci
--  ⚠️ 含 DROP TABLE IF EXISTS，【仅供开发环境反复执行】。
--     生产脚本必须去掉 DROP、改用 CREATE TABLE IF NOT EXISTS（见 docs/04 §9.1 原则 3）。
-- =====================================================================
DROP TABLE IF EXISTS `pms_category`;
CREATE TABLE `pms_category` (
  `id`          bigint      NOT NULL                COMMENT '主键，雪花ID',
  `parent_id`   bigint      NOT NULL DEFAULT 0      COMMENT '父类目ID，0表示一级类目',
  `name`        varchar(64) NOT NULL                COMMENT '类目名称',
  `level`       tinyint     NOT NULL DEFAULT 1      COMMENT '层级：1一级 2二级 3三级（本项目固定3级）',
  `sort`        int         NOT NULL DEFAULT 0      COMMENT '同级排序，值越小越靠前',
  `icon`        varchar(500)         DEFAULT NULL   COMMENT '类目图标URL',
  `banner`      varchar(500)         DEFAULT NULL   COMMENT '类目Banner图URL',
  `is_show`     tinyint     NOT NULL DEFAULT 1      COMMENT '是否前台展示：1是 0否',
  `status`      tinyint     NOT NULL DEFAULT 1      COMMENT '状态：1启用 0禁用',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     tinyint     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id_sort` (`parent_id`, `sort`),
  KEY `idx_level` (`level`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品类目表（树形）';

DROP TABLE IF EXISTS `pms_brand`;
CREATE TABLE `pms_brand` (
  `id`            bigint       NOT NULL                COMMENT '主键，雪花ID',
  `name`          varchar(64)  NOT NULL                COMMENT '品牌名称，如「Apple」',
  `name_cn`       varchar(64)           DEFAULT NULL   COMMENT '品牌中文名，如「苹果」',
  `logo`          varchar(500)          DEFAULT NULL   COMMENT '品牌Logo URL',
  `first_letter`  char(1)               DEFAULT NULL   COMMENT '品牌首字母，用于A-Z索引导航',
  `description`   varchar(1000)         DEFAULT NULL   COMMENT '品牌介绍',
  `sort`          int          NOT NULL DEFAULT 0      COMMENT '排序，值越小越靠前',
  `status`        tinyint      NOT NULL DEFAULT 1      COMMENT '状态：1启用 0禁用',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`, `deleted`),
  KEY `idx_first_letter_sort` (`first_letter`, `sort`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品品牌表';

DROP TABLE IF EXISTS `pms_attribute`;
CREATE TABLE `pms_attribute` (
  `id`          bigint      NOT NULL                COMMENT '主键，雪花ID',
  `category_id` bigint      NOT NULL                COMMENT '所属类目ID，逻辑关联 pms_category.id',
  `name`        varchar(64) NOT NULL                COMMENT '属性名，如「颜色」「屏幕尺寸」',
  `type`        tinyint     NOT NULL DEFAULT 1      COMMENT '属性类型：1规格属性(决定SKU) 2参数属性(仅展示)',
  `input_type`  tinyint     NOT NULL DEFAULT 1      COMMENT '录入方式：1从属性值列表单选 2多选 3手工输入',
  `unit`        varchar(16)          DEFAULT NULL   COMMENT '单位，如「英寸」「克」',
  `is_required` tinyint     NOT NULL DEFAULT 0      COMMENT '是否必填：1是 0否',
  `searchable`  tinyint     NOT NULL DEFAULT 0      COMMENT '是否作为筛选条件：1是 0否',
  `sort`        int         NOT NULL DEFAULT 0      COMMENT '排序，值越小越靠前',
  `status`      tinyint     NOT NULL DEFAULT 1      COMMENT '状态：1启用 0禁用',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     tinyint     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_id_name` (`category_id`, `name`, `deleted`),
  KEY `idx_category_id_type` (`category_id`, `type`),
  KEY `idx_type_searchable` (`type`, `searchable`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品属性表（规格属性/参数属性）';

DROP TABLE IF EXISTS `pms_attribute_value`;
CREATE TABLE `pms_attribute_value` (
  `id`           bigint       NOT NULL                COMMENT '主键，雪花ID',
  `attribute_id` bigint       NOT NULL                COMMENT '所属属性ID，逻辑关联 pms_attribute.id',
  `value`        varchar(128) NOT NULL                COMMENT '属性值，如「黑色」「128GB」',
  `sort`         int          NOT NULL DEFAULT 0      COMMENT '排序，值越小越靠前',
  `status`       tinyint      NOT NULL DEFAULT 1      COMMENT '状态：1启用 0禁用',
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attribute_id_value` (`attribute_id`, `value`, `deleted`),
  KEY `idx_attribute_id_sort` (`attribute_id`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品属性值表';

DROP TABLE IF EXISTS `pms_spu`;
CREATE TABLE `pms_spu` (
  `id`              bigint        NOT NULL                COMMENT '主键，雪花ID',
  `merchant_id`     bigint        NOT NULL DEFAULT 1      COMMENT '商家ID，多商户预留：1=自营',
  `category_id`     bigint        NOT NULL                COMMENT '三级类目ID，逻辑关联 pms_category.id',
  `brand_id`        bigint                 DEFAULT NULL   COMMENT '品牌ID，逻辑关联 pms_brand.id',
  `spu_name`        varchar(128)  NOT NULL                COMMENT '商品名称，如「Apple iPhone 15」',
  `sub_title`       varchar(255)           DEFAULT NULL   COMMENT '副标题/营销卖点',
  `keywords`        varchar(255)           DEFAULT NULL   COMMENT '搜索关键词，逗号分隔',
  `unit`            varchar(16)   NOT NULL DEFAULT '件'   COMMENT '计量单位',
  `main_image`      varchar(500)           DEFAULT NULL   COMMENT '主图URL（冗余自 pms_product_image 中 type=1&sku_id=0 的第一张）',
  `album_images`    json                   DEFAULT NULL   COMMENT '轮播图URL数组，如 ["https://a.jpg","https://b.jpg"]',
  `detail_html`     longtext               DEFAULT NULL   COMMENT '图文详情HTML（整块读写，不参与条件查询）',
  `price`           bigint        NOT NULL DEFAULT 0      COMMENT '展示价（单位：分），取该SPU下所有SKU的最低价，冗余字段',
  `original_price`  bigint        NOT NULL DEFAULT 0      COMMENT '划线价（单位：分），0表示不展示',
  `stock`           int           NOT NULL DEFAULT 0      COMMENT 'SPU总可售库存，冗余汇总自各SKU',
  `sale_count`      int           NOT NULL DEFAULT 0      COMMENT '累计销量，用于「按销量排序」',
  `weight`          int           NOT NULL DEFAULT 0      COMMENT '重量（克），用于运费计算',
  `publish_status`  tinyint       NOT NULL DEFAULT 0      COMMENT '发布状态：0草稿 1已提交待审 2审核通过 3审核驳回',
  `status`          tinyint       NOT NULL DEFAULT 0      COMMENT '上下架状态：1上架 0下架',
  `is_new`          tinyint       NOT NULL DEFAULT 0      COMMENT '是否新品：1是 0否',
  `is_recommend`    tinyint       NOT NULL DEFAULT 0      COMMENT '是否推荐：1是 0否',
  `sort`            int           NOT NULL DEFAULT 0      COMMENT '排序权重，值越大越靠前（运营手动置顶用）',
  `create_by`       bigint                 DEFAULT NULL   COMMENT '创建人ID（后台用户）',
  `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         tinyint       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_category_id_status` (`category_id`, `status`, `deleted`),
  KEY `idx_brand_id` (`brand_id`),
  KEY `idx_merchant_id_status` (`merchant_id`, `status`, `deleted`),
  KEY `idx_status_create_time` (`status`, `create_time`),
  KEY `idx_status_sort` (`status`, `sort`),
  KEY `idx_spu_name` (`spu_name`),
  KEY `idx_status_sale_count` (`status`, `sale_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='标准商品单元表（SPU）';

DROP TABLE IF EXISTS `pms_sku`;
CREATE TABLE `pms_sku` (
  `id`              bigint        NOT NULL                COMMENT '主键，雪花ID',
  `merchant_id`     bigint        NOT NULL DEFAULT 1      COMMENT '商家ID，多商户预留：1=自营',
  `spu_id`          bigint        NOT NULL                COMMENT '所属SPU ID，逻辑关联 pms_spu.id',
  `sku_code`        varchar(64)   NOT NULL                COMMENT 'SKU编码（商家自定义，全局唯一）',
  `sku_name`        varchar(200)  NOT NULL                COMMENT 'SKU全称，如「Apple iPhone 15 128GB 黑色」',
  `spec_desc`       varchar(255)           DEFAULT NULL   COMMENT '规格摘要，如「128GB / 黑色」，冗余便于列表展示',
  `spec_json`       json                   DEFAULT NULL   COMMENT '规格快照数组：[{"attributeId":1,"attributeName":"颜色","valueId":3,"valueName":"黑色"}]',
  `image`           varchar(500)           DEFAULT NULL   COMMENT 'SKU图片URL',
  `price`           bigint        NOT NULL DEFAULT 0      COMMENT '销售价（单位：分）',
  `original_price`  bigint        NOT NULL DEFAULT 0      COMMENT '划线价（单位：分），0表示不展示',
  `cost_price`      bigint        NOT NULL DEFAULT 0      COMMENT '成本价（单位：分），仅商家后台可见，前台接口必须排除',
  `stock`           int           NOT NULL DEFAULT 0      COMMENT '可售库存（冗余自 wms_stock，仅用于展示）',
  `locked_stock`    int           NOT NULL DEFAULT 0      COMMENT '锁定库存（冗余展示用）',
  `sale_count`      int           NOT NULL DEFAULT 0      COMMENT '累计销量',
  `weight`          int           NOT NULL DEFAULT 0      COMMENT '重量（克），用于运费',
  `status`          tinyint       NOT NULL DEFAULT 1      COMMENT '状态：1启用 0禁用',
  `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         tinyint       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_code` (`sku_code`, `deleted`),
  KEY `idx_spu_id` (`spu_id`, `status`),
  KEY `idx_merchant_id` (`merchant_id`),
  KEY `idx_price` (`price`),
  KEY `idx_status_sale_count` (`status`, `sale_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='最小库存单元表（SKU）';

DROP TABLE IF EXISTS `pms_spu_attribute`;
CREATE TABLE `pms_spu_attribute` (
  `id`            bigint      NOT NULL                COMMENT '主键，雪花ID',
  `spu_id`        bigint      NOT NULL                COMMENT 'SPU ID，逻辑关联 pms_spu.id',
  `attribute_id`  bigint      NOT NULL                COMMENT '属性ID，逻辑关联 pms_attribute.id',
  `attribute_name` varchar(64) NOT NULL               COMMENT '属性名冗余快照，如「颜色」',
  `type`          tinyint     NOT NULL DEFAULT 1      COMMENT '属性类型：1规格属性 2参数属性',
  `value_json`    json                 DEFAULT NULL   COMMENT '参数属性值：["2023年","全国联保"]；规格属性此列为NULL',
  `sort`          int         NOT NULL DEFAULT 0      COMMENT '展示排序',
  `create_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       tinyint     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_spu_id_attribute_id` (`spu_id`, `attribute_id`, `deleted`),
  KEY `idx_attribute_id` (`attribute_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SPU-属性关联表';

DROP TABLE IF EXISTS `pms_sku_attribute_value`;
CREATE TABLE `pms_sku_attribute_value` (
  `id`                  bigint       NOT NULL                COMMENT '主键，雪花ID',
  `sku_id`              bigint       NOT NULL                COMMENT 'SKU ID，逻辑关联 pms_sku.id',
  `spu_id`              bigint       NOT NULL                COMMENT 'SPU ID（冗余，避免与 pms_sku 做JOIN）',
  `attribute_id`        bigint       NOT NULL                COMMENT '属性ID，逻辑关联 pms_attribute.id',
  `attribute_name`      varchar(64)  NOT NULL                COMMENT '属性名快照，如「颜色」',
  `attribute_value_id`  bigint       NOT NULL                COMMENT '属性值ID，逻辑关联 pms_attribute_value.id',
  `attribute_value`     varchar(128) NOT NULL                COMMENT '属性值快照，如「黑色」',
  `sort`                int          NOT NULL DEFAULT 0      COMMENT '展示排序',
  `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_id_attribute_id` (`sku_id`, `attribute_id`, `deleted`),
  KEY `idx_spu_id_attribute_id` (`spu_id`, `attribute_id`),
  KEY `idx_attribute_value_id` (`attribute_value_id`),
  KEY `idx_attribute_id_value` (`attribute_id`, `attribute_value_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SKU-属性值组合表';

DROP TABLE IF EXISTS `pms_product_image`;
CREATE TABLE `pms_product_image` (
  `id`          bigint       NOT NULL                COMMENT '主键，雪花ID',
  `spu_id`      bigint       NOT NULL                COMMENT 'SPU ID，逻辑关联 pms_spu.id',
  `sku_id`      bigint       NOT NULL DEFAULT 0      COMMENT 'SKU ID，0表示SPU级图片（哨兵值，不用NULL）',
  `url`         varchar(500) NOT NULL                COMMENT '图片URL',
  `type`        tinyint      NOT NULL DEFAULT 1      COMMENT '图片类型：1主图 2轮播图 3详情图',
  `sort`        int          NOT NULL DEFAULT 0      COMMENT '排序，值越小越靠前',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_spu_id_sku_id_type_sort` (`spu_id`, `sku_id`, `type`, `sort`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品图片表';
