package com.openroots.device.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Keeps each device's Mosquitto login in step with its credentials. One broker login per device:
 * username is the device UUID, password is the most recently issued credential, and the
 * {@code device-publisher} role lets it publish only to its own telemetry topic.
 *
 * <p>Every change waits for Mosquitto's dynamic-security response and throws
 * {@link MqttProvisioningException} on any failure, so callers never report success for a
 * credential the broker does not accept.
 */
@Service
public class MosquittoDeviceProvisioner {

    static final String CONTROL_TOPIC = "$CONTROL/dynamic-security/v1";
    static final String RESPONSE_TOPIC = "$CONTROL/dynamic-security/v1/response";
    static final String DEVICE_ROLE = "device-publisher";
    private static final String CLIENT_NOT_FOUND = "Client not found";

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

    /** Sets the device's broker password and enables its login, creating the login if needed. */
    public void setPassword(UUID deviceId, String password) {
        if (!requireEnabled()) {
            return;
        }
        ObjectNode set = command("setClientPassword").put("username", deviceId.toString()).put("password", password);
        List<JsonNode> responses = send(
                List.of(set, command("enableClient").put("username", deviceId.toString())),
                List.of(CLIENT_NOT_FOUND));
        if (isError(responses.get(0), CLIENT_NOT_FOUND)) {
            ObjectNode create = command("createClient")
                    .put("username", deviceId.toString())
                    .put("password", password)
                    .put("textname", "OpenRoots device " + deviceId);
            create.putArray("roles").addObject().put("rolename", DEVICE_ROLE).put("priority", 10);
            send(List.of(create), List.of());
        }
    }

    /** Disables the device's broker login. A login that does not exist is already disabled. */
    public void disable(UUID deviceId) {
        if (!requireEnabled()) {
            return;
        }
        send(List.of(command("disableClient").put("username", deviceId.toString())), List.of(CLIENT_NOT_FOUND));
    }

    /** Deletes the device's broker login. A login that does not exist is already gone. */
    public void delete(UUID deviceId) {
        if (!requireEnabled()) {
            return;
        }
        send(List.of(command("deleteClient").put("username", deviceId.toString())), List.of(CLIENT_NOT_FOUND));
    }

    private ObjectNode command(String name) {
        return objectMapper.createObjectNode().put("command", name);
    }

    /**
     * Sends the commands in one request and checks each response. Errors listed in
     * {@code tolerated} are accepted; any other error fails the operation.
     */
    private List<JsonNode> send(List<ObjectNode> commands, List<String> tolerated) {
        ArrayNode array = objectMapper.createArrayNode();
        List<String> correlation = new ArrayList<>();
        for (ObjectNode command : commands) {
            String id = UUID.randomUUID().toString();
            command.put("correlationData", id);
            correlation.add(id);
            array.add(command);
        }
        ObjectNode request = objectMapper.createObjectNode();
        request.set("commands", array);

        List<JsonNode> responses = exchange(request, correlation);
        for (int index = 0; index < responses.size(); index++) {
            JsonNode response = responses.get(index);
            if (response.hasNonNull("error") && !tolerated.contains(response.get("error").asText())) {
                throw new MqttProvisioningException("Mosquitto rejected the device login change: "
                        + response.get("error").asText());
            }
        }
        return responses;
    }

    protected List<JsonNode> exchange(ObjectNode request, List<String> correlation) {
        MqttClient client = null;
        try {
            client = new MqttClient("tcp://" + brokerHost + ":" + brokerPort,
                    "openroots-provisioner-" + UUID.randomUUID(), new MemoryPersistence());
            // Mosquitto sends every control response to every admin client subscribed to the
            // response topic, so keep only the response that carries our correlation ID.
            String ours = correlation.get(0);
            ArrayBlockingQueue<JsonNode> responses = new ArrayBlockingQueue<>(1);
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    // A lost connection surfaces as a missing response below.
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    if (!RESPONSE_TOPIC.equals(topic)) {
                        return;
                    }
                    try {
                        JsonNode parsed = objectMapper.readTree(message.getPayload());
                        if (ours.equals(parsed.path("responses").path(0).path("correlationData").asText())) {
                            responses.offer(parsed);
                        }
                    } catch (java.io.IOException ignored) {
                        // Not a response we can read, so not ours.
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Confirmation comes from the response topic.
                }
            });

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(responseTimeoutSeconds);
            options.setUserName(adminUsername);
            options.setPassword(adminPassword.toCharArray());
            client.connect(options);
            client.subscribe(RESPONSE_TOPIC, 1);

            MqttMessage message = new MqttMessage(objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            client.publish(CONTROL_TOPIC, message);

            JsonNode response = responses.poll(responseTimeoutSeconds, TimeUnit.SECONDS);
            if (response == null) {
                throw new MqttProvisioningException("Mosquitto did not confirm the device login change");
            }
            return matchResponses(response, correlation);
        } catch (MqttProvisioningException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MqttProvisioningException("Interrupted while updating the Mosquitto device login", exception);
        } catch (MqttException | java.io.IOException exception) {
            throw new MqttProvisioningException("Could not update the Mosquitto device login", exception);
        } finally {
            closeQuietly(client);
        }
    }

    List<JsonNode> matchResponses(JsonNode body, List<String> correlation) {
        JsonNode responses = body.path("responses");
        if (!responses.isArray() || responses.size() != correlation.size()) {
            throw new MqttProvisioningException("Mosquitto returned an invalid dynamic-security response");
        }
        List<JsonNode> ordered = new ArrayList<>();
        for (int index = 0; index < correlation.size(); index++) {
            JsonNode response = responses.get(index);
            if (!correlation.get(index).equals(response.path("correlationData").asText())) {
                throw new MqttProvisioningException("Mosquitto returned a mismatched dynamic-security response");
            }
            ordered.add(response);
        }
        return ordered;
    }

    private static boolean isError(JsonNode response, String error) {
        return response.hasNonNull("error") && error.equals(response.get("error").asText());
    }

    private static void closeQuietly(MqttClient client) {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (MqttException ignored) {
            // The broker operation has already completed or failed.
        }
    }

    private boolean requireEnabled() {
        if (!enabled) {
            return false;
        }
        if (adminUsername.isBlank() || adminPassword.isBlank()) {
            throw new MqttProvisioningException("Mosquitto admin credentials are not configured");
        }
        return true;
    }
}
