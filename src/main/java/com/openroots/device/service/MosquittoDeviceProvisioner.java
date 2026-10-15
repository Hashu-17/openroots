package com.openroots.device.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openroots.device.dto.MqttDeviceCredentials;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MosquittoDeviceProvisioner {

    private static final String CONTROL_TOPIC = "$CONTROL/dynamic-security/v1";
    private static final String RESPONSE_TOPIC = "$CONTROL/dynamic-security/v1/response";
    private static final String DEVICE_ROLE = "device-publisher";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String brokerHost;
    private final int brokerPort;
    private final String adminUsername;
    private final String adminPassword;
    private final int responseTimeoutSeconds;

    public MosquittoDeviceProvisioner(
            ObjectMapper objectMapper,
            @Value("${openroots.mqtt.provisioning-enabled:false}") boolean enabled,
            @Value("${openroots.mqtt.broker-host:localhost}") String brokerHost,
            @Value("${openroots.mqtt.broker-port:1883}") int brokerPort,
            @Value("${openroots.mqtt.admin-username:}") String adminUsername,
            @Value("${openroots.mqtt.admin-password:}") String adminPassword,
            @Value("${openroots.mqtt.response-timeout-seconds:5}") int responseTimeoutSeconds) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.brokerHost = brokerHost;
        this.brokerPort = brokerPort;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.responseTimeoutSeconds = responseTimeoutSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public MqttDeviceCredentials provision(UUID deviceId) {
        requireEnabled();
        String password = generatePassword();

        ObjectNode command = objectMapper.createObjectNode()
                .put("command", "createClient")
                .put("username", deviceId.toString())
                .put("password", password)
                .put("textname", "OpenRoots device " + deviceId);
        ArrayNode roles = command.putArray("roles");
        roles.addObject().put("rolename", DEVICE_ROLE).put("priority", 10);

        sendCommand(command);
        return new MqttDeviceCredentials(
                deviceId.toString(),
                password,
                "openroots/v1/devices/" + deviceId + "/telemetry");
    }

    public void revoke(UUID deviceId) {
        requireEnabled();
        ObjectNode command = objectMapper.createObjectNode()
                .put("command", "deleteClient")
                .put("username", deviceId.toString());
        sendCommand(command);
    }

    private void sendCommand(ObjectNode command) {
        String correlationData = UUID.randomUUID().toString();
        command.put("correlationData", correlationData);
        ObjectNode request = objectMapper.createObjectNode();
        request.putArray("commands").add(command);

        String clientId = "openroots-provisioner-" + UUID.randomUUID();
        MqttClient client = null;
        try {
            client = new MqttClient(
                    "tcp://" + brokerHost + ":" + brokerPort,
                    clientId,
                    new MemoryPersistence());
            ArrayBlockingQueue<byte[]> responses = new ArrayBlockingQueue<>(1);
            client.setCallback(new org.eclipse.paho.client.mqttv3.MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    // The caller observes this as a timeout if no broker response arrives.
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    if (RESPONSE_TOPIC.equals(topic)) {
                        responses.offer(message.getPayload());
                    }
                }

                @Override
                public void deliveryComplete(
                        org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
                    // The response topic confirms whether the dynamic-security command succeeded.
                }
            });

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(responseTimeoutSeconds);
            options.setUserName(adminUsername);
            options.setPassword(adminPassword.toCharArray());
            client.connect(options);
            client.subscribe(RESPONSE_TOPIC, 1);

            MqttMessage message = new MqttMessage(
                    objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            client.publish(CONTROL_TOPIC, message);

            byte[] responseBytes = responses.poll(responseTimeoutSeconds, TimeUnit.SECONDS);
            if (responseBytes == null) {
                throw new MqttProvisioningException("Mosquitto did not confirm the device credential change");
            }
            requireSuccessfulResponse(responseBytes, correlationData);
        } catch (MqttProvisioningException exception) {
            throw exception;
        } catch (MqttException | java.io.IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new MqttProvisioningException("Could not provision the device with Mosquitto", exception);
        } finally {
            if (client != null && client.isConnected()) {
                try {
                    client.disconnect();
                } catch (MqttException ignored) {
                    // The broker operation has already completed or failed.
                }
            }
            if (client != null) {
                try {
                    client.close();
                } catch (MqttException ignored) {
                    // Releasing this short-lived client is best effort after disconnect.
                }
            }
        }
    }

    private void requireSuccessfulResponse(byte[] responseBytes, String correlationData)
            throws java.io.IOException {
        JsonNode response = objectMapper.readTree(responseBytes);
        JsonNode responses = response.path("responses");
        if (!responses.isArray() || responses.isEmpty()) {
            throw new MqttProvisioningException("Mosquitto returned an invalid dynamic-security response");
        }

        JsonNode result = responses.get(0);
        if (!correlationData.equals(result.path("correlationData").asText())) {
            throw new MqttProvisioningException("Mosquitto returned a mismatched provisioning response");
        }
        if (result.hasNonNull("error")) {
            throw new MqttProvisioningException("Mosquitto rejected the device credential change");
        }
    }

    private String generatePassword() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new MqttProvisioningException("MQTT device provisioning is disabled");
        }
        if (adminUsername.isBlank() || adminPassword.isBlank()) {
            throw new MqttProvisioningException("Mosquitto admin credentials are not configured");
        }
    }
}
