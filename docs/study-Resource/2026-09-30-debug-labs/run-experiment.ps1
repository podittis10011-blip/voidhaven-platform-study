param(
    [Parameter(Mandatory)][string]$Id,
    [string]$Repository='D:\学习资料\voidhaven-platform',
    [string]$Maven='D:\Maven\apache-maven-3.9.4\bin\mvn.cmd',
    [string]$JavaHome='D:\JDK\JDK17'
)
$ErrorActionPreference='Stop'
$items=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'experiments.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$entry=@($items | Where-Object { $_.id -eq $Id })
if($entry.Count -ne 1){throw "未知或重复实验编号：$Id"}
$entry=$entry[0]
$repoRoot=(Resolve-Path -LiteralPath $Repository).Path.TrimEnd('\')
$target=[IO.Path]::GetFullPath((Join-Path $repoRoot $entry.target))
if(-not $target.StartsWith($repoRoot+'\apps\api\src\test\java\',[StringComparison]::OrdinalIgnoreCase)){throw '实验只允许编辑测试目录'}
if(-not (Test-Path -LiteralPath $target)){throw "测试文件不存在，请先安装本层探针：$target"}
$before=[IO.File]::ReadAllBytes($target)
$beforeHash=(Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
$text=[IO.File]::ReadAllText($target).Replace("`r`n","`n")
$index=$text.IndexOf($entry.old,[StringComparison]::Ordinal)
if($index -lt 0){throw '未找到原始文本，文件可能已修改；未执行实验'}
$changed=$text.Substring(0,$index)+$entry.new+$text.Substring($index+$entry.old.Length)
$log=Join-Path ([IO.Path]::GetTempPath()) ("vh-"+$Id+'-'+[Guid]::NewGuid().ToString('N')+'.log')
$oldJava=$env:JAVA_HOME
$exitCode=-1
Write-Output "预期：$($entry.expected)"
Write-Output "日志：$log"
try {
    [IO.File]::WriteAllText($target,$changed,(New-Object Text.UTF8Encoding($false)))
    $env:JAVA_HOME=$JavaHome
    Push-Location (Join-Path $repoRoot 'apps\api')
    try { & $Maven -o "-Dtest=$($entry.driver)" test *> $log; $exitCode=$LASTEXITCODE }
    finally { Pop-Location }
} finally {
    [IO.File]::WriteAllBytes($target,$before)
    $env:JAVA_HOME=$oldJava
}
if((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $beforeHash){throw '实验恢复校验失败'}
Get-Content -LiteralPath $log -Tail 18
Write-Output "源码已按原字节恢复；Maven exit=$exitCode。请核对 XML 中的具体异常，非零退出本身不能证明预期实验成立。"
Write-Output "恢复后请重跑：mvn -o -Dtest=$($entry.driver) test"
if($entry.expected.StartsWith('PASS:') -and $exitCode -ne 0){throw '预期通过的实验未通过'}
if(-not $entry.expected.StartsWith('PASS:') -and $exitCode -eq 0){throw '预期出现红灯的实验没有出现红灯'}
