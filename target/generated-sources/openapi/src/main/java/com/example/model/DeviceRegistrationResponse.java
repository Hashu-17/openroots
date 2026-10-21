package com.example.model;

import java.net.URI;
import java.util.Objects;
import com.example.model.DeviceResponse;
import com.example.model.MqttDeviceCredentials;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import org.openapitools.jackson.nullable.JsonNullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * DeviceRegistrationResponse
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2026-10-17T21:33:36.711015800+05:30[Asia/Calcutta]", comments = "Generator version: 7.8.0")
public class DeviceRegistrationResponse {

  private DeviceResponse device;

  private JsonNullable<MqttDeviceCredentials> mqttCredentials = JsonNullable.<MqttDeviceCredentials>undefined();

  public DeviceRegistrationResponse() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public DeviceRegistrationResponse(DeviceResponse device, MqttDeviceCredentials mqttCredentials) {
    this.device = device;
    this.mqttCredentials = JsonNullable.of(mqttCredentials);
  }

  public DeviceRegistrationResponse device(DeviceResponse device) {
    this.device = device;
    return this;
  }

  /**
   * Get device
   * @return device
   */
  @NotNull @Valid 
  @Schema(name = "device", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("device")
  public DeviceResponse getDevice() {
    return device;
  }

  public void setDevice(DeviceResponse device) {
    this.device = device;
  }

  public DeviceRegistrationResponse mqttCredentials(MqttDeviceCredentials mqttCredentials) {
    this.mqttCredentials = JsonNullable.of(mqttCredentials);
    return this;
  }

  /**
   * Null when MQTT provisioning is disabled.
   * @return mqttCredentials
   */
  @NotNull @Valid 
  @Schema(name = "mqttCredentials", description = "Null when MQTT provisioning is disabled.", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("mqttCredentials")
  public JsonNullable<MqttDeviceCredentials> getMqttCredentials() {
    return mqttCredentials;
  }

  public void setMqttCredentials(JsonNullable<MqttDeviceCredentials> mqttCredentials) {
    this.mqttCredentials = mqttCredentials;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DeviceRegistrationResponse deviceRegistrationResponse = (DeviceRegistrationResponse) o;
    return Objects.equals(this.device, deviceRegistrationResponse.device) &&
        Objects.equals(this.mqttCredentials, deviceRegistrationResponse.mqttCredentials);
  }

  @Override
  public int hashCode() {
    return Objects.hash(device, mqttCredentials);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DeviceRegistrationResponse {\n");
    sb.append("    device: ").append(toIndentedString(device)).append("\n");
    sb.append("    mqttCredentials: ").append(toIndentedString(mqttCredentials)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

