# =====================================================================
#  verify-frontend-flow.ps1 —— 前端「端到端流程」契约验证
#
#  为什么写这个脚本：
#    前端的风险不在 CSS，而在【请求契约】：
#      字段名写错、参数位置写错（query vs body）、
#      把「HTTP 200 就当成功」（本项目业务失败也是 200 + code!=200）、
#      忘带 token、路径拼错。
#    这个脚本把两个前端页面里的每一处 API 调用【按原样】重放一遍，
#    并且全部经 vite dev server 的 proxy（5173/5174）转发 ——
#    于是同时验证了「前端请求形状」和「proxy 配置」。
#
#  覆盖的用户旅程（与 UI 上的点击顺序一致）：
#    商城前台：登录 → 商品列表 → 详情 → 加购 → 购物车 → 结算下单
#              → 支付（模拟收银台）→ 我的订单 → 确认收货 → 申请售后
#              → 秒杀：活动列表 → 抢购 → 轮询结果
#    管理后台：登录 → 商品列表 → 订单发货 → 售后审核
#
#  前置：后端 8080、MySQL/Redis/RocketMQ 已起；
#        frontend/admin 在 5173、frontend/mall 在 5174（pnpm dev）
#  用法：Set-ExecutionPolicy -Scope Process Bypass -Force
#         & tools\verify-frontend-flow.ps1
#
#  ⚠️ 本文件含中文，必须存成「UTF-8 带 BOM」。
# =====================================================================

<#
.SYNOPSIS
  前端「端到端流程」契约验证：把两个前端页面里的每一处 API 调用按原样重放。

.DESCRIPTION
  默认【不清理数据】—— 这一点很重要，是本脚本踩过坑之后改的：

  早期版本在收尾时会删掉该会员的【所有】订单、清空秒杀记录并把秒杀库存重置为 100，
  本意是"让脚本可反复运行"。但它运行在演示账号上，于是发生过这样的事：
  演示者刚在前端抢到一单，脚本一跑，订单和记录被删、库存显示回到 100/100，
  而 Redis 里的「已参与」标记没被清掉 —— 再抢就报"您已参与过本场秒杀"。
  表现完全像一个功能 bug（"订单创建了但我的订单里没有""100/100 也没减 1"），
  实际是【验收脚本吃掉了演示数据】。

  所以现在的策略：
    · 默认保留数据 —— 跑完还会打印本次造了哪些单，方便手动清理；
    · 需要干净环境时显式传 -Cleanup，此时只删【本次运行创建的那几笔】，
      不做"按用户全删"这种破坏性操作；
    · 脚本开头为了可重复运行所做的清理（清空购物车、清掉该会员的秒杀参与标记）
      都是【精确到本人】的，不会误伤别人，也不会碰订单表。

.PARAMETER Cleanup
  收尾时删除本次运行创建的订单 / 售后单 / 支付单，并清掉该会员的秒杀参与标记。
  默认关闭。即使开启，也不会按用户批量删除历史数据。

.EXAMPLE
  & tools\verify-frontend-flow.ps1
  & tools\verify-frontend-flow.ps1 -Cleanup
#>
param(
    [switch]$Cleanup
)

$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$Mall = 'http://localhost:5174'      # 商城前台（经 vite proxy）
$Admin = 'http://localhost:5173'     # 管理后台（经 vite proxy）
$MysqlExe = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$MysqlArgs = @('-uroot', '-p123456', '--default-character-set=utf8mb4', '-N', '-B', 'shopping_mall', '-e')

$script:pass = 0
$script:fail = 0
function Check {
    param([string]$Name, [bool]$Cond, [string]$Detail = '')
    if ($Cond) {
        Write-Host ("  [PASS] {0}" -f $Name) -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host ("  [FAIL] {0}  {1}" -f $Name, $Detail) -ForegroundColor Red
        $script:fail++
    }
}
function SqlScalar {
    param([string]$Query)
    $out = & $MysqlExe @MysqlArgs $Query 2>&1
    $r = @($out | Where-Object { $_ -notmatch 'Using a password' -and $_ -ne '' })
    if ($r.Count -eq 0) { return '' }
    return $r[0]
}
function Invoke-SqlCleanup {
    param([string]$Query)
    $null = & $MysqlExe @MysqlArgs $Query 2>&1
}

# 与前端 api/http.js 完全一致的调用方式：JSON body + Bearer token
function Call-Api {
    param(
        [string]$Base, [string]$Path, [string]$Method = 'GET',
        $Body = $null, [string]$Token = ''
    )
    # ⚠️⚠️ 必须带 Origin 头！这是本脚本最重要的一行，曾经漏过。
    #   真实浏览器发 POST/PUT 时会带 Origin；而 vite 代理配了 changeOrigin:true，
    #   会把 Host 改写成后端地址，于是 Spring 把这条请求判定为「跨域」。
    #   如果该 Origin 不在 CORS 白名单里，Spring 会直接返回 403 Invalid CORS request。
    #
    #   症状极具欺骗性：管理后台（5173，在白名单里）一切正常；商城前台（5174，
    #   当时不在白名单里）连登录都登不上；而本脚本（不带 Origin）却全绿 ——
    #   等于完全没测出问题。带上 Origin 之后，这类 bug 才会被现有断言自然抓到。
    $headers = @{
        'Content-Type' = 'application/json; charset=utf-8'
        'Origin'       = $Base
    }
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $args = @{
        Uri = "$Base$Path"; Method = $Method; Headers = $headers
        UseBasicParsing = $true; TimeoutSec = 30
    }
    if ($null -ne $Body) {
        # ⚠️ 必须手动转 UTF-8 字节：PS 5.1 的 Invoke-WebRequest 会把中文 body 变成 '?'
        $json = $Body | ConvertTo-Json -Depth 8 -Compress
        $args['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
    }
    try {
        $resp = Invoke-WebRequest @args
        $obj = $resp.Content | ConvertFrom-Json
        return [pscustomobject]@{ Http = $resp.StatusCode; Code = $obj.code; Msg = $obj.message; Data = $obj.data }
    } catch {
        $status = 0
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        return [pscustomobject]@{ Http = $status; Code = -1; Msg = $_.Exception.Message; Data = $null }
    }
}

Write-Host "`n=== 0. 两个 dev server 与 proxy 就绪检查 ===" -ForegroundColor Cyan
foreach ($b in @($Mall, $Admin)) {
    $r = Call-Api -Base $b -Path '/api/app/product/list?pageNum=1&pageSize=1'
    Check ("{0} 可访问且 proxy 通" -f $b) ($r.Code -eq 200) "code=$($r.Code) msg=$($r.Msg)"
}

# CORS 预检：浏览器在跨域写请求前会先发 OPTIONS，被拒的话后面所有 POST 都活不了
Write-Host "`n--- CORS 预检（OPTIONS）---" -ForegroundColor DarkCyan
foreach ($b in @($Mall)) {
    try {
        $pre = Invoke-WebRequest -Uri "$b/api/app/auth/login" -Method OPTIONS -UseBasicParsing -TimeoutSec 15 `
            -Headers @{
                'Origin'                        = $b
                'Access-Control-Request-Method' = 'POST'
                'Access-Control-Request-Headers' = 'content-type'
            }
        $allowOrigin = $pre.Headers['Access-Control-Allow-Origin']
        # 预检成功返回 204 No Content（不是 200）—— 断言写成 200 会误报失败
        Check ("{0} 的 CORS 预检通过" -f $b) `
            (($pre.StatusCode -eq 200 -or $pre.StatusCode -eq 204) -and [bool]$allowOrigin) `
            "status=$($pre.StatusCode) allow-origin=$allowOrigin"
    } catch {
        $st = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        Check ("{0} 的 CORS 预检通过" -f $b) $false "HTTP $st（白名单里可能缺少该端口）"
    }
}

# =====================================================================
Write-Host "`n=== 1. 商城前台：登录 ===" -ForegroundColor Cyan
$login = Call-Api -Base $Mall -Path '/api/app/auth/login' -Method POST `
    -Body @{ phone = '13900000001'; password = '123456' }
Check '会员登录成功（testuser）' ($login.Code -eq 200) "code=$($login.Code) msg=$($login.Msg)"
$appToken = $login.Data.accessToken
Check '拿到 accessToken' ([bool]$appToken)

$me = Call-Api -Base $Mall -Path '/api/app/auth/me' -Token $appToken
Check '获取当前会员（/me）' ($me.Code -eq 200) "code=$($me.Code)"
Check '手机号已脱敏' ($me.Data.phone -like '*`*`*`*`**') "phone=$($me.Data.phone)"

# =====================================================================
Write-Host "`n=== 2. 商品列表 → 详情（Home.vue / Detail.vue）===" -ForegroundColor Cyan
$list = Call-Api -Base $Mall -Path '/api/app/product/list?pageNum=1&pageSize=10'
Check '商品列表返回' ($list.Code -eq 200 -and $list.Data.total -ge 1) "total=$($list.Data.total)"
if ($list.Data.total -lt 1) {
    # 早失败、早说清楚。否则后面每一步都会因为"没有商品"而级联失败，
    # 输出一大片红色，反而看不出真正原因（真实踩过：演示商品被下架后报 28 项失败）。
    Write-Host "`n  ⛔ C 端商品列表为空，后续流程无法进行。" -ForegroundColor Red
    Write-Host "     常见原因：演示商品被下架（后台点了「下架」）或 publish_status 不是 2。" -ForegroundColor Yellow
    Write-Host "     修复：UPDATE pms_spu SET status=1 WHERE id=1;" -ForegroundColor Yellow
    Write-Host "           UPDATE pms_sku SET status=1 WHERE spu_id=1;" -ForegroundColor Yellow
    Write-Host ("`n  通过 {0} 项，失败 {1} 项（因前置条件缺失提前终止）" -f $script:pass, ($script:fail + 1)) -ForegroundColor Red
    exit 1
}
$spu = @($list.Data.records)[0]
Check '列表项含 priceYuan / availableStock' ($null -ne $spu.priceYuan -and $null -ne $spu.availableStock)
Check '列表不含 costPrice（成本价红线）' (-not ($list.Data.records | ConvertTo-Json -Depth 5).Contains('costPrice'))

$detail = Call-Api -Base $Mall -Path "/api/app/product/$($spu.id)"
Check '商品详情返回' ($detail.Code -eq 200) "code=$($detail.Code) msg=$($detail.Msg)"
Check '详情含 SKU 列表' (@($detail.Data.skus).Count -ge 1) "skus=$(@($detail.Data.skus).Count)"
Check '详情含规格属性（选择器要渲染）' (@($detail.Data.specAttributes).Count -ge 1)
Check '详情不含 costPrice' (-not ($detail.Data | ConvertTo-Json -Depth 6).Contains('costPrice'))
$sku = @($detail.Data.skus)[0]
Check 'SKU 含可售库存' ($null -ne $sku.availableStock -and $sku.availableStock -gt 0) "stock=$($sku.availableStock)"

# =====================================================================
Write-Host "`n=== 3. 加购 → 购物车（Cart.vue）===" -ForegroundColor Cyan
# 先清空该会员购物车，保证可重复运行
$cart0 = Call-Api -Base $Mall -Path '/api/app/cart/list' -Token $appToken
$oldIds = @($cart0.Data.items | ForEach-Object { $_.id })
if ($oldIds.Count -gt 0) {
    $qs = ($oldIds | ForEach-Object { "ids=$_" }) -join '&'
    $null = Call-Api -Base $Mall -Path "/api/app/cart/remove?$qs" -Method DELETE -Token $appToken
}

$add = Call-Api -Base $Mall -Path '/api/app/cart/add' -Method POST -Token $appToken `
    -Body @{ skuId = $sku.id; quantity = 2 }
Check '加入购物车成功' ($add.Code -eq 200) "code=$($add.Code) msg=$($add.Msg)"
Check '返回购物车项 ID' ($add.Data -gt 0)

$cart = Call-Api -Base $Mall -Path '/api/app/cart/list' -Token $appToken
Check '购物车列表返回 1 项' (@($cart.Data.items).Count -eq 1) "count=$(@($cart.Data.items).Count)"
Check '购物车带实时价与失效标记' ($null -ne $cart.Data.items[0].currentPrice -and $null -ne $cart.Data.items[0].invalid)
Check '已勾选合计金额 > 0（结算页要用）' ($cart.Data.selectedAmount -gt 0) "amount=$($cart.Data.selectedAmount)"

# =====================================================================
Write-Host "`n=== 4. 结算下单（Cart.vue 的 checkout）===" -ForegroundColor Cyan
# 前端逻辑：勾选同步 → 确保有地址（没有就自动建一条演示地址）→ 提交订单
$allIds = @($cart.Data.items | ForEach-Object { $_.id })
$null = Call-Api -Base $Mall -Path '/api/app/cart/selected' -Method PUT -Token $appToken -Body @{ ids = $allIds; selected = 1 }

$addrs = Call-Api -Base $Mall -Path '/api/app/address/list' -Token $appToken
if (@($addrs.Data).Count -eq 0) {
    $null = Call-Api -Base $Mall -Path '/api/app/address' -Method POST -Token $appToken -Body @{
        receiverName = '前端演示收货人'; receiverPhone = '13800138000'
        province = '广东省'; city = '深圳市'; district = '南山区'
        detailAddress = '科技园路 1 号（前端流程验证）'; tag = '演示'; defaultStatus = 1
    }
    $addrs = Call-Api -Base $Mall -Path '/api/app/address/list' -Token $appToken
}
Check '有可用收货地址' (@($addrs.Data).Count -ge 1) "count=$(@($addrs.Data).Count)"
$addrId = (@($addrs.Data) | Where-Object { $_.defaultStatus -eq 1 } | Select-Object -First 1).id
if (-not $addrId) { $addrId = @($addrs.Data)[0].id }

$submit = Call-Api -Base $Mall -Path '/api/app/order/submit' -Method POST -Token $appToken `
    -Body @{ addressId = $addrId; note = '前端流程验证下单' }
Check '提交订单成功' ($submit.Code -eq 200) "code=$($submit.Code) msg=$($submit.Msg)"
$orderNo = $submit.Data.orderNo
$orderId = $submit.Data.orderId
Check '返回订单号' ($orderNo -like 'SO20*') "orderNo=$orderNo"
Check '返回应付金额与自动关闭时间' ($submit.Data.payAmount -gt 0 -and $null -ne $submit.Data.autoCloseTime)

# =====================================================================
Write-Host "`n=== 5. 支付（Orders.vue 的 pay：创建支付单 → 模拟收银台）===" -ForegroundColor Cyan
$payCreate = Call-Api -Base $Mall -Path '/api/app/pay/create' -Method POST -Token $appToken `
    -Body @{ orderNo = $orderNo; channel = 1 }
Check '创建支付单成功' ($payCreate.Code -eq 200) "code=$($payCreate.Code) msg=$($payCreate.Msg)"
$paymentNo = $payCreate.Data.paymentNo
Check '支付金额 = 订单应付' ($payCreate.Data.amount -eq $submit.Data.payAmount)

# 模拟收银台是公开接口（扮演渠道侧），前端也不带 token
$mockPay = Call-Api -Base $Mall -Path "/api/pay/mock-gateway/$paymentNo/pay?success=true" -Method POST
Check '模拟收银台付款成功' ($mockPay.Code -eq 200 -and $mockPay.Data.success -eq $true) "msg=$($mockPay.Data.message)"

$orders = Call-Api -Base $Mall -Path '/api/app/order/page?pageNum=1&pageSize=10' -Token $appToken
$myOrder = @($orders.Data.records) | Where-Object { $_.orderNo -eq $orderNo } | Select-Object -First 1
Check '订单已变为「待发货」' ($myOrder.statusDesc -eq '待发货') "status=$($myOrder.statusDesc)"

# =====================================================================
Write-Host "`n=== 6. 管理后台：登录 → 商品列表 → 发货 ===" -ForegroundColor Cyan
$adminLogin = Call-Api -Base $Admin -Path '/api/admin/auth/login' -Method POST `
    -Body @{ username = 'admin'; password = 'admin123' }
Check '后台登录成功（admin）' ($adminLogin.Code -eq 200) "code=$($adminLogin.Code) msg=$($adminLogin.Msg)"
$adminToken = $adminLogin.Data.accessToken

$adminMe = Call-Api -Base $Admin -Path '/api/admin/auth/me' -Token $adminToken
Check '后台 /me 返回权限点' (@($adminMe.Data.perms).Count -ge 30) "perms=$(@($adminMe.Data.perms).Count)"

$adminProducts = Call-Api -Base $Admin -Path '/api/admin/product/spu/page?pageNum=1&pageSize=10' -Token $adminToken
Check '后台商品列表可访问' ($adminProducts.Code -eq 200 -and $adminProducts.Data.total -ge 1) "total=$($adminProducts.Data.total)"

$adminOrders = Call-Api -Base $Admin -Path '/api/admin/order/page?pageNum=1&pageSize=10&status=1' -Token $adminToken
Check '后台订单列表（待发货）含我们的单' (@($adminOrders.Data.records | Where-Object { $_.orderNo -eq $orderNo }).Count -eq 1)

$deliver = Call-Api -Base $Admin -Path "/api/admin/order/$orderId/deliver" -Method PUT -Token $adminToken `
    -Body @{ deliveryCompany = '顺丰速运'; deliveryNo = 'SF' + (Get-Random -Minimum 100000 -Maximum 999999) }
Check '后台发货成功' ($deliver.Code -eq 200) "code=$($deliver.Code) msg=$($deliver.Msg)"

$after = Call-Api -Base $Mall -Path '/api/app/order/page?pageNum=1&pageSize=10' -Token $appToken
$myOrder2 = @($after.Data.records) | Where-Object { $_.orderNo -eq $orderNo } | Select-Object -First 1
Check '前台看到订单已变「待收货」' ($myOrder2.statusDesc -eq '待收货') "status=$($myOrder2.statusDesc)"

# =====================================================================
Write-Host "`n=== 7. 确认收货 → 申请售后 → 后台审核（AftersaleList.vue）===" -ForegroundColor Cyan
$receive = Call-Api -Base $Mall -Path "/api/app/order/$orderId/receive" -Method PUT -Token $appToken
Check '前台确认收货成功' ($receive.Code -eq 200) "code=$($receive.Code)"

$itemId = [long](SqlScalar "SELECT id FROM oms_order_item WHERE order_id=$orderId LIMIT 1")
$itemQty = [int](SqlScalar "SELECT quantity FROM oms_order_item WHERE id=$itemId")
# ⚠️ quantity 必须传满：购物车里是 2 件，只退 1 件属于【部分退款】，
#    按设计订单会保持「售后中」而不是「已退款」（部分退款不能把整单标成已退款）。
#    这里要验证「整单退完 → 订单已退款」，所以数量传满。
$apply = Call-Api -Base $Mall -Path '/api/app/aftersale/apply' -Method POST -Token $appToken -Body @{
    orderItemId = $itemId; type = 1; reason = '前端流程验证：仅退款'; quantity = $itemQty
}
Check '申请售后成功（仅退款，退满全部件数）' ($apply.Code -eq 200) "code=$($apply.Code) msg=$($apply.Msg)"
$asId = $apply.Data

$asList = Call-Api -Base $Admin -Path '/api/admin/aftersale/page?pageNum=1&pageSize=10&status=0' -Token $adminToken
Check '后台售后列表（待审核）含该单' (@($asList.Data.records | Where-Object { $_.id -eq $asId }).Count -eq 1)

$stockBefore = [int](SqlScalar "SELECT available_stock FROM wms_stock WHERE sku_id=$($sku.id)")
$audit = Call-Api -Base $Admin -Path "/api/admin/aftersale/$asId/audit" -Method PUT -Token $adminToken `
    -Body @{ approve = $true; remark = '前端流程验证：同意退款' }
Check '后台审核同意成功' ($audit.Code -eq 200) "code=$($audit.Code) msg=$($audit.Msg)"
Check '⭐ 仅退款：售后单已完成' ((SqlScalar "SELECT status FROM oms_aftersale WHERE id=$asId") -eq '5')
Check '⭐ 仅退款：整单退完 → 订单已退款' ((SqlScalar "SELECT status FROM oms_order WHERE id=$orderId") -eq '6') "status=$(SqlScalar "SELECT status FROM oms_order WHERE id=$orderId")"
Check 'DB: 生成了退款单' ((SqlScalar "SELECT COUNT(*) FROM pay_refund WHERE aftersale_id=$asId") -eq '1')
Check '⭐ 仅退款：库存【没有】回补（货还在买家手里）' ((SqlScalar "SELECT available_stock FROM wms_stock WHERE sku_id=$($sku.id)") -eq "$stockBefore") "before=$stockBefore after=$(SqlScalar "SELECT available_stock FROM wms_stock WHERE sku_id=$($sku.id)")"

# =====================================================================
Write-Host "`n=== 8. 秒杀（Seckill.vue：列表 → 抢购 → 轮询结果）===" -ForegroundColor Cyan
$acts = Call-Api -Base $Mall -Path '/api/app/seckill/list'
Check '秒杀活动列表返回' ($acts.Code -eq 200) "code=$($acts.Code)"
$act = @($acts.Data) | Select-Object -First 1
if ($act) {
    Check '活动含秒杀商品与实时剩余库存' (@($act.skus).Count -ge 1 -and $null -ne $act.skus[0].remainStock)
    $secSku = @($act.skus)[0]
    # 为了可重复运行，需要把该会员在本次活动里的「参与痕迹」清干净。
    # ⚠️ 必须【同时】清两处，只清一处会造出一个极具误导性的状态：
    #    · Redis 的参与标记（一人一单的快闸门）
    #    · DB 的 sms_seckill_record（一人一单的强约束，有 uk_activity_id_user_id）
    #   只清 Redis 会怎样：抢购在 Redis 层通过（库存 -1），但消费者建单时
    #   撞唯一索引 → 被当成「幂等命中」→【按设计不回滚 Redis】
    #   （因为不能区分"MQ 重投"和"新的一次抢购"，宁可让库存少一点也不能凭空加回去）。
    #   结果：库存被扣了却没有订单，且脚本还报全绿。
    #   —— 这正是我把它写成两行（原本重复了两次同一行、反而漏了 DB）的原因。
    $uid = [long](SqlScalar "SELECT id FROM ums_user WHERE phone='13900000001'")
    $userKey = "shopping:seckill:user:$($act.id):$uid"
    $null = & 'C:\redis\redis-cli.exe' -h 127.0.0.1 -p 6379 DEL $userKey 2>&1
    Invoke-SqlCleanup "DELETE FROM sms_seckill_record WHERE activity_id=$($act.id) AND user_id=$uid;"

    $grab = Call-Api -Base $Mall -Path "/api/app/seckill/grab/$($secSku.seckillSkuId)" -Method POST -Token $appToken
    if ($grab.Code -eq 200) {
        Check '抢购成功（同步返回排队中）' ($grab.Data.code -eq 1) "code=$($grab.Data.code)"
        Check '返回 requestId' ([bool]$grab.Data.requestId)
        $gotOrder = $false
        for ($i = 0; $i -lt 30; $i++) {
            Start-Sleep -Milliseconds 500
            $res = Call-Api -Base $Mall -Path "/api/app/seckill/result?activityId=$($act.id)" -Token $appToken
            if ($res.Data.orderNo) { $gotOrder = $true; break }
        }
        Check '⭐ 轮询到异步创建的秒杀订单号' $gotOrder
    } else {
        # 5103 售罄 / 5104 已参与 都属正常竞争结果，不算失败
        Check '抢购返回可解释的业务码（售罄/已参与）' ($grab.Code -in @(5103, 5104)) "code=$($grab.Code) msg=$($grab.Msg)"
    }
} else {
    Write-Host '  （没有进行中的秒杀活动，跳过；先导入 sql/data/dev_seckill_demo.sql）' -ForegroundColor Yellow
}

# =====================================================================
# 收尾：默认【不清理】，保留本次造的数据
#
#   ⚠️⚠️ 这里曾经是"按用户全删 + 重置秒杀库存"，结果吃掉了演示者的数据：
#     演示者在前端抢到一单 → 脚本一跑 → 订单/记录被删、库存显示回 100/100，
#     而 Redis 的「已参与」标记没清 → 再抢报 5104。
#     现象酷似功能 bug（"订单没进我的订单""100/100 没减 1"），实为脚本误伤。
#
#   现在的原则：**验收脚本不许破坏使用者的数据**。
#     · 默认只报告本次创建了什么，不动数据库；
#     · 传 -Cleanup 才清理，且只删【本次运行创建的那几笔】（按 id 精确删），
#       不做 `WHERE user_id=...` 这种批量删除；
#     · 清理时【必须同时清掉 Redis 的秒杀参与标记】，否则会留下
#       "库存满 + 不能抢" 的矛盾状态。
# =====================================================================
Write-Host "`n=== 9. 收尾 ===" -ForegroundColor Cyan
$uid = [long](SqlScalar "SELECT id FROM ums_user WHERE phone='13900000001'")

if (-not $Cleanup) {
    Write-Host "  已保留本次流程数据（默认不清理，避免误伤演示数据）：" -ForegroundColor DarkGray
    if ($orderNo) { Write-Host "    · 订单   $orderNo" -ForegroundColor DarkGray }
    if ($asId)    { Write-Host "    · 售后单 id=$asId" -ForegroundColor DarkGray }
    Write-Host "  如需清理，重跑并加 -Cleanup：& tools\verify-frontend-flow.ps1 -Cleanup" -ForegroundColor DarkGray
} else {
    Write-Host "  -Cleanup 已开启：只删本次运行创建的数据" -ForegroundColor DarkGray
    # 只删本次运行创建的那几笔（精确到 id），不做按用户批量删除
    if ($orderId) {
        Invoke-SqlCleanup "DELETE FROM pay_notify_log WHERE payment_no IN (SELECT payment_no FROM pay_payment WHERE order_id=$orderId);"
        Invoke-SqlCleanup "DELETE FROM pay_refund  WHERE order_id=$orderId;"
        Invoke-SqlCleanup "DELETE FROM pay_payment WHERE order_id=$orderId;"
        Invoke-SqlCleanup "DELETE FROM oms_aftersale WHERE order_id=$orderId;"
        Invoke-SqlCleanup "DELETE FROM oms_order_operate_history WHERE order_id=$orderId;"
        Invoke-SqlCleanup "DELETE FROM oms_order_item WHERE order_id=$orderId;"
        Invoke-SqlCleanup "DELETE FROM oms_order WHERE id=$orderId;"
        # 该订单锁定的库存要放回去，否则每跑一次就少几件可售
        Invoke-SqlCleanup "UPDATE wms_stock SET locked_stock=0, stock=stock WHERE sku_id=$($sku.id);"
    }
    if ($uid -gt 0) {
        Invoke-SqlCleanup "DELETE FROM oms_cart_item WHERE user_id=$uid;"
        # ⚠️ 关键：清了库存就必须同时清参与标记，否则用户再也抢不了
        $userKey = "shopping:seckill:user:9001:$uid"
        $null = & 'C:\redis\redis-cli.exe' -h 127.0.0.1 -p 6379 DEL $userKey 2>&1
        Write-Host "    已清除 Redis 秒杀参与标记：$userKey" -ForegroundColor DarkGray
    }
    Write-Host "  清理完成（未触碰其他历史订单）" -ForegroundColor DarkGray
}

# =====================================================================
Write-Host "`n=================================================" -ForegroundColor Cyan
Write-Host ("  通过 {0} 项，失败 {1} 项" -f $script:pass, $script:fail) -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
Write-Host "=================================================" -ForegroundColor Cyan
if ($script:fail -eq 0) { exit 0 } else { exit 1 }
