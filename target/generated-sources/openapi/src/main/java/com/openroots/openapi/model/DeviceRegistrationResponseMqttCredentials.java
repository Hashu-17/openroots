package com.openroots.openapi.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.UUID;
import org.openapitools.jackson.nullable.JsonNullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * Null when MQTT provisioning is disabled.
 */

@Schema(name = "DeviceRegistrationResponse_mqttCredentials", description = "Null when MQTT provisioning is disabled.")
@JsonTypeName("DeviceRegistrationResponse_mqttCredentials")
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2026-10-21T20:32:16.593104+05:30[Asia/Calcutta]", comments = "Generator version: 7.8.0")
public class DeviceRegistrationResponseMqttCredentials {

  private UUID username;

  private String password;

  private String publishTopic;

  public DeviceRegistrationResponseMqttCredentials() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public DeviceRegistrationResponseMqttCredentials(UUID username, String password, String publishTopic) {
    this.username = username;
    this.password = password;
    this.publishTopic = publishTopic;
  }

  public DeviceRegistrationResponseMqttCredentials username(UUID username) {
    this.username = username;
    return this;
  }

  /**
   * Get username
   * @return username
   */
  @NotNull @Valid 
  @Schema(name = "username", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("username")
  public UUID getUsername() {
    return username;
  }

  public void setUsername(UUID username) {
    this.username = username;
  }

  public DeviceRegistrationResponseMqttCredentials password(String password) {
    this.password = password;
    return this;
  }

  /**
   * Get password
   * @return password
   */
  @NotNull 
  @Schema(name = "password", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("password")
  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public DeviceRegistrationResponseMqttCredentials publishTopic(String publishTopic) {
    this.publishTopic = publishTopic;
    return this;
  }

  /**
   * Get publishTopic
   * @return publishTopic
   */
  @NotNull 
  @Schema(name = "publishTopic", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("publishTopic")
  public String getPublishTopic() {
    return publishTopic;
  }

  public void setPublishTopic(String publishTopic) {
    this.publishTopic = publishTopic;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DeviceRegistrationResponseMqttCredentials deviceRegistrationResponseMqttCredentials = (DeviceRegistrationResponseMqttCredentials) o;
    return Objects.equals(this.username, deviceRegistrationResponseMqttCredentials.username) &&
        Objects.equals(this.password, deviceRegistrationResponseMqttCredentials.password) &&
        Objects.equals(this.publishTopic, deviceRegistrationResponseMqttCredentials.publishTopic);
  }

  @Override
  public int hashCode() {
    return Objects.hash(username, password, publishTopic);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DeviceRegistrationResponseMqttCredentials {\n");
    sb.append("    username: ").append(toIndentedString(username)).append("\n");
    sb.append("    password: ").append(toIndentedString(password)).append("\n");
    sb.append("    publishTopic: ").append(toIndentedString(publishTopic)).append("\n");
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

