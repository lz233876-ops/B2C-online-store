# =====================================================================
#  verify-auth.ps1 — Phase 1 认证授权与 RBAC 端到端验收
#
#  前置：
#    1. 数据库已初始化（sql/init.sql + sql/data/V1.1.1 + sql/data/dev_rbac_test_users.sql）
#    2. 应用已启动在 http://localhost:8080
#
#  用法：
#    Set-ExecutionPolicy -Scope Process Bypass -Force
#    & tools\verify-auth.ps1
#
#  为什么要有这个脚本：
#    「登录能用」这种断言只有在真的发请求时才知道真假。
#    本脚本覆盖了正常路径、异常路径和权限边界，可作为 Phase 1 的验收依据，
#    也是回归测试——以后动 SecurityConfig 时跑一遍就知道有没有破坏什么。
# =====================================================================

$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$BaseUrl = 'http://localhost:8080'
$script:Pass = 0
$script:Fail = 0
$script:Failures = @()

function Invoke-Api {
    param(
        [string]$Method = 'GET',
        [string]$Path,
        $Body = $null,
        [string]$Token = $null
    )
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }

    $params = @{
        Uri             = "$BaseUrl$Path"
        Method          = $Method
        Headers         = $headers
        UseBasicParsing = $true
        TimeoutSec      = 15
    }
    if ($null -ne $Body) {
        $params['Body'] = ($Body | ConvertTo-Json -Compress)
        $params['ContentType'] = 'application/json'
    }

    try {
        $resp = Invoke-WebRequest @params
        return [pscustomobject]@{ Status = [int]$resp.StatusCode; Json = ($resp.Content | ConvertFrom-Json) }
    }
    catch {
        $webResp = $_.Exception.Response
        $status = if ($null -ne $webResp) { [int]$webResp.StatusCode } else { 0 }

        # PowerShell 5.1 对非 2xx 响应会把响应体放进 ErrorDetails.Message；
        # 直接读 GetResponseStream() 有时读到空（流已被上层消费）。两条路都试。
        $content = $null
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            $content = $_.ErrorDetails.Message
        }
        if (-not $content -and $null -ne $webResp) {
            try {
                $stream = $webResp.GetResponseStream()
                if ($null -ne $stream) {
                    $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
                    $content = $reader.ReadToEnd()
                    $reader.Close()
                }
            }
            catch { }
        }

        $json = $null
        if ($content) {
            try { $json = $content | ConvertFrom-Json } catch { }
        }
        return [pscustomobject]@{ Status = $status; Json = $json; Raw = $content }
    }
}

function Assert-That {
    param([string]$Name, [bool]$Condition, [string]$Detail = '')
    if ($Condition) {
        $script:Pass++
        Write-Host ("  [PASS] {0}" -f $Name) -ForegroundColor Green
    }
    else {
        $script:Fail++
        $script:Failures += $Name
        Write-Host ("  [FAIL] {0}  {1}" -f $Name, $Detail) -ForegroundColor Red
    }
}

# =====================================================================
Write-Host "`n=========== 0. 服务就绪检查 ===========" -ForegroundColor Cyan
$ready = $false
for ($i = 1; $i -le 15; $i++) {
    $h = Invoke-Api -Path '/api/app/health'
    if ($h.Status -eq 200) { $ready = $true; break }
    Start-Sleep -Seconds 4
}
if (-not $ready) { Write-Host '服务未就绪，终止验收' -ForegroundColor Red; exit 1 }
Write-Host "  服务已就绪"

# =====================================================================
Write-Host "`n=========== 1. 登录（正常与异常路径）===========" -ForegroundColor Cyan

$r = Invoke-Api -Method POST -Path '/api/admin/auth/login' -Body @{ username = 'admin'; password = 'admin123' }
Assert-That '超管登录成功 (admin/admin123)' ($r.Status -eq 200 -and $r.Json.code -eq 200) "status=$($r.Status) code=$($r.Json.code) msg=$($r.Json.message)"
$adminAccess = $r.Json.data.accessToken
$adminRefresh = $r.Json.data.refreshToken
Assert-That '返回了 accessToken 与 refreshToken' ($adminAccess -and $adminRefresh)
Assert-That 'accessToken 有效期 1800 秒' ($r.Json.data.expiresIn -eq 1800) "expiresIn=$($r.Json.data.expiresIn)"

$r = Invoke-Api -Method POST -Path '/api/admin/auth/login' -Body @{ username = 'admin'; password = 'wrong-password' }
Assert-That '密码错误返回业务码 1001' ($r.Json.code -eq 1001) "code=$($r.Json.code)"

$r = Invoke-Api -Method POST -Path '/api/admin/auth/login' -Body @{ username = 'no-such-user-xyz'; password = 'admin123' }
Assert-That '用户不存在也返回 1001（防用户名枚举，与密码错误不可区分）' ($r.Json.code -eq 1001) "code=$($r.Json.code)"

$r = Invoke-Api -Method POST -Path '/api/admin/auth/login' -Body @{ username = 'disabled'; password = 'admin123' }
Assert-That '已禁用账号返回业务码 1002' ($r.Json.code -eq 1002) "code=$($r.Json.code)"

$r = Invoke-Api -Method POST -Path '/api/admin/auth/login' -Body @{ username = 'admin'; password = '1' }
Assert-That '参数校验：密码过短返回 400' ($r.Json.code -eq 400) "code=$($r.Json.code)"

# =====================================================================
Write-Host "`n=========== 2. 令牌校验与当前用户信息 ===========" -ForegroundColor Cyan

$r = Invoke-Api -Path '/api/admin/auth/me' -Token $adminAccess
Assert-That '/me 返回超管信息' ($r.Json.code -eq 200 -and $r.Json.data.username -eq 'admin') "code=$($r.Json.code)"
Assert-That '角色为 SUPER_ADMIN' ($r.Json.data.roles -contains 'SUPER_ADMIN')
# 33 = 38 个菜单授权节点 - 5 个目录（目录没有 perms，不构成权限点）
Assert-That '权限数量为 33（38 个授权节点减去 5 个无 perms 的目录）' ($r.Json.data.perms.Count -eq 33) "perms=$($r.Json.data.perms.Count)"
Assert-That '手机号已脱敏（含 ****）' ($r.Json.data.phone -and $r.Json.data.phone.Contains('****')) "phone=$($r.Json.data.phone)"
Assert-That '响应中不含 password 字段' ($null -eq $r.Json.data.password)

$r = Invoke-Api -Path '/api/admin/auth/me'
Assert-That '不带令牌访问 /me 返回 HTTP 401' ($r.Status -eq 401) "status=$($r.Status)"
Assert-That '401 响应体是统一 Result 结构' ($r.Json.code -eq 401) "code=$($r.Json.code)"

$r = Invoke-Api -Path '/api/admin/auth/me' -Token 'this.is.not.a.jwt'
Assert-That '伪造令牌返回 401 + 业务码 1004' ($r.Status -eq 401 -and $r.Json.code -eq 1004) "status=$($r.Status) code=$($r.Json.code)"

# =====================================================================
Write-Host "`n=========== 3. 动态路由与 RBAC 权限 ===========" -ForegroundColor Cyan

$r = Invoke-Api -Path '/api/admin/auth/menus' -Token $adminAccess
Assert-That '超管菜单树非空' ($r.Json.data.Count -gt 0) "roots=$($r.Json.data.Count)"
$childTotal = ($r.Json.data | ForEach-Object { $_.children.Count } | Measure-Object -Sum).Sum
Assert-That '菜单树是嵌套结构（有子节点）' ($childTotal -gt 0) "children=$childTotal"
Assert-That '菜单树不含按钮类型(3)' (@($r.Json.data | Where-Object { $_.menuType -eq 3 }).Count -eq 0)

$r = Invoke-Api -Path '/api/admin/system/user/page?current=1&size=5' -Token $adminAccess
Assert-That '超管访问 system:user:list 成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
Assert-That '分页返回 total=4' ($r.Json.data.total -eq 4) "total=$($r.Json.data.total)"
Assert-That '分页每页 5 条生效' ($r.Json.data.records.Count -le 5)
if ($r.Json.data.records.Count -gt 0) {
    # 不能用 records[0] 断言：列表按 create_time 排序，而初始化数据的时间几乎相同，
    # 排序不稳定，records[0] 可能正好是没填手机号的账号。所以检查【全部】有值的手机号。
    $phones = @($r.Json.data.records | Where-Object { $_.phone } | ForEach-Object { $_.phone })
    $unmasked = @($phones | Where-Object { -not $_.Contains('****') })
    Assert-That '列表项手机号全部已脱敏' (($phones.Count -gt 0) -and ($unmasked.Count -eq 0)) "phones=$($phones -join ' | ')"
    Assert-That '列表项不含 password' ($null -eq $r.Json.data.records[0].password)
}

# operator：有 OPERATOR 角色但没有 system:* 权限 → 必须 403
$r = Invoke-Api -Method POST -Path '/api/admin/auth/login' -Body @{ username = 'operator'; password = 'admin123' }
Assert-That 'operator 登录成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$opAccess = $r.Json.data.accessToken

$r = Invoke-Api -Path '/api/admin/auth/me' -Token $opAccess
Assert-That 'operator 角色为 OPERATOR' ($r.Json.data.roles -contains 'OPERATOR')
# 19 = 22 个菜单授权节点 - 3 个目录
Assert-That 'operator 权限数为 19（且不含任何 system:* ）' ($r.Json.data.perms.Count -eq 19) "perms=$($r.Json.data.perms.Count)"
Assert-That 'operator 确实没有 system:* 权限' (@($r.Json.data.perms | Where-Object { $_ -like 'system:*' }).Count -eq 0)

$r = Invoke-Api -Path '/api/admin/system/user/page' -Token $opAccess
Assert-That 'operator 访问 system:user:list 返回 HTTP 403' ($r.Status -eq 403) "status=$($r.Status)"
Assert-That '403 响应体是统一 Result 结构（未被 500 吞掉）' ($r.Json.code -eq 403) "code=$($r.Json.code)"

# merchant：零权限角色，验证不会空指针
$r = Invoke-Api -Method POST -Path '/api/admin/auth/login' -Body @{ username = 'merchant'; password = 'admin123' }
Assert-That '零权限账号 merchant 仍能登录' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$mcAccess = $r.Json.data.accessToken

$r = Invoke-Api -Path '/api/admin/auth/menus' -Token $mcAccess
Assert-That '零权限账号菜单树为空数组而非 null' ($r.Json.code -eq 200 -and @($r.Json.data).Count -eq 0) "code=$($r.Json.code) count=$(@($r.Json.data).Count)"

$r = Invoke-Api -Path '/api/admin/auth/me' -Token $mcAccess
Assert-That '零权限账号角色为 MERCHANT_ADMIN' ($r.Json.data.roles -contains 'MERCHANT_ADMIN')

# =====================================================================
Write-Host "`n=========== 4. 令牌刷新与轮换 ===========" -ForegroundColor Cyan

$r = Invoke-Api -Method POST -Path '/api/admin/auth/refresh' -Body @{ refreshToken = $adminRefresh }
Assert-That '刷新令牌成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$newAccess = $r.Json.data.accessToken
$newRefresh = $r.Json.data.refreshToken
Assert-That '刷新后返回了新的 accessToken' ($newAccess -ne $adminAccess)
Assert-That '刷新后 refreshToken 已轮换（与旧的不同）' ($newRefresh -ne $adminRefresh)

$r = Invoke-Api -Path '/api/admin/auth/me' -Token $newAccess
Assert-That '新 accessToken 可用' ($r.Json.code -eq 200) "code=$($r.Json.code)"

$r = Invoke-Api -Method POST -Path '/api/admin/auth/refresh' -Body @{ refreshToken = $adminRefresh }
Assert-That '重复使用旧 refreshToken 被拒绝（轮换生效）' ($r.Json.code -ne 200) "code=$($r.Json.code)"

# =====================================================================
Write-Host "`n=========== 5. 登出与黑名单 ===========" -ForegroundColor Cyan

$r = Invoke-Api -Method POST -Path '/api/admin/auth/logout' -Token $newAccess
Assert-That '登出成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"

$r = Invoke-Api -Path '/api/admin/auth/me' -Token $newAccess
Assert-That '登出后原 accessToken 立即失效（黑名单生效）' ($r.Status -eq 401) "status=$($r.Status)"

$r = Invoke-Api -Method POST -Path '/api/admin/auth/refresh' -Body @{ refreshToken = $newRefresh }
Assert-That '登出后 refreshToken 也失效' ($r.Json.code -ne 200) "code=$($r.Json.code)"

# 其他用户的会话不应被波及
$r = Invoke-Api -Path '/api/admin/auth/me' -Token $opAccess
Assert-That '登出 admin 不影响 operator 的会话' ($r.Json.code -eq 200) "code=$($r.Json.code)"

# =====================================================================
Write-Host "`n=========== 6. 免认证白名单 ===========" -ForegroundColor Cyan

$r = Invoke-Api -Path '/api/app/health'
Assert-That '/api/app/health 免认证可访问' ($r.Json.code -eq 200)

$r = Invoke-Api -Path '/actuator/health'
Assert-That '/actuator/health 免认证可访问' ($r.Status -eq 200) "status=$($r.Status)"

$r = Invoke-Api -Path '/api/nonexistent-path-xyz'
Assert-That '不存在的路径返回 404 而不是 401（/error 已放行）' ($r.Status -eq 404 -or $r.Status -eq 200) "status=$($r.Status)"

# =====================================================================
Write-Host "`n=========== 验收结果 ===========" -ForegroundColor Cyan
Write-Host ("  通过 {0} 项，失败 {1} 项" -f $script:Pass, $script:Fail) -ForegroundColor $(if ($script:Fail -eq 0) { 'Green' } else { 'Red' })
if ($script:Fail -gt 0) {
    Write-Host "`n  失败清单：" -ForegroundColor Red
    $script:Failures | ForEach-Object { Write-Host "    - $_" -ForegroundColor Red }
}
exit $(if ($script:Fail -eq 0) { 0 } else { 1 })
