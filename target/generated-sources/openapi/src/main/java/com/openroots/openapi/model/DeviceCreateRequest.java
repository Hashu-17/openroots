package com.openroots.openapi.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.openroots.openapi.model.SensorCreateRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.openapitools.jackson.nullable.JsonNullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * DeviceCreateRequest
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2026-10-21T16:57:20.315189600+05:30[Asia/Calcutta]", comments = "Generator version: 7.8.0")
public class DeviceCreateRequest {

  private String name;

  private String type;

  @Valid
  private List<@Valid SensorCreateRequest> sensors = new ArrayList<>();

  public DeviceCreateRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public DeviceCreateRequest(String name, String type, List<@Valid SensorCreateRequest> sensors) {
    this.name = name;
    this.type = type;
    this.sensors = sensors;
  }

  public DeviceCreateRequest name(String name) {
    this.name = name;
    return this;
  }

  /**
   * Get name
   * @return name
   */
  @NotNull @Size(min = 1) 
  @Schema(name = "name", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("name")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public DeviceCreateRequest type(String type) {
    this.type = type;
    return this;
  }

  /**
   * Get type
   * @return type
   */
  @NotNull @Size(min = 1) 
  @Schema(name = "type", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("type")
  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public DeviceCreateRequest sensors(List<@Valid SensorCreateRequest> sensors) {
    this.sensors = sensors;
    return this;
  }

  public DeviceCreateRequest addSensorsItem(SensorCreateRequest sensorsItem) {
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
  @NotNull @Valid @Size(min = 1) 
  @Schema(name = "sensors", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("sensors")
  public List<@Valid SensorCreateRequest> getSensors() {
    return sensors;
  }

  public void setSensors(List<@Valid SensorCreateRequest> sensors) {
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
    DeviceCreateRequest deviceCreateRequest = (DeviceCreateRequest) o;
    return Objects.equals(this.name, deviceCreateRequest.name) &&
        Objects.equals(this.type, deviceCreateRequest.type) &&
        Objects.equals(this.sensors, deviceCreateRequest.sensors);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type, sensors);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DeviceCreateRequest {\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
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

