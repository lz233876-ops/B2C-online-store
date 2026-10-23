# =====================================================================
#  ApiTestCommon.ps1 — 验收脚本公共辅助函数
#
#  用法（在其它脚本里 dot-source）：
#    . "$PSScriptRoot\ApiTestCommon.ps1"
#    Reset-TestState
#    Wait-ServiceReady
#    $token = Get-AdminToken
#    Assert-That '断言名' (条件) '失败时的补充信息'
#    Show-TestSummary
#
#  ⚠️ 本文件含中文，必须存成「UTF-8 带 BOM」，否则 Windows PowerShell 5.1
#     会按 GBK 解码，中文全变乱码并导致语法错误（见 docs/02 §5 坑 16）。
# =====================================================================

$script:BaseUrl = if ($env:SHOPPING_TEST_BASE_URL) { $env:SHOPPING_TEST_BASE_URL } else { 'http://localhost:8080' }
$script:Pass = 0
$script:Fail = 0
$script:Failures = @()

function Reset-TestState {
    $script:Pass = 0
    $script:Fail = 0
    $script:Failures = @()
}

<#
   发一个 HTTP 请求，无论成功失败都返回统一的 { Status, Json, Raw } 结构。
   关键点：PowerShell 5.1 对非 2xx 响应会抛异常，响应体在 ErrorDetails.Message 里，
   直接读 GetResponseStream() 有时读到空 —— 两条路都要试，否则永远拿不到 401/403 的响应体。
#>
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
        Uri             = "$script:BaseUrl$Path"
        Method          = $Method
        Headers         = $headers
        UseBasicParsing = $true
        TimeoutSec      = 15
    }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Compress -Depth 10
        # ⚠️ 关键：必须显式把请求体转成 UTF-8 字节，并在 ContentType 里声明 charset。
        #    PowerShell 5.1 的 Invoke-WebRequest 在 ContentType 不含 charset 时，
        #    会按 Latin1/ASCII 编码字符串 —— 中文会全部变成 '?'，
        #    于是「属性值『甲』和『乙』」到服务端都成了 '?'，触发意料之外的「重复」错误。
        #    这个坑会让所有含中文的接口测试产生假象，必须在这里修掉。
        $params['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
        $params['ContentType'] = 'application/json; charset=utf-8'
    }

    try {
        $resp = Invoke-WebRequest @params
        return [pscustomobject]@{ Status = [int]$resp.StatusCode; Json = ($resp.Content | ConvertFrom-Json); Raw = $resp.Content }
    }
    catch {
        $webResp = $_.Exception.Response
        $status = if ($null -ne $webResp) { [int]$webResp.StatusCode } else { 0 }

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

function Wait-ServiceReady {
    param([int]$TimeoutSeconds = 60)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $h = Invoke-Api -Path '/api/app/health'
        if ($h.Status -eq 200) {
            Write-Host "  服务已就绪"
            return $true
        }
        Start-Sleep -Seconds 4
    }
    Write-Host '  服务未就绪' -ForegroundColor Red
    return $false
}

<# 用超管账号登录并返回 accessToken #>
function Get-AdminToken {
    param([string]$Username = 'admin', [string]$Password = 'admin123')
    $r = Invoke-Api -Method POST -Path '/api/admin/auth/login' -Body @{ username = $Username; password = $Password }
    if ($r.Json.code -ne 200) {
        throw "登录失败：$($r.Json.message)"
    }
    return $r.Json.data.accessToken
}

function Show-TestSummary {
    Write-Host "`n=========== 验收结果 ===========" -ForegroundColor Cyan
    $color = if ($script:Fail -eq 0) { 'Green' } else { 'Red' }
    Write-Host ("  通过 {0} 项，失败 {1} 项" -f $script:Pass, $script:Fail) -ForegroundColor $color
    if ($script:Fail -gt 0) {
        Write-Host "`n  失败清单：" -ForegroundColor Red
        $script:Failures | ForEach-Object { Write-Host "    - $_" -ForegroundColor Red }
    }
    return $(if ($script:Fail -eq 0) { 0 } else { 1 })
}
