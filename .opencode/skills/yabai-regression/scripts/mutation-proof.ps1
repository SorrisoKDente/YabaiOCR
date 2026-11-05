#requires -Version 5.1
<#
.SYNOPSIS
Prova que um teste de regressão acusa uma mutação literal na causa raiz.

.DESCRIPTION
Faz backup do arquivo, aplica a mutação, roda apenas o teste indicado, restaura o arquivo
em finally e confere o hash. Sai com 0 somente quando o teste falha por assert com a mutação
e o arquivo volta exatamente ao original.

.PARAMETER File
Arquivo a mutar, relativo à raiz do repositório (ou absoluto). Precisa ser UTF-8.

.PARAMETER Find
Trecho literal original. Tem de ocorrer exatamente uma vez, a menos que -All seja usado.

.PARAMETER Replace
Trecho que reintroduz a causa raiz. Pode ser vazio (remoção).

.PARAMETER Test
Filtro do Gradle, ex.: dev.yabai.ocr.desktop.ScreenCaptureTest

.PARAMETER Task
Tarefa Gradle. Padrão: :desktop:test

.PARAMETER ProjectRoot
Raiz do repositório. Padrão: descoberta via git.

.PARAMETER All
Substitui todas as ocorrências de Find (use só quando todas representam a mesma causa).

.PARAMETER VerifyRestore
Depois de restaurar, roda o mesmo teste de novo e exige verde.

.EXAMPLE
.\scripts\mutation-proof.ps1 `
  -File "desktop\src\main\kotlin\dev\yabai\ocr\desktop\ScreenCapture.kt" `
  -Find "(bounds.width * scaleX).roundToInt()," `
  -Replace "bounds.width," `
  -Test "dev.yabai.ocr.desktop.ScreenCaptureTest"
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$File,
    [Parameter(Mandatory = $true)][string]$Find,
    [Parameter(Mandatory = $true)][string]$Replace,
    [Parameter(Mandatory = $true)][string]$Test,
    [string]$Task = ":desktop:test",
    [string]$ProjectRoot = "",
    [switch]$All,
    [switch]$VerifyRestore
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail([string]$message, [int]$code) {
    Write-Host ""
    Write-Host "ERRO: $message" -ForegroundColor Red
    exit $code
}

function Invoke-GradleTest {
    param([string]$GradleTask, [string]$Filter)

    Push-Location $script:Root
    $previous = $ErrorActionPreference
    # Windows PowerShell 5.1 transforma stderr de comando nativo em NativeCommandError quando
    # ErrorActionPreference=Stop; o Gradle escreve avisos em stderr e isso não é falha de teste.
    $ErrorActionPreference = "Continue"
    try {
        $lines = & .\gradlew.bat $GradleTask "--tests" $Filter "--console=plain" 2>&1
        $exit = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previous
        Pop-Location
    }
    return [pscustomobject]@{
        Exit   = $exit
        Output = (($lines | Out-String) -replace "`r`n", "`n")
    }
}

function Show-Relevant([string]$output) {
    $output -split "`n" |
        Where-Object { $_ -match "FAILED|tests completed|BUILD (SUCCESSFUL|FAILED)|error:" } |
        Select-Object -First 25 |
        ForEach-Object { Write-Host "  $_" }
}

# --- localizar projeto e arquivo -------------------------------------------------------------

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = [string](& git rev-parse --show-toplevel 2>$null)
}
if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    Fail "não descobri a raiz do repositório. Rode dentro do repo ou passe -ProjectRoot." 1
}
$script:Root = (Resolve-Path -LiteralPath $ProjectRoot).Path

if ([System.IO.Path]::IsPathRooted($File)) {
    $target = $File
}
else {
    $target = Join-Path $script:Root $File
}
if (-not (Test-Path -LiteralPath $target -PathType Leaf)) {
    Fail "arquivo não encontrado: $target" 1
}
$target = (Resolve-Path -LiteralPath $target).Path

$relative = $target
if ($relative.StartsWith($script:Root, [StringComparison]::OrdinalIgnoreCase)) {
    $relative = $relative.Substring($script:Root.Length).TrimStart('\', '/')
}

# --- validar a mutação antes de tocar no arquivo ---------------------------------------------

$original = [System.IO.File]::ReadAllText($target)
$occurrences = ([regex]::Matches($original, [regex]::Escape($Find))).Count

if ($occurrences -eq 0) {
    Fail "o trecho -Find não existe em $relative. Copie o texto exato do arquivo (inclusive quebras)." 1
}
if ($occurrences -gt 1 -and -not $All) {
    Fail "o trecho -Find aparece $occurrences vezes em $relative. Inclua mais contexto ou use -All." 1
}
if ($Find -ceq $Replace) {
    Fail "-Find e -Replace são iguais; nada seria mutado." 1
}

$index = $original.IndexOf($Find, [StringComparison]::Ordinal)
if ($All) {
    $mutated = $original.Replace($Find, $Replace)
}
else {
    $mutated = $original.Substring(0, $index) + $Replace + $original.Substring($index + $Find.Length)
}
if ($mutated -ceq $original) {
    Fail "a mutação não alterou o conteúdo." 1
}

$tempRoot = Join-Path $env:LOCALAPPDATA "Temp\opencode"
if (-not (Test-Path -LiteralPath $tempRoot)) {
    $tempRoot = [System.IO.Path]::GetTempPath()
}
$backup = Join-Path $tempRoot ("mutation-" + [guid]::NewGuid().ToString("N") + ".bak")
$originalHash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
Copy-Item -LiteralPath $target -Destination $backup -Force

Write-Host "arquivo : $relative"
Write-Host "teste   : $Test  ($Task)"
Write-Host "mutação : '$Find' -> '$Replace'"
Write-Host ""

$proven = $false
$restored = $false
$restoreOk = $false
$verifyOk = $null

try {
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($target, $mutated, $utf8)

    $run = Invoke-GradleTest -GradleTask $Task -Filter $Test
    Show-Relevant $run.Output

    $assertFailed =
        ($run.Output -match "There were failing tests") -or
        ($run.Output -match "(?m)FAILED\s*$")
    $compileFailed =
        ($run.Output -match "Compilation error") -or
        ($run.Output -match "compileKotlin FAILED") -or
        ($run.Output -match ":compile\w* FAILED")
    $proven = ($run.Exit -ne 0) -and $assertFailed -and (-not $compileFailed)
}
finally {
    Copy-Item -LiteralPath $backup -Destination $target -Force
    $restored = $true
    $restoredHash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
    $restoreOk = ($restoredHash -eq $originalHash)
    Remove-Item -LiteralPath $backup -Force -ErrorAction SilentlyContinue
}

if (-not $restored) {
    Fail "o arquivo não foi restaurado. Backup em: $backup" 3
}
if (-not $restoreOk) {
    Fail "o hash depois da restauração não bate com o original. Confira $relative (backup: $backup)." 3
}
Write-Host ""
Write-Host "restaurado: $relative (hash confere)" -ForegroundColor Green

if (-not $proven) {
    Write-Host ""
    Write-Host "MUTAÇÃO NÃO DETECTADA: o teste continuou verde ou falhou por compilação." -ForegroundColor Red
    Write-Host "O teste não cobre a causa raiz. Reescreva o teste antes de aceitar a correção." -ForegroundColor Red
    exit 2
}

if ($VerifyRestore) {
    Write-Host ""
    Write-Host "verificando a restauração (teste deve passar)…"
    $after = Invoke-GradleTest -GradleTask $Task -Filter $Test
    Show-Relevant $after.Output
    $verifyOk = ($after.Exit -eq 0)
    if (-not $verifyOk) {
        Fail "o teste não voltou a passar depois de restaurar." 3
    }
}

Write-Host ""
Write-Host "MUTAÇÃO PROVADA: o teste acusou a causa e o arquivo voltou ao original." -ForegroundColor Green
if ($VerifyRestore -and $verifyOk) {
    Write-Host "RESTAURAÇÃO VERIFICADA: o teste passa de novo." -ForegroundColor Green
}
exit 0
