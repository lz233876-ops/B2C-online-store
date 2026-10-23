# =====================================================================
#  verify-cart.ps1 — Phase 2 验收（一）：购物车 + 库存四操作
#
#  前置：
#    1. 数据库已初始化 + 演示数据（sql/init.sql + V1.1.1 + dev_rbac_test_users.sql）
#    2. 应用已启动在 http://localhost:8080
#
#  用法：
#    Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
#    & tools\verify-cart.ps1
#
#  ⚠️ 本文件含中文，必须存成「UTF-8 带 BOM」。
# =====================================================================

$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

. "$PSScriptRoot\ApiTestCommon.ps1"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$MysqlExe = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$MysqlArgs = @('-uroot', '-p123456', '--default-character-set=utf8mb4', '-N', '-B', 'shopping_mall', '-e')
function Invoke-Sql {
    param([string]$Query)
    $out = & $MysqlExe @MysqlArgs $Query 2>&1
    return ,@($out | Where-Object { $_ -notmatch 'Using a password' -and $_ -ne '' })
}

Reset-TestState
if (-not (Wait-ServiceReady)) { exit 1 }

# ---------------------------------------------------------------------
# 准备：清理并注册验收会员（手机号 136 段，避免与其它脚本冲突）
# ---------------------------------------------------------------------
$PhoneA = '13600000001'
$PhoneB = '13600000002'
# ⚠️ 这里用【物理删除】而不是「逻辑删除 + 改名」：
#    ums_user.phone 是 varchar(20)，而 LEFT(phone,10)+'_del_'+19位雪花ID = 34 字符，
#    会直接触发「数据过长」错误 → 清理静默失败 → 后续注册撞重复（phone 唯一索引）
#    → $userAId 为空 → 后面所有 SQL 变成 `user_id=` 语法错，报出一堆误导性的失败。
#    测试夹具不需要保留历史，物理删除最简单可靠。
foreach ($p in @($PhoneA, $PhoneB)) {
    Invoke-Sql "DELETE FROM oms_cart_item WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p');" | Out-Null
    Invoke-Sql "DELETE FROM ums_user_address WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p');" | Out-Null
    Invoke-Sql "DELETE FROM ums_user WHERE phone='$p';" | Out-Null
}

# 前置准备失败要【立刻停下并说清原因】，否则后面会连环报出误导性的错误
$regA = Invoke-Api -Method POST -Path '/api/app/auth/register' -Body @{ phone=$PhoneA; password='cart123456' }
if ($regA.Json.code -ne 200) {
    Write-Host "⛔ 前置准备失败：注册验收会员A 失败 → code=$($regA.Json.code) msg=$($regA.Json.message)" -ForegroundColor Red
    exit 1
}
$userAId = $regA.Json.data
$regB = Invoke-Api -Method POST -Path '/api/app/auth/register' -Body @{ phone=$PhoneB; password='cart123456' }
if ($regB.Json.code -ne 200) {
    Write-Host "⛔ 前置准备失败：注册验收会员B 失败 → code=$($regB.Json.code) msg=$($regB.Json.message)" -ForegroundColor Red
    exit 1
}
$tokenA = (Invoke-Api -Method POST -Path '/api/app/auth/login' -Body @{ phone=$PhoneA; password='cart123456' }).Json.data.accessToken
$tokenB = (Invoke-Api -Method POST -Path '/api/app/auth/login' -Body @{ phone=$PhoneB; password='cart123456' }).Json.data.accessToken

# =====================================================================
Write-Host "`n=========== 1. 加购 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Path '/api/app/cart/list'
Assert-That '不带令牌访问购物车 → 401' ($r.Status -eq 401) "status=$($r.Status)"

$r = Invoke-Api -Method POST -Path '/api/app/cart/add' -Token $tokenA -Body @{ skuId=1; quantity=2 }
Assert-That '加购成功（演示 SKU 1）' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
$cartItem1 = $r.Json.data

$r = Invoke-Api -Method POST -Path '/api/app/cart/add' -Token $tokenA -Body @{ skuId=1; quantity=3 }
Assert-That '同一 SKU 重复加购 → 返回同一个购物车项 id（累加而非新增）' ($r.Json.data -eq $cartItem1) "id=$($r.Json.data)"
$dbQty = Invoke-Sql "SELECT quantity FROM oms_cart_item WHERE id=$cartItem1"
Assert-That 'DB: 数量累加为 5' ($dbQty[0] -eq '5') "qty=$($dbQty[0])"
$dbCount = Invoke-Sql "SELECT COUNT(*) FROM oms_cart_item WHERE user_id=$userAId"
Assert-That 'DB: 购物车只有 1 条记录（没有重复插入）' ($dbCount[0] -eq '1') "count=$($dbCount[0])"

$r = Invoke-Api -Method POST -Path '/api/app/cart/add' -Token $tokenA -Body @{ skuId=99999999; quantity=1 }
Assert-That '加购不存在的 SKU → 2002' ($r.Json.code -eq 2002) "code=$($r.Json.code)"

$r = Invoke-Api -Method POST -Path '/api/app/cart/add' -Token $tokenA -Body @{ skuId=1; quantity=999 }
Assert-That '数量超过 200 → 参数校验 400' ($r.Json.code -eq 400) "code=$($r.Json.code)"

# SKU 1 库存 100，购物车已有 5 件。加 150 → 目标 155
#   155 ≤ 单 SKU 上限 200（不触发限额校验）→ 但 > 可售 100 → 应触发库存校验
# ⚠️ 不能传 200：5+200=205 会先撞「单商品最多 200 件」的上限校验，测不到库存这条
$r = Invoke-Api -Method POST -Path '/api/app/cart/add' -Token $tokenA -Body @{ skuId=1; quantity=150 }
Assert-That '总数量超过库存 → 2003 库存不足' ($r.Json.code -eq 2003) "code=$($r.Json.code) msg=$($r.Json.message)"

# 顺带验证「单 SKU 上限」本身：5+200=205 > 200 → 400
$r = Invoke-Api -Method POST -Path '/api/app/cart/add' -Token $tokenA -Body @{ skuId=1; quantity=200 }
Assert-That '累计超过单 SKU 上限 200 → 400' ($r.Json.code -eq 400) "code=$($r.Json.code)"

# 下架商品不能加购（用后台接口造一个已下架 SPU）
$adminToken = Get-AdminToken
$offlineSpu = Invoke-Api -Method POST -Path '/api/admin/product/spu' -Token $adminToken -Body @{
    categoryId=3; spuName='验收下架商品'; publishStatus=2; status=0
    specAttributes=@(@{ attributeId=1; valueIds=@(1) })
    skus=@(@{ price=1000; stock=10; specValues=@(@{attributeId=1;valueId=1}) })
}
$offlineSkuId = (Invoke-Sql "SELECT id FROM pms_sku WHERE spu_id=$($offlineSpu.Json.data) AND deleted=0")[0]
$r = Invoke-Api -Method POST -Path '/api/app/cart/add' -Token $tokenA -Body @{ skuId=[long]$offlineSkuId; quantity=1 }
Assert-That '把已下架商品加购 → 2001 商品已下架' ($r.Json.code -eq 2001) "code=$($r.Json.code) msg=$($r.Json.message)"

# =====================================================================
Write-Host "`n=========== 2. 购物车列表与汇总 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Path '/api/app/cart/list' -Token $tokenA
$cart = $r.Json.data
Assert-That '列表返回 1 项' ($cart.totalCount -eq 1) "count=$($cart.totalCount)"
Assert-That '总件数 = 5' ($cart.totalQuantity -eq 5) "qty=$($cart.totalQuantity)"
Assert-That '新加购默认勾选' ($cart.items[0].selected -eq 1) "selected=$($cart.items[0].selected)"
Assert-That '已勾选且有效的项数 = 1' ($cart.selectedCount -eq 1) "count=$($cart.selectedCount)"
Assert-That '结算金额 = 599900 × 5 = 2999500' ($cart.selectedAmount -eq 2999500) "amount=$($cart.selectedAmount)"
Assert-That '小计金额正确' ($cart.items[0].amount -eq 2999500) "amount=$($cart.items[0].amount)"
Assert-That '实时价来自 SKU（599900）' ($cart.items[0].currentPrice -eq 599900) "price=$($cart.items[0].currentPrice)"
Assert-That '可售库存 = 100' ($cart.items[0].availableStock -eq 100) "stock=$($cart.items[0].availableStock)"
Assert-That '商品有效（invalid=false）' ($cart.items[0].invalid -eq $false) "invalid=$($cart.items[0].invalid)"
Assert-That '带上了规格摘要快照' ($cart.items[0].specDesc -like '*/*') "spec=$($cart.items[0].specDesc)"
Assert-That '失效项数 = 0' ($cart.invalidCount -eq 0) "invalid=$($cart.invalidCount)"

# =====================================================================
Write-Host "`n=========== 3. 改数量 / 勾选 / 删除 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Method PUT -Path '/api/app/cart/quantity' -Token $tokenA -Body @{ id=$cartItem1; quantity=10 }
Assert-That '修改数量为 10 → 成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$r = Invoke-Api -Path '/api/app/cart/list' -Token $tokenA
Assert-That '数量已更新' ($r.Json.data.items[0].quantity -eq 10) "qty=$($r.Json.data.items[0].quantity)"

$r = Invoke-Api -Method PUT -Path '/api/app/cart/quantity' -Token $tokenA -Body @{ id=$cartItem1; quantity=150 }
Assert-That '改成超过库存的数量（150 > 可售 100）→ 2003' ($r.Json.code -eq 2003) "code=$($r.Json.code) msg=$($r.Json.message)"

$r = Invoke-Api -Method PUT -Path '/api/app/cart/selected' -Token $tokenA -Body @{ ids=@(); selected=0 }
Assert-That '取消全选 → 成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$r = Invoke-Api -Path '/api/app/cart/list' -Token $tokenA
Assert-That '取消全选后 selectedCount=0' ($r.Json.data.selectedCount -eq 0) "count=$($r.Json.data.selectedCount)"
Assert-That '取消全选后结算金额=0' ($r.Json.data.selectedAmount -eq 0) "amount=$($r.Json.data.selectedAmount)"
Assert-That '总数仍在（全选不影响条目）' ($r.Json.data.totalCount -eq 1) "count=$($r.Json.data.totalCount)"

$r = Invoke-Api -Method PUT -Path '/api/app/cart/selected' -Token $tokenA -Body @{ ids=@(); selected=1 }
Assert-That '全选 → 成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$r = Invoke-Api -Path '/api/app/cart/list' -Token $tokenA
Assert-That '全选后 selectedCount=1' ($r.Json.data.selectedCount -eq 1) "count=$($r.Json.data.selectedCount)"

# =====================================================================
Write-Host "`n=========== 4. ⭐ 购物车水平越权防护 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Path '/api/app/cart/list' -Token $tokenB
Assert-That 'B 的购物车为空（看不到 A 的）' ($r.Json.data.totalCount -eq 0) "count=$($r.Json.data.totalCount)"

$r = Invoke-Api -Method PUT -Path '/api/app/cart/quantity' -Token $tokenB -Body @{ id=$cartItem1; quantity=1 }
Assert-That 'B 改 A 的购物车数量 → 拒绝' ($r.Json.code -ne 200) "code=$($r.Json.code) msg=$($r.Json.message)"

$r = Invoke-Api -Method DELETE -Path "/api/app/cart/remove?ids=$cartItem1" -Token $tokenB
Assert-That 'B 删 A 的购物车项 → 返回成功但影响 0 行' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$stillThere = Invoke-Sql "SELECT COUNT(*) FROM oms_cart_item WHERE id=$cartItem1"
Assert-That 'DB: A 的购物车项仍在（删除带了 user_id 条件）' ($stillThere[0] -eq '1') "count=$($stillThere[0])"

# =====================================================================
Write-Host "`n=========== 5. 删除与清理 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Method PUT -Path '/api/app/cart/quantity' -Token $tokenA -Body @{ id=$cartItem1; quantity=0 }
Assert-That '数量改为 0 → 等价删除' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$r = Invoke-Api -Path '/api/app/cart/list' -Token $tokenA
Assert-That '删除后购物车为空' ($r.Json.data.totalCount -eq 0) "count=$($r.Json.data.totalCount)"

$r = Invoke-Api -Method POST -Path '/api/app/cart/add' -Token $tokenA -Body @{ skuId=1; quantity=1 }
$r = Invoke-Api -Method POST -Path '/api/app/cart/add' -Token $tokenA -Body @{ skuId=2; quantity=1 }
$r = Invoke-Api -Path '/api/app/cart/count' -Token $tokenA
Assert-That '购物车种类数 = 2' ($r.Json.data -eq 2) "count=$($r.Json.data)"
$ids = (Invoke-Sql "SELECT GROUP_CONCAT(id) FROM oms_cart_item WHERE user_id=$userAId")[0]
$r = Invoke-Api -Method DELETE -Path "/api/app/cart/remove?ids=$ids" -Token $tokenA
Assert-That '批量删除成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$r = Invoke-Api -Path '/api/app/cart/count' -Token $tokenA
Assert-That '批量删除后为空' ($r.Json.data -eq 0) "count=$($r.Json.data)"

# 清理验收造的下架商品
Invoke-Api -Method DELETE -Path "/api/admin/product/spu/$($offlineSpu.Json.data)" -Token $adminToken | Out-Null

# =====================================================================
Write-Host "`n=========== 6. ⭐ 库存四操作的 SQL 语义（防超卖核心）===========" -ForegroundColor Cyan
$sqlFile = Join-Path $RepoRoot 'sql\verify-stock-ops.sql'
$out = cmd /c "mysql -uroot -p123456 --default-character-set=utf8mb4 shopping_mall < `"$sqlFile`" 2>&1"
$lines = @($out | Where-Object { $_ -notmatch 'Using a password' })
$checks = @($lines | Where-Object { $_ -like 'CHECK|*' })
Assert-That '库存语义检查有输出（脚本被正确执行）' ($checks.Count -ge 15) "checks=$($checks.Count)"
$failed = @($checks | Where-Object { $_ -like '*|FAIL' })
Assert-That '库存四操作的全部语义检查通过（无 FAIL）' ($failed.Count -eq 0) ($failed -join ' ; ')
foreach ($c in $checks) { Write-Host ("    {0}" -f $c) -ForegroundColor DarkGray }

$exitCode = Show-TestSummary
exit $exitCode
