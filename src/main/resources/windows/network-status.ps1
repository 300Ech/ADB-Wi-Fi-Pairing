# Estado de la red y del Firewall de Windows para el diagnóstico de ADB Wi-Fi. Una línea por dato,
# separada por tabuladores; la lee WindowsNetworkParser. Los cmdlets devuelven los valores de sus enums
# en inglés en cualquier idioma de Windows.
$ErrorActionPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

# Tipo de red de cada conexión: Public, Private o DomainAuthenticated.
Get-NetConnectionProfile | ForEach-Object { "connection`t$($_.NetworkCategory)`t$($_.InterfaceAlias)" }

# Perfiles con el firewall activado (almacén efectivo: incluye la directiva de grupo).
Get-NetFirewallProfile -PolicyStore ActiveStore | ForEach-Object { "firewall`t$($_.Name)`t$($_.Enabled)" }

# Reglas de entrada activas del adb.exe que usa el plugin. Las rutas de las reglas pueden llevar
# variables como %USERPROFILE%: se expanden antes de comparar.
$adb = $env:ADB_WIFI_ADB
if ($adb) {
    Get-NetFirewallApplicationFilter -PolicyStore ActiveStore |
        Where-Object { [Environment]::ExpandEnvironmentVariables($_.Program) -ieq $adb } |
        Get-NetFirewallRule |
        Where-Object { $_.Enabled -eq 'True' -and $_.Direction -eq 'Inbound' } |
        ForEach-Object { "rule`t$($_.Action)`t$($_.Profile)" }
}
exit 0
