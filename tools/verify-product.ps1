# =====================================================================
#  verify-product.ps1 — 商品中心验收（类目 / 品牌 / 属性 / SPU+SKU）
#
#  前置：
#    1. 数据库已初始化（sql/init.sql + V1.1.1 演示数据 + dev_rbac_test_users.sql）
#    2. 应用已启动在 http://localhost:8080
#
#  用法：
#    Set-ExecutionPolicy -Scope Process Bypass -Force
#    & tools\verify-product.ps1
#
#  ⚠️ 本文件含中文，必须存成「UTF-8 带 BOM」，否则 PowerShell 5.1 会按 GBK 解码报语法错。
# =====================================================================

$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

. "$PSScriptRoot\ApiTestCommon.ps1"

$MysqlExe = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$MysqlArgs = @('-uroot', '-p123456', '--default-character-set=utf8mb4', '-N', '-B', 'shopping_mall', '-e')

<#
  直接查库，用于校验「API 看不到的」数据库级不变量（流水条数、生成列、冗余汇总）。

  ⚠️ 两个必须注意的 PowerShell 陷阱（我在这里栽过）：
  ① return 会把【单元素数组解包】成裸字符串，于是 $result[0] 取到的是首字符而不是首行
     —— 表现为 price=1、sum=1 这种诡异结果。必须用逗号运算符 `return ,$array` 强制保持数组。
  ② mysql -B 输出用 TAB 分隔多列，PowerShell 只按换行切分，所以一行多列是「a\tb\tc」
     这一个字符串。要取值必须自己 -split "`t"。
#>
function Invoke-Sql {
    param([string]$Query)
    $out = & $MysqlExe @MysqlArgs $Query 2>&1
    $lines = @($out | Where-Object { $_ -notmatch 'Using a password' -and $_ -ne '' })
    return ,$lines
}

<# 取单个标量（第一行整行） #>
function Invoke-SqlScalar {
    param([string]$Query)
    $lines = Invoke-Sql $Query
    if ($lines.Count -eq 0) { return '' }
    return $lines[0]
}

<# 取一行并拆成列数组（多列查询用） #>
function Invoke-SqlRow {
    param([string]$Query)
    $lines = Invoke-Sql $Query
    if ($lines.Count -eq 0) { return ,@() }
    return ,@($lines[0] -split "`t")
}

Reset-TestState
if (-not (Wait-ServiceReady)) { exit 1 }
$t = Get-AdminToken

# ---------------------------------------------------------------------
# 开场自清理：删掉上一轮验收可能残留的夹具商品。
# 为什么需要它：验收脚本必须能【反复运行】。上一轮如果中途失败，
# 收尾的 DELETE 可能没执行到，残留数据会让「种子数据断言」误报失败
# （第一版脚本就因此把 total=1 断言成了 total=2）。
# 用固定的名字前缀识别夹具，不碰真实数据。
# ---------------------------------------------------------------------
$leftovers = Invoke-Api -Path '/api/admin/product/spu/page?current=1&size=50' -Token $t
$removed = 0
foreach ($row in @($leftovers.Json.data.records)) {
    if ($row.spuName -like '验收*' -or $row.spuName -eq '草稿商品' -or $row.spuName -eq '跨类目属性') {
        Invoke-Api -Method DELETE -Path "/api/admin/product/spu/$($row.id)" -Token $t | Out-Null
        $removed++
    }
}
if ($removed -gt 0) { Write-Host "  已清理上一轮残留夹具 $removed 个" -ForegroundColor Yellow }

# =====================================================================
Write-Host "`n=========== 1. 类目 ===========" -ForegroundColor Cyan
$tree = Invoke-Api -Path '/api/admin/product/category/tree' -Token $t
Assert-That '类目树根节点数=1' ($tree.Json.data.Count -eq 1) "count=$($tree.Json.data.Count)"
Assert-That '类目树三层嵌套正确' ($tree.Json.data[0].children[0].children[0].name -eq '智能手机') `
    "deep=$($tree.Json.data[0].children[0].children[0].name)"

$r = Invoke-Api -Method POST -Path '/api/admin/product/category' -Token $t -Body @{ parentId=0; name='验收类目'; sort=99 }
Assert-That '新增一级类目成功' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
$rootId = $r.Json.data
$r = Invoke-Api -Method POST -Path '/api/admin/product/category' -Token $t -Body @{ parentId=$rootId; name='验收子类目'; sort=1 }
$childId = $r.Json.data
Assert-That '新增二级类目成功（level 自动派生）' ($r.Json.code -eq 200) "code=$($r.Json.code)"

$r = Invoke-Api -Method POST -Path '/api/admin/product/category' -Token $t -Body @{ parentId=3; name='第四级'; sort=1 }
Assert-That '超过三级被拒绝（2104）' ($r.Json.code -eq 2104) "code=$($r.Json.code)"
$r = Invoke-Api -Method POST -Path '/api/admin/product/category' -Token $t -Body @{ parentId=0; name='验收类目'; sort=1 }
Assert-That '同级重名被拒绝（2106）' ($r.Json.code -eq 2106) "code=$($r.Json.code)"
$r = Invoke-Api -Method PUT -Path '/api/admin/product/category' -Token $t -Body @{ id=$rootId; parentId=$childId; name='验收类目'; sort=99 }
Assert-That '移动到自己的子类目下被拒绝（2105 防成环）' ($r.Json.code -eq 2105) "code=$($r.Json.code)"
$r = Invoke-Api -Method DELETE -Path '/api/admin/product/category/1' -Token $t
Assert-That '删除有子类目的类目被拒绝（2102）' ($r.Json.code -eq 2102) "code=$($r.Json.code)"
$r = Invoke-Api -Method DELETE -Path '/api/admin/product/category/3' -Token $t
Assert-That '删除有商品的类目被拒绝（2103）' ($r.Json.code -eq 2103) "code=$($r.Json.code)"
$r = Invoke-Api -Method DELETE -Path "/api/admin/product/category/$childId" -Token $t
Assert-That '删除空子类目成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$r = Invoke-Api -Method DELETE -Path "/api/admin/product/category/$rootId" -Token $t
Assert-That '删除空根类目成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"

# =====================================================================
Write-Host "`n=========== 2. 品牌 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Path '/api/admin/product/brand/page?current=1&size=10' -Token $t
Assert-That '品牌分页 total=1' ($r.Json.data.total -eq 1) "total=$($r.Json.data.total)"
$r = Invoke-Api -Method POST -Path '/api/admin/product/brand' -Token $t -Body @{ name='Apple'; nameCn='苹果'; firstLetter='A' }
Assert-That '品牌重名被拒绝（2202）' ($r.Json.code -eq 2202) "code=$($r.Json.code)"
$r = Invoke-Api -Method POST -Path '/api/admin/product/brand' -Token $t -Body @{ name='VerifyBrand'; nameCn='验收品牌'; firstLetter='V' }
Assert-That '新增品牌成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$brandId = $r.Json.data
$r = Invoke-Api -Method POST -Path '/api/admin/product/brand' -Token $t -Body @{ name='Bad'; firstLetter='AB' }
Assert-That '首字母非单字母被参数校验拒绝（400）' ($r.Json.code -eq 400) "code=$($r.Json.code)"
$r = Invoke-Api -Method DELETE -Path '/api/admin/product/brand/1' -Token $t
Assert-That '删除被商品引用的品牌被拒绝（2203）' ($r.Json.code -eq 2203) "code=$($r.Json.code)"

# =====================================================================
Write-Host "`n=========== 3. 属性 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Path '/api/admin/product/attribute/spec?categoryId=3' -Token $t
$specNames = @($r.Json.data | ForEach-Object { $_.name })
Assert-That '类目3的规格属性含「颜色」与「机身内存」（仅 type=1）' `
    (($specNames -contains '颜色') -and ($specNames -contains '机身内存') -and ($specNames -notcontains '上市年份')) `
    "names=$($specNames -join ',')"
$r = Invoke-Api -Method POST -Path '/api/admin/product/attribute' -Token $t -Body @{ categoryId=3; name='无值规格'; type=1 }
Assert-That '规格属性无属性值被拒绝（2302）' ($r.Json.code -eq 2302) "code=$($r.Json.code)"
$r = Invoke-Api -Method DELETE -Path '/api/admin/product/attribute/1' -Token $t
Assert-That '删除被 SKU 引用的属性被拒绝（2303）' ($r.Json.code -eq 2303) "code=$($r.Json.code)"

# =====================================================================
Write-Host "`n=========== 4. SPU 种子数据读取 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Path '/api/admin/product/spu/page?current=1&size=10' -Token $t
Assert-That 'SPU 分页 total=1' ($r.Json.data.total -eq 1) "total=$($r.Json.data.total)"
Assert-That '列表补齐了类目名' ($r.Json.data.records[0].categoryName -eq '智能手机') "name=$($r.Json.data.records[0].categoryName)"
Assert-That '列表补齐了品牌名' ($r.Json.data.records[0].brandName -eq '苹果') "name=$($r.Json.data.records[0].brandName)"
Assert-That '列表带 SKU 数量=4' ($r.Json.data.records[0].skuCount -eq 4) "skuCount=$($r.Json.data.records[0].skuCount)"
Assert-That 'SPU 展示价=最低SKU价(599900)' ($r.Json.data.records[0].price -eq 599900) "price=$($r.Json.data.records[0].price)"
Assert-That 'SPU 总库存=4×100=400' ($r.Json.data.records[0].stock -eq 400) "stock=$($r.Json.data.records[0].stock)"

$d = Invoke-Api -Path '/api/admin/product/spu/1' -Token $t
Assert-That '详情返回 4 个 SKU' ($d.Json.data.skus.Count -eq 4) "count=$($d.Json.data.skus.Count)"
Assert-That '详情返回 2 个规格属性' ($d.Json.data.specAttributes.Count -eq 2) "count=$($d.Json.data.specAttributes.Count)"
Assert-That '详情返回 1 个参数属性' ($d.Json.data.paramAttributes.Count -eq 1) "count=$($d.Json.data.paramAttributes.Count)"
Assert-That '规格属性回填了 valueIds（编辑表单需要）' ($d.Json.data.specAttributes[0].valueIds.Count -eq 2) `
    "valueIds=$($d.Json.data.specAttributes[0].valueIds -join ',')"
Assert-That 'SKU 带 2 个规格值明细' ($d.Json.data.skus[0].specValues.Count -eq 2)
Assert-That 'SKU specDesc 形如「A / B」' ($d.Json.data.skus[0].specDesc -like '* / *') "desc=$($d.Json.data.skus[0].specDesc)"
Assert-That 'SKU 含成本价（后台可见）' ($null -ne $d.Json.data.skus[0].costPrice)

# =====================================================================
Write-Host "`n=========== 5. 新增 SPU（2 规格 × 2 值 = 4 SKU）===========" -ForegroundColor Cyan
$body = @{
    categoryId = 3; brandId = 1; spuName = '验收手机 X1'; subTitle = '2 规格 4 组合'
    unit = '台'; weight = 200; sort = 5; publishStatus = 2; status = 0
    images = @(@{ url='https://cdn.example.com/x1-main.jpg'; type=1; sort=0 },
               @{ url='https://cdn.example.com/x1-b1.jpg';   type=2; sort=1 })
    specAttributes = @(@{ attributeId=1; valueIds=@(1,2); sort=0 },
                       @{ attributeId=2; valueIds=@(3,4); sort=10 })
    paramAttributes = @(@{ attributeId=3; values=@('2025年','全国联保'); sort=100 })
    skus = @(
        @{ price=199900; originalPrice=229900; costPrice=150000; stock=10; status=1; specValues=@(@{attributeId=1;valueId=1},@{attributeId=2;valueId=3}) },
        @{ price=229900; originalPrice=259900; costPrice=170000; stock=20; status=1; specValues=@(@{attributeId=1;valueId=1},@{attributeId=2;valueId=4}) },
        @{ price=199900; originalPrice=229900; costPrice=150000; stock=30; status=1; specValues=@(@{attributeId=1;valueId=2},@{attributeId=2;valueId=3}) },
        @{ price=239900; originalPrice=269900; costPrice=180000; stock=40; status=1; specValues=@(@{attributeId=1;valueId=2},@{attributeId=2;valueId=4}) }
    )
}
$r = Invoke-Api -Method POST -Path '/api/admin/product/spu' -Token $t -Body $body
Assert-That '新增 SPU 成功' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
$spuId = $r.Json.data

$dbSku = Invoke-Sql "SELECT COUNT(*) FROM pms_sku WHERE spu_id=$spuId AND deleted=0"
Assert-That 'DB: 生成 4 条 SKU' ($dbSku[0] -eq '4') "count=$($dbSku[0])"
$dbAv = Invoke-Sql "SELECT COUNT(*) FROM pms_sku_attribute_value WHERE spu_id=$spuId AND deleted=0"
Assert-That 'DB: 生成 8 条 SKU 规格组合（4 SKU × 2 属性）' ($dbAv[0] -eq '8') "count=$($dbAv[0])"
$dbStock = Invoke-Sql "SELECT COUNT(*) FROM wms_stock s JOIN pms_sku k ON k.id=s.sku_id WHERE k.spu_id=$spuId AND s.deleted=0"
Assert-That 'DB: 每个 SKU 都初始化了 wms_stock' ($dbStock[0] -eq '4') "count=$($dbStock[0])"
$dbLog = Invoke-Sql "SELECT COUNT(*) FROM wms_stock_log l JOIN pms_sku k ON k.id=l.sku_id WHERE k.spu_id=$spuId AND l.biz_type=7"
Assert-That 'DB: 写了 4 条入库流水（biz_type=7）' ($dbLog[0] -eq '4') "count=$($dbLog[0])"
$dbTotal = Invoke-SqlRow "SELECT SUM(s.stock), SUM(s.total_in), SUM(s.available_stock) FROM wms_stock s JOIN pms_sku k ON k.id=s.sku_id WHERE k.spu_id=$spuId"
Assert-That 'DB: 库存合计 = 10+20+30+40 = 100' ($dbTotal[0] -eq '100') "sum=$($dbTotal[0])"
Assert-That 'DB: 累计入库 = 100（对账口径成立）' ($dbTotal[1] -eq '100') "total_in=$($dbTotal[1])"
Assert-That 'DB: 生成列 available_stock 合计 = 100' ($dbTotal[2] -eq '100') "available=$($dbTotal[2])"
$dbAgg = Invoke-SqlRow "SELECT price, stock FROM pms_spu WHERE id=$spuId"
Assert-That 'DB: SPU 冗余价 = 最低 SKU 价 199900' ($dbAgg[0] -eq '199900') "price=$($dbAgg[0])"
Assert-That 'DB: SPU 冗余库存 = ΣSKU = 100' ($dbAgg[1] -eq '100') "stock=$($dbAgg[1])"
$dbSpecJson = Invoke-Sql "SELECT spec_json FROM pms_sku WHERE spu_id=$spuId AND price=239900 AND deleted=0"
Assert-That 'DB: spec_json 含颜色/机身内存两个字段' (($dbSpecJson[0] -like '*attributeName*') -and ($dbSpecJson[0] -like '*valueName*')) `
    "json=$($dbSpecJson[0])"

# =====================================================================
Write-Host "`n=========== 6. 新增 SPU 的边界校验 ===========" -ForegroundColor Cyan
$bad = $body.Clone()
$bad.specAttributes = @(@{ attributeId=1; valueIds=@(1) })
$r = Invoke-Api -Method POST -Path '/api/admin/product/spu' -Token $t -Body $bad
Assert-That 'SKU 规格值个数与规格属性数不匹配 → 400' ($r.Json.code -eq 400) "code=$($r.Json.code) msg=$($r.Json.message)"

$bad = $body.Clone()
$bad.skus = @(@{ price=100; specValues=@(@{attributeId=1;valueId=1},@{attributeId=2;valueId=3}) },
              @{ price=200; specValues=@(@{attributeId=1;valueId=1},@{attributeId=2;valueId=3}) })
$r = Invoke-Api -Method POST -Path '/api/admin/product/spu' -Token $t -Body $bad
Assert-That '两个 SKU 组合完全相同 → 400' ($r.Json.code -eq 400) "code=$($r.Json.code) msg=$($r.Json.message)"

$bad = $body.Clone()
$bad.skus = @(@{ price=100; specValues=@(@{attributeId=1;valueId=1},@{attributeId=2;valueId=999}) })
$r = Invoke-Api -Method POST -Path '/api/admin/product/spu' -Token $t -Body $bad
Assert-That '使用未声明的属性值 → 400' ($r.Json.code -eq 400) "code=$($r.Json.code)"

$bad = $body.Clone()
$bad.specAttributes = @(@{ attributeId=3; valueIds=@(1) })   # 属性3 是参数属性
$bad.skus = @(@{ price=100; specValues=@(@{attributeId=3;valueId=1}) })
$r = Invoke-Api -Method POST -Path '/api/admin/product/spu' -Token $t -Body $bad
Assert-That '把参数属性当规格用 → 400' ($r.Json.code -eq 400) "code=$($r.Json.code) msg=$($r.Json.message)"

$bad = @{ categoryId=3; spuName='跨类目属性'; specAttributes=@(@{attributeId=1;valueIds=@(1)}); skus=@(@{price=1;specValues=@(@{attributeId=1;valueId=1})}) }
$r = Invoke-Api -Method POST -Path '/api/admin/product/spu' -Token $t -Body $bad
# 属性1属于类目3，这里类目也是3，所以应当成功；换个不属于的类目再试
Assert-That '类目3 + 属性1 搭配合法（前置确认）' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$tmpSpu = $r.Json.data
$bad2 = @{ categoryId=1; spuName='类目不匹配'; specAttributes=@(@{attributeId=1;valueIds=@(1)}); skus=@(@{price=1;specValues=@(@{attributeId=1;valueId=1})}) }
$r = Invoke-Api -Method POST -Path '/api/admin/product/spu' -Token $t -Body $bad2
Assert-That '属性不属于所选类目 → 2305' ($r.Json.code -eq 2305) "code=$($r.Json.code) msg=$($r.Json.message)"

# =====================================================================
Write-Host "`n=========== 7. 修改 SPU（SKU 增量同步）===========" -ForegroundColor Cyan
# 取回详情，然后【不带 SKU id】提交改价 —— 验证「按规格组合匹配」是否生效
$d = Invoke-Api -Path "/api/admin/product/spu/$spuId" -Token $t
$skuIdsBefore = ($d.Json.data.skus | ForEach-Object { $_.id }) -join ','
$body2 = $body.Clone()
$body2.id = $spuId
$body2.skus = @(
    @{ price=189900; originalPrice=229900; costPrice=150000; stock=10; status=1; specValues=@(@{attributeId=1;valueId=1},@{attributeId=2;valueId=3}) },
    @{ price=229900; originalPrice=259900; costPrice=170000; stock=20; status=1; specValues=@(@{attributeId=1;valueId=1},@{attributeId=2;valueId=4}) },
    @{ price=199900; originalPrice=229900; costPrice=150000; stock=30; status=1; specValues=@(@{attributeId=1;valueId=2},@{attributeId=2;valueId=3}) },
    @{ price=239900; originalPrice=269900; costPrice=180000; stock=40; status=1; specValues=@(@{attributeId=1;valueId=2},@{attributeId=2;valueId=4}) }
)
$r = Invoke-Api -Method PUT -Path '/api/admin/product/spu' -Token $t -Body $body2
Assert-That '修改 SPU 成功（不带 SKU id）' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
$d2 = Invoke-Api -Path "/api/admin/product/spu/$spuId" -Token $t
$skuIdsAfter = ($d2.Json.data.skus | ForEach-Object { $_.id }) -join ','
Assert-That 'SKU 仍是 4 个（没有全删重建）' ($d2.Json.data.skus.Count -eq 4) "count=$($d2.Json.data.skus.Count)"
Assert-That 'SKU 的 id 完全没变（按组合匹配成功）' ($skuIdsAfter -eq $skuIdsBefore) "before=$skuIdsBefore after=$skuIdsAfter"
$minPrice = ($d2.Json.data.skus | Measure-Object -Property price -Minimum).Minimum
Assert-That '改价已生效（最低价变成 189900）' ($minPrice -eq 189900) "min=$minPrice"
$dbAgg2 = Invoke-Sql "SELECT price FROM pms_spu WHERE id=$spuId"
Assert-That 'DB: SPU 冗余价同步更新为 189900' ($dbAgg2[0] -eq '189900') "price=$($dbAgg2[0])"
$dbLog2 = Invoke-Sql "SELECT COUNT(*) FROM wms_stock_log l JOIN pms_sku k ON k.id=l.sku_id WHERE k.spu_id=$spuId AND l.biz_type=5"
Assert-That 'DB: 库存未变时不写调整流水（delta=0 跳过）' ($dbLog2[0] -eq '0') "count=$($dbLog2[0])"

# 去掉一个组合 → 该 SKU 被逻辑删除
$body3 = $body2.Clone()
$body3.skus = @($body2.skus[0], $body2.skus[1], $body2.skus[2])
$r = Invoke-Api -Method PUT -Path '/api/admin/product/spu' -Token $t -Body $body3
Assert-That '减少一个组合 → 修改成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$d3 = Invoke-Api -Path "/api/admin/product/spu/$spuId" -Token $t
Assert-That 'SKU 变为 3 个' ($d3.Json.data.skus.Count -eq 3) "count=$($d3.Json.data.skus.Count)"
$dbDel = Invoke-Sql "SELECT COUNT(*) FROM pms_sku WHERE spu_id=$spuId AND deleted=1"
Assert-That 'DB: 被移除的 SKU 是逻辑删除' ($dbDel[0] -eq '1') "deleted=$($dbDel[0])"
$dbAgg3 = Invoke-Sql "SELECT stock FROM pms_spu WHERE id=$spuId"
Assert-That 'DB: SPU 冗余库存随之重算为 10+20+30=60' ($dbAgg3[0] -eq '60') "stock=$($dbAgg3[0])"

# 库存调整要写流水
$body4 = $body3.Clone()
$body4.skus = @(
    @{ price=189900; stock=99; originalPrice=229900; costPrice=150000; status=1; specValues=@(@{attributeId=1;valueId=1},@{attributeId=2;valueId=3}) },
    @{ price=229900; stock=20;  originalPrice=259900; costPrice=170000; status=1; specValues=@(@{attributeId=1;valueId=1},@{attributeId=2;valueId=4}) },
    @{ price=199900; stock=30;  originalPrice=229900; costPrice=150000; status=1; specValues=@(@{attributeId=1;valueId=2},@{attributeId=2;valueId=3}) }
)
$r = Invoke-Api -Method PUT -Path '/api/admin/product/spu' -Token $t -Body $body4
Assert-That '调整库存（10→99）成功' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
$dbLog3 = Invoke-Sql "SELECT COUNT(*) FROM wms_stock_log l JOIN pms_sku k ON k.id=l.sku_id WHERE k.spu_id=$spuId AND l.biz_type=5"
Assert-That 'DB: 写了一条后台调整流水（biz_type=5）' ($dbLog3[0] -eq '1') "count=$($dbLog3[0])"
$dbDelta = Invoke-SqlRow "SELECT l.change_num, l.before_stock, l.after_stock, l.biz_no FROM wms_stock_log l JOIN pms_sku k ON k.id=l.sku_id WHERE k.spu_id=$spuId AND l.biz_type=5"
Assert-That 'DB: 流水 change_num=+89' ($dbDelta[0] -eq '89') "change=$($dbDelta[0])"
Assert-That 'DB: 流水前后库存 10→99' (($dbDelta[1] -eq '10') -and ($dbDelta[2] -eq '99')) "before=$($dbDelta[1]) after=$($dbDelta[2])"
Assert-That 'DB: biz_no 形如 ADJ_xxx' ($dbDelta[3] -like 'ADJ_*') "bizNo=$($dbDelta[3])"
$dbIn = Invoke-Sql "SELECT total_in FROM wms_stock s JOIN pms_sku k ON k.id=s.sku_id WHERE k.spu_id=$spuId AND s.stock=99"
Assert-That 'DB: 累计入库同步增加（10+89=99）' ($dbIn[0] -eq '99') "total_in=$($dbIn[0])"

# =====================================================================
Write-Host "`n=========== 8. 上下架与删除 ===========" -ForegroundColor Cyan
$r = Invoke-Api -Method PUT -Path "/api/admin/product/spu/$spuId/status?status=1" -Token $t
Assert-That '上架成功（审核已通过）' ($r.Json.code -eq 200) "code=$($r.Json.code) msg=$($r.Json.message)"
$dbStatus = Invoke-Sql "SELECT status FROM pms_spu WHERE id=$spuId"
Assert-That 'DB: pms_spu.status=1' ($dbStatus[0] -eq '1') "status=$($dbStatus[0])"
$dbSkuStatus = Invoke-Sql "SELECT COUNT(*) FROM pms_sku WHERE spu_id=$spuId AND deleted=0 AND status=1"
Assert-That 'DB: SKU 启用状态跟随上架' ($dbSkuStatus[0] -eq '3') "count=$($dbSkuStatus[0])"

# 未审核通过不能上架
$draft = @{ categoryId=3; spuName='草稿商品'; publishStatus=0; specAttributes=@(@{attributeId=1;valueIds=@(1)});
            skus=@(@{price=100; specValues=@(@{attributeId=1;valueId=1})}) }
$r = Invoke-Api -Method POST -Path '/api/admin/product/spu' -Token $t -Body $draft
$draftId = $r.Json.data
$r = Invoke-Api -Method PUT -Path "/api/admin/product/spu/$draftId/status?status=1" -Token $t
Assert-That '草稿商品不能上架（审核未通过）' ($r.Json.code -eq 400) "code=$($r.Json.code) msg=$($r.Json.message)"

$r = Invoke-Api -Method DELETE -Path "/api/admin/product/spu/$draftId" -Token $t
Assert-That '删除商品成功' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$dbDelSpu = Invoke-Sql "SELECT deleted FROM pms_spu WHERE id=$draftId"
Assert-That 'DB: 商品是逻辑删除' ($dbDelSpu[0] -eq '1') "deleted=$($dbDelSpu[0])"
$dbDelSku = Invoke-Sql "SELECT COUNT(*) FROM pms_sku WHERE spu_id=$draftId AND deleted=1"
Assert-That 'DB: 其 SKU 一并逻辑删除' ($dbDelSku[0] -eq '1') "deleted=$($dbDelSku[0])"

# 清理本次验收造的数据（SPU 及其 SKU 一并删）
Invoke-Api -Method DELETE -Path "/api/admin/product/spu/$spuId" -Token $t | Out-Null
Invoke-Api -Method DELETE -Path "/api/admin/product/spu/$tmpSpu" -Token $t | Out-Null
Invoke-Api -Method DELETE -Path "/api/admin/product/brand/$brandId" -Token $t | Out-Null

# =====================================================================
Write-Host "`n=========== 9. 权限 ===========" -ForegroundColor Cyan
$mc = Get-AdminToken -Username 'merchant'
$r = Invoke-Api -Path '/api/admin/product/spu/page' -Token $mc
Assert-That '零权限账号访问商品列表 → 403' ($r.Status -eq 403) "status=$($r.Status)"
$op = Get-AdminToken -Username 'operator'
$r = Invoke-Api -Path '/api/admin/product/spu/page' -Token $op
Assert-That '运营账号有 product:spu:list → 200' ($r.Json.code -eq 200) "code=$($r.Json.code)"
$r = Invoke-Api -Path '/api/admin/product/spu/page'
Assert-That '不带令牌 → 401' ($r.Status -eq 401) "status=$($r.Status)"

$exitCode = Show-TestSummary
exit $exitCode
