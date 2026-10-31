<#
.SYNOPSIS
清空 QSL 管理插件的数据（按插件自带的扩展 API 批量删除）。

.DESCRIPTION
走 Halo 的扩展 CRUD 接口逐条删除，不碰数据库、不需要停机，Halo 会自行维护索引。
凭据用 Halo 个人令牌（控制台 → 个人中心 → 个人令牌，形如 pat_xxx）。

.PARAMETER BaseUrl
站点地址，默认 http://localhost:8090

.PARAMETER Token
Halo 个人令牌，pat_ 开头

.PARAMETER Scope
business = 只清业务数据（通联、卡片、收卡、换卡、申请、作业、审计），保留全部配置
all      = 连配置一起清（卡片版本、地址簿、卡片局、设备库、本台设备、线下活动、同步配置、本台资料、系统参数、迁移状态）＝恢复出厂

.PARAMETER DryRun
只打印会删哪些、共多少条，不实际删除

.EXAMPLE
.\clear-plugin-data.ps1 -Token pat_xxx -DryRun

.EXAMPLE
.\clear-plugin-data.ps1 -Token pat_xxx -Scope all
#>

[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8090',
    [Parameter(Mandatory = $true)][string]$Token,
    [ValidateSet('business', 'all')][string]$Scope = 'business',
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
# 让中文提示在 Windows PowerShell 5.1 的代码页下也不乱码
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
$BaseUrl = $BaseUrl.TrimEnd('/')

if (-not $Token.StartsWith('pat_')) {
    Write-Warning '个人令牌通常以 pat_ 开头，请确认没有把别的凭据贴进来。'
}

$headers = @{ Authorization = "Bearer $Token" }
$apiRoot = '/apis/qsl-management.bi1kbu.com/v1alpha1'

# 业务数据：清了就是从零开始录入
$businessPlurals = @(
    'qso-records',
    'card-records',
    'receive-records',
    'offline-exchange-cards',
    'exchange-requests',
    'qsl-card-requests',
    'qsl-card-request-qso-reservations',
    'import-export-jobs',
    'log-sync-jobs',
    'qsl-audit-logs'
)

# 配置数据：清了重启插件会重建 system-settings 与 station-profiles 的默认值（呼号回落 BI1KBU）
$configPlurals = @(
    'log-sync-profiles',
    'station-cards',
    'address-book-entries',
    'bureau-entries',
    'equipment-catalog-entries',
    'station-equipments',
    'offline-activities',
    'station-profiles',
    'system-settings',
    'qsl-migration-states'
)

$targets = if ($Scope -eq 'all') { $businessPlurals + $configPlurals } else { $businessPlurals }

function Get-Total {
    param([string]$Plural)
    $uri = "$BaseUrl$apiRoot/$Plural`?page=0&size=1"
    try {
        $response = Invoke-RestMethod -Uri $uri -Method Get -Headers $headers
        return [int]$response.total
    }
    catch {
        Write-Warning "读取 $Plural 失败：$($_.Exception.Message)"
        return -1
    }
}

function Clear-Plural {
    param([string]$Plural)

    $removed = 0
    $round = 0
    while ($true) {
        $round++
        $uri = "$BaseUrl$apiRoot/$Plural`?page=0&size=100"
        $response = Invoke-RestMethod -Uri $uri -Method Get -Headers $headers
        $items = @($response.items)
        if ($items.Count -eq 0) { break }

        foreach ($item in $items) {
            $name = $item.metadata.name
            if ($DryRun) {
                $removed++
                continue
            }
            $deleteUri = "$BaseUrl$apiRoot/$Plural/$name"
            Invoke-RestMethod -Uri $deleteUri -Method Delete -Headers $headers | Out-Null
            $removed++
        }

        if ($DryRun) { break }
        if ($round -gt 1000) { Write-Warning "$Plural 删除轮次异常，已停止"; break }
    }
    return $removed
}

Write-Host "站点：$BaseUrl    范围：$Scope    $(if ($DryRun) { '（预览，不删除）' })"
Write-Host ''

$summary = @()
foreach ($plural in $targets) {
    $before = Get-Total -Plural $plural
    if ($before -lt 0) { continue }
    if ($before -eq 0) {
        $summary += [pscustomobject]@{ 类型 = $plural; 清理前 = 0; 已删除 = 0 }
        continue
    }

    Write-Host "清理 $plural（$before 条）..."
    $removed = Clear-Plural -Plural $plural
    $after = if ($DryRun) { $before } else { Get-Total -Plural $plural }
    $summary += [pscustomobject]@{ 类型 = $plural; 清理前 = $before; 已删除 = $removed }
    Write-Host "  完成：删 $removed 条，剩余 $(if ($after -lt 0) { '?' } else { $after }) 条"
}

Write-Host ''
$summary | Format-Table -AutoSize

if ($DryRun) {
    Write-Host ''
    Write-Host '以上是预览结果，去掉 -DryRun 才会真正删除。'
}
else {
    Write-Host ''
    Write-Host '扩展数据已处理完毕。还需要手工清两处：'
    Write-Host '  1) 卡片图片：控制台 → 附件，删除卡片版本/制卡时上传的图（删扩展实例不会删附件）。'
    Write-Host '  2) 浏览器 localStorage：控制台 F12 执行'
    Write-Host "     Object.keys(localStorage).filter(k => k.startsWith('qsl:')).forEach(k => localStorage.removeItem(k))"
    if ($Scope -eq 'all') {
        Write-Host '  3) 重启插件（或重启 Halo）后，系统参数与本台资料会重建为默认值，呼号回落 BI1KBU。'
    }
}
