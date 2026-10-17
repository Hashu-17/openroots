#!/bin/sh
set -eu

: "${MQTT_ADMIN_USERNAME:?Set MQTT_ADMIN_USERNAME}"
: "${MQTT_ADMIN_PASSWORD:?Set MQTT_ADMIN_PASSWORD}"
: "${MQTT_INGESTOR_USERNAME:?Set MQTT_INGESTOR_USERNAME}"
: "${MQTT_INGESTOR_PASSWORD:?Set MQTT_INGESTOR_PASSWORD}"

DYNSEC_FILE=/mosquitto/data/dynamic-security.json
BROKER_PID=

if [ ! -f "$DYNSEC_FILE" ]; then
  mosquitto_ctrl dynsec init "$DYNSEC_FILE" "$MQTT_ADMIN_USERNAME" "$MQTT_ADMIN_PASSWORD"
  chmod 600 "$DYNSEC_FILE"
fi
chown mosquitto:mosquitto "$DYNSEC_FILE"

broker_control() {
  mosquitto_ctrl -h 127.0.0.1 -p 1883 \
    -u "$MQTT_ADMIN_USERNAME" -P "$MQTT_ADMIN_PASSWORD" dynsec "$@"
}

stop_bootstrap_broker() {
  if [ -n "$BROKER_PID" ] && kill -0 "$BROKER_PID" 2>/dev/null; then
    kill -TERM "$BROKER_PID"
    wait "$BROKER_PID" || true
  fi
}

trap stop_bootstrap_broker EXIT INT TERM
/usr/sbin/mosquitto -c /mosquitto/config/mosquitto.conf &
BROKER_PID=$!

ready=false
for attempt in $(seq 1 30); do
  if broker_control getDefaultACLAccess publishClientSend >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done

if [ "$ready" != true ]; then
  echo "Mosquitto did not become ready for dynamic-security setup" >&2
  exit 1
fi

broker_control setDefaultACLAccess publishClientSend deny
broker_control setDefaultACLAccess subscribe deny
broker_control setDefaultACLAccess publishClientReceive deny
broker_control setDefaultACLAccess unsubscribe allow

if ! broker_control getRole device-publisher 2>&1 | grep -q '^Rolename:'; then
  broker_control createRole device-publisher
  broker_control addRoleACL device-publisher publishClientSend \
    'openroots/v1/devices/%u/telemetry' allow 10
fi

if ! broker_control getRole telemetry-ingestor 2>&1 | grep -q '^Rolename:'; then
  broker_control createRole telemetry-ingestor
  broker_control addRoleACL telemetry-ingestor subscribePattern \
    'openroots/v1/devices/+/telemetry' allow 10
  broker_control addRoleACL telemetry-ingestor publishClientReceive \
    'openroots/v1/devices/+/telemetry' allow 10
fi

if broker_control getClient "$MQTT_INGESTOR_USERNAME" 2>&1 | grep -q '^Username:'; then
  broker_control setClientPassword "$MQTT_INGESTOR_USERNAME" "$MQTT_INGESTOR_PASSWORD"
else
  broker_control createClient "$MQTT_INGESTOR_USERNAME" -p "$MQTT_INGESTOR_PASSWORD"
  broker_control addClientRole "$MQTT_INGESTOR_USERNAME" telemetry-ingestor 10
fi

stop_bootstrap_broker
BROKER_PID=
trap - EXIT INT TERM
exec /usr/sbin/mosquitto -c /mosquitto/config/mosquitto.conf
