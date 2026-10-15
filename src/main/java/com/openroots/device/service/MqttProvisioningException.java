package com.openroots.device.service;

public class MqttProvisioningException extends RuntimeException {

    public MqttProvisioningException(String message) {
        super(message);
    }

    public MqttProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
