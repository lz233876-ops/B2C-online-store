# shopping-mall · Java 电商商城（校招求职项目）

> 一个「单体分层 + 模块化」的 **B2C 自营商城**，含**秒杀高并发**场景。
> 面向 **Java 后端校招 / 实习**求职，目标是**能演示、能讲透、有数据**。

---

## 📌 这个项目是什么

| | |
|---|---|
| **业务** | B2C 自营商城，跑通「浏览商品 → 加购 → 下单 → 支付 → 发货 → 收货 → 售后」全闭环，并额外具备**秒杀**高并发场景 |
| **定位** | 求职作品集，不是生产系统。所有设计取舍都以「应届生能讲清楚」为准 |
| **不做** | 微服务、Seata、分库分表、K8s、ES —— **每一个"不做"都在文档里给了理由** |
| **最大亮点** | 秒杀链路的**三层防超卖 + MQ 削峰 + Redis Lua 原子预扣**，并产出**可复现的压测数据** |

### 技术栈一句话

`Java 17` · `Spring Boot 3.2` · `MyBatis-Plus` · `MySQL 8` · `Redis 7` · `Redisson` · `RocketMQ 5` · `Spring Security + JWT` · `Vue 3 + Element Plus`

---

## 📚 文档导航

**建议按顺序读。** `00` 是唯一事实源，其余文档都以它为准。

> 🚧 **中断后恢复工作，先读 [`PROGRESS.md`](PROGRESS.md)** —— 那里有当前进度、环境凭据、启动命令、下一步待办与所有本机特有的坑。

| 文档 | 内容 | 什么时候看 |
|---|---|---|
| [00 · 蓝图与术语表](docs/00-蓝图与术语表.md) | **唯一事实源**。项目定位、范围取舍、模块清单、技术栈版本锁定、表清单、ADR、术语表、环境坑 | **先看这个** |
| [01 · 需求规格说明书](docs/01-需求规格说明书.md) | 角色、功能需求（含优先级）、非功能需求、业务规则、验收标准、**待确认清单** | 想知道"做什么/不做什么" |
| [02 · 技术选型与论证](docs/02-技术选型与论证.md) | 每项技术的候选对比 + 选择理由 + 代价 + **面试问答**；不引入组件的论证；**12 个版本兼容坑** | 搭环境前**必读** |
| [03 · 系统架构设计](docs/03-系统架构设计.md) | 架构图、依赖边界、请求生命周期、四条核心链路、缓存、一致性、幂等、安全、**演进路径** | 写代码前 |
| [04 · 数据库设计](docs/04-数据库设计.md) | 完整 DDL（41 张表）、ER 图、索引设计、核心表深度剖析、执行计划、秒杀 DB 侧设计 | 建库时 |
| [05 · 开发里程碑与排期](docs/05-开发里程碑与排期.md) | 10 周详细排期（含复选框任务）、风险登记册、**降级方案**、Git 规范 | **每周一打开** |
| [06 · 求职包装与面试准备](docs/06-求职包装与面试准备.md) | 简历模板（3 版）、STAR 口播稿、**高频追问清单**、演示脚本、投递策略 | 投简历前一个月 |

---

## 🗺️ 路线图

| 阶段 | 主题 | 核心产出 | 状态 |
|---|---|---|---|
| **Phase 0** | 设计 + 骨架 | 需求 / 架构 / 数据库设计 ✅ · Maven 多模块骨架 ✅ · DDL 落库（41 张表）✅ | ✅ **已完成** |
| **Phase 1** | 用户与商品 | 认证授权、用户中心、商品中心（SPU/SKU）、系统管理 | 🟡 认证授权 ✅（44/44）· 商品中心 ✅（75/75）· **C 端会员 ✅（49/49）**；系统管理 CRUD ⬜ |
| **Phase 2** | 交易闭环 | 购物车、库存、订单状态机、支付（**自建模拟网关**）、超时关单、售后 | ✅ **全部完成**：购物车 41/41 · 订单+状态机+关单 95/95 · 模拟支付 81/81 · **售后+退款+库存回补 77/77** |
| **Phase 3** | 营销与高并发 | 优惠券、**秒杀**、压测与优化 | 🟡 **秒杀 ✅（57/57）+ 真实压测 ✅**：1000 并发抢 100 件 → 恰好 100 成功、**零超卖**；单请求 P50 **4ms**，QPS 在 **~500** 饱和。优惠券 ⬜ |
| **Phase 4** | 平台化扩展 | 多商户、店铺、结算（选做） | ⬜ |

---

## ✅ 已确认决策速览

需求已冻结，权威版本见 [00 文档 §11](docs/00-蓝图与术语表.md)。

| 决策项 | 结论 |
|---|---|
| 消息队列 | **RocketMQ 5.x**（单体应用用 MQ，**不是微服务** —— 见 [02 文档的澄清](docs/02-技术选型与论证.md)） |
| 支付方式 | **自建模拟支付网关**，接口契约对标支付宝（不接真实渠道） |
| 前端范围 | 管理后台（Element Plus）+ 商城前台（Vant）**4 个核心页面** |
| Docker | 可用；⚠️ 本机 MySQL/Redis 已占 3306/6379，需错开端口 |
| 多商户 | 只预留 `merchant_id` 字段，不进入主线 |
| 总周期 | 希望尽快 → 可压缩至 **6 周**，但需每周 20h+ 且只做核心范围 |
| 开源项目复用 | **后端必须自己写**；前端用通用脚手架（vue-element-admin 等）；**mall4j 只当参考实现读**——它是 AGPLv3，且套用会在面试中被一眼认出。详见 [02 §4.5](docs/02-技术选型与论证.md) |

> ⚠️ **不能砍的三件事**：秒杀模块 + 真实压测数据、端到端可演示、文档与面试稿。

---

## 🔑 三个关键设计决策

面试时这三条最能体现你的工程判断力：

### 1. 单体多模块，而不是微服务
校招场景下，微服务是**减分项**：组件多、调试成本高、极易做成"分布式单体"。本项目用单体多模块，但在 `03` 文档里写清了**"如果要拆，我会按什么顺序拆、为什么"**——体现架构预判能力，比硬上 Nacos 高级得多。

### 2. 多商户从第一天就预留，但不实现
`pms_spu` / `pms_sku` / `oms_order` / `oms_order_item` / `wms_stock` / `sms_seckill_activity` 六张表**从 Phase 1 起就带 `merchant_id DEFAULT 1`**。Phase 1-3 做纯自营（无多商户复杂度），Phase 4 只需补 3 张表 + 数据隔离，**不用重构核心表**。

### 3. 不做的事，都有理由
不引入 Seata（单库无分布式事务，本地消息表更好讲）、不做分库分表（数据量不到千万级是负收益）、不上 ES（千级商品 `LIKE` 够用）。
**能说清"不做什么"的人，比什么都做的人更像工程师。**

---

## 🚀 快速开始

> ⚠️ 动手前请先读 [02 文档 §5 版本兼容性坑](docs/02-技术选型与论证.md)，那里有 **18 个**会让新手卡一整天的坑。

### 环境要求

| 组件 | 本机实测 | 说明 |
|---|---|---|
| JDK | 17 (`C:\jdk-17.0.12`) | ⚠️ `PATH` 上的 `java` 是 JDK 8，注意别用错 |
| Maven | 3.9.10 | 已就绪 |
| MySQL | 8.0 | ✅ 运行中（3306），`root` 已有密码，需建库并执行 `sql/` 脚本 |
| Redis | 7.x (`C:\redis`) | ✅ 运行中（6379） |
| Docker | `F:\Docker` | ⚠️ **daemon 未启动**，需手动启动 Docker Desktop 应用（用于跑 RocketMQ） |
| Node / pnpm | 已安装 | 用于前端 |

### 三个必踩的环境坑

```powershell
# 坑 1：PATH 上的 java 是 1.8，JAVA_HOME 是 17，Maven 跟 JAVA_HOME 走
#       命令行直接 javac 会报 "class file version 61.0" 错误
java -version          # -> 1.8.0_152  ← 陷阱
mvn -v                 # -> Java 17.0.12 ← 实际编译用这个

# 坑 2：系统默认编码 GBK，必须强制 UTF-8
#       父 POM 已设 project.build.sourceEncoding=UTF-8
mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Dfile.encoding=UTF-8"

# 坑 3：MySQL root 已有密码但未记录，且不要用 root 跑业务
#       建议建低权限账号，密码放 application-dev.yml（该文件进 .gitignore）
mysql -ushopping -p -e "select version()"
```

### 🔑 演示账号

| 账号 | 密码 | 角色 | 权限 | 用途 |
|---|---|---|---|---|
| `admin` | `admin123` | SUPER_ADMIN | 33 个（全部） | 演示全功能 |
| `operator` | `admin123` | OPERATOR | 19 个（**无 `system:*`**） | 演示 **403 权限拦截** |
| `merchant` | `admin123` | MERCHANT_ADMIN | 0 个 | 演示零权限账号不会崩 |
| `disabled` | `admin123` | — | — | 演示**禁用账号**登录被拒（1002） |
| `testuser` | `123456` | C 端会员 | — | Phase 2 起用于 C 端登录 |

> ⚠️ 这些都是**开发环境默认凭据**，写在 `docs/04` §8 里并且公开可见。
> **生产环境必须全部更换**，并改用环境变量注入。
> 后三个账号来自 `sql/data/dev_rbac_test_users.sql`（仅本地验证用，不在 `init.sql` 里）。

### 数据库初始化

```powershell
# 一键建库 + 建全部 41 张表 + 初始化数据（⚠️ 会 DROP DATABASE，仅开发环境）
cmd /c "mysql -uroot -p --default-character-set=utf8mb4 < sql\init.sql"

# 建库后自检：结构 + 初始化数据，全 PASS 才算对
cmd /c "mysql -uroot -p --default-character-set=utf8mb4 shopping_mall < sql\verify.sql"

# 数据库级防线验证（必须加 --force，因为脚本【故意】触发错误来证明约束生效）
cmd /c "mysql -uroot -p --default-character-set=utf8mb4 --force shopping_mall < sql\verify-constraints.sql"

# 改了 docs/04 的表结构后，重新生成建表脚本
Set-ExecutionPolicy -Scope Process Bypass -Force
& tools\extract-ddl.ps1

# 加载本地验证用的 RBAC 测试账号（可选，但验收脚本依赖它）
cmd /c "mysql -uroot -p --default-character-set=utf8mb4 shopping_mall < sql\data\dev_rbac_test_users.sql"

# Phase 1 认证授权验收：44 项断言，全 PASS 才算通过（需要应用已在运行）
& tools\verify-auth.ps1

# 商品中心验收：75 项断言（类目/品牌/属性/SPU+SKU，含 DB 层不变量检查）
& tools\verify-product.ps1

# C 端会员验收：49 项断言（注册登录/收货地址/两条认证链隔离/水平越权防护）
& tools\verify-app.ps1

# 订单验收：95 项断言（下单事务/状态机/超时关单/支付与关单撞车的并发安全）
& tools\verify-order.ps1

# 支付验收：81 项断言（验签/幂等/金额校验/伪造与重放攻击/丢单缺陷防护）
& tools\verify-pay.ps1

# 售后验收：77 项断言（仅退款不回补库存 / 退货退款才回补 / 多商品订单逐个退）
& tools\verify-aftersale.ps1

# 秒杀功能验收：57 项断言（一人一单/不超卖/失败补偿/售罄边界）
#   ⚠️ 需要 RocketMQ 在跑：cd docker; docker compose up -d
& tools\verify-seckill.ps1

# 秒杀压测：1000 个真实会员并发抢 100 件，校验「恰好 100 成功、零超卖」
& tools\loadtest-seckill.ps1 -UserCount 1000 -Concurrency 200 -Stock 100
```

`sql/` 下的建表脚本是**从 `docs/04-数据库设计.md` 抽取生成的，不要手改**——要改表结构先改文档再重新生成。

### 启动顺序（Phase 0 已验证 ✅）

```powershell
# 0. 前置：MySQL(3306) 与 Redis(6379) 已在本机运行；库和账号已建好

# 1. 编译打包（Maven 本地仓库在 E:\localRepository）
mvn -B clean install -DskipTests

# 2. 起后端
#    ⚠️ 不要把 -Dfile.encoding=UTF-8 直接写在命令行：PowerShell 会把它拆开，
#       报 ClassNotFoundException: /encoding=UTF-8。用环境变量传。
$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'
$env:SPRING_PROFILES_ACTIVE = 'dev'
& 'C:\jdk-17.0.12\bin\java.exe' -jar shopping-web\target\shopping-mall.jar

# 3. 前端（Phase 1 开始做）
cd frontend/admin; pnpm install; pnpm dev
```

### 访问地址

| 服务 | 地址 | 状态 |
|---|---|---|
| 健康检查 | http://localhost:8080/api/app/health | ✅ 已验证 |
| 组件探活（DB/Redis） | http://localhost:8080/actuator/health | ✅ 已验证 |
| 接口文档（Knife4j） | http://localhost:8080/doc.html | ✅ 已验证 |
| 后台登录 | `POST /api/admin/auth/login` | ✅ 已验证（44/44） |
| 当前用户信息 | `GET /api/admin/auth/me` | ✅ 已验证 |
| 动态路由 | `GET /api/admin/auth/menus` | ✅ 已验证 |
| 后台用户分页 | `GET /api/admin/system/user/page` | ✅ 已验证（RBAC 保护） |
| 类目树 | `GET /api/admin/product/category/tree` | ✅ 已验证 |
| 商品（SPU）聚合保存 | `POST /api/admin/product/spu` | ✅ 已验证（75/75） |
| 商品分页 / 详情 | `GET /api/admin/product/spu/page` · `/{id}` | ✅ 已验证 |
| 商品上下架 | `PUT /api/admin/product/spu/{id}/status` | ✅ 已验证 |
| C 端注册 / 登录 | `POST /api/app/auth/register` · `/login` | ✅ 已验证（49/49） |
| C 端当前会员 | `GET /api/app/auth/me` | ✅ 已验证 |
| C 端收货地址 | `GET/POST/PUT/DELETE /api/app/address/**` | ✅ 已验证（含越权防护） |
| C 端购物车 | `/api/app/cart/**` | ✅ 已验证（41/41） |
| C 端下单 / 我的订单 | `POST /api/app/order/submit` · `GET /page` | ✅ 已验证（95/95） |
| 订单取消 / 确认收货 | `PUT /api/app/order/{id}/cancel` · `/receive` | ✅ 已验证 |
| 管理端订单 / 发货 | `GET /api/admin/order/page` · `PUT /{id}/deliver` | ✅ 已验证 |
| 手工触发超时关单 | `POST /api/admin/order/close-timeout` | ✅ 已验证（幂等） |
| C 端发起支付 | `POST /api/app/pay/create` | ✅ 已验证（81/81） |
| **支付渠道回调** | `POST /api/pay/notify/{channel}` | ✅ 已验证（**返回纯文本 success/failure，不套 Result**） |
| 模拟收银台 | `POST /api/pay/mock-gateway/{paymentNo}/pay` · `/build-notify` | ✅ 已验证（渠道侧模拟，验签/幂等可被攻击测试） |
| C 端售后申请 / 列表 | `POST /api/app/aftersale/apply` · `GET /page` | ✅ 已验证（77/77） |
| C 端撤销售后 / 填退货物流 | `PUT /api/app/aftersale/{id}/cancel` · `/{id}/return` | ✅ 已验证 |
| 管理端售后审核 / 确认收货退款 | `PUT /api/admin/aftersale/{id}/audit` · `/{id}/receive` | ✅ 已验证（**仅退款不回补库存、退货退款才回补**） |

---

## 📁 规划的工程结构

```
shopping-mall/
├── docs/                       # 📚 本套规划文档（00-06）
├── sql/                        # 建表与初始化（由工具从 docs/04 生成，勿手改）
│   ├── schema/                 #   8 个建表脚本，共 41 张表，按 Phase 分文件
│   ├── data/                   #   4 个初始化数据脚本
│   ├── migration/  rollback/   #   后续变更与回滚（只增不改）
│   ├── init.sql                #   一键初始化（⚠️ 会 DROP DATABASE）
│   ├── verify.sql              #   建库后自检（结构 + 初始化数据）
│   └── verify-constraints.sql  #   数据库级防线验证（幂等 / 防超卖）
├── tools/
│   ├── extract-ddl.ps1         # 从 docs/04 生成 sql/ 下的建表脚本
│   ├── ApiTestCommon.ps1       # 验收脚本公共函数（HTTP / 断言 / 查库）
│   ├── verify-auth.ps1         # 认证授权与 RBAC 验收（44 项断言）
│   ├── verify-product.ps1      # 商品中心验收：类目/品牌/属性/SPU+SKU（75 项断言）
│   ├── verify-app.ps1          # C 端会员验收：注册登录/地址/两条链隔离（49 项断言）
│   ├── verify-cart.ps1         # 购物车 + 库存四操作验收（41 项断言 + 22 项 SQL 自校验）
│   ├── verify-order.ps1        # 订单 + 状态机 + 超时关单验收（95 项断言）
│   ├── verify-pay.ps1          # 模拟支付网关验收：验签/幂等/金额校验（81 项断言）
│   ├── verify-aftersale.ps1    # 售后 + 退款 + 库存回补验收（77 项断言）
│   ├── verify-seckill.ps1      # 秒杀功能验收（57 项断言：一人一单/不超卖/失败补偿）
│   ├── loadtest-seckill.ps1    # 秒杀压测驱动（造 1000 会员 → 并发抢 → 校验零超卖）
│   └── LoadTest.java           # JDK 单文件压测器（java LoadTest.java，输出 QPS/分位数）
├── docker/
│   ├── docker-compose.yml      # RocketMQ 5.3.1（namesrv 9876 + broker 10911）
│   └── broker.conf             # ⚠️ brokerIP1 必须写 127.0.0.1，见 docs/02 坑 30
├── docker/                     # docker-compose.yml + broker.conf（Phase 2 起）
├── frontend/
│   ├── admin/                  # Vue3 + Element Plus 管理后台（P0 必须）
│   └── mall/                   # Vue3 + Vant 商城前台（P0 只做 4 页：列表/详情/购物车/下单）
├── shopping-common/            # 通用：Result/异常/枚举/工具/常量
├── shopping-model/             # 数据模型：DO + DTO + VO + Query
├── shopping-dao/               # 持久层：Mapper + XML
├── shopping-service/           # 业务层
├── shopping-web/               # 接入层：Controller(app/admin) + 启动类 + 配置
└── pom.xml                     # 父 POM（含 Enforcer 依赖方向约束）
```

> **建表脚本为什么是生成的**：`docs/04-数据库设计.md` 是数据库设计的唯一事实源。
> 41 张表手抄必然出错，所以 `sql/schema/` 与 `sql/data/` 全部由 `tools/extract-ddl.ps1` 从文档抽取。
> **要改表结构 → 先改文档 → 重新生成**，禁止直接改 `sql/` 下的文件。

依赖方向严格单向：`web → service → dao → model`，`common` 全层可见。

---

## ❓ 下一步

需求已冻结，直接进入 **Phase 0**：

1. **先读 [02 §5 版本兼容性坑](docs/02-技术选型与论证.md)**，按 §5.1 的最小验证顺序把环境跑通（这是最容易卡住的一步，12 个坑里有 3 个会在你的机器上直接触发）；
2. 处理三件环境事：**修正 `PATH` 上的 JDK 8**、**确认 MySQL 密码并建低权限账号**、**启动 Docker Desktop 并注意端口冲突**（见 [00 §9 与 §11.3](docs/00-蓝图与术语表.md)）；
3. 打开 [05 排期](docs/05-开发里程碑与排期.md)，从 **W1** 开始执行——**这一周不能压缩**，设计没定稿后面全是返工。

---

## ⚖️ 说明

- 本项目为**个人学习与求职作品**，业务模型参考通用电商设计，代码与设计均为原创。
- 文档中所有性能数字（QPS / TP99）均为**目标值**，实际数字**必须自己压测得到**，禁止照抄。
- 简历内容必须**真实做过**才能写。具体提醒见 [06 文档](docs/06-求职包装与面试准备.md)。
