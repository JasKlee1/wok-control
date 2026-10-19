<#
.SYNOPSIS
  Muestra el estado de salud de cada componente y los servicios registrados en Eureka.
#>
$servicios = [ordered]@{ 'config-server' = 8888; 'discovery-service' = 8761; 'api-gateway' = 8080; 'usuarios-service' = 8081;
    'menu-service' = 8082; 'pedidos-service' = 8083; 'inventario-service' = 8084; 'proveedores-service' = 8085;
    'facturacion-service' = 8086; 'reportes-service' = 8087 }
foreach ($s in $servicios.Keys) {
    $estado = 'DETENIDO'
    try {
        $r = Invoke-RestMethod -TimeoutSec 3 "http://localhost:$($servicios[$s])/actuator/health"
        $estado = $r.status
    } catch { }
    $color = if ($estado -eq 'UP') { 'Green' } else { 'Red' }
    Write-Host ("{0,-22} {1,-6} {2}" -f $s, $servicios[$s], $estado) -ForegroundColor $color
}
try {
    $apps = Invoke-RestMethod -TimeoutSec 3 -Headers @{ Accept = 'application/json' } 'http://localhost:8761/eureka/apps'
    $nombres = $apps.applications.application | ForEach-Object { $_.name }
    Write-Host ''
    Write-Host ('Registrados en Eureka: ' + ($nombres -join ', '))
} catch { Write-Host 'Eureka no disponible' -ForegroundColor Red }
