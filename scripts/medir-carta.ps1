<#
.SYNOPSIS
  HU-039 / AC-039.1 (NFR-WOK-001): mide 50 cargas de la carta del cliente con una red 4G simulada y
  calcula el p95.

.DESCRIPTION
  1. Abre una sesion de mesa con la URL del QR (la misma que abre el celular).
  2. Carga 50 veces /cliente/carta, con su hoja de iconos, limitando la descarga con curl
     (--limit-rate, por defecto 1.6 Mbit/s, como el perfil "4G lento" de Chrome).
  3. Muestra el promedio, el p95 y si cumple el limite de 3 s.

  No guarda ni imprime el token de la sesion: queda en un archivo temporal de cookies que se borra
  al terminar.

.PARAMETER Qr
  URL completa del QR de una mesa, por ejemplo https://<gateway>/cliente/mesa/<codigo>.
  Se copia desde la pantalla "QR de la mesa" (Mesas > QR).

.PARAMETER Veces
  Numero de cargas (50 por defecto).

.PARAMETER Velocidad
  Limite de descarga para curl (200K = 1.6 Mbit/s por defecto).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\medir-carta.ps1 -Qr "https://wokcontrol-exito-gateway.onrender.com/cliente/mesa/<codigo>"
#>
param(
    [Parameter(Mandatory = $true)][string]$Qr,
    [int]$Veces = 50,
    [string]$Velocidad = '200K'
)

$ErrorActionPreference = 'Stop'
$curl = (Get-Command curl.exe).Source
$base = ([Uri]$Qr).GetLeftPart([UriPartial]::Authority)
$cookies = [IO.Path]::GetTempFileName()

try {
    # 1. sesion de mesa (el QR responde 302 a /cliente/carta y deja la cookie)
    $codigo = & $curl -s -o NUL -w '%{http_code}' -c $cookies $Qr
    if ($codigo -ne '302') {
        throw "El QR respondio $codigo. Revise que la mesa este activa (AC-033.2) y que la URL sea la del QR."
    }

    # 2. una carga de calentamiento (Render puede estar despertando) y luego las mediciones
    & $curl -s -o NUL -b $cookies "$base/cliente/carta" | Out-Null
    $tiempos = New-Object System.Collections.Generic.List[double]
    $bytes = 0
    for ($i = 1; $i -le $Veces; $i++) {
        $html = & $curl -s -o NUL -b $cookies --limit-rate $Velocidad -w '%{time_total} %{size_download} %{http_code}' "$base/cliente/carta"
        $css = & $curl -s -o NUL -b $cookies --limit-rate $Velocidad -w '%{time_total}' "$base/webjars/bootstrap-icons/font/bootstrap-icons.min.css"
        $partes = $html -split ' '
        if ($partes[2] -ne '200') { throw "La carta respondio $($partes[2]) en la carga $i" }
        $total = [double]::Parse($partes[0], [Globalization.CultureInfo]::InvariantCulture) +
                 [double]::Parse($css, [Globalization.CultureInfo]::InvariantCulture)
        $tiempos.Add($total)
        $bytes = [int]$partes[1]
        Write-Progress -Activity 'Midiendo la carta' -Status "$i de $Veces" -PercentComplete ($i * 100 / $Veces)
    }

    # 3. resultado
    $ordenados = $tiempos | Sort-Object
    $p95 = $ordenados[[math]::Ceiling($Veces * 0.95) - 1]
    $promedio = ($tiempos | Measure-Object -Average).Average
    ''
    'AC-039.1 - Carta del cliente con red 4G simulada ({0} por segundo)' -f $Velocidad
    '  Cargas ........... {0}' -f $Veces
    '  Peso del HTML .... {0:N1} KB' -f ($bytes / 1024)
    '  Promedio ......... {0:N2} s' -f $promedio
    '  p95 .............. {0:N2} s' -f $p95
    if ($p95 -lt 3) { '  Resultado ........ CUMPLE (p95 menor de 3 s)' } else { '  Resultado ........ NO CUMPLE (p95 de 3 s o mas)' }
}
finally {
    Remove-Item $cookies -ErrorAction SilentlyContinue
}
