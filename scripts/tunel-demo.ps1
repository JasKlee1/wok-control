<#
.SYNOPSIS
  Publica el gateway local (http://localhost:8080) en una URL https pública y temporal con un
  túnel rápido de Cloudflare, para que un celular pueda escanear el QR de una mesa en la demo.

.DESCRIPTION
  - No necesita cuenta de Cloudflare ni pago: usa un "quick tunnel" (https://<algo>.trycloudflare.com).
  - La URL cambia cada vez que se ejecuta. Se cierra con Ctrl+C.
  - El QR se arma con la dirección desde la que se abre la pantalla (window.location.origin), así que
    hay que abrir "Mesas > QR" DESDE LA URL DEL TÚNEL para que el código impreso apunte a ella.
  - Primero hay que tener los servicios corriendo (tarea "Wok: iniciar todo").
#>
param([int]$Puerto = 8080)

$exe = (Get-Command cloudflared -ErrorAction SilentlyContinue).Source
if (-not $exe) {
    foreach ($p in "${env:ProgramFiles(x86)}\cloudflared\cloudflared.exe", "$env:ProgramFiles\cloudflared\cloudflared.exe") {
        if (Test-Path $p) { $exe = $p; break }
    }
}
if (-not $exe) { throw 'No se encontró cloudflared. Instálelo con: winget install --id Cloudflare.cloudflared -e' }

try {
    $salud = Invoke-RestMethod -TimeoutSec 5 "http://localhost:$Puerto/actuator/health"
    if ($salud.status -ne 'UP') { throw 'no UP' }
} catch {
    throw "El gateway no responde en http://localhost:$Puerto. Ejecute primero la tarea 'Wok: iniciar todo' y espere a que todo esté UP."
}

Write-Host ''
Write-Host 'Abriendo el túnel... busque abajo la línea con https://....trycloudflare.com' -ForegroundColor Cyan
Write-Host '1. Abra esa URL en el computador e inicie sesión.' -ForegroundColor Cyan
Write-Host '2. Vaya a Mesas > QR: el código ya apunta a la URL del túnel.' -ForegroundColor Cyan
Write-Host '3. Escanéelo con el celular. Ctrl+C cierra el túnel.' -ForegroundColor Cyan
Write-Host ''
& $exe tunnel --no-autoupdate --url "http://localhost:$Puerto"
