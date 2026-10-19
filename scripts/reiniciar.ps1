<#
.SYNOPSIS
  Recompila y reinicia un solo microservicio sin detener el resto del sistema.
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\reiniciar.ps1 -Servicio menu-service
#>
param([Parameter(Mandatory = $true)][string]$Servicio, [switch]$SinCompilar)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz
$puertos = @{ 'config-server' = 8888; 'discovery-service' = 8761; 'api-gateway' = 8080; 'usuarios-service' = 8081; 'menu-service' = 8082;
    'pedidos-service' = 8083; 'inventario-service' = 8084; 'proveedores-service' = 8085; 'facturacion-service' = 8086; 'reportes-service' = 8087 }
if (-not $puertos.ContainsKey($Servicio)) { throw "Servicio desconocido: $Servicio" }
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1' }

if (-not $SinCompilar) {
    & .\mvnw.cmd -q -B package -DskipTests -pl "wok-common,$Servicio" -am
    if ($LASTEXITCODE -ne 0) { throw 'La compilación falló.' }
}
$destino = Join-Path $raiz ".run\$Servicio.jar"
Get-CimInstance Win32_Process -Filter "name='java.exe'" | Where-Object { $_.CommandLine -match [regex]::Escape($destino) } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -Confirm:$false }
Start-Sleep -Seconds 2
Copy-Item "$Servicio\target\$Servicio-1.0.0-SNAPSHOT.jar" $destino -Force
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$p = Start-Process -FilePath $java -ArgumentList @('-jar', "`"$destino`"") -WorkingDirectory $raiz -WindowStyle Hidden `
    -RedirectStandardOutput "logs\$Servicio.log" -RedirectStandardError "logs\$Servicio.err.log" -PassThru
Add-Content '.run\pids.txt' $p.Id
$limite = (Get-Date).AddSeconds(180)
while ((Get-Date) -lt $limite) {
    try { if ((Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 "http://localhost:$($puertos[$Servicio])/actuator/health").StatusCode -eq 200) {
            Write-Host "$Servicio reiniciado (pid $($p.Id))"; exit 0 } } catch { Start-Sleep -Seconds 2 }
}
throw "$Servicio no respondió; revise logs\$Servicio.log"
