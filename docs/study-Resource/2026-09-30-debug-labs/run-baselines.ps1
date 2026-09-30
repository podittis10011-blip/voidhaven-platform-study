param(
 [ValidateSet('all','vh02','vh03','vh04','vh05','vh06')][string]$Layer='all',
 [string]$Repository='D:\学习资料\voidhaven-platform',
 [string]$Maven='D:\Maven\apache-maven-3.9.4\bin\mvn.cmd'
)
$ErrorActionPreference='Stop'
$sets=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'baseline-classes.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if($Layer -eq 'all'){$classes=@($sets.PSObject.Properties | ForEach-Object {$_.Value.Split(',')} | Sort-Object -Unique)}
else {$number=[int]$Layer.Substring(2);$classes=$sets.PSObject.Properties["$number"].Value.Split(',')}
$manifest=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'probe-manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
foreach($p in $manifest){if(($Layer -eq 'all' -or $p.layer -eq $Layer) -and -not (Test-Path -LiteralPath (Join-Path $Repository $p.target))){throw "请先安装探针：$($p.file)"}}
$oldJava=$env:JAVA_HOME
try {
 $env:JAVA_HOME='D:\JDK\JDK17'
 Push-Location (Join-Path $Repository 'apps\api')
 try {& $Maven -o "-Dtest=$($classes -join ',')" test;$code=$LASTEXITCODE} finally {Pop-Location}
} finally {$env:JAVA_HOME=$oldJava}
if($code -ne 0){throw "基线未通过，Maven exit=$code"}
