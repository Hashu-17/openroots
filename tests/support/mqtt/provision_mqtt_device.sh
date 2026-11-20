#!/usr/bin/env bash
set -euo pipefail

: "${MQTT_SMOKE_DEVICE_ID:?Set MQTT_SMOKE_DEVICE_ID}"
: "${MQTT_SMOKE_DEVICE_PASSWORD:?Set MQTT_SMOKE_DEVICE_PASSWORD}"

case "$MQTT_SMOKE_DEVICE_PASSWORD" in
  *$'\n'*|*$'\r'*)
    echo 'MQTT_SMOKE_DEVICE_PASSWORD must be a single-line value.' >&2
    exit 1
    ;;
esac

printf '%s\n' "$MQTT_SMOKE_DEVICE_PASSWORD" |
  docker exec -i openroots-mosquitto sh -c '
    set -eu
    DEVICE_ID=$1
    IFS= read -r DEVICE_PASSWORD

    dynsec() {
      OUTPUT=$(mosquitto_ctrl -h 127.0.0.1 -u "$MQTT_ADMIN_USERNAME" \
        -P "$MQTT_ADMIN_PASSWORD" dynsec "$@" 2>&1) || {
        printf "%s\n" "$OUTPUT" >&2
        return 1
      }
      if printf "%s\n" "$OUTPUT" | grep -Fq "Error:"; then
        printf "%s\n" "$OUTPUT" >&2
        return 1
      fi
      printf "%s\n" "$OUTPUT"
    }

    DETAILS=$(mosquitto_ctrl -h 127.0.0.1 -u "$MQTT_ADMIN_USERNAME" \
      -P "$MQTT_ADMIN_PASSWORD" dynsec getClient "$DEVICE_ID" 2>&1 || true)
    if printf "%s\n" "$DETAILS" | grep -Fq "Error: Client not found"; then
      dynsec createClient "$DEVICE_ID" -p "$DEVICE_PASSWORD" >/dev/null
      dynsec addClientRole "$DEVICE_ID" device-publisher 10 >/dev/null
    elif printf "%s\n" "$DETAILS" | grep -Fq "Username:"; then
      dynsec setClientPassword "$DEVICE_ID" "$DEVICE_PASSWORD" >/dev/null
      if ! printf "%s\n" "$DETAILS" | grep -Fq "device-publisher"; then
        dynsec addClientRole "$DEVICE_ID" device-publisher 10 >/dev/null
      fi
    else
      printf "%s\n" "$DETAILS" >&2
      echo "Could not determine whether broker client $DEVICE_ID exists" >&2
      exit 1
    fi

    DETAILS=$(dynsec getClient "$DEVICE_ID")
    printf "%s\n" "$DETAILS" | grep -Fq "Username:"
    printf "%s\n" "$DETAILS" | grep -Fq "device-publisher"
    echo "Provisioned smoke-test MQTT account for $DEVICE_ID"
  ' sh "$MQTT_SMOKE_DEVICE_ID"
