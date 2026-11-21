package com.openroots.device.service;

/** Thrown when Mosquitto does not confirm a device credential change. The caller's transaction rolls back. */
public class MqttProvisioningException extends RuntimeException {

    public MqttProvisioningException(String message) {
        super(message);
    }

    public MqttProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
