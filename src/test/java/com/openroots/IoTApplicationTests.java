package com.openroots;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Needs a reachable Postgres/Timescale database (see docker-compose.yml). The JWT secret has no
// default outside the dev profile, so the test supplies its own.
@SpringBootTest(properties = "openroots.jwt.secret=dGVzdC1vbmx5LWp3dC1zZWNyZXQtMzItYnl0ZXMtbWluaW11bS0wMDAx")
class IoTApplicationTests {

    @Test
    void contextLoads() {
    }
}
