package com.openroots;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts the full application against a throwaway TimescaleDB container (the same image as
 * docker-compose.yml), so Flyway runs every migration and Hibernate validates the schema.
 * Runs in {@code mvn verify} and needs Docker. The MQTT broker is not started; the inbound
 * adapter just keeps retrying in the background, which does not affect the context.
 */
@Testcontainers
@SpringBootTest(properties = "openroots.jwt.secret=dGVzdC1vbmx5LWp3dC1zZWNyZXQtMzItYnl0ZXMtbWluaW11bS0wMDAx")
class IoTApplicationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb:2.17.2-pg16").asCompatibleSubstituteFor("postgres"));

    @Test
    void contextLoads() {
    }
}
