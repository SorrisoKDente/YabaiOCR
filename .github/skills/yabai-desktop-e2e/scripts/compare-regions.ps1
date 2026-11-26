#requires -Version 5.1
<#
.SYNOPSIS
Compara duas capturas de tela pixel a pixel em regiões nomeadas.

.DESCRIPTION
As regiões são informadas como "nome=x,y,w,h" (coordenadas da imagem, canto superior esquerdo).
Ignora alfa e compara B/G/R com tolerância por canal. Mostra o primeiro pixel diferente de cada
região. Use regiões que NÃO contenham desenho do app (caixas, rubi, faixas) nem a barra de tarefas
do Windows (acrílico).

.EXAMPLE
.\compare-regions.ps1 `
  -Before "C:\...\picker-0-antes.png" `
  -After  "C:\...\picker-1-seletor.png" `
  -Region "pagina=850,210,140,180", "xadrez=520,435,460,20", "fundo=100,540,200,80" `
  -FailOnDiff
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Before,
    [Parameter(Mandatory = $true)][string]$After,
    [Parameter(Mandatory = $true)][string[]]$Region,
    [ValidateRange(0, 255)][int]$Tolerance = 0,
    [switch]$FailOnDiff
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

function Load-Rgba([string]$path) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "imagem não encontrada: $path"
    }
    $bitmap = [System.Drawing.Bitmap]::FromFile($path)
    $rect = New-Object System.Drawing.Rectangle 0, 0, $bitmap.Width, $bitmap.Height
    $data = $bitmap.LockBits(
        $rect,
        [System.Drawing.Imaging.ImageLockMode]::ReadOnly,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )
    try {
        $bytes = New-Object byte[] ($data.Stride * $data.Height)
        [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $bytes.Length)
        $stride = $data.Stride
    }
    finally {
        $bitmap.UnlockBits($data)
    }
    return [pscustomobject]@{
        Path   = $path
        Bitmap = $bitmap
        Bytes  = $bytes
        Stride = $stride
        Width  = $bitmap.Width
        Height = $bitmap.Height
    }
}

$regions = @()
$pattern = '^\s*(?<name>[^=]+?)\s*=\s*(?<x>\d+)\s*,\s*(?<y>\d+)\s*,\s*(?<w>\d+)\s*,\s*(?<h>\d+)\s*$'
foreach ($spec in $Region) {
    $match = [regex]::Match($spec, $pattern)
    if (-not $match.Success) {
        throw "região inválida: '$spec'. Use nome=x,y,w,h."
    }
    $regions += [pscustomobject]@{
        Name   = $match.Groups['name'].Value
        X      = [int]$match.Groups['x'].Value
        Y      = [int]$match.Groups['y'].Value
        Width  = [int]$match.Groups['w'].Value
        Height = [int]$match.Groups['h'].Value
    }
}

$left = Load-Rgba $Before
$right = Load-Rgba $After
$anyDiff = $false

try {
    if ($left.Width -ne $right.Width -or $left.Height -ne $right.Height) {
        throw ("tamanhos diferentes: antes {0}x{1}, depois {2}x{3}" -f $left.Width, $left.Height, $right.Width, $right.Height)
    }

    Write-Host ("antes : {0} ({1}x{2})" -f $left.Path, $left.Width, $left.Height)
    Write-Host ("depois: {0} ({1}x{2})" -f $right.Path, $right.Width, $right.Height)
    Write-Host ("tolerância: {0} por canal" -f $Tolerance)
    Write-Host ""

    foreach ($r in $regions) {
        if ($r.X -lt 0 -or $r.Y -lt 0 -or
            ($r.X + $r.Width) -gt $left.Width -or
            ($r.Y + $r.Height) -gt $left.Height -or
            $r.Width -le 0 -or $r.Height -le 0) {
            throw ("região '{0}' fora da imagem: {1},{2} {3}x{4}" -f $r.Name, $r.X, $r.Y, $r.Width, $r.Height)
        }

        $diff = 0
        $first = $null

        for ($y = $r.Y; $y -lt ($r.Y + $r.Height); $y++) {
            $row = $y * $left.Stride
            for ($x = $r.X; $x -lt ($r.X + $r.Width); $x++) {
                $i = $row + ($x * 4)   # BGRA, little-endian
                $db = [math]::Abs([int]$left.Bytes[$i] - [int]$right.Bytes[$i])
                $dg = [math]::Abs([int]$left.Bytes[$i + 1] - [int]$right.Bytes[$i + 1])
                $dr = [math]::Abs([int]$left.Bytes[$i + 2] - [int]$right.Bytes[$i + 2])
                if ($db -gt $Tolerance -or $dg -gt $Tolerance -or $dr -gt $Tolerance) {
                    $diff++
                    if ($null -eq $first) {
                        $first = [pscustomobject]@{
                            X = $x; Y = $y
                            Before = "{0},{1},{2}" -f $left.Bytes[$i + 2], $left.Bytes[$i + 1], $left.Bytes[$i]
                            After  = "{0},{1},{2}" -f $right.Bytes[$i + 2], $right.Bytes[$i + 1], $right.Bytes[$i]
                        }
                    }
                }
            }
        }

        $total = $r.Width * $r.Height
        $percent = if ($total -gt 0) { 100.0 * $diff / $total } else { 0 }
        $color = if ($diff -eq 0) { "Green" } else { "Yellow" }
        Write-Host ("{0}: {1} de {2} pixels diferentes ({3:N2}%)" -f $r.Name, $diff, $total, $percent) -ForegroundColor $color
        if ($null -ne $first) {
            Write-Host ("   primeiro: ({0},{1}) antes={2} depois={3}" -f $first.X, $first.Y, $first.Before, $first.After)
            $anyDiff = $true
        }
    }
}
finally {
    $left.Bitmap.Dispose()
    $right.Bitmap.Dispose()
}

if ($FailOnDiff -and $anyDiff) {
    Write-Host ""
    Write-Host "FALHA: houve diferença de pixels." -ForegroundColor Red
    exit 1
}

Write-Host ""
if ($anyDiff) {
    Write-Host "Diferenças encontradas — confirme se a região não contém desenho do app." -ForegroundColor Yellow
}
else {
    Write-Host "Nenhuma diferença nas regiões medidas." -ForegroundColor Green
}
exit 0
