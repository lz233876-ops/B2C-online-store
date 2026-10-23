# =====================================================================
#  verify-app.ps1 — C 端会员体系验收（会员认证 + 收货地址 + 两条链隔离）
#
#  前置：
#    1. 数据库已初始化（sql/init.sql + V1.1.1 演示数据 + dev_rbac_test_users.sql）
#    2. 应用已启动在 http://localhost:8080
#
#  用法：
#    Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
#    & tools\verify-app.ps1
#
#  ⚠️ 本文件含中文，必须存成「UTF-8 带 BOM」。
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

Reset-TestState
if (-not (Wait-ServiceReady)) { exit 1 }

# ---------------------------------------------------------------------
# 开场自清理：删掉本脚本注册的验收会员（手机号前缀 137 段），保证可反复运行
# ---------------------------------------------------------------------
$cleanupPhones = @('13700000001', '13700000002')
foreach ($p in $cleanupPhones) {
    Invoke-Sql "DELETE FROM ums_user_address WHERE user_id IN (SELECT id FROM ums_user WHERE phone='$p');" | Out-Null
    # ⚠️ 用物理删除而不是「逻辑删除 + 改名」：ums_user.phone 是 varchar(20)，
    #    而 LEFT(phone,10)+'_del_'+19位雪花ID = 34 字符，会触发「数据过长」错误
    #    导致清理静默失败、后续注册撞重复。测试夹具不需要保留历史。
    Invoke-Sql "DELETE FROM ums_user WHERE phone='$p';" | Out-Null
}

# =====================================================================
Write-Host "`n=========== 1. 会员注册 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Method POST -Path '/api/app/auth/register' -Body @{ phone='13700000001'; password='shop123456'; nickname='验收会员A' }
Assert-That '注册成功' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
$userAId = $r.Json.data

$r = Invoke-Api -Method POST -Path '/api/app/auth/register' -Body @{ phone='13700000001'; password='shop123456' }
Assert-That '同手机号重复注册 → 1006' ($r.Json.code -eq 1006) "code=$($r.Json.code) msg=$($r.Json.message)"

$r = Invoke-Api -Method POST -Path '/api/app/auth/register' -Body @{ phone='12345'; password='shop123456' }
Assert-That '手机号格式非法 → 400（参数校验）' ($r.Json.code -eq 400) "code=$($r.Json.code)"

$r = Invoke-Api -Method POST -Path '/api/app/auth/register' -Body @{ phone='13700000003'; password='123' }
Assert-That '密码过短 → 400（参数校验）' ($r.Json.code -eq 400) "code=$($r.Json.code)"

$dbPwd = Invoke-Sql "SELECT password FROM ums_user WHERE id=$userAId"
Assert-That 'DB: 密码是 BCrypt 哈希（$2a$ 开头），不是明文' ($dbPwd[0] -like '$2a$*') "pwd=$($dbPwd[0])"
Assert-That 'DB: 密码哈希长度 60' ($dbPwd[0].Length -eq 60) "len=$($dbPwd[0].Length)"
Assert-That 'DB: 新会员默认等级=1' ((Invoke-Sql "SELECT member_level_id FROM ums_user WHERE id=$userAId")[0] -eq '1')

# =====================================================================
Write-Host "`n=========== 2. 会员登录 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Method POST -Path '/api/app/auth/login' -Body @{ phone='13900000001'; password='123456' }
Assert-That '演示会员登录成功（testuser/123456）' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
$demoToken = $r.Json.data.accessToken
$demoRefresh = $r.Json.data.refreshToken

$r = Invoke-Api -Method POST -Path '/api/app/auth/login' -Body @{ phone='13700000001'; password='shop123456' }
Assert-That '新注册会员登录成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$tokenA = $r.Json.data.accessToken
$refreshA = $r.Json.data.refreshToken

$r = Invoke-Api -Method POST -Path '/api/app/auth/login' -Body @{ phone='13700000001'; password='wrong-password' }
Assert-That '密码错误 → 1001' ($r.Json.code -eq 1001) "code=$($r.Json.code)"
$r = Invoke-Api -Method POST -Path '/api/app/auth/login' -Body @{ phone='13799999999'; password='shop123456' }
Assert-That '手机号不存在也返回 1001（防手机号枚举）' ($r.Json.code -eq 1001) "code=$($r.Json.code)"

$r = Invoke-Api -Path '/api/app/auth/me' -Token $demoToken
Assert-That '/me 返回会员信息' ($r.Json.code -eq 200) "code=$($r.Json.code)"
Assert-That '手机号已脱敏' ($r.Json.data.phone -and $r.Json.data.phone.Contains('****')) "phone=$($r.Json.data.phone)"

# =====================================================================
Write-Host "`n=========== 3. ⭐ 两条认证链的隔离（本轮重点）===========" -ForegroundColor Cyan
$adminToken = Get-AdminToken

# 3.1 会员令牌打后台接口
$r = Invoke-Api -Path '/api/admin/auth/me' -Token $tokenA
Assert-That '会员令牌访问后台 /auth/me → 401' ($r.Status -eq 401) "status=$($r.Status) code=$($r.Json.code)"
$r = Invoke-Api -Path '/api/admin/product/spu/page' -Token $tokenA
Assert-That '会员令牌访问后台商品接口 → 401' ($r.Status -eq 401) "status=$($r.Status)"

# 3.2 后台令牌打 C 端接口
$r = Invoke-Api -Path '/api/app/auth/me' -Token $adminToken
Assert-That '后台令牌访问 C 端 /auth/me → 401' ($r.Status -eq 401) "status=$($r.Status)"
$r = Invoke-Api -Path '/api/app/address/list' -Token $adminToken
Assert-That '后台令牌访问 C 端地址列表 → 401' ($r.Status -eq 401) "status=$($r.Status)"

# 3.3 跨链刷新
$r = Invoke-Api -Method POST -Path '/api/admin/auth/refresh' -Body @{ refreshToken = $refreshA }
Assert-That 'C 端 refreshToken 拿去后台刷新 → 拒绝' ($r.Json.code -ne 200) "code=$($r.Json.code)"
$adminRefresh = (Invoke-Api -Method POST -Path '/api/admin/auth/login' -Body @{ username='admin'; password='admin123' }).Json.data.refreshToken
$r = Invoke-Api -Method POST -Path '/api/app/auth/refresh' -Body @{ refreshToken = $adminRefresh }
Assert-That '后台 refreshToken 拿去 C 端刷新 → 拒绝（scope 校验生效）' ($r.Json.code -ne 200) "code=$($r.Json.code) msg=$($r.Json.message)"

# 3.4 各自刷新正常
$r = Invoke-Api -Method POST -Path '/api/app/auth/refresh' -Body @{ refreshToken = $refreshA }
Assert-That 'C 端自己的 refreshToken 刷新成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$tokenA2 = $r.Json.data.accessToken
$refreshA2 = $r.Json.data.refreshToken
$r = Invoke-Api -Method POST -Path '/api/app/auth/refresh' -Body @{ refreshToken = $refreshA }
Assert-That 'C 端旧 refreshToken 已轮换，不能重复使用' ($r.Json.code -ne 200) "code=$($r.Json.code)"

# =====================================================================
Write-Host "`n=========== 4. 收货地址 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Path '/api/app/address/list'
Assert-That '不带令牌访问地址列表 → 401' ($r.Status -eq 401) "status=$($r.Status)"

$r = Invoke-Api -Method POST -Path '/api/app/address' -Token $tokenA2 -Body @{
    receiverName='张三'; receiverPhone='13800001111'; province='广东省'; city='深圳市'; district='南山区'
    detailAddress='科技园路 1 号'; postalCode='518000'; tag='公司' }
Assert-That '新增第一条地址成功' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
$addr1 = $r.Json.data

$r = Invoke-Api -Path '/api/app/address/list' -Token $tokenA2
Assert-That '地址列表返回 1 条' ($r.Json.data.Count -eq 1) "count=$($r.Json.data.Count)"
Assert-That '第一条地址自动成为默认地址' ($r.Json.data[0].defaultStatus -eq 1) "default=$($r.Json.data[0].defaultStatus)"
Assert-That '完整地址已拼接' ($r.Json.data[0].fullAddress -eq '广东省深圳市南山区科技园路 1 号') "full=$($r.Json.data[0].fullAddress)"
Assert-That '收货人手机号已脱敏' ($r.Json.data[0].receiverPhone.Contains('****')) "phone=$($r.Json.data[0].receiverPhone)"

$r = Invoke-Api -Method POST -Path '/api/app/address' -Token $tokenA2 -Body @{
    receiverName='李四'; receiverPhone='13800002222'; province='北京市'; city='北京市'; district='朝阳区'
    detailAddress='建国路 88 号'; tag='家'; defaultStatus=1 }
$addr2 = $r.Json.data
$r = Invoke-Api -Path '/api/app/address/list' -Token $tokenA2
Assert-That '新增第二条地址成功' ($r.Json.data.Count -eq 2) "count=$($r.Json.data.Count)"
Assert-That '列表按默认优先排序（默认的排最前）' ($r.Json.data[0].id -eq $addr2) "first=$($r.Json.data[0].id)"
$defaultCount = @($r.Json.data | Where-Object { $_.defaultStatus -eq 1 }).Count
Assert-That 'DB 语义：默认地址有且只有一个' ($defaultCount -eq 1) "defaultCount=$defaultCount"

$r = Invoke-Api -Method PUT -Path "/api/app/address/$addr1/default" -Token $tokenA2
Assert-That '切换默认地址成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$r = Invoke-Api -Path '/api/app/address/list' -Token $tokenA2
Assert-That '切换后仍只有一个默认地址' ((@($r.Json.data | Where-Object { $_.defaultStatus -eq 1 }).Count) -eq 1)
Assert-That '默认地址已变为第一条' (($r.Json.data | Where-Object { $_.defaultStatus -eq 1 }).id -eq $addr1)

$r = Invoke-Api -Method PUT -Path '/api/app/address' -Token $tokenA2 -Body @{
    id=$addr1; receiverName='张三丰'; receiverPhone='13800001111'; province='广东省'; city='深圳市'
    district='南山区'; detailAddress='科技园路 2 号'; tag='公司'; defaultStatus=0 }
Assert-That '修改地址成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$r = Invoke-Api -Path "/api/app/address/$addr1" -Token $tokenA2
Assert-That '修改已生效（姓名）' ($r.Json.data.receiverName -eq '张三丰') "name=$($r.Json.data.receiverName)"
Assert-That '默认状态不能通过改传 0 取消（仍是默认）' ($r.Json.data.defaultStatus -eq 1) "default=$($r.Json.data.defaultStatus)"

# 删除默认地址 → 自动提升
$r = Invoke-Api -Method DELETE -Path "/api/app/address/$addr1" -Token $tokenA2
Assert-That '删除默认地址成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$r = Invoke-Api -Path '/api/app/address/list' -Token $tokenA2
Assert-That '删除后剩 1 条' ($r.Json.data.Count -eq 1) "count=$($r.Json.data.Count)"
Assert-That '删除默认后自动把剩余地址提升为默认' ($r.Json.data[0].defaultStatus -eq 1) "default=$($r.Json.data[0].defaultStatus)"

# =====================================================================
Write-Host "`n=========== 5. ⭐ 地址水平越权防护 ===========" -ForegroundColor Cyan
# 再注册一个会员 B，尝试访问 A 的地址
$r = Invoke-Api -Method POST -Path '/api/app/auth/register' -Body @{ phone='13700000002'; password='shop123456'; nickname='验收会员B' }
$r = Invoke-Api -Method POST -Path '/api/app/auth/login' -Body @{ phone='13700000002'; password='shop123456' }
$tokenB = $r.Json.data.accessToken
Assert-That '会员B 登录成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"

$r = Invoke-Api -Path "/api/app/address/$addr2" -Token $tokenB
Assert-That 'B 查 A 的地址 → 3005 地址不存在（不泄漏存在性）' ($r.Json.code -eq 3005) "code=$($r.Json.code) msg=$($r.Json.message)"
$r = Invoke-Api -Path '/api/app/address/list' -Token $tokenB
Assert-That 'B 的地址列表为空（看不到 A 的地址）' (@($r.Json.data).Count -eq 0) "count=$(@($r.Json.data).Count)"
$r = Invoke-Api -Method DELETE -Path "/api/app/address/$addr2" -Token $tokenB
Assert-That 'B 删 A 的地址 → 3005（拒绝）' ($r.Json.code -eq 3005) "code=$($r.Json.code)"
$r = Invoke-Api -Method PUT -Path "/api/app/address/$addr2/default" -Token $tokenB
Assert-That 'B 把 A 的地址设为默认 → 3005（拒绝）' ($r.Json.code -eq 3005) "code=$($r.Json.code)"

# 越权尝试后 A 的数据没被破坏
$r = Invoke-Api -Path '/api/app/address/list' -Token $tokenA2
Assert-That '越权尝试后 A 的地址仍在（未被删除）' ($r.Json.data.Count -eq 1) "count=$($r.Json.data.Count)"

# =====================================================================
Write-Host "`n=========== 6. 登出与黑名单 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Method POST -Path '/api/app/auth/logout' -Token $tokenA2
Assert-That '会员登出成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$r = Invoke-Api -Path '/api/app/auth/me' -Token $tokenA2
Assert-That '登出后 accessToken 立即失效' ($r.Status -eq 401) "status=$($r.Status)"
$r = Invoke-Api -Method POST -Path '/api/app/auth/refresh' -Body @{ refreshToken = $refreshA2 }
Assert-That '登出后 refreshToken 也失效' ($r.Json.code -ne 200) "code=$($r.Json.code)"
$r = Invoke-Api -Path '/api/app/auth/me' -Token $tokenB
Assert-That '登出 A 不影响 B 的会话' ($r.Json.code -eq 200) "code=$($r.Json.code)"

$exitCode = Show-TestSummary
exit $exitCode
