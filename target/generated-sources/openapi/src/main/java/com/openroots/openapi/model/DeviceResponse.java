package com.openroots.openapi.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.openroots.openapi.model.SensorResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.openapitools.jackson.nullable.JsonNullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * DeviceResponse
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2026-10-21T19:57:58.322158500+05:30[Asia/Calcutta]", comments = "Generator version: 7.8.0")
public class DeviceResponse {

  private UUID id;

  private UUID projectId;

  private String name;

  private String type;

  private String status;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime registeredAt;

  @Valid
  private List<SensorResponse> sensors = new ArrayList<>();

  public DeviceResponse() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public DeviceResponse(UUID id, UUID projectId, String name, String type, String status, OffsetDateTime registeredAt, List<SensorResponse> sensors) {
    this.id = id;
    this.projectId = projectId;
    this.name = name;
    this.type = type;
    this.status = status;
    this.registeredAt = registeredAt;
    this.sensors = sensors;
  }

  public DeviceResponse id(UUID id) {
    this.id = id;
    return this;
  }

  /**
   * Get id
   * @return id
   */
  @NotNull @Valid 
  @Schema(name = "id", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("id")
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public DeviceResponse projectId(UUID projectId) {
    this.projectId = projectId;
    return this;
  }

  /**
   * Get projectId
   * @return projectId
   */
  @NotNull @Valid 
  @Schema(name = "projectId", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("projectId")
  public UUID getProjectId() {
    return projectId;
  }

  public void setProjectId(UUID projectId) {
    this.projectId = projectId;
  }

  public DeviceResponse name(String name) {
    this.name = name;
    return this;
  }

  /**
   * Get name
   * @return name
   */
  @NotNull 
  @Schema(name = "name", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("name")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public DeviceResponse type(String type) {
    this.type = type;
    return this;
  }

  /**
   * Get type
   * @return type
   */
  @NotNull 
  @Schema(name = "type", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("type")
  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public DeviceResponse status(String status) {
    this.status = status;
    return this;
  }

  /**
   * Get status
   * @return status
   */
  @NotNull 
  @Schema(name = "status", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("status")
  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public DeviceResponse registeredAt(OffsetDateTime registeredAt) {
    this.registeredAt = registeredAt;
    return this;
  }

  /**
   * Get registeredAt
   * @return registeredAt
   */
  @NotNull @Valid 
  @Schema(name = "registeredAt", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("registeredAt")
  public OffsetDateTime getRegisteredAt() {
    return registeredAt;
  }

  public void setRegisteredAt(OffsetDateTime registeredAt) {
    this.registeredAt = registeredAt;
  }

  public DeviceResponse sensors(List<SensorResponse> sensors) {
    this.sensors = sensors;
    return this;
  }

  public DeviceResponse addSensorsItem(SensorResponse sensorsItem) {
    if (this.sensors == null) {
      this.sensors = new ArrayList<>();
    }
    this.sensors.add(sensorsItem);
    return this;
  }

  /**
   * Get sensors
   * @return sensors
   */
  @NotNull @Valid 
  @Schema(name = "sensors", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("sensors")
  public List<SensorResponse> getSensors() {
    return sensors;
  }

  public void setSensors(List<SensorResponse> sensors) {
    this.sensors = sensors;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DeviceResponse deviceResponse = (DeviceResponse) o;
    return Objects.equals(this.id, deviceResponse.id) &&
        Objects.equals(this.projectId, deviceResponse.projectId) &&
        Objects.equals(this.name, deviceResponse.name) &&
        Objects.equals(this.type, deviceResponse.type) &&
        Objects.equals(this.status, deviceResponse.status) &&
        Objects.equals(this.registeredAt, deviceResponse.registeredAt) &&
        Objects.equals(this.sensors, deviceResponse.sensors);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, projectId, name, type, status, registeredAt, sensors);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DeviceResponse {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    projectId: ").append(toIndentedString(projectId)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    registeredAt: ").append(toIndentedString(registeredAt)).append("\n");
    sb.append("    sensors: ").append(toIndentedString(sensors)).append("\n");
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

