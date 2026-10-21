package com.example.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.UUID;
import org.openapitools.jackson.nullable.JsonNullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * Returned once during registration when MQTT provisioning is enabled.
 */

@Schema(name = "MqttDeviceCredentials", description = "Returned once during registration when MQTT provisioning is enabled.")
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2026-10-17T21:33:36.711015800+05:30[Asia/Calcutta]", comments = "Generator version: 7.8.0")
public class MqttDeviceCredentials {

  private UUID username;

  private String password;

  private String publishTopic;

  public MqttDeviceCredentials() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public MqttDeviceCredentials(UUID username, String password, String publishTopic) {
    this.username = username;
    this.password = password;
    this.publishTopic = publishTopic;
  }

  public MqttDeviceCredentials username(UUID username) {
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

  public MqttDeviceCredentials password(String password) {
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

  public MqttDeviceCredentials publishTopic(String publishTopic) {
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
    MqttDeviceCredentials mqttDeviceCredentials = (MqttDeviceCredentials) o;
    return Objects.equals(this.username, mqttDeviceCredentials.username) &&
        Objects.equals(this.password, mqttDeviceCredentials.password) &&
        Objects.equals(this.publishTopic, mqttDeviceCredentials.publishTopic);
  }

  @Override
  public int hashCode() {
    return Objects.hash(username, password, publishTopic);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class MqttDeviceCredentials {\n");
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

