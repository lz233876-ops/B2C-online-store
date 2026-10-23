# =====================================================================
#  loadtest-seckill.ps1 — Phase 3 压测：秒杀抢购接口
#
#  做什么：
#    1. 批量造 N 个真实会员（含收货地址），并逐个登录拿到 JWT
#    2. 重置秒杀活动（库存 = STOCK，已售 = 0）并预热 Redis
#    3. 用 tools/LoadTest.java（纯 JDK 压测器）以 C 并发打抢购接口
#    4. 压测后校验：成功数必须【恰好】等于库存，零超卖
#
#  前置：MySQL / Redis / RocketMQ 都在跑，应用已在 8080。
#
#  用法：Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
#         & tools\loadtest-seckill.ps1
#         & tools\loadtest-seckill.ps1 -UserCount 2000 -Concurrency 200 -Stock 100
#
#  ⚠️ 本文件含中文，必须存成「UTF-8 带 BOM」。
# =====================================================================

param(
    [int]$UserCount = 1000,
    [int]$Concurrency = 200,
    [int]$Stock = 100,
    [int]$Warmup = 20
)

$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

. "$PSScriptRoot\ApiTestCommon.ps1"

$MysqlExe = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$MysqlArgs = @('-uroot', '-p123456', '--default-character-set=utf8mb4', '-N', '-B', 'shopping_mall', '-e')
$RedisCli = 'C:\redis\redis-cli.exe'
$ActivityId = 9001
$SeckillSkuId = 90011
$IdBase = 600000000          # 压测用户的 id 段，避免与其它数据混
$PhonePrefix = '131'

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
function Redis-Del {
    param([string]$Key)
    $null = & $RedisCli -h 127.0.0.1 -p 6379 DEL $Key 2>&1
}
# 批量删 Redis key（一次 DEL 太多会超 Windows 命令行长度，分批）
function Remove-RedisKeysBatch {
    param([string[]]$Keys, [int]$Batch = 200)
    for ($i = 0; $i -lt $Keys.Count; $i += $Batch) {
        $slice = $Keys[$i..([Math]::Min($i + $Batch - 1, $Keys.Count - 1))]
        $null = & $RedisCli -h 127.0.0.1 -p 6379 DEL @slice 2>&1
    }
}

if (-not (Wait-ServiceReady)) { exit 1 }
$adminToken = Get-AdminToken

# ---------------------------------------------------------------------
# 1. 造压测会员 + 收货地址（直接写库，比调 1000 次接口快得多）
#    ⚠️ 密码哈希是 123456 的 BCrypt 值（与种子数据里的 testuser 一致）
#       这里必须用【单引号】包 SQL：哈希里有 $，双引号会被 PowerShell 当变量
# ---------------------------------------------------------------------
$pwdHash = '$2a$10$bTX30qCPpCQoWZAKui/xIu3c4B584Oe/tMvLaC/ZuAscv5mIHuNx2'

Write-Host "`n=== 1. 准备 $UserCount 个压测会员 ===" -ForegroundColor Cyan
# 清掉上一轮（先删地址再删用户；注意顺序）
Invoke-Sql "DELETE FROM ums_user_address WHERE user_id BETWEEN $($IdBase+1) AND $($IdBase+$UserCount);" | Out-Null
Invoke-Sql "DELETE FROM ums_user WHERE id BETWEEN $($IdBase+1) AND $($IdBase+$UserCount);" | Out-Null

$digits = "(SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9)"
$seqSql = "SELECT a.i + b.i*10 + c.i*100 + d.i*1000 + 1 AS n FROM $digits a, $digits b, $digits c, $digits d"

$sw = [System.Diagnostics.Stopwatch]::StartNew()
Invoke-Sql ("INSERT INTO ums_user (id, phone, password, nickname, gender, member_level_id, growth, points, total_points, status, register_source, create_time, update_time, deleted) " +
            "SELECT $IdBase + n, CONCAT('$PhonePrefix', LPAD(n, 8, '0')), '$pwdHash', CONCAT('压测用户', n), 0, 1, 0, 0, 0, 1, 2, NOW(), NOW(), 0 " +
            "FROM ($seqSql) t WHERE t.n <= $UserCount") | Out-Null
Invoke-Sql ("INSERT INTO ums_user_address (id, user_id, receiver_name, receiver_phone, province, city, district, detail_address, default_status, create_time, update_time, deleted) " +
            "SELECT $IdBase + n + 100000000, $IdBase + n, CONCAT('压测收货人', n), CONCAT('$PhonePrefix', LPAD(n, 8, '0')), '广东省', '深圳市', '南山区', CONCAT('科技园路 ', n, ' 号'), 1, NOW(), NOW(), 0 " +
            "FROM ($seqSql) t WHERE t.n <= $UserCount") | Out-Null
$sw.Stop()

$userCnt = SqlScalar "SELECT COUNT(*) FROM ums_user WHERE id BETWEEN $($IdBase+1) AND $($IdBase+$UserCount)"
$addrCnt = SqlScalar "SELECT COUNT(*) FROM ums_user_address WHERE user_id BETWEEN $($IdBase+1) AND $($IdBase+$UserCount)"
Write-Host ("  会员 {0} 个，地址 {1} 个（{2:N0} ms）" -f $userCnt, $addrCnt, $sw.ElapsedMilliseconds)
if ($userCnt -ne "$UserCount" -or $addrCnt -ne "$UserCount") {
    Write-Host "⛔ 压测用户/地址准备失败，终止" -ForegroundColor Red; exit 1
}

# ---------------------------------------------------------------------
# 2. 逐个登录拿 JWT（必须真实登录：压测要打的是带鉴权的真实接口）
# ---------------------------------------------------------------------
Write-Host "`n=== 2. 登录 $UserCount 个用户获取 JWT ===" -ForegroundColor Cyan
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$tokenFile = Join-Path 'E:\shopping\target' 'seckill-tokens.txt'
if (-not (Test-Path 'E:\shopping\target')) { New-Item -ItemType Directory -Path 'E:\shopping\target' -Force | Out-Null }
$tokens = New-Object System.Collections.Generic.List[string]
$fail = 0
for ($n = 1; $n -le $UserCount; $n++) {
    $phone = "$PhonePrefix{0:D8}" -f $n
    $r = Invoke-Api -Method POST -Path '/api/app/auth/login' -Body @{ phone=$phone; password='123456' }
    if ($r.Json.code -eq 200 -and $r.Json.data.accessToken) {
        $tokens.Add($r.Json.data.accessToken)
    } else {
        $fail++
        if ($fail -le 3) { Write-Host ("  ⚠️ 登录失败 {0}: code={1} msg={2}" -f $phone, $r.Json.code, $r.Json.message) -ForegroundColor Yellow }
    }
}
$sw.Stop()
Write-Host ("  拿到 {0} 个 token，失败 {1} 个（{2:N0} ms）" -f $tokens.Count, $fail, $sw.ElapsedMilliseconds)
if ($tokens.Count -lt 10) { Write-Host "⛔ 有效 token 太少，终止" -ForegroundColor Red; exit 1 }
[System.IO.File]::WriteAllLines($tokenFile, $tokens, (New-Object System.Text.UTF8Encoding($false)))

# ---------------------------------------------------------------------
# 3. 重置秒杀活动 + 预热
# ---------------------------------------------------------------------
Write-Host "`n=== 3. 重置秒杀活动（库存 $Stock）并预热 ===" -ForegroundColor Cyan
Invoke-Sql "DELETE FROM oms_order_operate_history WHERE order_id IN (SELECT id FROM oms_order WHERE order_type=2);" | Out-Null
Invoke-Sql "DELETE FROM oms_order_item WHERE order_id IN (SELECT id FROM oms_order WHERE order_type=2);" | Out-Null
Invoke-Sql "DELETE FROM oms_order WHERE order_type=2;" | Out-Null
Invoke-Sql "DELETE FROM sms_seckill_record WHERE activity_id=$ActivityId;" | Out-Null

# ⚠️⚠️ 必须把【真实库存】也恢复干净：
#      上一轮压测创建的订单各自锁定了 1 件真实库存，只删订单不释放 locked_stock
#      会让 available_stock 变成 0。后果非常隐蔽：抢购在 Redis 层成功，
#      但消费者建单时 lockStock 失败 → 回滚 Redis →
#      表现成「抢到了但什么都没发生，库存还回到满」。
#      踩过：第二次压测 1000 请求 227 成功，但 sold=0 / 订单=0 / Redis 库存=100。
Invoke-Sql "UPDATE wms_stock SET stock=100, locked_stock=0, total_in=100, total_out=0 WHERE sku_id=1;" | Out-Null
Invoke-Sql "DELETE FROM wms_stock_log WHERE sku_id=1;" | Out-Null
Invoke-Sql "INSERT INTO wms_stock_log (id, merchant_id, sku_id, warehouse_id, biz_type, biz_no, change_num, before_stock, after_stock, operator_type, remark, create_time, update_time, deleted) VALUES (1,1,1,1,7,'IN_1',100,0,100,3,'期初入库',NOW(),NOW(),0);" | Out-Null

Invoke-Sql "UPDATE sms_seckill_sku SET seckill_stock=$Stock, seckill_sold=0, status=1 WHERE id=$SeckillSkuId;" | Out-Null
# 重新导入演示活动（刷新时间窗口，确保「进行中」）
$null = cmd /c "mysql -uroot -p123456 --default-character-set=utf8mb4 shopping_mall < `"E:\shopping\sql\data\dev_seckill_demo.sql`" 2>&1"
Invoke-Sql "UPDATE sms_seckill_sku SET seckill_stock=$Stock, seckill_sold=0 WHERE id=$SeckillSkuId;" | Out-Null

# 秒杀投放量必须 <= 真实可售库存，否则用户抢到了也建不出单（运营配置责任）
$realAvail = [int](SqlScalar "SELECT available_stock FROM wms_stock WHERE sku_id=1")
Write-Host ("  真实可售库存 = {0}，秒杀投放 = {1}" -f $realAvail, $Stock)
if ($realAvail -lt $Stock) {
    Write-Host "⛔ 真实可售库存不足，秒杀必然建单失败，终止" -ForegroundColor Red; exit 1
}

# 清掉这些压测用户上一轮的「一人一单」标记，否则本次全部返回 5104
$keys = New-Object System.Collections.Generic.List[string]
for ($n = 1; $n -le $UserCount; $n++) { $keys.Add("shopping:seckill:user:${ActivityId}:$($IdBase + $n)") }
Remove-RedisKeysBatch -Keys $keys.ToArray()
Redis-Del "shopping:seckill:stock:$SeckillSkuId"

$r = Invoke-Api -Method POST -Path '/api/admin/seckill/warm-up' -Token $adminToken
if ($r.Json.code -ne 200) { Write-Host "⛔ 预热失败：$($r.Json.message)" -ForegroundColor Red; exit 1 }
$redisStock = (& $RedisCli -h 127.0.0.1 -p 6379 GET "shopping:seckill:stock:$SeckillSkuId" 2>&1) -join ''
Write-Host ("  Redis 库存 = {0}（期望 {1}）" -f $redisStock, $Stock)
if ($redisStock -ne "$Stock") { Write-Host "⛔ Redis 库存未按预期预热，终止" -ForegroundColor Red; exit 1 }

# ---------------------------------------------------------------------
# 4. 压测
# ---------------------------------------------------------------------
Write-Host "`n=== 4. 开始压测（并发 $Concurrency，请求 $($tokens.Count)）===" -ForegroundColor Cyan
# ⚠️ 预热【不能】打抢购接口：抢购会真的消耗库存，预热 20 次就等于少卖 20 件，
#    数字会变成「1000 请求成功 80」这种让人困惑的结果。
#    改为预热「活动列表」接口：同样能把连接、JIT、认证链路热起来，但不消耗库存。
for ($i = 0; $i -lt 30; $i++) {
    try { $null = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/app/seckill/list' -UseBasicParsing -TimeoutSec 10 } catch { }
}
Write-Host "  预热完成（打列表接口，不消耗库存）" -ForegroundColor DarkGray

$url = "http://127.0.0.1:8080/api/app/seckill/grab/$SeckillSkuId"
$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'
$javaExe = 'C:\jdk-17.0.12\bin\java.exe'
& $javaExe 'E:\shopping\tools\LoadTest.java' --url $url --tokens $tokenFile --concurrency $Concurrency --warmup 0 2>&1
$javaExit = $LASTEXITCODE
Write-Host "`n  压测器退出码: $javaExit"

# ---------------------------------------------------------------------
# 5. 压测后校验：零超卖
# ---------------------------------------------------------------------
Write-Host "`n=== 5. 压测结果校验（零超卖）===" -ForegroundColor Cyan
$sold = [int](SqlScalar "SELECT seckill_sold FROM sms_seckill_sku WHERE id=$SeckillSkuId")
$records = [int](SqlScalar "SELECT COUNT(*) FROM sms_seckill_record WHERE activity_id=$ActivityId")
$redisLeft = [int]((& $RedisCli -h 127.0.0.1 -p 6379 GET "shopping:seckill:stock:$SeckillSkuId" 2>&1) -join '')

Write-Host "  等待 MQ 消费者把订单建完..." -ForegroundColor DarkGray
$sw = [System.Diagnostics.Stopwatch]::StartNew()
while ($sw.Elapsed.TotalSeconds -lt 120) {
    $orders = [int](SqlScalar "SELECT COUNT(*) FROM oms_order WHERE order_type=2")
    if ($orders -ge $Stock) { break }
    Start-Sleep -Seconds 1
}
$orders = [int](SqlScalar "SELECT COUNT(*) FROM oms_order WHERE order_type=2")
$locked = [int](SqlScalar "SELECT locked_stock FROM wms_stock WHERE sku_id=1")

$pass = $true
function Check([string]$name, [bool]$cond, [string]$detail) {
    if ($cond) { Write-Host ("  [PASS] {0}" -f $name) -ForegroundColor Green }
    else { Write-Host ("  [FAIL] {0}  {1}" -f $name, $detail) -ForegroundColor Red; $script:pass = $false }
}

Check "Redis 库存归零" ($redisLeft -eq 0) "redisLeft=$redisLeft"
Check "秒杀已售 == 库存（$Stock）" ($sold -eq $Stock) "sold=$sold"
Check "⭐ 已售没有超过库存（零超卖）" ($sold -le $Stock) "sold=$sold stock=$Stock"
Check "抢购记录数 == 库存（一一对应）" ($records -eq $Stock) "records=$records"
Check "异步订单数 == 库存" ($orders -eq $Stock) "orders=$orders"
Check "真实库存锁定数 == 库存" ($locked -eq $Stock) "locked=$locked"
Check "DB: 库存恒等式仍成立" ((SqlScalar "SELECT COUNT(*) FROM wms_stock WHERE deleted=0 AND (available_stock <> stock - locked_stock OR total_in - total_out <> stock)") -eq '0') "有不一致行"

Write-Host ""
if ($pass) {
    Write-Host "===== 压测结论：$($tokens.Count) 个并发用户抢 $Stock 件，成功 $sold 件，零超卖 =====" -ForegroundColor Green
} else {
    Write-Host "===== 压测结论：存在失败项，见上 =====" -ForegroundColor Red
}

Write-Host "`n（压测数据保留：秒杀订单 $orders 笔、抢购记录 $records 条。清理请跑 tools\verify-seckill.ps1 的开头清理段）" -ForegroundColor DarkGray
if ($pass) { exit 0 } else { exit 1 }
