<#
.SYNOPSIS
  Detiene los procesos de Wok Control iniciados con scripts\iniciar.ps1.
#>
$raiz = Split-Path -Parent $PSScriptRoot
$archivo = Join-Path $raiz '.run\pids.txt'
$detenidos = 0
if (Test-Path $archivo) {
    foreach ($id in Get-Content $archivo) {
        try { Stop-Process -Id ([int]$id) -Force -Confirm:$false -ErrorAction Stop; $detenidos++ } catch { }
    }
    Remove-Item $archivo -Force
}
# respaldo: procesos java que ejecutan los jar copiados en .run\
Get-CimInstance Win32_Process -Filter "name='java.exe'" |
    Where-Object { $_.CommandLine -match [regex]::Escape((Join-Path $raiz '.run')) } |
    ForEach-Object { try { Stop-Process -Id $_.ProcessId -Force -Confirm:$false -ErrorAction Stop; $detenidos++ } catch { } }
Write-Host "Procesos detenidos: $detenidos"
