param(
    [string]$SettingsPath = (Join-Path $PSScriptRoot ".env")
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $SettingsPath -PathType Leaf)) {
    throw "Settings file not found: $SettingsPath. Copy examples/.env.example to examples/.env and fill in the test device credentials."
}

# Read only the two provisioning values. This supports the simple KEY=value
# format used by examples/.env.example, with optional single or double quotes.
$settings = @{}
foreach ($line in Get-Content -LiteralPath $SettingsPath) {
    if ($line -match '^\s*(MQTT_SMOKE_DEVICE_ID|MQTT_SMOKE_DEVICE_PASSWORD)\s*=\s*(.*?)\s*$') {
        $value = $Matches[2]
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $settings[$Matches[1]] = $value
    }
}

$deviceId = $settings["MQTT_SMOKE_DEVICE_ID"]
$devicePassword = $settings["MQTT_SMOKE_DEVICE_PASSWORD"]
if ([string]::IsNullOrWhiteSpace($deviceId) -or $deviceId -notmatch '^[0-9a-fA-F-]{36}$') {
    throw "Set MQTT_SMOKE_DEVICE_ID in $SettingsPath to the registered device UUID."
}
if ([string]::IsNullOrWhiteSpace($devicePassword)) {
    throw "Set MQTT_SMOKE_DEVICE_PASSWORD in $SettingsPath to a test password."
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Push-Location $repoRoot
try {
    # Pass values as positional arguments so shell characters in the password
    # are not interpreted by the container shell.
    $createCommand = 'mosquitto_ctrl -h 127.0.0.1 -u "$MQTT_ADMIN_USERNAME" -P "$MQTT_ADMIN_PASSWORD" dynsec createClient "$1" -p "$2"'
    & docker compose exec -T mosquitto sh -c $createCommand sh $deviceId $devicePassword
    if ($LASTEXITCODE -ne 0) {
        throw "Mosquitto could not create the client. If this UUID already has an MQTT client, delete or update that client before rerunning."
    }

    $roleCommand = 'mosquitto_ctrl -h 127.0.0.1 -u "$MQTT_ADMIN_USERNAME" -P "$MQTT_ADMIN_PASSWORD" dynsec addClientRole "$1" device-publisher'
    & docker compose exec -T mosquitto sh -c $roleCommand sh $deviceId
    if ($LASTEXITCODE -ne 0) {
        throw "The MQTT client was created, but assigning device-publisher failed. Check the broker and rerun after resolving the error."
    }

    Write-Host "Provisioned MQTT publisher for device $deviceId."
    Write-Host "Use the same UUID and password as GitHub secrets MQTT_SMOKE_DEVICE_ID and MQTT_SMOKE_DEVICE_PASSWORD."
}
finally {
    Pop-Location
}
