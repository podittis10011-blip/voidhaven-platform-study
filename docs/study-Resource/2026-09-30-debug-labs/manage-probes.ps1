param(
    [Parameter(Mandatory)][ValidateSet('Install','Clean')][string]$Action,
    [ValidateSet('all','vh02','vh03','vh04','vh05','vh06')][string]$Layer='all',
    [string]$Repository='D:\学习资料\voidhaven-platform'
)
$ErrorActionPreference='Stop'
$repoRoot=(Resolve-Path -LiteralPath $Repository).Path.TrimEnd('\')
$manifest=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'probe-manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$selected=@($manifest | Where-Object { $Layer -eq 'all' -or $_.layer -eq $Layer })
$sha=[System.Security.Cryptography.SHA256]::Create()
try { $repoId=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($repoRoot)))).Replace('-','') } finally { $sha.Dispose() }
$statePath=Join-Path ([IO.Path]::GetTempPath()) "vh-debug-probes-$repoId.json"
$state=@()
if(Test-Path -LiteralPath $statePath){ $state=@(Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json) }
foreach($entry in $selected){
    $target=[IO.Path]::GetFullPath((Join-Path $repoRoot $entry.target))
    if(-not $target.StartsWith($repoRoot+'\apps\api\src\test\java\',[StringComparison]::OrdinalIgnoreCase)){throw "目标越界：$target"}
    $source=Join-Path $PSScriptRoot $entry.file
    if($Action -eq 'Install'){
        if(Test-Path -LiteralPath $target){throw "已有同名文件，未覆盖：$target"}
        if((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne $entry.sha256){throw "探针原件与清单不一致：$source"}
    } else {
        if(-not (Test-Path -LiteralPath $target)){continue}
        if(-not ($state | Where-Object { $_.target -eq $target -and $_.sha256 -eq $entry.sha256 })){throw "不是本脚本登记的副本，未删除：$target"}
        if((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $entry.sha256){throw "副本存在修改，请先保存/恢复，未删除：$target"}
    }
}
foreach($entry in $selected){
    $target=[IO.Path]::GetFullPath((Join-Path $repoRoot $entry.target))
    if($Action -eq 'Install'){
        New-Item -ItemType Directory -Path (Split-Path $target) -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot $entry.file) -Destination $target
        $state=@($state | Where-Object { $_.target -ne $target })+@([PSCustomObject]@{target=$target;sha256=$entry.sha256})
    } elseif(Test-Path -LiteralPath $target){
        Remove-Item -LiteralPath $target
        $state=@($state | Where-Object { $_.target -ne $target })
    }
    ConvertTo-Json -InputObject @($state) -Depth 4 | Set-Content -LiteralPath $statePath -Encoding UTF8
    Write-Output "$Action : $target"
}
