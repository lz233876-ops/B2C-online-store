# =====================================================================
#  verify-seckill.ps1 — Phase 3 验收（一）：秒杀模块功能
#
#  前置：
#    1. 数据库已初始化 + 演示数据 + 秒杀演示数据
#         cmd /c "mysql ... shopping_mall < sql\data\dev_seckill_demo.sql"
#    2. RocketMQ 已启动：cd docker; docker compose up -d
#    3. 应用已启动在 http://localhost:8080
#
#  用法：Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
#         & tools\verify-seckill.ps1
#
#  验证秒杀最核心的 5 件事：
#    1. 预热：库存只进 Redis，列表展示的剩余量以 Redis 为准
#    2. MQ 异步建单：抢到后轮询能拿到 orderNo，订单是秒杀价、orderType=2
#    3. 一人一单：同一用户重复抢 → 5104，DB 只有 1 条记录
#    4. 失败补偿：无地址导致建单失败 → Redis 占位被还回（库存不泄漏），补地址后可重抢
#    5. 不超卖：40 个用户并发抢，成功数 / 记录数 / 已售数 / 真实库存锁定数四者一致
#
#  ⚠️ 本文件含中文，必须存成「UTF-8 带 BOM」。
# =====================================================================

$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

. "$PSScriptRoot\ApiTestCommon.ps1"

$MysqlExe = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$MysqlArgs = @('-uroot', '-p123456', '--default-character-set=utf8mb4', '-N', '-B', 'shopping_mall', '-e')
$RedisCli = 'C:\redis\redis-cli.exe'

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
function Redis-Get {
    param([string]$Key)
    $out = & $RedisCli -h 127.0.0.1 -p 6379 GET $Key 2>&1
    return (@($out | Where-Object { "$_" -ne '' }) -join '')
}
function Redis-Del {
    param([string]$Key)
    $null = & $RedisCli -h 127.0.0.1 -p 6379 DEL $Key 2>&1
}
function Redis-Set {
    param([string]$Key, [string]$Value)
    $null = & $RedisCli -h 127.0.0.1 -p 6379 SET $Key $Value 2>&1
}
function Reset-DemoStock {
    Invoke-Sql "UPDATE wms_stock SET stock=100, locked_stock=0, total_in=100, total_out=0 WHERE sku_id IN (1,2,3,4);" | Out-Null
    Invoke-Sql "DELETE FROM wms_stock_log WHERE sku_id IN (1,2,3,4);" | Out-Null
    Invoke-Sql "INSERT INTO wms_stock_log (id, merchant_id, sku_id, warehouse_id, biz_type, biz_no, change_num, before_stock, after_stock, operator_type, remark, create_time, update_time, deleted) VALUES (1,1,1,1,7,'IN_1',100,0,100,3,'期初入库',NOW(),NOW(),0),(2,1,2,1,7,'IN_2',100,0,100,3,'期初入库',NOW(),NOW(),0),(3,1,3,1,7,'IN_3',100,0,100,3,'期初入库',NOW(),NOW(),0),(4,1,4,1,7,'IN_4',100,0,100,3,'期初入库',NOW(),NOW(),0);" | Out-Null
}
<#
  轮询等待。
  ⚠️ 秒杀是「异步建单」，绝不能用固定 Start-Sleep 去猜：
     RocketMQ PushConsumer 首次消费要经历「拉取位点 + 消费者组 rebalance」，
     冷启动时第一条消息可能延迟 5~15 秒。固定 sleep 4 秒会得到一堆假失败。
#>
function Wait-Until {
    param([scriptblock]$Condition, [int]$TimeoutSec = 40, [int]$IntervalMs = 500, [string]$What = '条件')
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        if (& $Condition) { return $true }
        Start-Sleep -Milliseconds $IntervalMs
    }
    Write-Host ("  ⏱ 等待超时（{0}s）：{1}" -f $TimeoutSec, $What) -ForegroundColor Yellow
    return $false
}

Reset-TestState
if (-not (Wait-ServiceReady)) { exit 1 }
Reset-DemoStock

$ActivityId = 9001
$SeckillSkuId = 90011
$SeckillStock = 100

# ---------------------------------------------------------------------
# 准备：清理上一轮痕迹 + 重置 Redis + 重新导入演示活动
# ---------------------------------------------------------------------
Invoke-Sql "DELETE FROM oms_order_operate_history WHERE order_id IN (SELECT id FROM oms_order WHERE order_type=2);" | Out-Null
Invoke-Sql "DELETE FROM oms_order_item WHERE order_id IN (SELECT id FROM oms_order WHERE order_type=2);" | Out-Null
Invoke-Sql "DELETE FROM oms_order WHERE order_type=2;" | Out-Null
Invoke-Sql "DELETE FROM sms_seckill_record WHERE activity_id=$ActivityId;" | Out-Null
Redis-Del "shopping:seckill:stock:$SeckillSkuId"
$allPhones = @('13200000001','13200000002','13220000001','13220000002')
for ($i = 1; $i -le 40; $i++) { $allPhones += ("1321000{0:D4}" -f $i) }
foreach ($p in $allPhones) {
    $uid = SqlScalar "SELECT id FROM ums_user WHERE phone='$p'"
    if ($uid) { Redis-Del "shopping:seckill:user:${ActivityId}:$uid" }
    Invoke-Sql "DELETE FROM ums_user_address WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p');" | Out-Null
    Invoke-Sql "DELETE FROM ums_user WHERE phone='$p';" | Out-Null
}
$null = cmd /c "mysql -uroot -p123456 --default-character-set=utf8mb4 shopping_mall < `"E:\shopping\sql\data\dev_seckill_demo.sql`" 2>&1"

$adminToken = Get-AdminToken
$r = Invoke-Api -Method POST -Path '/api/admin/seckill/warm-up' -Token $adminToken
Assert-That '手动预热成功' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
$r = Invoke-Api -Path '/api/admin/seckill/cached-count' -Token $adminToken
Assert-That '进程内缓存已加载秒杀商品' ($r.Json.data -ge 1) "count=$($r.Json.data)"

# =====================================================================
Write-Host "`n=========== 1. 活动浏览（公开）与预热 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Path '/api/app/seckill/list'
Assert-That '活动列表免登录可访问' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
$act = @($r.Json.data) | Where-Object { $_.id -eq $ActivityId } | Select-Object -First 1
Assert-That '包含演示活动 9001' ($null -ne $act) "activities=$(@($r.Json.data).Count)"
Assert-That '状态描述为「进行中」' ($act.statusDesc -eq '进行中') "desc=$($act.statusDesc)"
$secSku = @($act.skus) | Where-Object { $_.seckillSkuId -eq $SeckillSkuId } | Select-Object -First 1
Assert-That '包含秒杀商品 90011' ($null -ne $secSku) "skus=$(@($act.skus).Count)"
Assert-That '秒杀价 2999.00 元' ($secSku.seckillPriceYuan -eq '2999.00') "price=$($secSku.seckillPriceYuan)"
Assert-That '原价 5999.00 元' ($secSku.originalPriceYuan -eq '5999.00') "price=$($secSku.originalPriceYuan)"
Assert-That '折扣显示为 5.0 折' ($secSku.discount -eq '5.0 折') "discount=$($secSku.discount)"
Assert-That '⭐ 剩余库存来自 Redis（100）' ($secSku.remainStock -eq $SeckillStock) "remain=$($secSku.remainStock)"
Assert-That 'Redis 库存 key 已预热为 100' ((Redis-Get "shopping:seckill:stock:$SeckillSkuId") -eq '100') "redis=$(Redis-Get "shopping:seckill:stock:$SeckillSkuId")"
$r = Invoke-Api -Path "/api/app/seckill/detail/$ActivityId"
Assert-That '活动详情免登录可访问' ($r.Json.code -eq 200) "code=$($r.Json.code)"

# =====================================================================
Write-Host "`n=========== 2. 抢购 → MQ 异步建单 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Method POST -Path "/api/app/seckill/grab/$SeckillSkuId"
Assert-That '不带令牌抢购 → 401' ($r.Status -eq 401) "status=$($r.Status)"

$null = Invoke-Api -Method POST -Path '/api/app/auth/register' -Body @{ phone='13200000001'; password='sec123456' }
$tokenA = (Invoke-Api -Method POST -Path '/api/app/auth/login' -Body @{ phone='13200000001'; password='sec123456' }).Json.data.accessToken
$uidA = SqlScalar "SELECT id FROM ums_user WHERE phone='13200000001'"
$null = Invoke-Api -Method POST -Path '/api/app/address' -Token $tokenA -Body @{
    receiverName='秒杀验收员'; receiverPhone='13200000001'; province='北京市'; city='北京市'
    district='海淀区'; detailAddress='中关村大街 1 号' }

$remainBefore = [int](Redis-Get "shopping:seckill:stock:$SeckillSkuId")
$r = Invoke-Api -Method POST -Path "/api/app/seckill/grab/$SeckillSkuId" -Token $tokenA
Assert-That '抢购请求被受理（同步返回排队中）' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
Assert-That '返回 code=1（排队中）' ($r.Json.data.code -eq 1) "code=$($r.Json.data.code)"
Assert-That '返回 32 位 requestId 供轮询' ($r.Json.data.requestId.Length -eq 32) "reqId=$($r.Json.data.requestId)"
Assert-That '⭐ 同步阶段已原子扣减 Redis 库存' ((Redis-Get "shopping:seckill:stock:$SeckillSkuId") -eq "$($remainBefore - 1)") "redis=$(Redis-Get "shopping:seckill:stock:$SeckillSkuId")"
# ⚠️ 这里【不能】断言「此刻数据库还没有记录」：那是异步链路的中间态。
#    消费者预热后几十毫秒就把单建完了，断言中间态必然 flaky。
#    改为断言一个【确定】的性质：同步响应里没有订单号（订单一定来自异步建单）。
Assert-That '⭐ 抢购响应不含订单号（订单由异步创建）' ($null -eq $r.Json.data.orderNo) "orderNo=$($r.Json.data.orderNo)"

# 轮询等异步建单（首次消费要等消费者组初始化，给足 40 秒）
$null = Wait-Until -TimeoutSec 40 -What 'MQ 消费并建单' -Condition {
    $v = SqlScalar "SELECT order_no FROM sms_seckill_record WHERE activity_id=$ActivityId AND user_id=$uidA"
    return ($v -ne '' -and $v -ne 'NULL' -and $v.Length -gt 5)
}
$orderNo = SqlScalar "SELECT order_no FROM sms_seckill_record WHERE activity_id=$ActivityId AND user_id=$uidA"
Assert-That '⭐ MQ 异步创建了订单号' ($orderNo -like 'SO20*') "orderNo=$orderNo"

$r = Invoke-Api -Path "/api/app/seckill/result?activityId=$ActivityId" -Token $tokenA
Assert-That '结果查询：已抢到' ($r.Json.data.grabbed -eq $true) "grabbed=$($r.Json.data.grabbed)"
Assert-That '结果查询：processing=false（订单已建好）' ($r.Json.data.processing -eq $false) "processing=$($r.Json.data.processing)"
Assert-That '结果查询：状态「已抢到，待支付」' ($r.Json.data.statusDesc -like '*待支付*') "desc=$($r.Json.data.statusDesc)"

Assert-That 'DB: 订单类型=2（秒杀订单）' ((SqlScalar "SELECT order_type FROM oms_order WHERE order_no='$orderNo'") -eq '2')
Assert-That 'DB: 应付=秒杀价 299900（0 运费）' ((SqlScalar "SELECT pay_amount FROM oms_order WHERE order_no='$orderNo'") -eq '299900') "pay=$(SqlScalar "SELECT pay_amount FROM oms_order WHERE order_no='$orderNo'")"
Assert-That 'DB: 明细单价=秒杀价（不是原价）' ((SqlScalar "SELECT price FROM oms_order_item WHERE order_no='$orderNo'") -eq '299900')
Assert-That 'DB: 秒杀已售 +1' ((SqlScalar "SELECT seckill_sold FROM sms_seckill_sku WHERE id=$SeckillSkuId") -eq '1') "sold=$(SqlScalar "SELECT seckill_sold FROM sms_seckill_sku WHERE id=$SeckillSkuId")"
Assert-That 'DB: 真实库存已锁定 1 件' ((SqlScalar "SELECT locked_stock FROM wms_stock WHERE sku_id=1") -eq '1') "locked=$(SqlScalar "SELECT locked_stock FROM wms_stock WHERE sku_id=1")"
Assert-That 'DB: 写了秒杀订单状态流水' ((SqlScalar "SELECT COUNT(*) FROM oms_order_operate_history WHERE order_no='$orderNo' AND action='CREATE'") -eq '1')

# =====================================================================
Write-Host "`n=========== 3. ⭐ 一人一单 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Method POST -Path "/api/app/seckill/grab/$SeckillSkuId" -Token $tokenA
Assert-That '同一用户再抢 → 5104 已参与过' ($r.Json.code -eq 5104) "code=$($r.Json.code) msg=$($r.Json.message)"
Start-Sleep -Seconds 2
Assert-That '⭐ DB: 该用户只有 1 条抢购记录' ((SqlScalar "SELECT COUNT(*) FROM sms_seckill_record WHERE activity_id=$ActivityId AND user_id=$uidA") -eq '1')
Assert-That 'DB: 秒杀订单仍是 1 笔（没有重复下单）' ((SqlScalar "SELECT COUNT(*) FROM oms_order WHERE order_type=2") -eq '1') "orders=$(SqlScalar "SELECT COUNT(*) FROM oms_order WHERE order_type=2")"
Assert-That 'DB: 已售仍是 1（一人一单没多扣）' ((SqlScalar "SELECT seckill_sold FROM sms_seckill_sku WHERE id=$SeckillSkuId") -eq '1')

# =====================================================================
Write-Host "`n=========== 4. ⭐⭐ 建单失败要回滚 Redis（库存不泄漏）===========" -ForegroundColor Cyan
# 会员 B：只抢不填地址 → 消费者建单时找不到地址 → 必须把 Redis 占位还回去
$null = Invoke-Api -Method POST -Path '/api/app/auth/register' -Body @{ phone='13200000002'; password='sec123456' }
$tokenB = (Invoke-Api -Method POST -Path '/api/app/auth/login' -Body @{ phone='13200000002'; password='sec123456' }).Json.data.accessToken
$uidB = SqlScalar "SELECT id FROM ums_user WHERE phone='13200000002'"

$stockBeforeB = [int](Redis-Get "shopping:seckill:stock:$SeckillSkuId")
$r = Invoke-Api -Method POST -Path "/api/app/seckill/grab/$SeckillSkuId" -Token $tokenB
Assert-That '无地址也能抢到名额' ($r.Json.code -eq 200) "code=$($r.Json.code)"
Assert-That '抢到后 Redis 已扣减（不变量：不会变多）' ([int](Redis-Get "shopping:seckill:stock:$SeckillSkuId") -le $stockBeforeB) "redis=$(Redis-Get "shopping:seckill:stock:$SeckillSkuId")"
# ⚠️ 同样不断言「此刻正好等于 stockBeforeB - 1」：B 没有地址，消费者会很快失败并回滚，
#    这个中间态只存在几十毫秒。要断言的是【最终态】（下面的 Wait-Until + restored）。

$restored = Wait-Until -TimeoutSec 40 -What '消费失败后回滚 Redis 库存' -Condition {
    return ([int](Redis-Get "shopping:seckill:stock:$SeckillSkuId") -eq $stockBeforeB)
}
Assert-That '⭐ 建单失败后 Redis 库存被还回（不泄漏）' $restored "redis=$(Redis-Get "shopping:seckill:stock:$SeckillSkuId") 期望=$stockBeforeB"
Assert-That '⭐ 一人一单标记也被清除（允许补地址后重抢）' ((Redis-Get "shopping:seckill:user:${ActivityId}:$uidB") -eq '') "mark=$(Redis-Get "shopping:seckill:user:${ActivityId}:$uidB")"
Assert-That 'DB: 没有留下 B 的抢购记录（事务已回滚）' ((SqlScalar "SELECT COUNT(*) FROM sms_seckill_record WHERE activity_id=$ActivityId AND user_id=$uidB") -eq '0')
Assert-That 'DB: B 没有产生订单' ((SqlScalar "SELECT COUNT(*) FROM oms_order WHERE order_type=2") -eq '1')

# 补地址后重抢应成功（证明标记确实被清了）
$null = Invoke-Api -Method POST -Path '/api/app/address' -Token $tokenB -Body @{
    receiverName='秒杀验收员B'; receiverPhone='13200000002'; province='上海市'; city='上海市'
    district='浦东新区'; detailAddress='世纪大道 2 号' }
$r = Invoke-Api -Method POST -Path "/api/app/seckill/grab/$SeckillSkuId" -Token $tokenB
Assert-That '⭐ 补地址后重新抢购成功' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
$null = Wait-Until -TimeoutSec 40 -What 'B 的订单创建完成' -Condition {
    $v = SqlScalar "SELECT order_no FROM sms_seckill_record WHERE activity_id=$ActivityId AND user_id=$uidB"
    return ($v -ne '' -and $v -ne 'NULL' -and $v.Length -gt 5)
}
Assert-That 'DB: B 的订单已创建（秒杀订单共 2 笔）' ((SqlScalar "SELECT COUNT(*) FROM oms_order WHERE order_type=2") -eq '2') "orders=$(SqlScalar "SELECT COUNT(*) FROM oms_order WHERE order_type=2")"

# =====================================================================
Write-Host "`n=========== 5. ⭐⭐ 并发抢购：不超卖 ===========" -ForegroundColor Cyan
$stockLeft = [int](Redis-Get "shopping:seckill:stock:$SeckillSkuId")
Write-Host "  并发前剩余库存: $stockLeft" -ForegroundColor DarkGray

$tokens = @()
for ($i = 1; $i -le 40; $i++) {
    $phone = "1321000{0:D4}" -f $i
    $null = Invoke-Api -Method POST -Path '/api/app/auth/register' -Body @{ phone=$phone; password='sec123456' }
    $tk = (Invoke-Api -Method POST -Path '/api/app/auth/login' -Body @{ phone=$phone; password='sec123456' }).Json.data.accessToken
    $null = Invoke-Api -Method POST -Path '/api/app/address' -Token $tk -Body @{
        receiverName="压测用户$i"; receiverPhone=$phone; province='广东省'; city='深圳市'
        district='南山区'; detailAddress="科技园 $i 号" }
    $tokens += $tk
}
Assert-That '准备了 40 个可抢购会员（含地址）' ($tokens.Count -eq 40) "count=$($tokens.Count)"

# ⚠️ 本机是 Windows PowerShell 5.1，没有 ForEach-Object -Parallel。
#    用 HttpClient 的 SendAsync 一次性发出全部请求，才是【真并发】
#    （逐个 Invoke-WebRequest 是串行的，根本测不出秒杀的并发行为）。
Add-Type -AssemblyName System.Net.Http
$handler = New-Object System.Net.Http.HttpClientHandler
$handler.MaxConnectionsPerServer = 200
$client = New-Object System.Net.Http.HttpClient($handler)
$client.Timeout = [TimeSpan]::FromSeconds(60)

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$taskList = New-Object System.Collections.ArrayList
foreach ($tk in $tokens) {
    $req = New-Object System.Net.Http.HttpRequestMessage(
        [System.Net.Http.HttpMethod]::Post, "http://localhost:8080/api/app/seckill/grab/$SeckillSkuId")
    $req.Headers.Add('Authorization', "Bearer $tk")
    $null = $taskList.Add($client.SendAsync($req))
}
[System.Threading.Tasks.Task[]]$taskArr = $taskList.ToArray()
[System.Threading.Tasks.Task]::WaitAll($taskArr)
$sw.Stop()

$results = @()
foreach ($t in $taskArr) {
    try {
        $body = $t.Result.Content.ReadAsStringAsync().Result
        $json = $body | ConvertFrom-Json
        $results += [pscustomobject]@{ code = $json.code; msg = $json.message }
    } catch {
        $results += [pscustomobject]@{ code = -1; msg = $_.Exception.Message }
    }
}
$client.Dispose()

$okCount = @($results | Where-Object { $_.code -eq 200 }).Count
$repeatCount = @($results | Where-Object { $_.code -eq 5104 }).Count
$soldOutCount = @($results | Where-Object { $_.code -eq 5103 }).Count
$otherCount = @($results | Where-Object { $_.code -ne 200 -and $_.code -ne 5104 -and $_.code -ne 5103 }).Count
Write-Host ("  40 并发耗时 {0:N0} ms | 成功 {1} | 已参与 {2} | 售罄 {3} | 其它 {4}" -f $sw.ElapsedMilliseconds, $okCount, $repeatCount, $soldOutCount, $otherCount) -ForegroundColor DarkGray

Assert-That '40 个不同用户并发全部成功（库存充足）' ($okCount -eq 40) "ok=$okCount"
Assert-That '没有出现非预期错误' ($otherCount -eq 0) "other=$otherCount"
Assert-That '⭐ Redis 库存精确扣减 40（无并发丢失）' ((Redis-Get "shopping:seckill:stock:$SeckillSkuId") -eq "$($stockLeft - 40)") "redis=$(Redis-Get "shopping:seckill:stock:$SeckillSkuId") 期望=$($stockLeft - 40)"

$expectedOrders = 42
Write-Host "  等待 MQ 消费者把 40 笔订单建完..." -ForegroundColor DarkGray
$null = Wait-Until -TimeoutSec 90 -IntervalMs 1000 -What "MQ 建完 $expectedOrders 笔订单" -Condition {
    $c = SqlScalar "SELECT COUNT(*) FROM oms_order WHERE order_type=2"
    return ([int]$c -ge $expectedOrders)
}
$orderCnt = [int](SqlScalar "SELECT COUNT(*) FROM oms_order WHERE order_type=2")
Assert-That "⭐ MQ 已异步创建 $expectedOrders 笔秒杀订单" ($orderCnt -eq $expectedOrders) "orders=$orderCnt"
Assert-That '⭐ DB: 抢购记录数 = 订单数（一一对应）' ((SqlScalar "SELECT COUNT(*) FROM sms_seckill_record WHERE activity_id=$ActivityId") -eq "$expectedOrders") "records=$(SqlScalar "SELECT COUNT(*) FROM sms_seckill_record WHERE activity_id=$ActivityId")"
Assert-That '⭐ DB: 秒杀已售 = 记录数（无超卖）' ((SqlScalar "SELECT seckill_sold FROM sms_seckill_sku WHERE id=$SeckillSkuId") -eq "$expectedOrders") "sold=$(SqlScalar "SELECT seckill_sold FROM sms_seckill_sku WHERE id=$SeckillSkuId")"
Assert-That '⭐ DB: 已售 <= 总库存' ((SqlScalar "SELECT seckill_sold <= seckill_stock FROM sms_seckill_sku WHERE id=$SeckillSkuId") -eq '1')
Assert-That '⭐ DB: 真实库存锁定数 = 订单数' ((SqlScalar "SELECT locked_stock FROM wms_stock WHERE sku_id=1") -eq "$expectedOrders") "locked=$(SqlScalar "SELECT locked_stock FROM wms_stock WHERE sku_id=1")"

# =====================================================================
Write-Host "`n=========== 6. 售罄边界 ===========" -ForegroundColor Cyan
# 把总库存调成「已售 + 1」，Redis 也改成 1：抢掉最后一件，下一个必须 5103
Invoke-Sql "UPDATE sms_seckill_sku SET seckill_stock = seckill_sold + 1 WHERE id=$SeckillSkuId;" | Out-Null
Redis-Set "shopping:seckill:stock:$SeckillSkuId" 1

$null = Invoke-Api -Method POST -Path '/api/app/auth/register' -Body @{ phone='13220000001'; password='sec123456' }
$tokenZ = (Invoke-Api -Method POST -Path '/api/app/auth/login' -Body @{ phone='13220000001'; password='sec123456' }).Json.data.accessToken
$null = Invoke-Api -Method POST -Path '/api/app/address' -Token $tokenZ -Body @{
    receiverName='售罄测试'; receiverPhone='13220000001'; province='浙江省'; city='杭州市'; district='西湖区'; detailAddress='文一路 1 号' }
$r = Invoke-Api -Method POST -Path "/api/app/seckill/grab/$SeckillSkuId" -Token $tokenZ
Assert-That '抢到最后一件成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
Assert-That 'Redis 库存归零' ((Redis-Get "shopping:seckill:stock:$SeckillSkuId") -eq '0') "redis=$(Redis-Get "shopping:seckill:stock:$SeckillSkuId")"

$null = Invoke-Api -Method POST -Path '/api/app/auth/register' -Body @{ phone='13220000002'; password='sec123456' }
$tokenY = (Invoke-Api -Method POST -Path '/api/app/auth/login' -Body @{ phone='13220000002'; password='sec123456' }).Json.data.accessToken
$r = Invoke-Api -Method POST -Path "/api/app/seckill/grab/$SeckillSkuId" -Token $tokenY
Assert-That '⭐ 售罄后再抢 → 5103 已售罄' ($r.Json.code -eq 5103) "code=$($r.Json.code) msg=$($r.Json.message)"
Assert-That 'DB: 已售没有超过总库存（DB 兜底闸门）' ((SqlScalar "SELECT seckill_sold <= seckill_stock FROM sms_seckill_sku WHERE id=$SeckillSkuId") -eq '1')
Assert-That 'DB: 没有为售罄请求创建记录' ((SqlScalar "SELECT COUNT(*) FROM sms_seckill_record WHERE activity_id=$ActivityId AND user_id=(SELECT id FROM ums_user WHERE phone='13220000002')") -eq '0')

# =====================================================================
Write-Host "`n=========== 7. 对账与清理 ===========" -ForegroundColor Cyan
$null = Wait-Until -TimeoutSec 40 -What '最后一件的订单创建完成' -Condition {
    return ([int](SqlScalar "SELECT COUNT(*) FROM oms_order WHERE order_type=2") -ge 43)
}
$finalSold = [int](SqlScalar "SELECT seckill_sold FROM sms_seckill_sku WHERE id=$SeckillSkuId")
$finalRecords = [int](SqlScalar "SELECT COUNT(*) FROM sms_seckill_record WHERE activity_id=$ActivityId")
$finalOrders = [int](SqlScalar "SELECT COUNT(*) FROM oms_order WHERE order_type=2")
Assert-That '⭐ 最终：已售 == 记录数 == 订单数（三者一致）' ($finalSold -eq $finalRecords -and $finalRecords -eq $finalOrders) "sold=$finalSold records=$finalRecords orders=$finalOrders"
Assert-That '⭐ 最终：已售 <= 总库存（全程无超卖）' ((SqlScalar "SELECT seckill_sold <= seckill_stock FROM sms_seckill_sku WHERE id=$SeckillSkuId") -eq '1')
Assert-That 'DB: 所有库存行满足两条恒等式' ((SqlScalar "SELECT COUNT(*) FROM wms_stock WHERE deleted=0 AND (available_stock <> stock - locked_stock OR total_in - total_out <> stock)") -eq '0')

# 清理：秒杀订单 + 记录 + 会员 + Redis
Invoke-Sql "DELETE FROM oms_order_operate_history WHERE order_id IN (SELECT id FROM oms_order WHERE order_type=2);" | Out-Null
Invoke-Sql "DELETE FROM oms_order_item WHERE order_id IN (SELECT id FROM oms_order WHERE order_type=2);" | Out-Null
Invoke-Sql "DELETE FROM oms_order WHERE order_type=2;" | Out-Null
Invoke-Sql "DELETE FROM sms_seckill_record WHERE activity_id=$ActivityId;" | Out-Null
foreach ($p in $allPhones) {
    $uid = SqlScalar "SELECT id FROM ums_user WHERE phone='$p'"
    if ($uid) { Redis-Del "shopping:seckill:user:${ActivityId}:$uid" }
    Invoke-Sql "DELETE FROM ums_user_address WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p');" | Out-Null
    Invoke-Sql "DELETE FROM ums_user WHERE phone='$p';" | Out-Null
}
Invoke-Sql "UPDATE sms_seckill_sku SET seckill_stock=100, seckill_sold=0 WHERE id=$SeckillSkuId;" | Out-Null
Redis-Del "shopping:seckill:stock:$SeckillSkuId"
Reset-DemoStock
Write-Host "`n  （已清理秒杀验收数据并恢复演示库存基线）" -ForegroundColor DarkGray

$exitCode = Show-TestSummary
exit $exitCode
