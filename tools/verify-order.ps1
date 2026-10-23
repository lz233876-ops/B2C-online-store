# =====================================================================
#  verify-order.ps1 — Phase 2 验收（二）：订单主链路 + 状态机 + 超时关单
#
#  前置：
#    1. 数据库已初始化 + 演示数据（sql/init.sql + V1.1.1 + dev_rbac_test_users.sql）
#    2. 应用已启动在 http://localhost:8080
#
#  用法：
#    Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
#    & tools\verify-order.ps1
#
#  ⚠️ 本文件含中文，必须存成「UTF-8 带 BOM」。
#  ⚠️ 本脚本会【重置演示 SKU 1~4 的库存与流水到种子基线 (100/0/100/0)】，
#     这样脚本可以反复运行；结束时会再重置一次，让演示数据保持干净。
#     （verify-cart.ps1 的 SQL 测试也依赖这个基线，两者互不干扰）
# =====================================================================

$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

. "$PSScriptRoot\ApiTestCommon.ps1"

$MysqlExe = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$MysqlArgs = @('-uroot', '-p123456', '--default-character-set=utf8mb4', '-N', '-B', 'shopping_mall', '-e')
function Invoke-Sql {
    param([string]$Query)
    $out = & $MysqlExe @MysqlArgs $Query 2>&1
    return ,@($out | Where-Object { $_ -notmatch 'Using a password' -and $_ -ne '' })
}
function SqlScalar {
    param([string]$Query)
    $r = Invoke-Sql $Query
    if ($r.Count -eq 0) { return '' }
    return $r[0]
}
function Reset-DemoStock {
    # 把演示 SKU 的库存与流水恢复到种子基线，保证脚本可反复运行
    Invoke-Sql "UPDATE wms_stock SET stock=100, locked_stock=0, total_in=100, total_out=0 WHERE sku_id IN (1,2,3,4);" | Out-Null
    Invoke-Sql "DELETE FROM wms_stock_log WHERE sku_id IN (1,2,3,4);" | Out-Null
    # ⚠️ 必须【同时】重建期初入库流水：只重置 wms_stock 而不写流水，
    #    会让「SUM(change_num) == available_stock」这条对账恒等式不成立，
    #    于是第 12 节的恒等式断言会误报成「后端 bug」。
    Invoke-Sql "INSERT INTO wms_stock_log (id, merchant_id, sku_id, warehouse_id, biz_type, biz_no, change_num, before_stock, after_stock, operator_type, remark, create_time, update_time, deleted) VALUES (1,1,1,1,7,'IN_1',100,0,100,3,'期初入库',NOW(),NOW(),0),(2,1,2,1,7,'IN_2',100,0,100,3,'期初入库',NOW(),NOW(),0),(3,1,3,1,7,'IN_3',100,0,100,3,'期初入库',NOW(),NOW(),0),(4,1,4,1,7,'IN_4',100,0,100,3,'期初入库',NOW(),NOW(),0);" | Out-Null
}

Reset-TestState
if (-not (Wait-ServiceReady)) { exit 1 }
Reset-DemoStock

# ---------------------------------------------------------------------
# 准备：验收会员（手机号 135 段）+ 收货地址
# ---------------------------------------------------------------------
$PhoneA = '13500000001'
$PhoneB = '13500000002'
foreach ($p in @($PhoneA, $PhoneB)) {
    Invoke-Sql "DELETE FROM oms_order_operate_history WHERE order_id IN (SELECT id FROM oms_order WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p'));" | Out-Null
    Invoke-Sql "DELETE FROM oms_order_item WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p');" | Out-Null
    Invoke-Sql "DELETE FROM oms_order WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p');" | Out-Null
    Invoke-Sql "DELETE FROM oms_cart_item WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p');" | Out-Null
    Invoke-Sql "DELETE FROM ums_user_address WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p');" | Out-Null
    Invoke-Sql "DELETE FROM ums_user WHERE phone='$p';" | Out-Null
}
foreach ($p in @($PhoneA, $PhoneB)) {
    $reg = Invoke-Api -Method POST -Path '/api/app/auth/register' -Body @{ phone=$p; password='order123456' }
    if ($reg.Json.code -ne 200) { Write-Host "⛔ 前置准备失败：注册 $p → $($reg.Json.message)" -ForegroundColor Red; exit 1 }
}
$tokenA = (Invoke-Api -Method POST -Path '/api/app/auth/login' -Body @{ phone=$PhoneA; password='order123456' }).Json.data.accessToken
$tokenB = (Invoke-Api -Method POST -Path '/api/app/auth/login' -Body @{ phone=$PhoneB; password='order123456' }).Json.data.accessToken
$userAId = [long](SqlScalar "SELECT id FROM ums_user WHERE phone='$PhoneA'")
$userBId = [long](SqlScalar "SELECT id FROM ums_user WHERE phone='$PhoneB'")

# A 的地址
$addrA = (Invoke-Api -Method POST -Path '/api/app/address' -Token $tokenA -Body @{
    receiverName='订单验收员'; receiverPhone='13500000001'; province='浙江省'; city='杭州市'
    district='西湖区'; detailAddress='文一西路 100 号' }).Json.data
# B 的地址（用于越权测试）
$addrB = (Invoke-Api -Method POST -Path '/api/app/address' -Token $tokenB -Body @{
    receiverName='越权测试员'; receiverPhone='13500000002'; province='江苏省'; city='南京市'
    district='玄武区'; detailAddress='中山路 1 号' }).Json.data

# 造一个便宜的商品（价格 1000 分 < 免运费门槛 9900，用于验证运费）
$adminToken = Get-AdminToken
$cheapSpu = (Invoke-Api -Method POST -Path '/api/admin/product/spu' -Token $adminToken -Body @{
    categoryId=3; spuName='验收便宜商品'; publishStatus=2; status=1
    specAttributes=@(@{ attributeId=1; valueIds=@(1) })
    skus=@(@{ price=1000; stock=50; specValues=@(@{attributeId=1;valueId=1}) })
}).Json.data
$cheapSkuId = [long](SqlScalar "SELECT id FROM pms_sku WHERE spu_id=$cheapSpu AND deleted=0")

# =====================================================================
Write-Host "`n=========== 1. 下单前置校验 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Path '/api/app/order/page'
Assert-That '不带令牌访问订单 → 401' ($r.Status -eq 401) "status=$($r.Status)"

Invoke-Api -Method POST -Path '/api/app/cart/add' -Token $tokenA -Body @{ skuId=1; quantity=1 } | Out-Null
$r = Invoke-Api -Method POST -Path '/api/app/order/submit' -Token $tokenA -Body @{ addressId=$addrB }
Assert-That '用别人的地址下单 → 3005 地址不存在' ($r.Json.code -eq 3005) "code=$($r.Json.code)"

$r = Invoke-Api -Method POST -Path '/api/app/order/submit' -Token $tokenA -Body @{ addressId=99999999 }
Assert-That '用不存在的地址下单 → 3005' ($r.Json.code -eq 3005) "code=$($r.Json.code)"

# 购物车：取消勾选后再下单 → 3004
Invoke-Api -Method PUT -Path '/api/app/cart/selected' -Token $tokenA -Body @{ ids=@(); selected=0 } | Out-Null
$r = Invoke-Api -Method POST -Path '/api/app/order/submit' -Token $tokenA -Body @{ addressId=$addrA }
Assert-That '购物车没有勾选项 → 3004' ($r.Json.code -eq 3004) "code=$($r.Json.code) msg=$($r.Json.message)"
Invoke-Api -Method PUT -Path '/api/app/cart/selected' -Token $tokenA -Body @{ ids=@(); selected=1 } | Out-Null

# 未勾选的项不能被下单：加一个便宜商品但取消勾选
Invoke-Api -Method POST -Path '/api/app/cart/add' -Token $tokenA -Body @{ skuId=$cheapSkuId; quantity=2 } | Out-Null
$cheapCartId = [long](SqlScalar "SELECT id FROM oms_cart_item WHERE user_id=$userAId AND sku_id=$cheapSkuId")
Invoke-Api -Method PUT -Path '/api/app/cart/selected' -Token $tokenA -Body @{ ids=@($cheapCartId); selected=0 } | Out-Null

# =====================================================================
Write-Host "`n=========== 2. ⭐ 下单成功（最长的事务）===========" -ForegroundColor Cyan
$stockBefore = SqlScalar "SELECT stock FROM wms_stock WHERE sku_id=1"
$r = Invoke-Api -Method POST -Path '/api/app/order/submit' -Token $tokenA -Body @{ addressId=$addrA; note='验收订单' }
Assert-That '下单成功' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
$order1 = $r.Json.data.orderId
$orderNo1 = $r.Json.data.orderNo
Assert-That '订单号形如 SO+日期+雪花ID' ($orderNo1 -like 'SO20*') "orderNo=$orderNo1"
Assert-That '应付金额 = 599900（满 99 免运费）' ($r.Json.data.payAmount -eq 599900) "pay=$($r.Json.data.payAmount)"
Assert-That '返回了自动关闭时间（前端做倒计时）' ($null -ne $r.Json.data.autoCloseTime) "autoClose=$($r.Json.data.autoCloseTime)"

Assert-That 'DB: 订单状态=待付款(0)' ((SqlScalar "SELECT status FROM oms_order WHERE id=$order1") -eq '0')
Assert-That 'DB: 未勾选的商品没有进订单（只有 1 条明细）' ((SqlScalar "SELECT COUNT(*) FROM oms_order_item WHERE order_id=$order1") -eq '1')
Assert-That 'DB: 明细快照了商品名' ((SqlScalar "SELECT spu_name FROM oms_order_item WHERE order_id=$order1") -like '*iPhone*')
Assert-That 'DB: 明细快照了收货人（订单里有姓名）' ((SqlScalar "SELECT receiver_name FROM oms_order WHERE id=$order1") -eq '订单验收员')
Assert-That 'DB: 运费=0（满 99 免运费）' ((SqlScalar "SELECT freight_amount FROM oms_order WHERE id=$order1") -eq '0')
Assert-That 'DB: 自动关闭时间 ≈ 下单时间 + 30 分钟' ((SqlScalar "SELECT TIMESTAMPDIFF(MINUTE, create_time, auto_close_time) FROM oms_order WHERE id=$order1") -eq '30')

# 库存：锁定而非扣减
Assert-That 'DB: locked_stock=1（锁定而非扣减）' ((SqlScalar "SELECT locked_stock FROM wms_stock WHERE sku_id=1") -eq '1')
Assert-That 'DB: stock 未变（货还在仓库）' ((SqlScalar "SELECT stock FROM wms_stock WHERE sku_id=1") -eq $stockBefore)
Assert-That 'DB: available_stock = 99' ((SqlScalar "SELECT available_stock FROM wms_stock WHERE sku_id=1") -eq '99')
Assert-That 'DB: 写了锁定流水（biz_type=1）' ((SqlScalar "SELECT COUNT(*) FROM wms_stock_log WHERE biz_type=1 AND biz_no='$orderNo1'") -eq '1')
Assert-That 'DB: 锁定流水 change_num=-1（占用可售）' ((SqlScalar "SELECT change_num FROM wms_stock_log WHERE biz_type=1 AND biz_no='$orderNo1'") -eq '-1')
Assert-That 'DB: 库存流水的 order_no 已回填' ((SqlScalar "SELECT order_no FROM wms_stock_log WHERE biz_type=1 AND biz_no='$orderNo1'") -eq $orderNo1)

# 流水与购物车
Assert-That 'DB: 写了状态流水（CREATE, -1 → 0）' ((SqlScalar "SELECT CONCAT(from_status,'>',to_status) FROM oms_order_operate_history WHERE order_id=$order1 AND action='CREATE'") -eq '-1>0')
Assert-That 'DB: 已下单的 SKU 从购物车清除' ((SqlScalar "SELECT COUNT(*) FROM oms_cart_item WHERE user_id=$userAId AND sku_id=1") -eq '0')
Assert-That 'DB: 未勾选的商品保留在购物车' ((SqlScalar "SELECT COUNT(*) FROM oms_cart_item WHERE user_id=$userAId AND sku_id=$cheapSkuId") -eq '1')

# 不在这里累加购物车 —— 后面各节要用的项在各自的节里显式准备，
# 避免「测试依赖前面累加出来的数量」这种脆弱的隐式状态（踩过：以为便宜商品是 2 件，
# 实际因为这里多加了一次变成 3 件，于是金额断言全错）

# =====================================================================
Write-Host "`n=========== 3. 运费规则 ===========" -ForegroundColor Cyan
Invoke-Api -Method PUT -Path '/api/app/cart/selected' -Token $tokenA -Body @{ ids=@(); selected=0 } | Out-Null
$cheapCartId = [long](SqlScalar "SELECT id FROM oms_cart_item WHERE user_id=$userAId AND sku_id=$cheapSkuId")
# 显式把数量设为 2：测试里的数字必须是自己写下的，而不是前面步骤累积出来的
Invoke-Api -Method PUT -Path '/api/app/cart/quantity' -Token $tokenA -Body @{ id=$cheapCartId; quantity=2 } | Out-Null
Invoke-Api -Method PUT -Path '/api/app/cart/selected' -Token $tokenA -Body @{ ids=@($cheapCartId); selected=1 } | Out-Null
$r = Invoke-Api -Method POST -Path '/api/app/order/submit' -Token $tokenA -Body @{ addressId=$addrA }
Assert-That '便宜商品下单成功（1000 分 × 2 件）' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
$order2 = $r.Json.data.orderId
# 注意：这个便宜商品在购物车里是 2 件（第 1 节加了 2 件），所以总额 2000
Assert-That '商品总额 = 1000 × 2 = 2000' ((SqlScalar "SELECT total_amount FROM oms_order WHERE id=$order2") -eq '2000')
Assert-That '运费=1000（2000 分未满 99 元门槛）' ((SqlScalar "SELECT freight_amount FROM oms_order WHERE id=$order2") -eq '1000')
Assert-That '应付 = 2000 + 1000 = 3000' ((SqlScalar "SELECT pay_amount FROM oms_order WHERE id=$order2") -eq '3000') "pay=$(SqlScalar "SELECT pay_amount FROM oms_order WHERE id=$order2")"

# =====================================================================
Write-Host "`n=========== 4. 订单查询 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Path '/api/app/order/page?pageNum=1&pageSize=10' -Token $tokenA
Assert-That '订单分页返回 2 笔' ($r.Json.data.total -eq 2) "total=$($r.Json.data.total)"
Assert-That '状态描述为「待付款」' ($r.Json.data.records[0].statusDesc -eq '待付款') "desc=$($r.Json.data.records[0].statusDesc)"
Assert-That '含商品件数汇总' ($r.Json.data.records[0].totalQuantity -ge 1) "qty=$($r.Json.data.records[0].totalQuantity)"
Assert-That '含待支付剩余秒数（倒计时用）' ($r.Json.data.records[0].remainPaySeconds -gt 1500) "remain=$($r.Json.data.records[0].remainPaySeconds)"
Assert-That '收货人手机号已脱敏' ($r.Json.data.records[0].receiverPhone -like '*`*`*`*`**') "phone=$($r.Json.data.records[0].receiverPhone)"
Assert-That '完整收货地址已拼接' ($r.Json.data.records[0].receiverFullAddress -eq '浙江省杭州市西湖区文一西路 100 号')

$r = Invoke-Api -Path "/api/app/order/$order1" -Token $tokenA
Assert-That '订单详情成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
Assert-That '详情含商品明细快照' ($r.Json.data.items.Count -eq 1) "items=$($r.Json.data.items.Count)"
Assert-That '详情含规格摘要快照' ($null -ne $r.Json.data.items[0].specDesc) "spec=$($r.Json.data.items[0].specDesc)"
Assert-That '详情含状态流转记录' ($r.Json.data.histories.Count -ge 1) "hist=$($r.Json.data.histories.Count)"
Assert-That '流转记录第一项是 CREATE' ($r.Json.data.histories[0].action -eq 'CREATE') "action=$($r.Json.data.histories[0].action)"

$r = Invoke-Api -Path '/api/app/order/page?status=4' -Token $tokenA
Assert-That '按状态筛选（已取消=0 笔）' ($r.Json.data.total -eq 0) "total=$($r.Json.data.total)"

# =====================================================================
Write-Host "`n=========== 5. ⭐ 订单水平越权防护 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Path "/api/app/order/$order1" -Token $tokenB
Assert-That 'B 查 A 的订单 → 3001 订单不存在（不泄漏存在性）' ($r.Json.code -eq 3001) "code=$($r.Json.code)"
$r = Invoke-Api -Path '/api/app/order/page' -Token $tokenB
Assert-That 'B 的订单列表为空' ($r.Json.data.total -eq 0) "total=$($r.Json.data.total)"
$r = Invoke-Api -Method PUT -Path "/api/app/order/$order1/cancel" -Token $tokenB
Assert-That 'B 取消 A 的订单 → 3001（拒绝）' ($r.Json.code -eq 3001) "code=$($r.Json.code)"
Assert-That 'DB: A 的订单状态未被改动' ((SqlScalar "SELECT status FROM oms_order WHERE id=$order1") -eq '0')

# =====================================================================
Write-Host "`n=========== 6. 状态机：非法流转必须被拒 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Method PUT -Path "/api/app/order/$order1/receive" -Token $tokenA
Assert-That '待付款直接确认收货 → 拒绝' ($r.Json.code -ne 200) "code=$($r.Json.code) msg=$($r.Json.message)"
Assert-That 'DB: 状态仍为待付款' ((SqlScalar "SELECT status FROM oms_order WHERE id=$order1") -eq '0')

$r = Invoke-Api -Method PUT -Path "/api/admin/order/$order1/deliver" -Token $adminToken -Body @{ deliveryCompany='顺丰'; deliveryNo='SF123' }
Assert-That '待付款订单直接发货 → 拒绝' ($r.Json.code -ne 200) "code=$($r.Json.code)"
Assert-That 'DB: 状态未被改成待收货' ((SqlScalar "SELECT status FROM oms_order WHERE id=$order1") -eq '0')

# =====================================================================
Write-Host "`n=========== 7. 取消订单（释放库存）===========" -ForegroundColor Cyan
$r = Invoke-Api -Method PUT -Path "/api/app/order/$order1/cancel?reason=不想要了" -Token $tokenA
Assert-That '取消订单成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
Assert-That 'DB: 状态=已取消(4)' ((SqlScalar "SELECT status FROM oms_order WHERE id=$order1") -eq '4')
Assert-That 'DB: 取消原因已记录' ((SqlScalar "SELECT cancel_reason FROM oms_order WHERE id=$order1") -eq '不想要了')
Assert-That 'DB: close_time 已填' ("" -ne (SqlScalar "SELECT close_time FROM oms_order WHERE id=$order1"))
Assert-That 'DB: 库存已释放（locked 回到 0）' ((SqlScalar "SELECT locked_stock FROM wms_stock WHERE sku_id=1") -eq '0')
Assert-That 'DB: 可售库存恢复到 100' ((SqlScalar "SELECT available_stock FROM wms_stock WHERE sku_id=1") -eq '100')
Assert-That 'DB: 写了释放流水（biz_type=3）' ((SqlScalar "SELECT COUNT(*) FROM wms_stock_log WHERE biz_type=3 AND biz_no='${orderNo1}_CANCEL'") -eq '1')
Assert-That 'DB: 释放流水 change_num=+1' ((SqlScalar "SELECT change_num FROM wms_stock_log WHERE biz_type=3 AND biz_no='${orderNo1}_CANCEL'") -eq '1')
Assert-That 'DB: 状态流水记录了 CANCEL' ((SqlScalar "SELECT COUNT(*) FROM oms_order_operate_history WHERE order_id=$order1 AND action='CANCEL'") -eq '1')

$r = Invoke-Api -Method PUT -Path "/api/app/order/$order1/cancel" -Token $tokenA
Assert-That '重复取消 → 拒绝（已取消是终态）' ($r.Json.code -ne 200) "code=$($r.Json.code)"
Assert-That 'DB: 库存没有被重复释放' ((SqlScalar "SELECT COUNT(*) FROM wms_stock_log WHERE biz_no='${orderNo1}_CANCEL'") -eq '1')

# =====================================================================
Write-Host "`n=========== 8. 完整流转：待发货 → 待收货 → 已完成 ===========" -ForegroundColor Cyan
# 模拟支付成功（Phase 2 第三批会做真实的模拟支付网关，这里先直接改库）
Invoke-Sql "UPDATE oms_order SET status=1, pay_time=NOW(), paid_amount=pay_amount WHERE id=$order2;" | Out-Null
Assert-That 'DB: 前置确认——订单2 已是待发货' ((SqlScalar "SELECT status FROM oms_order WHERE id=$order2") -eq '1')

$r = Invoke-Api -Method PUT -Path "/api/admin/order/$order2/deliver" -Token $adminToken -Body @{ deliveryCompany='顺丰速运'; deliveryNo='SF9988776655' }
Assert-That '发货成功' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
Assert-That 'DB: 状态=待收货(2)' ((SqlScalar "SELECT status FROM oms_order WHERE id=$order2") -eq '2')
Assert-That 'DB: 发货时间已填' ("" -ne (SqlScalar "SELECT delivery_time FROM oms_order WHERE id=$order2"))
# ⚠️ mysql -B 把 NULL 输出成字符串 "NULL"，不是空串 —— 判空必须比 "NULL"
Assert-That 'DB: 完成时间仍为空（发货不是完成）' ((SqlScalar "SELECT finish_time FROM oms_order WHERE id=$order2") -eq 'NULL') "finish=$(SqlScalar "SELECT finish_time FROM oms_order WHERE id=$order2")"
Assert-That 'DB: 物流信息已保存' ((SqlScalar "SELECT delivery_no FROM oms_order WHERE id=$order2") -eq 'SF9988776655')

$r = Invoke-Api -Method PUT -Path "/api/admin/order/$order2/deliver" -Token $adminToken -Body @{ deliveryCompany='顺丰速运'; deliveryNo='SF000' }
Assert-That '重复发货 → 拒绝' ($r.Json.code -ne 200) "code=$($r.Json.code)"

$r = Invoke-Api -Method PUT -Path "/api/app/order/$order2/receive" -Token $tokenA
Assert-That '确认收货成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
Assert-That 'DB: 状态=已完成(3)' ((SqlScalar "SELECT status FROM oms_order WHERE id=$order2") -eq '3')
Assert-That 'DB: 收货时间与完成时间都已填' ((SqlScalar "SELECT COUNT(*) FROM oms_order WHERE id=$order2 AND receive_time IS NOT NULL AND finish_time IS NOT NULL") -eq '1')
Assert-That 'DB: 状态流水共 3 条（创建/发货/收货）' ((SqlScalar "SELECT COUNT(*) FROM oms_order_operate_history WHERE order_id=$order2") -eq '3')
Assert-That 'DB: 发货流水的操作人是管理员' ((SqlScalar "SELECT operator_type FROM oms_order_operate_history WHERE order_id=$order2 AND action='DELIVER'") -eq '2')

$r = Invoke-Api -Method PUT -Path "/api/app/order/$order2/cancel" -Token $tokenA
Assert-That '已完成订单不能取消 → 拒绝' ($r.Json.code -ne 200) "code=$($r.Json.code) msg=$($r.Json.message)"

# =====================================================================
Write-Host "`n=========== 9. ⭐ 超时关单 ===========" -ForegroundColor Cyan
Invoke-Api -Method PUT -Path '/api/app/cart/selected' -Token $tokenA -Body @{ ids=@(); selected=0 } | Out-Null
Invoke-Api -Method POST -Path '/api/app/cart/add' -Token $tokenA -Body @{ skuId=3; quantity=2 } | Out-Null
$r = Invoke-Api -Method POST -Path '/api/app/order/submit' -Token $tokenA -Body @{ addressId=$addrA }
Assert-That '为超时测试下第 3 单' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$order3 = $r.Json.data.orderId
$orderNo3 = $r.Json.data.orderNo
Assert-That 'DB: SKU3 已锁定 2 件' ((SqlScalar "SELECT locked_stock FROM wms_stock WHERE sku_id=3") -eq '2')

# 把自动关闭时间改到过去，模拟「已经等了 30 分钟」
Invoke-Sql "UPDATE oms_order SET auto_close_time = DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE id=$order3;" | Out-Null
$r = Invoke-Api -Method POST -Path '/api/admin/order/close-timeout' -Token $adminToken
Assert-That '手工触发超时关单成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
Assert-That '关闭了 1 笔' ($r.Json.data -eq 1) "closed=$($r.Json.data)"
Assert-That 'DB: 订单3 状态=已取消(4)' ((SqlScalar "SELECT status FROM oms_order WHERE id=$order3") -eq '4')
Assert-That 'DB: 取消原因是超时未支付' ((SqlScalar "SELECT cancel_reason FROM oms_order WHERE id=$order3") -like '*超时*')
Assert-That 'DB: SKU3 库存已释放' ((SqlScalar "SELECT locked_stock FROM wms_stock WHERE sku_id=3") -eq '0')
Assert-That 'DB: 写了 TIMEOUT_CLOSE 流水' ((SqlScalar "SELECT COUNT(*) FROM oms_order_operate_history WHERE order_id=$order3 AND action='TIMEOUT_CLOSE'") -eq '1')
Assert-That 'DB: 超时关单的操作人是系统(3)' ((SqlScalar "SELECT operator_type FROM oms_order_operate_history WHERE order_id=$order3 AND action='TIMEOUT_CLOSE'") -eq '3')
Assert-That 'DB: 释放流水 biz_type=3' ((SqlScalar "SELECT COUNT(*) FROM wms_stock_log WHERE biz_type=3 AND biz_no='${orderNo3}_CANCEL'") -eq '1')

# 幂等：再触发一次，不应重复关闭/重复释放
$r = Invoke-Api -Method POST -Path '/api/admin/order/close-timeout' -Token $adminToken
Assert-That '再次触发 → 关闭 0 笔（无新的超时订单）' ($r.Json.data -eq 0) "closed=$($r.Json.data)"
Assert-That 'DB: 订单3 仍是已取消（未重复处理）' ((SqlScalar "SELECT status FROM oms_order WHERE id=$order3") -eq '4')
Assert-That 'DB: 释放流水仍只有 1 条（幂等）' ((SqlScalar "SELECT COUNT(*) FROM wms_stock_log WHERE biz_no='${orderNo3}_CANCEL'") -eq '1')
Assert-That 'DB: 状态流水仍只有 2 条（CREATE + TIMEOUT_CLOSE）' ((SqlScalar "SELECT COUNT(*) FROM oms_order_operate_history WHERE order_id=$order3") -eq '2')

# =====================================================================
Write-Host "`n=========== 10. ⭐⭐ 支付回调与超时关单撞车（条件更新的价值）===========" -ForegroundColor Cyan
# 造一笔「已支付但 auto_close_time 已过」的订单，模拟：
#   支付回调先到、超时扫描后到（或两者同时）
Invoke-Api -Method PUT -Path '/api/app/cart/selected' -Token $tokenA -Body @{ ids=@(); selected=0 } | Out-Null
Invoke-Api -Method POST -Path '/api/app/cart/add' -Token $tokenA -Body @{ skuId=4; quantity=1 } | Out-Null
$r = Invoke-Api -Method POST -Path '/api/app/order/submit' -Token $tokenA -Body @{ addressId=$addrA }
$order4 = $r.Json.data.orderId
$orderNo4 = $r.Json.data.orderNo
Invoke-Sql "UPDATE oms_order SET status=1, pay_time=NOW(), paid_amount=pay_amount, auto_close_time=DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE id=$order4;" | Out-Null
Invoke-Sql "UPDATE wms_stock SET locked_stock=0, stock=stock-1, total_out=total_out+1 WHERE sku_id=4;" | Out-Null

$lockedBefore = SqlScalar "SELECT locked_stock FROM wms_stock WHERE sku_id=4"
$r = Invoke-Api -Method POST -Path '/api/admin/order/close-timeout' -Token $adminToken
Assert-That '触发超时关单（该单已支付但时间已过）' ($r.Json.code -eq 200) "code=$($r.Json.code)"
Assert-That '⭐ 已支付的订单没有被关闭（条件更新挡住了）' ((SqlScalar "SELECT status FROM oms_order WHERE id=$order4") -eq '1') "status=$(SqlScalar "SELECT status FROM oms_order WHERE id=$order4")"
Assert-That '⭐ 已支付订单的库存没有被错误释放' ((SqlScalar "SELECT locked_stock FROM wms_stock WHERE sku_id=4") -eq $lockedBefore)
Assert-That '⭐ 没有为已支付订单写 TIMEOUT_CLOSE 流水' ((SqlScalar "SELECT COUNT(*) FROM oms_order_operate_history WHERE order_id=$order4 AND action='TIMEOUT_CLOSE'") -eq '0')

$r = Invoke-Api -Method PUT -Path "/api/app/order/$order4/cancel" -Token $tokenA
Assert-That '已支付的订单用户也不能直接取消（要走售后）' ($r.Json.code -ne 200) "code=$($r.Json.code) msg=$($r.Json.message)"

# =====================================================================
Write-Host "`n=========== 11. 管理端权限 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Path '/api/admin/order/page' -Token $adminToken
Assert-That '管理员可查全部订单（≥4 笔）' ($r.Json.data.total -ge 4) "total=$($r.Json.data.total)"
$r = Invoke-Api -Path '/api/admin/order/page'
Assert-That '不带令牌访问管理端订单 → 401' ($r.Status -eq 401) "status=$($r.Status)"
$merchantToken = (Invoke-Api -Method POST -Path '/api/admin/auth/login' -Body @{ username='merchant'; password='admin123' }).Json.data.accessToken
$r = Invoke-Api -Path '/api/admin/order/page' -Token $merchantToken
Assert-That '零权限账号查订单 → 403' ($r.Status -eq 403) "status=$($r.Status)"

# =====================================================================
Write-Host "`n=========== 12. 对账恒等式（下单-取消-关单之后仍成立）===========" -ForegroundColor Cyan
$bad = SqlScalar "SELECT COUNT(*) FROM wms_stock WHERE deleted=0 AND (available_stock <> stock - locked_stock OR total_in - total_out <> stock)"
Assert-That 'DB: 所有库存行满足两条恒等式' ($bad -eq '0') "违反行数=$bad"
# SUM(change_num) 应等于 available_stock（仅对本次脚本完全掌控的 SKU 1~4 断言）
$mismatch = SqlScalar @"
SELECT COUNT(*) FROM (
  SELECT s.sku_id, s.available_stock, COALESCE(l.sum_change,0) AS sum_change
  FROM wms_stock s
  LEFT JOIN (SELECT sku_id, SUM(change_num) AS sum_change FROM wms_stock_log WHERE sku_id IN (1,2,3,4) GROUP BY sku_id) l
    ON l.sku_id = s.sku_id
  WHERE s.sku_id IN (1,2,3,4) AND s.available_stock <> COALESCE(l.sum_change,0)
) t
"@
Assert-That 'DB: SUM(change_num) == available_stock（SKU 1~4）' ($mismatch -eq '0') "不一致行数=$mismatch"

# =====================================================================
# 收尾：清理验收造的数据，并把演示库存恢复基线
# =====================================================================
Invoke-Api -Method DELETE -Path "/api/admin/product/spu/$cheapSpu" -Token $adminToken | Out-Null
foreach ($p in @($PhoneA, $PhoneB)) {
    Invoke-Sql "DELETE FROM oms_order_operate_history WHERE order_id IN (SELECT id FROM oms_order WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p'));" | Out-Null
    Invoke-Sql "DELETE FROM oms_order_item WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p');" | Out-Null
    Invoke-Sql "DELETE FROM oms_order WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p');" | Out-Null
    Invoke-Sql "DELETE FROM oms_cart_item WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p');" | Out-Null
    Invoke-Sql "DELETE FROM ums_user_address WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p');" | Out-Null
    Invoke-Sql "DELETE FROM ums_user WHERE phone='$p';" | Out-Null
}
Reset-DemoStock
Write-Host "`n  （已清理验收数据并恢复演示库存基线）" -ForegroundColor DarkGray

$exitCode = Show-TestSummary
exit $exitCode
