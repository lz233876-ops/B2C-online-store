# =====================================================================
#  verify-aftersale.ps1 — Phase 2 验收（四）：售后 + 退款 + 库存回补
#
#  前置：数据库已初始化 + 演示数据；应用已启动在 http://localhost:8080
#  用法：Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
#         & tools\verify-aftersale.ps1
#
#  本脚本重点验证三条最容易写错的业务规则：
#    1. 售后挂在【订单明细】上，不是整笔订单
#    2. 【仅退款】不回补库存；只有【退货退款】才回补（否则账面凭空多货 → 超卖）
#    3. 退款金额上限 = 明细实付 - 已退金额（不是订单总额，也不是单价×数量）
#
#  ⚠️ 本文件含中文，必须存成「UTF-8 带 BOM」。
# =====================================================================

$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

. "$PSScriptRoot\ApiTestCommon.ps1"

$BaseUrl = 'http://localhost:8080'
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
    Invoke-Sql "UPDATE wms_stock SET stock=100, locked_stock=0, total_in=100, total_out=0 WHERE sku_id IN (1,2,3,4);" | Out-Null
    Invoke-Sql "DELETE FROM wms_stock_log WHERE sku_id IN (1,2,3,4);" | Out-Null
    Invoke-Sql "INSERT INTO wms_stock_log (id, merchant_id, sku_id, warehouse_id, biz_type, biz_no, change_num, before_stock, after_stock, operator_type, remark, create_time, update_time, deleted) VALUES (1,1,1,1,7,'IN_1',100,0,100,3,'期初入库',NOW(),NOW(),0),(2,1,2,1,7,'IN_2',100,0,100,3,'期初入库',NOW(),NOW(),0),(3,1,3,1,7,'IN_3',100,0,100,3,'期初入库',NOW(),NOW(),0),(4,1,4,1,7,'IN_4',100,0,100,3,'期初入库',NOW(),NOW(),0);" | Out-Null
}

Reset-TestState
if (-not (Wait-ServiceReady)) { exit 1 }
Reset-DemoStock

# ---------------------------------------------------------------------
# 准备：验收会员 + 地址 + 管理员
# ---------------------------------------------------------------------
$PhoneA = '13300000001'
$PhoneB = '13300000002'
foreach ($p in @($PhoneA, $PhoneB)) {
    Invoke-Sql "DELETE FROM pay_refund WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p');" | Out-Null
    Invoke-Sql "DELETE FROM pay_notify_log WHERE payment_no IN (SELECT payment_no FROM pay_payment WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p'));" | Out-Null
    Invoke-Sql "DELETE FROM pay_payment WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p');" | Out-Null
    Invoke-Sql "DELETE FROM oms_aftersale WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p');" | Out-Null
    Invoke-Sql "DELETE FROM oms_order_operate_history WHERE order_id IN (SELECT id FROM oms_order WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p'));" | Out-Null
    Invoke-Sql "DELETE FROM oms_order_item WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p');" | Out-Null
    Invoke-Sql "DELETE FROM oms_order WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p');" | Out-Null
    Invoke-Sql "DELETE FROM oms_cart_item WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p');" | Out-Null
    Invoke-Sql "DELETE FROM ums_user_address WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p');" | Out-Null
    Invoke-Sql "DELETE FROM ums_user WHERE phone='$p';" | Out-Null
}
foreach ($p in @($PhoneA, $PhoneB)) {
    $reg = Invoke-Api -Method POST -Path '/api/app/auth/register' -Body @{ phone=$p; password='as123456' }
    if ($reg.Json.code -ne 200) { Write-Host "⛔ 前置准备失败：注册 $p → $($reg.Json.message)" -ForegroundColor Red; exit 1 }
}
$tokenA = (Invoke-Api -Method POST -Path '/api/app/auth/login' -Body @{ phone=$PhoneA; password='as123456' }).Json.data.accessToken
$tokenB = (Invoke-Api -Method POST -Path '/api/app/auth/login' -Body @{ phone=$PhoneB; password='as123456' }).Json.data.accessToken
$adminToken = Get-AdminToken
$addrA = (Invoke-Api -Method POST -Path '/api/app/address' -Token $tokenA -Body @{
    receiverName='售后验收员'; receiverPhone=$PhoneA; province='广东省'; city='广州市'
    district='天河区'; detailAddress='天河路 1 号' }).Json.data

# 造一笔「已支付 + 已发货」=> 待收货(2) 的订单
function New-PaidOrder {
    param([long]$SkuId, [int]$Qty = 1)
    Invoke-Api -Method PUT -Path '/api/app/cart/selected' -Token $tokenA -Body @{ ids=@(); selected=0 } | Out-Null
    Invoke-Api -Method POST -Path '/api/app/cart/add' -Token $tokenA -Body @{ skuId=$SkuId; quantity=$Qty } | Out-Null
    Invoke-Api -Method PUT -Path '/api/app/cart/selected' -Token $tokenA -Body @{ ids=@(); selected=1 } | Out-Null
    $r = Invoke-Api -Method POST -Path '/api/app/order/submit' -Token $tokenA -Body @{ addressId=$addrA }
    if ($r.Json.code -ne 200) { throw "下单失败：$($r.Json.message)" }
    $orderId = $r.Json.data.orderId; $orderNo = $r.Json.data.orderNo
    $pay = (Invoke-Api -Method POST -Path '/api/app/pay/create' -Token $tokenA -Body @{ orderNo=$orderNo; channel=1 }).Json.data
    $mock = Invoke-Api -Method POST -Path "/api/pay/mock-gateway/$($pay.paymentNo)/pay?success=true"
    if ($mock.Json.data.success -ne $true) { throw "支付失败：$($mock.Json.data.message)" }
    $d = Invoke-Api -Method PUT -Path "/api/admin/order/$orderId/deliver" -Token $adminToken -Body @{ deliveryCompany='顺丰'; deliveryNo="SF$orderId" }
    if ($d.Json.code -ne 200) { throw "发货失败：$($d.Json.message)" }
    $itemId = [long](SqlScalar "SELECT id FROM oms_order_item WHERE order_id=$orderId LIMIT 1")
    return [pscustomobject]@{ OrderId=$orderId; OrderNo=$orderNo; ItemId=$itemId; PaymentNo=$pay.paymentNo; Qty=$Qty }
}

# =====================================================================
Write-Host "`n=========== 1. 申请售后的前置校验 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Path '/api/app/aftersale/page'
Assert-That '不带令牌访问售后 → 401' ($r.Status -eq 401) "status=$($r.Status)"

# 待付款订单不能申请售后
Invoke-Api -Method PUT -Path '/api/app/cart/selected' -Token $tokenA -Body @{ ids=@(); selected=0 } | Out-Null
Invoke-Api -Method POST -Path '/api/app/cart/add' -Token $tokenA -Body @{ skuId=1; quantity=1 } | Out-Null
Invoke-Api -Method PUT -Path '/api/app/cart/selected' -Token $tokenA -Body @{ ids=@(); selected=1 } | Out-Null
$unpaid = (Invoke-Api -Method POST -Path '/api/app/order/submit' -Token $tokenA -Body @{ addressId=$addrA }).Json.data
$unpaidItemId = [long](SqlScalar "SELECT id FROM oms_order_item WHERE order_id=$($unpaid.orderId) LIMIT 1")
$r = Invoke-Api -Method POST -Path '/api/app/aftersale/apply' -Token $tokenA -Body @{
    orderItemId=$unpaidItemId; type=1; reason='七天无理由' }
Assert-That '待付款订单申请售后 → 3203' ($r.Json.code -eq 3203) "code=$($r.Json.code) msg=$($r.Json.message)"
Invoke-Api -Method PUT -Path "/api/app/order/$($unpaid.orderId)/cancel?reason=清理" -Token $tokenA | Out-Null

# 正常订单
$orderA = New-PaidOrder -SkuId 1 -Qty 2
Assert-That 'DB: 前置——订单A 处于待收货(2)' ((SqlScalar "SELECT status FROM oms_order WHERE id=$($orderA.OrderId)") -eq '2')

$r = Invoke-Api -Method POST -Path '/api/app/aftersale/apply' -Token $tokenA -Body @{
    orderItemId=$orderA.ItemId; type=3; reason='换个颜色' }
Assert-That '换货类型 → 3205（暂未开放）' ($r.Json.code -eq 3205) "code=$($r.Json.code)"

$r = Invoke-Api -Method POST -Path '/api/app/aftersale/apply' -Token $tokenA -Body @{
    orderItemId=$orderA.ItemId; type=1; reason='七天无理由'; refundAmount=99999999 }
Assert-That '退款金额超过可退上限 → 3206' ($r.Json.code -eq 3206) "code=$($r.Json.code) msg=$($r.Json.message)"

$r = Invoke-Api -Method POST -Path '/api/app/aftersale/apply' -Token $tokenA -Body @{
    orderItemId=$orderA.ItemId; type=1; reason='七天无理由'; quantity=99 }
Assert-That '退货数量超过购买数量 → 3206' ($r.Json.code -eq 3206) "code=$($r.Json.code)"

$r = Invoke-Api -Method POST -Path '/api/app/aftersale/apply' -Token $tokenB -Body @{
    orderItemId=$orderA.ItemId; type=1; reason='不是我的单' }
Assert-That 'B 用 A 的订单明细申请 → 3001（不泄漏存在性）' ($r.Json.code -eq 3001) "code=$($r.Json.code)"

# =====================================================================
Write-Host "`n=========== 2. 审核拒绝 → 订单还原 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Method POST -Path '/api/app/aftersale/apply' -Token $tokenA -Body @{
    orderItemId=$orderA.ItemId; type=1; reason='商品有划痕'; description='边角有明显划痕'
    images=@('https://example.com/a.jpg'); quantity=2 }
Assert-That '申请售后成功' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
$asA = $r.Json.data
Assert-That '售后单号形如 AS+日期+雪花ID' ((SqlScalar "SELECT aftersale_no FROM oms_aftersale WHERE id=$asA") -like 'AS20*')
Assert-That 'DB: 售后状态=待审核(0)' ((SqlScalar "SELECT status FROM oms_aftersale WHERE id=$asA") -eq '0')
Assert-That 'DB: 订单变为售后中(5)' ((SqlScalar "SELECT status FROM oms_order WHERE id=$($orderA.OrderId)") -eq '5')
Assert-That 'DB: 退款金额按实付单价×数量算出（2 件 → 全额）' ((SqlScalar "SELECT refund_amount FROM oms_aftersale WHERE id=$asA") -eq "$(SqlScalar "SELECT pay_amount FROM oms_order_item WHERE id=$($orderA.ItemId)")")
Assert-That 'DB: 凭证图片以 JSON 数组存库' ((SqlScalar "SELECT images FROM oms_aftersale WHERE id=$asA") -like '*a.jpg*')
Assert-That 'DB: 自动关闭时间 ≈ 7 天后' ((SqlScalar "SELECT DATEDIFF(auto_close_time, create_time) FROM oms_aftersale WHERE id=$asA") -eq '7')
Assert-That 'DB: 订单流水记录了申请售后' ((SqlScalar "SELECT COUNT(*) FROM oms_order_operate_history WHERE order_id=$($orderA.OrderId) AND action='APPLY_AFTER_SALE'") -eq '1')

$r = Invoke-Api -Method POST -Path '/api/app/aftersale/apply' -Token $tokenA -Body @{
    orderItemId=$orderA.ItemId; type=1; reason='再申请一次' }
Assert-That '同一明细同类型重复申请 → 3204' ($r.Json.code -eq 3204) "code=$($r.Json.code)"

$stockBeforeReject = [int](SqlScalar "SELECT available_stock FROM wms_stock WHERE sku_id=1")
$r = Invoke-Api -Method PUT -Path "/api/admin/aftersale/$asA/audit" -Token $adminToken -Body @{ approve=$false; remark='凭证不足' }
Assert-That '审核拒绝成功' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
Assert-That 'DB: 售后状态=已拒绝(2)' ((SqlScalar "SELECT status FROM oms_aftersale WHERE id=$asA") -eq '2')
Assert-That 'DB: 审核意见已记录' ((SqlScalar "SELECT audit_remark FROM oms_aftersale WHERE id=$asA") -eq '凭证不足')
Assert-That '⭐ DB: 订单已还原到售后前的「待收货(2)」' ((SqlScalar "SELECT status FROM oms_order WHERE id=$($orderA.OrderId)") -eq '2')
Assert-That 'DB: 没有产生退款单' ((SqlScalar "SELECT COUNT(*) FROM pay_refund WHERE order_id=$($orderA.OrderId)") -eq '0')
Assert-That 'DB: 库存未变（拒绝不涉及退货）' ((SqlScalar "SELECT available_stock FROM wms_stock WHERE sku_id=1") -eq "$stockBeforeReject")

# =====================================================================
Write-Host "`n=========== 3. ⭐⭐ 仅退款：立即退款完成，但【不回补库存】===========" -ForegroundColor Cyan
$orderB = New-PaidOrder -SkuId 2 -Qty 1
$stockBeforeB = [int](SqlScalar "SELECT available_stock FROM wms_stock WHERE sku_id=2")
$r = Invoke-Api -Method POST -Path '/api/app/aftersale/apply' -Token $tokenA -Body @{
    orderItemId=$orderB.ItemId; type=1; reason='不想要了，仅退款' }
$asB = $r.Json.data
Assert-That '仅退款申请成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"

$r = Invoke-Api -Method PUT -Path "/api/admin/aftersale/$asB/audit" -Token $adminToken -Body @{ approve=$true; remark='同意退款' }
Assert-That '审核同意（仅退款）→ 立即完成' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
Assert-That 'DB: 售后状态=退款完成(5)' ((SqlScalar "SELECT status FROM oms_aftersale WHERE id=$asB") -eq '5')
Assert-That 'DB: 售后完成时间已填' ((SqlScalar "SELECT finish_time FROM oms_aftersale WHERE id=$asB") -ne 'NULL')
Assert-That 'DB: 实际退款金额已回填' ((SqlScalar "SELECT actual_refund_amount FROM oms_aftersale WHERE id=$asB") -eq "$(SqlScalar "SELECT refund_amount FROM oms_aftersale WHERE id=$asB")")
Assert-That 'DB: 生成了退款单' ((SqlScalar "SELECT COUNT(*) FROM pay_refund WHERE aftersale_id=$asB") -eq '1')
Assert-That 'DB: 退款单状态=退款成功(2)' ((SqlScalar "SELECT status FROM pay_refund WHERE aftersale_id=$asB") -eq '2')
Assert-That 'DB: 退款单号形如 RF+日期+雪花ID' ((SqlScalar "SELECT refund_no FROM pay_refund WHERE aftersale_id=$asB") -like 'RF20*')
Assert-That 'DB: 退款类型=全额(1)' ((SqlScalar "SELECT refund_type FROM pay_refund WHERE aftersale_id=$asB") -eq '1')
Assert-That 'DB: 明细退款状态=全额退款(2)' ((SqlScalar "SELECT refund_status FROM oms_order_item WHERE id=$($orderB.ItemId)") -eq '2')
Assert-That 'DB: 明细已退金额已累计' ((SqlScalar "SELECT refund_amount FROM oms_order_item WHERE id=$($orderB.ItemId)") -eq "$(SqlScalar "SELECT pay_amount FROM oms_order_item WHERE id=$($orderB.ItemId)")")
Assert-That 'DB: 支付单状态=全额退款(5)' ((SqlScalar "SELECT status FROM pay_payment WHERE payment_no='$($orderB.PaymentNo)'") -eq '5')
Assert-That 'DB: 订单状态=已退款(6)' ((SqlScalar "SELECT status FROM oms_order WHERE id=$($orderB.OrderId)") -eq '6')
Assert-That 'DB: 退款单关联了原支付单' ((SqlScalar "SELECT payment_no FROM pay_refund WHERE aftersale_id=$asB") -eq $orderB.PaymentNo)
Assert-That 'DB: 订单流水记录了售后退款' ((SqlScalar "SELECT COUNT(*) FROM oms_order_operate_history WHERE order_id=$($orderB.OrderId) AND action='AFTER_SALE_REFUND'") -eq '1')

# ⭐ 最关键的一条：仅退款，货没回来，库存不能加
Assert-That '⭐⭐ DB: 仅退款【没有】回补库存（货还在买家手里）' ((SqlScalar "SELECT available_stock FROM wms_stock WHERE sku_id=2") -eq "$stockBeforeB") "avail=$(SqlScalar "SELECT available_stock FROM wms_stock WHERE sku_id=2") 期望=$stockBeforeB"
Assert-That '⭐ DB: 没有产生退款回补流水（biz_type=4）' ((SqlScalar "SELECT COUNT(*) FROM wms_stock_log WHERE biz_type=4 AND order_no='$($orderB.OrderNo)'") -eq '0')

# =====================================================================
Write-Host "`n=========== 4. ⭐⭐ 退货退款：完整流程 + 回补库存 ===========" -ForegroundColor Cyan
$orderC = New-PaidOrder -SkuId 3 -Qty 2
$stockBeforeC = [int](SqlScalar "SELECT available_stock FROM wms_stock WHERE sku_id=3")
$r = Invoke-Api -Method POST -Path '/api/app/aftersale/apply' -Token $tokenA -Body @{
    orderItemId=$orderC.ItemId; type=2; reason='尺寸不合适，退货退款'; quantity=2 }
Assert-That '退货退款申请成功' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
$asC = $r.Json.data

$r = Invoke-Api -Method PUT -Path "/api/admin/aftersale/$asC/audit" -Token $adminToken -Body @{ approve=$true; remark='同意退货' }
Assert-That '审核同意（退货退款）' ($r.Json.code -eq 200) "code=$($r.Json.code)"
Assert-That 'DB: 售后状态=退货中(3)' ((SqlScalar "SELECT status FROM oms_aftersale WHERE id=$asC") -eq '3')
Assert-That '⭐ DB: 此时【还没有】退款单（等收到货才退）' ((SqlScalar "SELECT COUNT(*) FROM pay_refund WHERE aftersale_id=$asC") -eq '0')
Assert-That '⭐ DB: 此时【还没有】回补库存' ((SqlScalar "SELECT available_stock FROM wms_stock WHERE sku_id=3") -eq "$stockBeforeC")
Assert-That 'DB: 订单仍是售后中(5)' ((SqlScalar "SELECT status FROM oms_order WHERE id=$($orderC.OrderId)") -eq '5')

# 还没收货就确认收货 → 应拒绝（这里状态已是退货中，测「重复确认」）
$r = Invoke-Api -Method PUT -Path "/api/app/aftersale/$asC/return" -Token $tokenA -Body @{ returnCompany='中通快递'; returnNo='ZT88889999' }
Assert-That '买家填写退货物流成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
Assert-That 'DB: 退货物流单号已保存' ((SqlScalar "SELECT return_no FROM oms_aftersale WHERE id=$asC") -eq 'ZT88889999')
Assert-That 'DB: 退货时间已填' ((SqlScalar "SELECT return_time FROM oms_aftersale WHERE id=$asC") -ne 'NULL')
Assert-That 'DB: 状态仍是退货中(3)' ((SqlScalar "SELECT status FROM oms_aftersale WHERE id=$asC") -eq '3')

$r = Invoke-Api -Method PUT -Path "/api/admin/aftersale/$asC/receive" -Token $adminToken
Assert-That '商家确认收到退货 → 触发退款' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
Assert-That 'DB: 售后状态=退款完成(5)' ((SqlScalar "SELECT status FROM oms_aftersale WHERE id=$asC") -eq '5')
Assert-That 'DB: 商家收货时间已填' ((SqlScalar "SELECT receive_time FROM oms_aftersale WHERE id=$asC") -ne 'NULL')
Assert-That 'DB: 生成退款单' ((SqlScalar "SELECT COUNT(*) FROM pay_refund WHERE aftersale_id=$asC") -eq '1')
Assert-That 'DB: 订单状态=已退款(6)' ((SqlScalar "SELECT status FROM oms_order WHERE id=$($orderC.OrderId)") -eq '6')

# ⭐ 最关键的一条：退货退款，货回来了，库存必须加回去
Assert-That '⭐⭐ DB: 退货退款【已回补】库存（+2）' ((SqlScalar "SELECT available_stock FROM wms_stock WHERE sku_id=3") -eq "$($stockBeforeC + 2)") "avail=$(SqlScalar "SELECT available_stock FROM wms_stock WHERE sku_id=3") 期望=$($stockBeforeC + 2)"
Assert-That '⭐ DB: 产生了退款回补流水（biz_type=4）' ((SqlScalar "SELECT COUNT(*) FROM wms_stock_log WHERE biz_type=4 AND order_no='$($orderC.OrderNo)'") -eq '1')
Assert-That 'DB: 回补流水 change_num=+2' ((SqlScalar "SELECT change_num FROM wms_stock_log WHERE biz_type=4 AND order_no='$($orderC.OrderNo)'") -eq '2')
Assert-That 'DB: 回补后对账恒等式仍成立' ((SqlScalar "SELECT COUNT(*) FROM wms_stock WHERE sku_id=3 AND total_in - total_out = stock") -eq '1')

# =====================================================================
Write-Host "`n=========== 5. 用户撤销 + 超时自动关闭 ===========" -ForegroundColor Cyan
$orderD = New-PaidOrder -SkuId 4 -Qty 1
$r = Invoke-Api -Method POST -Path '/api/app/aftersale/apply' -Token $tokenA -Body @{
    orderItemId=$orderD.ItemId; type=1; reason='先申请看看' }
$asD = $r.Json.data
$r = Invoke-Api -Method PUT -Path "/api/app/aftersale/$asD/cancel" -Token $tokenA
Assert-That '用户撤销售后成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
Assert-That 'DB: 售后状态=已关闭(6)' ((SqlScalar "SELECT status FROM oms_aftersale WHERE id=$asD") -eq '6')
Assert-That 'DB: 订单还原为待收货(2)' ((SqlScalar "SELECT status FROM oms_order WHERE id=$($orderD.OrderId)") -eq '2')
Assert-That 'DB: 没有退款单' ((SqlScalar "SELECT COUNT(*) FROM pay_refund WHERE aftersale_id=$asD") -eq '0')

# 超时自动关闭：造一笔待审核 + 超时的
$r = Invoke-Api -Method POST -Path '/api/app/aftersale/apply' -Token $tokenA -Body @{
    orderItemId=$orderD.ItemId; type=1; reason='超时测试（注意：同类型不能重复，用另一单）' }
Assert-That '同一明细同类型已被拒绝过一次，仍占用唯一键 → 3204' ($r.Json.code -eq 3204) "code=$($r.Json.code)"

$orderE = New-PaidOrder -SkuId 4 -Qty 1
$r = Invoke-Api -Method POST -Path '/api/app/aftersale/apply' -Token $tokenA -Body @{
    orderItemId=$orderE.ItemId; type=1; reason='超时未处理测试' }
$asE = $r.Json.data
Invoke-Sql "UPDATE oms_aftersale SET auto_close_time = DATE_SUB(NOW(), INTERVAL 1 DAY) WHERE id=$asE;" | Out-Null
$r = Invoke-Api -Method POST -Path '/api/admin/aftersale/close-timeout' -Token $adminToken
Assert-That '手工触发超时关单成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
Assert-That '关闭了 1 笔' ($r.Json.data -eq 1) "closed=$($r.Json.data)"
Assert-That 'DB: 售后状态=已关闭(6)' ((SqlScalar "SELECT status FROM oms_aftersale WHERE id=$asE") -eq '6')
Assert-That 'DB: 订单还原为待收货(2)' ((SqlScalar "SELECT status FROM oms_order WHERE id=$($orderE.OrderId)") -eq '2')
$r = Invoke-Api -Method POST -Path '/api/admin/aftersale/close-timeout' -Token $adminToken
Assert-That '再次触发 → 关闭 0 笔（幂等）' ($r.Json.data -eq 0) "closed=$($r.Json.data)"

# =====================================================================
Write-Host "`n=========== 6. 越权与权限 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Path '/api/app/aftersale/page' -Token $tokenB
Assert-That 'B 的售后列表为空' ($r.Json.data.total -eq 0) "total=$($r.Json.data.total)"
$r = Invoke-Api -Path "/api/app/aftersale/$asC" -Token $tokenB
Assert-That 'B 查 A 的售后单 → 3201（不泄漏存在性）' ($r.Json.code -eq 3201) "code=$($r.Json.code)"
$r = Invoke-Api -Method PUT -Path "/api/app/aftersale/$asC/cancel" -Token $tokenB
Assert-That 'B 撤销 A 的售后 → 3201（拒绝）' ($r.Json.code -eq 3201) "code=$($r.Json.code)"

$r = Invoke-Api -Path '/api/admin/aftersale/page' -Token $adminToken
Assert-That '管理员可查全部售后' ($r.Json.data.total -ge 3) "total=$($r.Json.data.total)"
$r = Invoke-Api -Path '/api/admin/aftersale/page'
Assert-That '不带令牌访问管理端售后 → 401' ($r.Status -eq 401) "status=$($r.Status)"
$merchantToken = (Invoke-Api -Method POST -Path '/api/admin/auth/login' -Body @{ username='merchant'; password='admin123' }).Json.data.accessToken
$r = Invoke-Api -Path '/api/admin/aftersale/page' -Token $merchantToken
Assert-That '零权限账号查售后 → 403' ($r.Status -eq 403) "status=$($r.Status)"

# =====================================================================
Write-Host "`n=========== 7. 对账恒等式 ===========" -ForegroundColor Cyan
Assert-That 'DB: 所有库存行满足两条恒等式' ((SqlScalar "SELECT COUNT(*) FROM wms_stock WHERE deleted=0 AND (available_stock <> stock - locked_stock OR total_in - total_out <> stock)") -eq '0')
$mismatch = SqlScalar "SELECT COUNT(*) FROM (SELECT s.sku_id FROM wms_stock s LEFT JOIN (SELECT sku_id, SUM(change_num) AS sc FROM wms_stock_log WHERE sku_id IN (1,2,3,4) GROUP BY sku_id) l ON l.sku_id=s.sku_id WHERE s.sku_id IN (1,2,3,4) AND s.available_stock <> COALESCE(l.sc,0)) t"
Assert-That 'DB: SUM(change_num) == available_stock（SKU 1~4）' ($mismatch -eq '0') "不一致=$mismatch"
$refundSum = SqlScalar "SELECT COUNT(*) FROM pay_refund WHERE refund_amount <= 0"
Assert-That 'DB: 不存在金额非法的退款单' ($refundSum -eq '0')

# =====================================================================
# 收尾
# =====================================================================
$uid = [long](SqlScalar "SELECT id FROM ums_user WHERE phone='$PhoneA'")
Invoke-Sql "DELETE FROM pay_refund WHERE user_id=$uid;" | Out-Null
Invoke-Sql "DELETE FROM pay_notify_log WHERE payment_no IN (SELECT payment_no FROM pay_payment WHERE user_id=$uid);" | Out-Null
Invoke-Sql "DELETE FROM pay_payment WHERE user_id=$uid;" | Out-Null
Invoke-Sql "DELETE FROM oms_aftersale WHERE user_id=$uid;" | Out-Null
Invoke-Sql "DELETE FROM oms_order_operate_history WHERE order_id IN (SELECT id FROM oms_order WHERE user_id=$uid);" | Out-Null
Invoke-Sql "DELETE FROM oms_order_item WHERE user_id=$uid;" | Out-Null
Invoke-Sql "DELETE FROM oms_order WHERE user_id=$uid;" | Out-Null
Invoke-Sql "DELETE FROM oms_cart_item WHERE user_id=$uid;" | Out-Null
Invoke-Sql "DELETE FROM ums_user_address WHERE user_id=$uid;" | Out-Null
foreach ($p in @($PhoneA, $PhoneB)) { Invoke-Sql "DELETE FROM ums_user WHERE phone='$p';" | Out-Null }
Reset-DemoStock
Write-Host "`n  （已清理验收数据并恢复演示库存基线）" -ForegroundColor DarkGray

$exitCode = Show-TestSummary
exit $exitCode
