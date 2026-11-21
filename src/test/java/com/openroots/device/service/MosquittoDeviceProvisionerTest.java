package com.openroots.device.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MosquittoDeviceProvisionerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID deviceId = UUID.randomUUID();

    /** Records each broker request and answers with the errors configured per command name. */
    private final class ScriptedBroker extends MosquittoDeviceProvisioner {
        final List<List<String>> requests = new ArrayList<>();
        final Map<String, String> errors;

        ScriptedBroker(boolean enabled, Map<String, String> errors) {
            super(objectMapper, enabled, "localhost", 1883, "admin", "secret", 5);
            this.errors = errors;
        }

        @Override
        protected List<JsonNode> exchange(ObjectNode request, List<String> correlation) {
            List<String> names = new ArrayList<>();
            List<JsonNode> responses = new ArrayList<>();
            ArrayNode commands = (ArrayNode) request.get("commands");
            for (int index = 0; index < commands.size(); index++) {
                String name = commands.get(index).get("command").asText();
                names.add(name);
                ObjectNode response = objectMapper.createObjectNode();
                response.put("correlationData", correlation.get(index));
                if (errors.containsKey(name)) {
                    response.put("error", errors.get(name));
                }
                responses.add(response);
            }
            requests.add(names);
            return responses;
        }
    }

    @Test
    void settingAPasswordUpdatesAndEnablesAnExistingLogin() {
        ScriptedBroker broker = new ScriptedBroker(true, Map.of());

        broker.setPassword(deviceId, "new-secret");

        assertThat(broker.requests).containsExactly(List.of("setClientPassword", "enableClient"));
    }

    @Test
    void settingAPasswordCreatesTheLoginWhenItDoesNotExist() {
        ScriptedBroker broker = new ScriptedBroker(true, Map.of("setClientPassword", "Client not found",
                "enableClient", "Client not found"));

        broker.setPassword(deviceId, "new-secret");

        assertThat(broker.requests).containsExactly(List.of("setClientPassword", "enableClient"), List.of("createClient"));
    }

    @Test
    void disablingAMissingLoginIsNotAnError() {
        ScriptedBroker broker = new ScriptedBroker(true, Map.of("disableClient", "Client not found"));

        broker.disable(deviceId);

        assertThat(broker.requests).containsExactly(List.of("disableClient"));
    }

    @Test
    void anUnexpectedBrokerErrorFailsTheOperation() {
        ScriptedBroker broker = new ScriptedBroker(true, Map.of("setClientPassword", "Not authorised"));

        assertThatThrownBy(() -> broker.setPassword(deviceId, "new-secret"))
                .isInstanceOf(MqttProvisioningException.class)
                .hasMessageContaining("Not authorised");
    }

    @Test
    void aResponseForAnotherRequestIsRejected() throws Exception {
        ScriptedBroker broker = new ScriptedBroker(true, Map.of());
        JsonNode body = objectMapper.readTree("{\"responses\":[{\"correlationData\":\"someone-else\"}]}");

        assertThatThrownBy(() -> broker.matchResponses(body, List.of("expected")))
                .isInstanceOf(MqttProvisioningException.class)
                .hasMessageContaining("mismatched");
    }

    @Test
    void aMissingResponseIsRejected() throws Exception {
        ScriptedBroker broker = new ScriptedBroker(true, Map.of());
        JsonNode body = objectMapper.readTree("{\"responses\":[]}");

        assertThatThrownBy(() -> broker.matchResponses(body, List.of("expected")))
                .isInstanceOf(MqttProvisioningException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void whenProvisioningIsDisabledNothingIsSent() {
        ScriptedBroker broker = new ScriptedBroker(false, Map.of());

        broker.setPassword(deviceId, "new-secret");
        broker.disable(deviceId);
        broker.delete(deviceId);

        assertThat(broker.requests).isEmpty();
    }
}
