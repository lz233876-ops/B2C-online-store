# =====================================================================
#  verify-pay.ps1 — Phase 2 验收（三）：模拟支付网关
#
#  前置：
#    1. 数据库已初始化 + 演示数据
#    2. 应用已启动在 http://localhost:8080
#
#  用法：
#    Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
#    & tools\verify-pay.ps1
#
#  本脚本重点验证「假支付最容易被糊弄过去的两件事」：
#    · 验签：篡改报文后必须被拒
#    · 幂等：重复回调 / 换 notify_id 重投，都只能生效一次（库存不能扣两次）
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

# 直接以表单形式投递渠道通知（真实渠道就是 form-urlencoded，不是 JSON）
function Invoke-Notify {
    param([int]$Channel, $Params)
    $pairs = @()
    foreach ($k in $Params.Keys) { $pairs += ("{0}={1}" -f $k, [uri]::EscapeDataString([string]$Params[$k])) }
    $body = $pairs -join '&'
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    $resp = Invoke-WebRequest -Uri "$BaseUrl/api/pay/notify/$Channel" -Method POST -Body $bytes `
        -ContentType 'application/x-www-form-urlencoded; charset=utf-8' -UseBasicParsing
    return $resp.Content
}
function ConvertTo-Hashtable {
    param($Obj)
    $h = @{}
    $Obj.PSObject.Properties | ForEach-Object { $h[$_.Name] = [string]$_.Value }
    return $h
}

Reset-TestState
if (-not (Wait-ServiceReady)) { exit 1 }
Reset-DemoStock

# ---------------------------------------------------------------------
# 准备：验收会员 + 地址 + 一笔待付款订单
# ---------------------------------------------------------------------
$Phone = '13400000001'
Invoke-Sql "DELETE FROM pay_notify_log WHERE payment_no IN (SELECT payment_no FROM pay_payment WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$Phone'));" | Out-Null
Invoke-Sql "DELETE FROM pay_payment WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$Phone');" | Out-Null
Invoke-Sql "DELETE FROM oms_order_operate_history WHERE order_id IN (SELECT id FROM oms_order WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$Phone'));" | Out-Null
Invoke-Sql "DELETE FROM oms_order_item WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$Phone');" | Out-Null
Invoke-Sql "DELETE FROM oms_order WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$Phone');" | Out-Null
Invoke-Sql "DELETE FROM oms_cart_item WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$Phone');" | Out-Null
Invoke-Sql "DELETE FROM ums_user_address WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$Phone');" | Out-Null
Invoke-Sql "DELETE FROM ums_user WHERE phone='$Phone';" | Out-Null

$reg = Invoke-Api -Method POST -Path '/api/app/auth/register' -Body @{ phone=$Phone; password='pay123456' }
if ($reg.Json.code -ne 200) { Write-Host "⛔ 前置准备失败：注册 → $($reg.Json.message)" -ForegroundColor Red; exit 1 }
$token = (Invoke-Api -Method POST -Path '/api/app/auth/login' -Body @{ phone=$Phone; password='pay123456' }).Json.data.accessToken
$userId = [long](SqlScalar "SELECT id FROM ums_user WHERE phone='$Phone'")

$addr = (Invoke-Api -Method POST -Path '/api/app/address' -Token $token -Body @{
    receiverName='支付验收员'; receiverPhone='13400000001'; province='上海市'; city='上海市'
    district='浦东新区'; detailAddress='张江路 1 号' }).Json.data

function New-TestOrder {
    param([long]$SkuId, [int]$Quantity = 1)
    Invoke-Api -Method PUT -Path '/api/app/cart/selected' -Token $token -Body @{ ids=@(); selected=0 } | Out-Null
    Invoke-Api -Method POST -Path '/api/app/cart/add' -Token $token -Body @{ skuId=$SkuId; quantity=$Quantity } | Out-Null
    Invoke-Api -Method PUT -Path '/api/app/cart/selected' -Token $token -Body @{ ids=@(); selected=1 } | Out-Null
    $r = Invoke-Api -Method POST -Path '/api/app/order/submit' -Token $token -Body @{ addressId=$addr }
    if ($r.Json.code -ne 200) { throw "下单失败：$($r.Json.message)" }
    return $r.Json.data
}

$order = New-TestOrder -SkuId 1 -Quantity 2
$orderNo = $order.orderNo
$orderId = $order.orderId

# =====================================================================
Write-Host "`n=========== 1. 发起支付 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Method POST -Path '/api/app/pay/create'
Assert-That '不带令牌发起支付 → 401' ($r.Status -eq 401) "status=$($r.Status)"

$r = Invoke-Api -Method POST -Path '/api/app/pay/create' -Token $token -Body @{ orderNo=$orderNo; channel=1 }
Assert-That '发起支付成功' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
$paymentNo = $r.Json.data.paymentNo
Assert-That '支付单号形如 PAY+日期+雪花ID' ($paymentNo -like 'PAY20*') "paymentNo=$paymentNo"
Assert-That '支付金额 = 订单应付金额 1199800' ($r.Json.data.amount -eq 1199800) "amount=$($r.Json.data.amount)"
Assert-That '状态为待支付' ($r.Json.data.statusDesc -eq '待支付') "desc=$($r.Json.data.statusDesc)"
Assert-That '返回了模拟收银台地址' ($r.Json.data.cashierUrl -like '*/mock-gateway/*') "url=$($r.Json.data.cashierUrl)"
Assert-That 'DB: 支付单状态=0（待支付）' ((SqlScalar "SELECT status FROM pay_payment WHERE payment_no='$paymentNo'") -eq '0')

$r2 = Invoke-Api -Method POST -Path '/api/app/pay/create' -Token $token -Body @{ orderNo=$orderNo; channel=1 }
Assert-That '重复发起支付 → 复用同一张支付单（不产生新单）' ($r2.Json.data.paymentNo -eq $paymentNo) "paymentNo=$($r2.Json.data.paymentNo)"
Assert-That 'DB: 该订单该渠道只有 1 张待支付单' ((SqlScalar "SELECT COUNT(*) FROM pay_payment WHERE order_id=$orderId AND channel=1 AND status=0") -eq '1')

$r = Invoke-Api -Method POST -Path '/api/app/pay/create' -Token $token -Body @{ orderNo=$orderNo; channel=9 }
Assert-That '不支持的渠道 → 4005' ($r.Json.code -eq 4005) "code=$($r.Json.code)"

# =====================================================================
Write-Host "`n=========== 2. ⭐ 模拟渠道回调完成支付 ===========" -ForegroundColor Cyan
$stockBefore = [int](SqlScalar "SELECT stock FROM wms_stock WHERE sku_id=1")
$lockedBefore = [int](SqlScalar "SELECT locked_stock FROM wms_stock WHERE sku_id=1")
$availBefore = [int](SqlScalar "SELECT available_stock FROM wms_stock WHERE sku_id=1")

$r = Invoke-Api -Method POST -Path "/api/pay/mock-gateway/$paymentNo/pay?success=true"
Assert-That '模拟收银台付款 → 投递通知成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
Assert-That '业务处理成功' ($r.Json.data.success -eq $true) "msg=$($r.Json.data.message)"
Assert-That '渠道回执为 success' ($r.Json.data.notifyResponse -eq 'success')
$notifyParams = $r.Json.data.params
Assert-That '通知报文含签名' ($r.Json.data.sign.Length -eq 64) "signLen=$($r.Json.data.sign.Length)"
$channel = [int]$notifyParams.channel

Assert-That 'DB: 支付单状态=1（支付成功）' ((SqlScalar "SELECT status FROM pay_payment WHERE payment_no='$paymentNo'") -eq '1')
Assert-That 'DB: 渠道流水号已回填' ((SqlScalar "SELECT trade_no FROM pay_payment WHERE payment_no='$paymentNo'") -like 'MOCKTRADE*')
Assert-That 'DB: 支付时间已填' ((SqlScalar "SELECT pay_time FROM pay_payment WHERE payment_no='$paymentNo'") -ne 'NULL')

Assert-That 'DB: 订单状态=1（待发货）' ((SqlScalar "SELECT status FROM oms_order WHERE id=$orderId") -eq '1')
Assert-That 'DB: 订单实付金额已回填' ((SqlScalar "SELECT paid_amount FROM oms_order WHERE id=$orderId") -eq '1199800')
Assert-That 'DB: 订单支付方式已回填' ((SqlScalar "SELECT pay_type FROM oms_order WHERE id=$orderId") -eq '1')
Assert-That 'DB: 订单支付时间已填' ((SqlScalar "SELECT pay_time FROM oms_order WHERE id=$orderId") -ne 'NULL')

# 库存：从「冻结」转「实扣」
Assert-That 'DB: 库存实扣（stock 100 → 98）' ((SqlScalar "SELECT stock FROM wms_stock WHERE sku_id=1") -eq "$($stockBefore - 2)") "stock=$(SqlScalar "SELECT stock FROM wms_stock WHERE sku_id=1")"
Assert-That 'DB: 冻结清零（locked 2 → 0）' ((SqlScalar "SELECT locked_stock FROM wms_stock WHERE sku_id=1") -eq '0')
Assert-That 'DB: 可售库存不变（扣减对可售无净影响）' ((SqlScalar "SELECT available_stock FROM wms_stock WHERE sku_id=1") -eq "$availBefore") "avail=$(SqlScalar "SELECT available_stock FROM wms_stock WHERE sku_id=1")"
Assert-That 'DB: 写了扣减流水（biz_type=2）' ((SqlScalar "SELECT COUNT(*) FROM wms_stock_log WHERE biz_type=2 AND biz_no='$paymentNo'") -eq '1')
Assert-That 'DB: 扣减流水 change_num=0（可售没变）' ((SqlScalar "SELECT change_num FROM wms_stock_log WHERE biz_type=2 AND biz_no='$paymentNo'") -eq '0')
Assert-That 'DB: 累计出库增加 2' ((SqlScalar "SELECT total_out FROM wms_stock WHERE sku_id=1") -eq '2')
Assert-That 'DB: 对账恒等式仍成立（total_in-total_out=stock）' ((SqlScalar "SELECT COUNT(*) FROM wms_stock WHERE sku_id=1 AND total_in - total_out = stock") -eq '1')

Assert-That 'DB: 订单流水写了 PAY' ((SqlScalar "SELECT COUNT(*) FROM oms_order_operate_history WHERE order_id=$orderId AND action='PAY'") -eq '1')

# 回调日志
$notifyId1 = $r.Json.data.notifyId
Assert-That 'DB: 回调日志已留痕' ((SqlScalar "SELECT COUNT(*) FROM pay_notify_log WHERE channel_notify_id='$notifyId1'") -eq '1')
Assert-That 'DB: 日志验签通过(1)' ((SqlScalar "SELECT verify_status FROM pay_notify_log WHERE channel_notify_id='$notifyId1'") -eq '1')
Assert-That 'DB: 日志处理成功(1)' ((SqlScalar "SELECT handle_status FROM pay_notify_log WHERE channel_notify_id='$notifyId1'") -eq '1')
Assert-That 'DB: 日志记录了耗时' ((SqlScalar "SELECT cost_time >= 0 FROM pay_notify_log WHERE channel_notify_id='$notifyId1'") -eq '1')

$r = Invoke-Api -Method GET -Path "/api/app/pay/$orderNo" -Token $token
Assert-That '查询支付状态 → 支付成功' ($r.Json.data.statusDesc -eq '支付成功') "desc=$($r.Json.data.statusDesc)"

# =====================================================================
Write-Host "`n=========== 3. ⭐ 重复回调（同一条通知原样重投）===========" -ForegroundColor Cyan
$replay = ConvertTo-Hashtable $notifyParams
$resp1 = Invoke-Notify -Channel $channel -Params $replay
Assert-That '重投同一条通知 → 返回纯文本 success（让渠道停止重试）' ($resp1 -eq 'success') "resp=$resp1"
Assert-That 'DB: 没有扣第二次库存' ((SqlScalar "SELECT stock FROM wms_stock WHERE sku_id=1") -eq "$($stockBefore - 2)")
Assert-That 'DB: 扣减流水仍只有 1 条' ((SqlScalar "SELECT COUNT(*) FROM wms_stock_log WHERE biz_type=2 AND biz_no='$paymentNo'") -eq '1')
Assert-That 'DB: 该通知只有 1 行日志（同 notify_id 不重复落库）' ((SqlScalar "SELECT COUNT(*) FROM pay_notify_log WHERE channel_notify_id='$notifyId1'") -eq '1')

# =====================================================================
Write-Host "`n=========== 4. ⭐⭐ 换 notify_id 重投（真实渠道的行为）===========" -ForegroundColor Cyan
# 关键：真实渠道重试时常常换一个 notify_id，唯一索引挡不住它。
# 幂等必须落在「支付单状态的条件更新」上。
$r = Invoke-Api -Method POST -Path "/api/pay/mock-gateway/$paymentNo/pay?success=true&overrideNotifyId=REPLAY-NEW-ID-0001"
Assert-That '换 notify_id 重投 → 被识别为重复' ($r.Json.data.duplicate -eq $true) "msg=$($r.Json.data.message)"
Assert-That '⭐ 仍然返回 success（渠道不需要再重试）' ($r.Json.data.notifyResponse -eq 'success')
Assert-That '⭐ 库存没有被扣第二次' ((SqlScalar "SELECT stock FROM wms_stock WHERE sku_id=1") -eq "$($stockBefore - 2)") "stock=$(SqlScalar "SELECT stock FROM wms_stock WHERE sku_id=1")"
Assert-That '⭐ 扣减流水仍然只有 1 条' ((SqlScalar "SELECT COUNT(*) FROM wms_stock_log WHERE biz_type=2 AND biz_no='$paymentNo'") -eq '1')
Assert-That 'DB: 新通知有独立日志行（可见性）' ((SqlScalar "SELECT COUNT(*) FROM pay_notify_log WHERE channel_notify_id='REPLAY-NEW-ID-0001'") -eq '1')
Assert-That 'DB: 该行被标记为「重复通知已忽略」(3)' ((SqlScalar "SELECT handle_status FROM pay_notify_log WHERE channel_notify_id='REPLAY-NEW-ID-0001'") -eq '3')
Assert-That 'DB: 订单仍是待发货（未被重复推进）' ((SqlScalar "SELECT status FROM oms_order WHERE id=$orderId") -eq '1')

# =====================================================================
Write-Host "`n=========== 5. ⭐⭐ 伪造/篡改回调必须被拦 ===========" -ForegroundColor Cyan
# 新下一单，专门用来做攻击测试
$order2 = New-TestOrder -SkuId 2 -Quantity 1
$orderId2 = $order2.orderId
$orderNo2 = $order2.orderNo
$pay2 = (Invoke-Api -Method POST -Path '/api/app/pay/create' -Token $token -Body @{ orderNo=$orderNo2; channel=1 }).Json.data
$paymentNo2 = $pay2.paymentNo

# ⚠️ 必须用 build-notify 而不是 pay 来「拿报文」：
#    pay 会真的完成支付，那样后面的篡改测试就全部失去意义（订单已经付掉了）。
#    这正是把「生成报文」与「投递报文」拆成两个接口的原因 ——
#    「读取」与「写入」绝不能共用一个入口。
$built = Invoke-Api -Method POST -Path "/api/pay/mock-gateway/$paymentNo2/build-notify?success=true"
Assert-That '生成通知报文（不投递）成功' ($built.Json.code -eq 200) "code=$($built.Json.code)"
Assert-That 'DB: 生成报文不会改动支付单状态' ((SqlScalar "SELECT status FROM pay_payment WHERE payment_no='$paymentNo2'") -eq '0')

# 5.1 篡改金额但复用原签名
$tampered = ConvertTo-Hashtable $built.Json.data.params
$tampered['total_amount'] = '1'
$resp = Invoke-Notify -Channel 1 -Params $tampered
Assert-That '⭐ 篡改金额（签名不匹配）→ 返回 failure' ($resp -eq 'failure') "resp=$resp"
Assert-That 'DB: 支付单仍是待支付（未被篡改成功）' ((SqlScalar "SELECT status FROM pay_payment WHERE payment_no='$paymentNo2'") -eq '0')
Assert-That 'DB: 订单仍是待付款' ((SqlScalar "SELECT status FROM oms_order WHERE id=$orderId2") -eq '0')
Assert-That 'DB: 库存未被扣减' ((SqlScalar "SELECT locked_stock FROM wms_stock WHERE sku_id=2") -eq '1')

# 5.2 伪造签名（用独立的 notify_id，模拟另一次投递）
$forged = ConvertTo-Hashtable $built.Json.data.params
$forged['notify_id'] = 'FORGED-SIGN-0001'
$forged['sign'] = 'deadbeef' * 8
$resp = Invoke-Notify -Channel 1 -Params $forged
Assert-That '⭐ 伪造签名 → 返回 failure' ($resp -eq 'failure') "resp=$resp"

# 5.3 完全不带签名
$nosign = ConvertTo-Hashtable $built.Json.data.params
$nosign['notify_id'] = 'NO-SIGN-0002'
$nosign.Remove('sign') | Out-Null
$resp = Invoke-Notify -Channel 1 -Params $nosign
Assert-That '⭐ 不带签名 → 返回 failure' ($resp -eq 'failure') "resp=$resp"

$failedLogs = SqlScalar "SELECT COUNT(*) FROM pay_notify_log WHERE payment_no='$paymentNo2' AND verify_status=2"
Assert-That 'DB: 验签失败的通知都留了痕（verify_status=2）' ([int]$failedLogs -ge 3) "count=$failedLogs"
Assert-That 'DB: 篡改后订单依然待付款' ((SqlScalar "SELECT status FROM oms_order WHERE id=$orderId2") -eq '0')

# 5.4 签名正确但金额就是错的（走金额校验分支，不是验签分支）
$r = Invoke-Api -Method POST -Path "/api/pay/mock-gateway/$paymentNo2/pay?success=true&overrideAmount=1"
Assert-That '⭐ 签名正确但金额被篡改 → 被金额校验拦下' ($r.Json.data.success -eq $false) "msg=$($r.Json.data.message)"
Assert-That 'DB: 支付单仍待支付' ((SqlScalar "SELECT status FROM pay_payment WHERE payment_no='$paymentNo2'") -eq '0')
Assert-That 'DB: 订单仍待付款（金额校验生效）' ((SqlScalar "SELECT status FROM oms_order WHERE id=$orderId2") -eq '0')

# 5.5 正常支付应当成功（证明前面的拦截不是「一律失败」）
$r = Invoke-Api -Method POST -Path "/api/pay/mock-gateway/$paymentNo2/pay?success=true"
Assert-That '正常支付成功（对照组）' ($r.Json.data.success -eq $true) "msg=$($r.Json.data.message)"
Assert-That 'DB: 订单2 变为待发货' ((SqlScalar "SELECT status FROM oms_order WHERE id=$orderId2") -eq '1')

# 5.6 ⭐⭐ 伪造通知不能「占用」notify_id，把渠道随后的真通知挤成「重复」而丢弃
#     这是个会【丢单】的缺陷：若按 notify_id 去重发生在验签之前，
#     攻击者先用垃圾通知占坑，渠道用同一个 notify_id 发来的真通知就会被当成重复直接返回 success，
#     而我们根本没处理 —— 渠道以为成功、不会再重试，这笔钱就收不到了。
$order2b = New-TestOrder -SkuId 2 -Quantity 1
$orderId2b = $order2b.orderId
$paymentNo2b = (Invoke-Api -Method POST -Path '/api/app/pay/create' -Token $token -Body @{ orderNo=$order2b.orderNo; channel=1 }).Json.data.paymentNo
$victimNotifyId = 'VICTIM-NOTIFY-0003'
# ⚠️ 必须让网关【一开始就用这个 notify_id】生成报文并计算签名。
#    notify_id 本身是【参与签名】的字段：拿到报文后再改它而不重算签名，
#    连「真通知」都会验签失败，那样就测不出想测的东西了（我第一版正是这么错的）。
$built2b = Invoke-Api -Method POST -Path "/api/pay/mock-gateway/$paymentNo2b/build-notify?success=true&overrideNotifyId=$victimNotifyId"

# 先用这个 notify_id 发一条伪造签名的通知（会被拒）
$attack = ConvertTo-Hashtable $built2b.Json.data.params
$attack['sign'] = 'deadbeef' * 8
$resp = Invoke-Notify -Channel 1 -Params $attack
Assert-That '伪造通知（占用该 notify_id）被拒' ($resp -eq 'failure') "resp=$resp"
Assert-That 'DB: 伪造通知没有改动支付单' ((SqlScalar "SELECT status FROM pay_payment WHERE payment_no='$paymentNo2b'") -eq '0')

# 再用【同一个 notify_id】发一条签名正确的真通知（报文原样，签名有效）—— 必须能正常处理
$legit = ConvertTo-Hashtable $built2b.Json.data.params
$resp = Invoke-Notify -Channel 1 -Params $legit
Assert-That '⭐ 真通知没有被伪造通知「占位」挤掉（返回 success）' ($resp -eq 'success') "resp=$resp"
Assert-That '⭐ DB: 支付单确实被处理成功（不是空转的 success）' ((SqlScalar "SELECT status FROM pay_payment WHERE payment_no='$paymentNo2b'") -eq '1') "status=$(SqlScalar "SELECT status FROM pay_payment WHERE payment_no='$paymentNo2b'")"
Assert-That '⭐ DB: 订单确实被推进到待发货' ((SqlScalar "SELECT status FROM oms_order WHERE id=$orderId2b") -eq '1')
Assert-That 'DB: 伪造记录的 key 带 :INVALID 后缀（不占用真 notify_id）' ((SqlScalar "SELECT COUNT(*) FROM pay_notify_log WHERE channel_notify_id='${victimNotifyId}:INVALID'") -eq '1')
Assert-That 'DB: 真通知的日志使用原始 notify_id' ((SqlScalar "SELECT COUNT(*) FROM pay_notify_log WHERE channel_notify_id='$victimNotifyId'") -eq '1')

# =====================================================================
Write-Host "`n=========== 6. ⭐⭐ 钱到了但订单已关闭（最容易被忽略的分支）===========" -ForegroundColor Cyan
$order3 = New-TestOrder -SkuId 3 -Quantity 1
$orderId3 = $order3.orderId
$orderNo3 = $order3.orderNo
$pay3 = (Invoke-Api -Method POST -Path '/api/app/pay/create' -Token $token -Body @{ orderNo=$orderNo3; channel=1 }).Json.data
$paymentNo3 = $pay3.paymentNo

# 模拟「订单被关闭，但支付单没来得及关」的竞态：通知已经在路上
Invoke-Sql "UPDATE oms_order SET status=4, close_time=NOW(), cancel_reason='竞态模拟：订单先关闭' WHERE id=$orderId3;" | Out-Null
Assert-That 'DB: 前置——支付单仍待支付、订单已关闭' ((SqlScalar "SELECT CONCAT((SELECT status FROM pay_payment WHERE payment_no='$paymentNo3'),'/',(SELECT status FROM oms_order WHERE id=$orderId3))") -eq '0/4')

$r = Invoke-Api -Method POST -Path "/api/pay/mock-gateway/$paymentNo3/pay?success=true"
Assert-That '⭐ 被识别为「需原路退款」' ($r.Json.data.needRefund -eq $true) "msg=$($r.Json.data.message)"
Assert-That 'DB: 支付单仍被标记为成功（钱确实到账了，账必须对上）' ((SqlScalar "SELECT status FROM pay_payment WHERE payment_no='$paymentNo3'") -eq '1')
Assert-That 'DB: error_msg 记录了待退款原因' ((SqlScalar "SELECT error_msg FROM pay_payment WHERE payment_no='$paymentNo3'") -like '*退款*') "err=$(SqlScalar "SELECT error_msg FROM pay_payment WHERE payment_no='$paymentNo3'")"
Assert-That 'DB: 订单仍是已取消（没有被错误推进）' ((SqlScalar "SELECT status FROM oms_order WHERE id=$orderId3") -eq '4')

# =====================================================================
Write-Host "`n=========== 7. 订单取消会关闭待支付单 ===========" -ForegroundColor Cyan
$order4 = New-TestOrder -SkuId 4 -Quantity 1
$orderId4 = $order4.orderId
$pay4 = (Invoke-Api -Method POST -Path '/api/app/pay/create' -Token $token -Body @{ orderNo=$order4.orderNo; channel=1 }).Json.data
Assert-That 'DB: 支付单已创建（待支付）' ((SqlScalar "SELECT status FROM pay_payment WHERE payment_no='$($pay4.paymentNo)'") -eq '0')
$r = Invoke-Api -Method PUT -Path "/api/app/order/$orderId4/cancel?reason=不想买了" -Token $token
Assert-That '取消订单成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
Assert-That 'DB: 支付单被关闭(3)' ((SqlScalar "SELECT status FROM pay_payment WHERE payment_no='$($pay4.paymentNo)'") -eq '3')
Assert-That 'DB: 关闭原因已记录' ((SqlScalar "SELECT error_msg FROM pay_payment WHERE payment_no='$($pay4.paymentNo)'") -like '*取消*')
$resp = Invoke-Api -Method POST -Path "/api/pay/mock-gateway/$($pay4.paymentNo)/pay?success=true"
Assert-That '已关闭的支付单不能再支付成功' ($resp.Json.data.duplicate -eq $true) "msg=$($resp.Json.data.message)"

# =====================================================================
Write-Host "`n=========== 8. 回调日志与对账 ===========" -ForegroundColor Cyan
Assert-That 'DB: 回调日志总计 ≥ 7 条（含被拒的）' ([int](SqlScalar "SELECT COUNT(*) FROM pay_notify_log") -ge 7) "count=$(SqlScalar "SELECT COUNT(*) FROM pay_notify_log")"
Assert-That 'DB: 没有卡在「待处理」的日志' ((SqlScalar "SELECT COUNT(*) FROM pay_notify_log WHERE handle_status=0") -eq '0')
Assert-That 'DB: 所有库存行满足两条恒等式' ((SqlScalar "SELECT COUNT(*) FROM wms_stock WHERE deleted=0 AND (available_stock <> stock - locked_stock OR total_in - total_out <> stock)") -eq '0')
$mismatch = SqlScalar "SELECT COUNT(*) FROM (SELECT s.sku_id FROM wms_stock s LEFT JOIN (SELECT sku_id, SUM(change_num) AS sc FROM wms_stock_log WHERE sku_id IN (1,2,3,4) GROUP BY sku_id) l ON l.sku_id=s.sku_id WHERE s.sku_id IN (1,2,3,4) AND s.available_stock <> COALESCE(l.sc,0)) t"
Assert-That 'DB: SUM(change_num) == available_stock（SKU 1~4）' ($mismatch -eq '0') "不一致=$mismatch"

# =====================================================================
# 收尾
# =====================================================================
Invoke-Sql "DELETE FROM pay_notify_log WHERE payment_no IN (SELECT payment_no FROM pay_payment WHERE user_id=$userId);" | Out-Null
Invoke-Sql "DELETE FROM pay_payment WHERE user_id=$userId;" | Out-Null
Invoke-Sql "DELETE FROM oms_order_operate_history WHERE order_id IN (SELECT id FROM oms_order WHERE user_id=$userId);" | Out-Null
Invoke-Sql "DELETE FROM oms_order_item WHERE user_id=$userId;" | Out-Null
Invoke-Sql "DELETE FROM oms_order WHERE user_id=$userId;" | Out-Null
Invoke-Sql "DELETE FROM oms_cart_item WHERE user_id=$userId;" | Out-Null
Invoke-Sql "DELETE FROM ums_user_address WHERE user_id=$userId;" | Out-Null
Invoke-Sql "DELETE FROM ums_user WHERE id=$userId;" | Out-Null
Reset-DemoStock
Write-Host "`n  （已清理验收数据并恢复演示库存基线）" -ForegroundColor DarkGray

$exitCode = Show-TestSummary
exit $exitCode
