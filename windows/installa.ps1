# Irbas TV per Windows: un piccolo "ponte" sul PC. Apre una pagina nel browser da cui si fa tutto:
# trovare la TV sulla Wi-Fi, autorizzarla, installare Irbas TV, spegnere la schermata originale (o rimetterla).
# Non serve il terminale: si avvia con doppio clic su Installa-Irbas-TV.bat.
param([switch]$Ripristina)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Qui = Split-Path -Parent $MyInvocation.MyCommand.Path
$Repo = (Get-Content (Join-Path $Qui 'repo.txt') -ErrorAction SilentlyContinue | Select-Object -First 1)
$Dir = Join-Path $env:LOCALAPPDATA 'IrbasTV'
New-Item -ItemType Directory -Force -Path $Dir | Out-Null
$AdbExe = Join-Path $Dir 'platform-tools\adb.exe'
$Apk = Join-Path $Dir 'IrbasTV.apk'
$Port = 8797
$Launchers = @('timvision.launcher', 'com.google.android.apps.tv.launcherx', 'com.google.android.tvlauncher', 'com.google.android.leanbacklauncher')
$Svc = 'it.irbas.tv/it.irbas.tv.HomeKeyService'
$script:ApkReady = $false

# adb senza che gli errori del programma fermino tutto
function Adb([string[]]$a) {
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $out = (& $AdbExe @a 2>&1 | ForEach-Object { "$_" }) -join "`n" }
    finally { $ErrorActionPreference = $old }
    return "$out".Trim()
}

function Prepare {
    if (-not (Test-Path $AdbExe)) {
        Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile (Join-Path $Dir 'pt.zip') -UseBasicParsing
        Expand-Archive (Join-Path $Dir 'pt.zip') $Dir -Force
    }
    if (-not $script:ApkReady) {
        if (-not $Repo) { throw "Manca repo.txt accanto a questo file: scarica di nuovo lo zip dalla pagina di Irbas TV." }
        Invoke-WebRequest "https://github.com/$Repo/releases/latest/download/IrbasTV.apk" -OutFile $Apk -UseBasicParsing
        $script:ApkReady = $true
    }
    Adb @('start-server') | Out-Null
}

# Cerca sulla rete di casa i dispositivi con il debug di rete acceso (porta 5555)
function Scan {
    $found = New-Object System.Collections.ArrayList
    $locals = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and $_.PrefixLength -ge 16 -and $_.PrefixLength -le 30 }
    foreach ($local in $locals) {
        $base = (($local.IPAddress -split '\.')[0..2]) -join '.'
        $tries = New-Object System.Collections.ArrayList
        for ($i = 1; $i -le 254; $i++) {
            $ip = "$base.$i"
            if ($ip -eq $local.IPAddress) { continue }
            $client = New-Object Net.Sockets.TcpClient
            [void]$tries.Add(@{ ip = $ip; client = $client; task = $client.ConnectAsync($ip, 5555) })
        }
        Start-Sleep -Milliseconds 1500
        foreach ($t in $tries) {
            if ($t.task.Status -eq 'RanToCompletion' -and -not $found.Contains($t.ip)) { [void]$found.Add($t.ip) }
            try { $t.client.Close() } catch { }
        }
    }
    return ,$found.ToArray()
}

function Target($ip) {
    $clean = ("$ip".Trim()) -replace ':5555$', ''
    if ($clean -notmatch '^\d{1,3}(\.\d{1,3}){3}$') { throw "Indirizzo non valido: $ip" }
    return "${clean}:5555"
}

function Connect($ip) {
    $tv = Target $ip
    Adb @('connect', $tv) | Out-Null
    $list = Adb @('devices')
    $state = 'offline'
    foreach ($line in ($list -split "`n")) {
        if ($line -match ('^' + [regex]::Escape($tv) + '\s+(\S+)')) { $state = $Matches[1] }
    }
    $name = ''
    if ($state -eq 'device') { $name = Adb @('-s', $tv, 'shell', 'getprop', 'ro.product.model') }
    return @{ state = $state; name = $name }
}

function Install($ip, $hide) {
    $tv = Target $ip
    $log = New-Object System.Collections.ArrayList
    $result = Adb @('-s', $tv, 'install', '-r', $Apk)
    if ($result -notmatch 'Success') { throw "Installazione non riuscita: $result" }
    [void]$log.Add('Irbas TV installata')
    Adb @('-s', $tv, 'shell', 'pm', 'grant', 'it.irbas.tv', 'android.permission.RECORD_AUDIO') | Out-Null
    Adb @('-s', $tv, 'shell', 'appops', 'set', 'it.irbas.tv', 'GET_USAGE_STATS', 'allow') | Out-Null
    Adb @('-s', $tv, 'shell', 'cmd', 'package', 'set-home-activity', 'it.irbas.tv/.HomeActivity') | Out-Null
    Adb @('-s', $tv, 'shell', 'cmd', 'role', 'add-role-holder', 'android.app.role.HOME', 'it.irbas.tv') | Out-Null
    Adb @('-s', $tv, 'shell', 'cmd', 'role', 'add-role-holder', 'android.app.role.ASSISTANT', 'it.irbas.tv') | Out-Null
    [void]$log.Add('Microfono e schermata principale')
    $current = Adb @('-s', $tv, 'shell', 'settings', 'get', 'secure', 'enabled_accessibility_services')
    if ($current -notmatch [regex]::Escape($Svc)) {
        if (-not $current -or $current -eq 'null') { $new = $Svc } else { $new = "${current}:$Svc" }
        Adb @('-s', $tv, 'shell', 'settings', 'put', 'secure', 'enabled_accessibility_services', $new) | Out-Null
    }
    Adb @('-s', $tv, 'shell', 'settings', 'put', 'secure', 'accessibility_enabled', '1') | Out-Null
    [void]$log.Add('Tasto Home di Irbas')
    if ($hide) {
        foreach ($p in $Launchers) {
            $there = Adb @('-s', $tv, 'shell', 'pm', 'list', 'packages', $p)
            if ($there -match ('package:' + [regex]::Escape($p) + '(\s|$)')) {
                Adb @('-s', $tv, 'shell', 'pm', 'disable-user', '--user', '0', $p) | Out-Null
                [void]$log.Add("Schermata originale spenta ($p)")
            }
        }
    }
    Adb @('-s', $tv, 'shell', 'am', 'start', '-n', 'it.irbas.tv/.HomeActivity') | Out-Null
    return @{ ok = $true; log = $log.ToArray() }
}

function Restore($ip) {
    $tv = Target $ip
    foreach ($p in $Launchers) { Adb @('-s', $tv, 'shell', 'pm', 'enable', $p) | Out-Null }
    Adb @('-s', $tv, 'shell', 'cmd', 'role', 'remove-role-holder', 'android.app.role.HOME', 'it.irbas.tv') | Out-Null
    Adb @('-s', $tv, 'shell', 'cmd', 'role', 'remove-role-holder', 'android.app.role.ASSISTANT', 'it.irbas.tv') | Out-Null
    $current = Adb @('-s', $tv, 'shell', 'settings', 'get', 'secure', 'enabled_accessibility_services')
    $rest = (($current -split ':') | Where-Object { $_ -and $_ -ne 'null' -and $_ -ne $Svc }) -join ':'
    if ($rest) { Adb @('-s', $tv, 'shell', 'settings', 'put', 'secure', 'enabled_accessibility_services', $rest) | Out-Null }
    else { Adb @('-s', $tv, 'shell', 'settings', 'delete', 'secure', 'enabled_accessibility_services') | Out-Null }
    Adb @('-s', $tv, 'shell', 'input', 'keyevent', 'KEYCODE_HOME') | Out-Null
    return @{ ok = $true }
}

# ---- il server della pagina (solo su questo PC: 127.0.0.1) ----

function Reply($stream, [string]$status, [string]$type, [byte[]]$bytes) {
    $head = "HTTP/1.1 $status`r`nContent-Type: $type`r`nContent-Length: $($bytes.Length)`r`nCache-Control: no-store`r`nConnection: close`r`n`r`n"
    $h = [Text.Encoding]::ASCII.GetBytes($head)
    $stream.Write($h, 0, $h.Length)
    $stream.Write($bytes, 0, $bytes.Length)
    $stream.Flush()
}

function ReplyJson($stream, $object) {
    Reply $stream '200 OK' 'application/json; charset=utf-8' ([Text.Encoding]::UTF8.GetBytes(($object | ConvertTo-Json -Depth 6 -Compress)))
}

$listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $Port)
try { $listener.Start() } catch {
    Start-Process "http://localhost:$Port/"
    Write-Host "Irbas TV e' gia' aperto nel browser."
    exit 0
}
$start = "http://localhost:$Port/"
if ($Ripristina) { $start += '#ripristina' }
Start-Process $start
Write-Host ""
Write-Host "  Irbas TV: la pagina di installazione e' aperta nel browser." -ForegroundColor Cyan
Write-Host "  Non chiudere questa finestra finche' non hai finito."
Write-Host "  (Se il browser non si e' aperto, vai su $start)"

$running = $true
while ($running) {
    $client = $listener.AcceptTcpClient()
    try {
        $stream = $client.GetStream()
        $reader = New-Object IO.StreamReader($stream, [Text.Encoding]::UTF8, $false, 8192, $true)
        $first = $reader.ReadLine()
        if (-not $first) { continue }
        $parts = $first.Split(' ')
        $method = $parts[0]
        $path = $parts[1]
        $length = 0
        while ($true) {
            $header = $reader.ReadLine()
            if ($header -eq $null -or $header -eq '') { break }
            if ($header -match '^Content-Length:\s*(\d+)') { $length = [int]$Matches[1] }
        }
        $body = $null
        if ($length -gt 0) {
            $buffer = New-Object char[] $length
            $read = 0
            while ($read -lt $length) {
                $n = $reader.Read($buffer, $read, $length - $read)
                if ($n -le 0) { break }
                $read += $n
            }
            $text = [string]::new($buffer, 0, $read)
            if ($text) { $body = $text | ConvertFrom-Json }
        }

        try {
            switch ("$method $path") {
                'GET /' {
                    Reply $stream '200 OK' 'text/html; charset=utf-8' ([IO.File]::ReadAllBytes((Join-Path $Qui 'installer.html')))
                }
                'POST /api/prepare' { Prepare; ReplyJson $stream @{ ok = $true } }
                'POST /api/scan' { ReplyJson $stream @{ ok = $true; ips = @(Scan) } }
                'POST /api/connect' { $r = Connect $body.ip; $r.ok = $true; ReplyJson $stream $r }
                'POST /api/install' { ReplyJson $stream (Install $body.ip ([bool]$body.hide)) }
                'POST /api/restore' { ReplyJson $stream (Restore $body.ip) }
                'POST /api/quit' { ReplyJson $stream @{ ok = $true }; $running = $false }
                default { Reply $stream '404 Not Found' 'text/plain' ([Text.Encoding]::UTF8.GetBytes('?')) }
            }
        } catch {
            ReplyJson $stream @{ ok = $false; error = $_.Exception.Message }
        }
    } catch {
        # una richiesta andata male non ferma il ponte
    } finally {
        $client.Close()
    }
}
$listener.Stop()
Write-Host "Fatto. Puoi chiudere questa finestra."
