package com.openroots.device.dto;

public record MqttDeviceCredentials(String username, String password, String publishTopic) {
}
