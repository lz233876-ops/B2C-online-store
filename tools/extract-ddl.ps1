# =====================================================================
#  extract-ddl.ps1 — 从设计文档抽取建表脚本
#
#  为什么要有这个脚本：
#    docs/04-数据库设计.md 是数据库设计的【唯一事实源】。
#    41 张表的 DDL 手抄一遍必然出错（漏字段、漏索引、写错注释）。
#    所以建表脚本一律从文档【生成】，而不是手工维护两份。
#
#  用法：
#    pwsh -NoProfile -File tools/extract-ddl.ps1
#
#  输出（遵循 docs/04 §9.1 的文件组织规范）：
#    sql/schema/V1.0.*__schema_*.sql   8 个文件，共 41 张表
#    sql/data/V1.0.8 ~ V1.1.1__init_*.sql  4 个文件
#    sql/init.sql                       一键初始化
# =====================================================================

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$docPath  = Join-Path $repoRoot 'docs\04-数据库设计.md'
$sqlRoot  = Join-Path $repoRoot 'sql'

if (-not (Test-Path -LiteralPath $docPath)) { throw "找不到设计文档：$docPath" }

$lines = Get-Content -LiteralPath $docPath -Encoding UTF8
Write-Host "读取 $docPath （$($lines.Count) 行）"

# ---------- 1. 切分所有 ```sql 代码块，并记录每个块最近的上方标题 ----------
$blocks = New-Object System.Collections.Generic.List[object]
$inBlock = $false
$startLine = 0
$buffer = New-Object System.Collections.Generic.List[string]

for ($i = 0; $i -lt $lines.Count; $i++) {
    $line = $lines[$i]
    if (-not $inBlock -and $line -match '^```sql\s*$') {
        $inBlock = $true
        $startLine = $i + 2          # 1-based，且跳过 fence 本身
        $buffer.Clear()
        continue
    }
    if ($inBlock -and $line -match '^```\s*$') {
        $inBlock = $false
        # 向上找最近的标题（##/###/####）
        $heading = ''
        for ($j = $startLine - 2; $j -ge 0; $j--) {
            if ($lines[$j] -match '^#{2,4}\s+(.+?)\s*$') { $heading = $Matches[1]; break }
        }
        $blocks.Add([pscustomobject]@{
            StartLine = $startLine
            EndLine   = $i + 1
            Heading   = $heading
            Text      = (($buffer -join "`n").Trim())
        })
        continue
    }
    if ($inBlock) { $buffer.Add($line) }
}

Write-Host "共发现 $($blocks.Count) 个 sql 代码块"

# ---------- 2. 文件映射（严格对应 docs/04 §9.1） ----------
$schemaFiles = [ordered]@{
    '3.1' = @{ Name = 'V1.0.0__schema_phase1_system.sql';       Desc = 'Phase 1 · system 模块（9 张 sys_* + sys_file）' }
    '3.2' = @{ Name = 'V1.0.1__schema_phase1_user.sql';         Desc = 'Phase 1 · user + auth 模块（4 张 ums_*）' }
    '3.3' = @{ Name = 'V1.0.2__schema_phase1_product.sql';      Desc = 'Phase 1 · product 模块（9 张 pms_*）' }
    '3.4' = @{ Name = 'V1.0.3__schema_phase2_cart_stock.sql';   Desc = 'Phase 2 · cart + inventory 模块（oms_cart_item / wms_*）' }
    '3.5' = @{ Name = 'V1.0.4__schema_phase2_trade.sql';        Desc = 'Phase 2 · trade 模块（4 张 oms_*）' }
    '3.6' = @{ Name = 'V1.0.5__schema_phase2_payment.sql';      Desc = 'Phase 2 · payment 模块 + 基础设施（3 张 pay_* + sys_message）' }
    '3.7' = @{ Name = 'V1.0.6__schema_phase3_promotion.sql';    Desc = 'Phase 3 · promotion 模块（5 张 sms_*）' }
    '3.8' = @{ Name = 'V1.0.7__schema_phase4_merchant.sql';     Desc = 'Phase 4 · merchant 模块（3 张 mms_*，选做）' }
}

$dataFiles = [ordered]@{
    '8.1' = @{ Name = 'V1.0.8__init_system.sql';        Desc = '初始化 · 默认管理员与角色';       Order = 1 }
    '8.2' = @{ Name = 'V1.0.8__init_system.sql';        Desc = '初始化 · 菜单与权限点';           Order = 1 }
    '8.5' = @{ Name = 'V1.0.8__init_system.sql';        Desc = '初始化 · 字典数据';               Order = 1 }
    '8.3' = @{ Name = 'V1.0.9__init_merchant.sql';      Desc = '初始化 · 默认商家 merchant_id=1'; Order = 2 }
    '8.4' = @{ Name = 'V1.1.0__init_member_level.sql';  Desc = '初始化 · 会员等级';               Order = 3 }
    '8.6' = @{ Name = 'V1.1.1__init_product_demo.sql';  Desc = '演示数据 · C 端会员';             Order = 4 }
    '8.7' = @{ Name = 'V1.1.1__init_product_demo.sql';  Desc = '演示数据 · 自营商品（SPU/SKU）';  Order = 4 }
}

function Get-SectionKey([string]$heading) {
    if ($heading -match '^(3\.\d+|8\.\d+)') { return $Matches[1] }
    return $null
}

# ---------- 3. 归集 ----------
$schemaBuckets = @{}
foreach ($k in $schemaFiles.Keys) { $schemaBuckets[$k] = New-Object System.Collections.Generic.List[string] }
$dataBuckets = @{}
foreach ($k in ($dataFiles.Keys | Select-Object -Unique)) { $dataBuckets[$k] = New-Object System.Collections.Generic.List[string] }

$schemaCount = 0
$dataCount = 0
$skipped = New-Object System.Collections.Generic.List[string]

foreach ($b in $blocks) {
    $key = Get-SectionKey $b.Heading
    if ($b.Text -match '(?m)^\s*CREATE\s+TABLE') {
        if (-not $key -or -not $schemaFiles.Contains($key)) {
            throw "第 $($b.StartLine) 行的 CREATE TABLE 无法归属到章节，标题为：'$($b.Heading)'"
        }
        $schemaBuckets[$key].Add($b.Text)
        $schemaCount++
    }
    elseif ($key -and $dataFiles.Contains($key)) {
        $dataBuckets[$key].Add($b.Text)
        $dataCount++
    }
    else {
        $skipped.Add("L$($b.StartLine) [$($b.Heading)]")
    }
}

# ---------- 4. 断言，防止文档改动后静默生成残缺脚本 ----------
if ($schemaCount -ne 41) { throw "预期 41 张表，实际抽到 $schemaCount 张 —— 请检查 docs/04 的章节编号是否被改动" }
if ($dataCount -ne 7)   { throw "预期 7 个初始化数据块，实际抽到 $dataCount 个" }

# ---------- 5. 写文件 ----------
function Write-SqlFile([string]$dir, [string]$name, [string]$title, [string]$body, [switch]$IsSchema) {
    $path = Join-Path $dir $name
    $header = @()
    $header += "-- ====================================================================="
    $header += "--  $title"
    $header += "--  ⚠️ 本文件由 tools/extract-ddl.ps1 从 docs/04-数据库设计.md 自动生成，请勿手工修改。"
    $header += "--     要改表结构，先改设计文档，再重新生成。"
    $header += "--  目标：MySQL 8.0 / InnoDB / utf8mb4_0900_ai_ci"
    if ($IsSchema) {
        $header += "--  ⚠️ 含 DROP TABLE IF EXISTS，【仅供开发环境反复执行】。"
        $header += "--     生产脚本必须去掉 DROP、改用 CREATE TABLE IF NOT EXISTS（见 docs/04 §9.1 原则 3）。"
    }
    $header += "-- ====================================================================="
    $header += ""
    $content = (($header -join "`n") + $body + "`n")
    # 统一用 UTF-8 无 BOM，避免 mysql 客户端把 BOM 当成语句的一部分而报语法错
    [System.IO.File]::WriteAllText($path, $content, (New-Object System.Text.UTF8Encoding($false)))
    return $path
}

$schemaDir = Join-Path $sqlRoot 'schema'
$dataDir   = Join-Path $sqlRoot 'data'
foreach ($d in @($schemaDir, $dataDir, (Join-Path $sqlRoot 'migration'), (Join-Path $sqlRoot 'rollback'))) {
    if (-not (Test-Path -LiteralPath $d)) { New-Item -ItemType Directory -Path $d -Force | Out-Null }
}

$manifest = New-Object System.Collections.Generic.List[object]

foreach ($k in $schemaFiles.Keys) {
    $meta = $schemaFiles[$k]
    $body = ($schemaBuckets[$k] -join "`n`n")
    $tableCount = ([regex]::Matches($body, '(?m)^\s*CREATE\s+TABLE')).Count
    if ($tableCount -eq 0) { throw "章节 $k 没有抽到表，但映射表里声明了它" }
    $p = Write-SqlFile -dir $schemaDir -name $meta.Name -title $meta.Desc -body $body -IsSchema
    $manifest.Add([pscustomobject]@{ Section = $k; File = "schema/$($meta.Name)"; Tables = $tableCount })
}

# 数据文件按 Order 排序后再合并（8.1/8.2/8.5 要按文档顺序拼进同一个文件）
foreach ($name in ($dataFiles.Values | ForEach-Object { $_.Name } | Select-Object -Unique | Sort-Object)) {
    $keys = $dataFiles.Keys | Where-Object { $dataFiles[$_].Name -eq $name }
    $titles = ($keys | ForEach-Object { $dataFiles[$_].Desc }) -join ' + '
    $body = (($keys | ForEach-Object { $dataBuckets[$_] }) | Where-Object { $_ } | ForEach-Object { $_ -join "`n`n" }) -join "`n`n"
    $cnt = ([regex]::Matches($body, '(?m)^\s*INSERT\s+INTO')).Count
    $p = Write-SqlFile -dir $dataDir -name $name -title ("初始化数据 · " + $titles) -body $body
    $manifest.Add([pscustomobject]@{ Section = ($keys -join ','); File = "data/$name"; Tables = $cnt })
}

# ---------- 6. 生成 init.sql ----------
$initBody = @()
$initBody += "DROP DATABASE IF EXISTS ``shopping_mall``;"
$initBody += "CREATE DATABASE ``shopping_mall``"
$initBody += "  DEFAULT CHARACTER SET utf8mb4"
$initBody += "  DEFAULT COLLATE utf8mb4_0900_ai_ci;"
$initBody += "USE ``shopping_mall``;"
$initBody += ""
foreach ($k in $schemaFiles.Keys) {
    $initBody += "SOURCE sql/schema/$($schemaFiles[$k].Name);"
}
$initBody += ""
$initBody += "-- 数据顺序不能乱：商家 -> 权限 -> 等级 -> 演示数据"
foreach ($name in ($dataFiles.Values | ForEach-Object { $_.Name } | Select-Object -Unique | Sort-Object)) {
    $comment = if ($name -eq 'V1.1.1__init_product_demo.sql') { '  -- 演示数据，按需开启' } else { '' }
    $prefix  = if ($name -eq 'V1.1.1__init_product_demo.sql') { '-- ' } else { '' }
    $initBody += "$prefix" + "SOURCE sql/data/$name;$comment"
}
Write-SqlFile -dir $sqlRoot -name 'init.sql' `
    -title '本地开发环境一键初始化（⚠️ 会 DROP DATABASE，仅供开发环境！）' `
    -body ((@(
        "-- 用法（必须在项目根目录执行，因为 SOURCE 的路径相对客户端工作目录）：",
        "--   mysql -uroot -p --default-character-set=utf8mb4 < sql/init.sql",
        ""
    ) + $initBody) -join "`n") | Out-Null

# ---------- 7. 报告 ----------
Write-Host ""
Write-Host "=== 生成清单 ===" -ForegroundColor Cyan
$manifest | Format-Table -AutoSize | Out-String | Write-Host
Write-Host "建表脚本合计表数 : $(($manifest | Where-Object { $_.File -like 'schema/*' } | Measure-Object -Property Tables -Sum).Sum)"
Write-Host "数据脚本语句合计 : $(($manifest | Where-Object { $_.File -like 'data/*' } | Measure-Object -Property Tables -Sum).Sum)"
Write-Host ""
Write-Host "=== 未纳入生成的文件（讲解用示例，符合预期）===" -ForegroundColor DarkGray
Write-Host ("共 {0} 个 sql 块被跳过" -f $skipped.Count)
