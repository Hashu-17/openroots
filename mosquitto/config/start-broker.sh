#!/bin/sh
set -eu

: "${MQTT_DEV_DEVICE_ID:?Set MQTT_DEV_DEVICE_ID to a registered device UUID}"
: "${MQTT_DEV_DEVICE_PASSWORD:?Set MQTT_DEV_DEVICE_PASSWORD}"
: "${MQTT_INGESTOR_PASSWORD:?Set MQTT_INGESTOR_PASSWORD}"

if ! printf '%s' "$MQTT_DEV_DEVICE_ID" | grep -Eq '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'; then
  echo "MQTT_DEV_DEVICE_ID must be a lowercase UUID" >&2
  exit 1
fi

PASSWD_FILE=/mosquitto/data/passwd
ACL_FILE=/mosquitto/data/aclfile
DEVICE_USER=$MQTT_DEV_DEVICE_ID

umask 077

# This dev-only setup passes credentials to mosquitto_passwd via argv.
mosquitto_passwd -b -c "$PASSWD_FILE" "$DEVICE_USER" "$MQTT_DEV_DEVICE_PASSWORD"
mosquitto_passwd -b "$PASSWD_FILE" telemetry-ingestor "$MQTT_INGESTOR_PASSWORD"

printf '%s\n' \
  "user $DEVICE_USER" \
  "topic write openroots/v1/devices/$DEVICE_USER/telemetry" \
  '' \
  'user telemetry-ingestor' \
  'topic read openroots/v1/devices/+/telemetry' > "$ACL_FILE"

chown mosquitto:mosquitto "$PASSWD_FILE" "$ACL_FILE"
chmod 600 "$PASSWD_FILE"
chmod 640 "$ACL_FILE"

exec /usr/sbin/mosquitto -c /mosquitto/config/mosquitto.conf
