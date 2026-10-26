package com.openroots.config;

import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import com.openroots.ingestion.mqtt.MqttTelemetryConsumer;

@Configuration
public class MqttConfig {

    @Bean
    DefaultMqttPahoClientFactory mqttClientFactory(
            @Value("${openroots.mqtt.url:tcp://localhost:1883}") String brokerUrl,
            @Value("${openroots.mqtt.username:}") String username,
            @Value("${openroots.mqtt.password:}") String password) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] { brokerUrl });
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(20);
        if (!username.isBlank()) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        }

        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    MqttPahoMessageDrivenChannelAdapter mqttInbound(
            DefaultMqttPahoClientFactory mqttClientFactory,
            @Value("${openroots.mqtt.topic:openroots/v1/devices/+/telemetry}") String topic) {
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                "openroots-spring-" + UUID.randomUUID(),
                mqttClientFactory,
                topic);
        adapter.setQos(1);
        adapter.setCompletionTimeout(5_000);
        return adapter;
    }

    @Bean
    TaskExecutor mqttTelemetryExecutor(
            @Value("${openroots.mqtt.consumer.core-pool-size:4}") int corePoolSize,
            @Value("${openroots.mqtt.consumer.max-pool-size:10}") int maxPoolSize,
            @Value("${openroots.mqtt.consumer.queue-capacity:1000}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("mqtt-telemetry-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean
    IntegrationFlow mqttTelemetryFlow(
            MqttPahoMessageDrivenChannelAdapter mqttInbound,
            TaskExecutor mqttTelemetryExecutor,
            MqttTelemetryConsumer consumer) {
        return IntegrationFlow.from(mqttInbound)
                .channel(new ExecutorChannel(mqttTelemetryExecutor))
                .handle(consumer)
                .get();
    }
}
