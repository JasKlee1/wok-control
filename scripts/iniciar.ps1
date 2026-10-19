<#
.SYNOPSIS
  Inicia Wok Control en local en el orden correcto y espera a que cada servicio esté saludable.

.DESCRIPTION
  Orden de arranque: config-server -> discovery-service -> servicios de negocio -> api-gateway.
  Los jar se copian a .run\ antes de ejecutarlos para que Maven pueda recompilar mientras el
  sistema está en marcha (Windows bloquea los jar en uso). Los registros quedan en logs\.

.PARAMETER Compilar
  Ejecuta "mvnw package -DskipTests" antes de iniciar.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\iniciar.ps1 -Compilar
#>
param([switch]$Compilar)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

# --- Java 21 ---
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $jdk = Get-ChildItem 'C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^(jdk-)?21' } | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path $java)) { throw 'No se encontró un JDK 21. Defina JAVA_HOME apuntando a Java 21.' }
# java -version escribe en stderr; se lee a través de cmd para que PowerShell 5.1 no lo trate como error
$version = (cmd /c "`"$java`" -version 2>&1" | Select-Object -First 1)
if ($version -notmatch '"21') { throw "Se requiere Java 21; JAVA_HOME apunta a: $version" }

# --- .env ---
if (-not (Test-Path '.env')) { throw 'Falta el archivo .env. Copie .env.example como .env y complete MONGODB_URI y JWT_SECRET.' }
$envTexto = Get-Content '.env' -Raw
if ($envTexto -match 'MONGODB_URI=.*(<db_password>|CAMBIAR_PASSWORD)') { throw 'Configure la contraseña real de MongoDB Atlas en MONGODB_URI del archivo .env.' }
if ($envTexto -notmatch 'JWT_SECRET=\S{32,}') { throw 'JWT_SECRET en .env debe tener al menos 32 caracteres.' }

if ($Compilar) {
    & .\mvnw.cmd -q -B package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw 'La compilación falló.' }
}

New-Item -ItemType Directory -Force '.run', 'logs' | Out-Null
$pids = @()

function Iniciar-Servicio([string]$modulo) {
    $jar = Join-Path $raiz "$modulo\target\$modulo-1.0.0-SNAPSHOT.jar"
    if (-not (Test-Path $jar)) { throw "No existe $jar. Ejecute primero: .\mvnw.cmd package -DskipTests (o use -Compilar)." }
    $destino = Join-Path $raiz ".run\$modulo.jar"
    Copy-Item $jar $destino -Force
    $p = Start-Process -FilePath $java -ArgumentList @('-jar', "`"$destino`"") -WorkingDirectory $raiz -WindowStyle Hidden `
        -RedirectStandardOutput "logs\$modulo.log" -RedirectStandardError "logs\$modulo.err.log" -PassThru
    Write-Host ("  iniciado {0,-20} pid {1}" -f $modulo, $p.Id)
    return $p.Id
}

function Esperar-Salud([string]$nombre, [int]$puerto, [int]$segundos = 120) {
    $limite = (Get-Date).AddSeconds($segundos)
    while ((Get-Date) -lt $limite) {
        try {
            $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 "http://localhost:$puerto/actuator/health"
            if ($r.StatusCode -eq 200) { Write-Host ("  [OK] {0,-20} http://localhost:{1}" -f $nombre, $puerto) -ForegroundColor Green; return }
        } catch { Start-Sleep -Seconds 2 }
    }
    throw "$nombre no respondió en $segundos s. Revise logs\$nombre.log"
}

Write-Host 'Wok Control - arranque local' -ForegroundColor Yellow
Write-Host '1/4 Configuración centralizada'
$pids += Iniciar-Servicio 'config-server'
Esperar-Salud 'config-server' 8888

Write-Host '2/4 Descubrimiento de servicios'
$pids += Iniciar-Servicio 'discovery-service'
Esperar-Salud 'discovery-service' 8761

Write-Host '3/4 Servicios de negocio'
$negocio = [ordered]@{ 'usuarios-service' = 8081; 'menu-service' = 8082; 'pedidos-service' = 8083; 'inventario-service' = 8084;
    'proveedores-service' = 8085; 'facturacion-service' = 8086; 'reportes-service' = 8087 }
foreach ($m in $negocio.Keys) { $pids += Iniciar-Servicio $m }
foreach ($m in $negocio.Keys) { Esperar-Salud $m $negocio[$m] 180 }

Write-Host '4/4 API Gateway'
$pids += Iniciar-Servicio 'api-gateway'
Esperar-Salud 'api-gateway' 8080

$pids | Set-Content '.run\pids.txt'
Write-Host ''
Write-Host 'Wok Control está en marcha:' -ForegroundColor Yellow
Write-Host '  Aplicación web ....... http://localhost:8080'
Write-Host '  Swagger (central) .... http://localhost:8080/swagger-ui.html'
Write-Host '  Eureka ............... http://localhost:8761'
Write-Host '  Detener .............. powershell -File scripts\detener.ps1'
Write-Host '  Nota: el gateway tarda unos segundos en ver los servicios registrados en Eureka.'
