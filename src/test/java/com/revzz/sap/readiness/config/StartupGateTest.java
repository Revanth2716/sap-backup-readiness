package com.revzz.sap.readiness.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The start-up gate.
 *
 * <p>Spring Boot opens the port before the seeders have run. The gate keeps
 * the application closed until they have, so nobody is served a landscape
 * that is only half built.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class StartupGateTest {

    @Autowired
    private StartupGate gate;

    @Test
    @DisplayName("the application is open once the seeders have run")
    void theApplicationIsOpenAfterStartup() {
        assertTrue(gate.isReady(),
                "the gate must be open by the time the application serves requests");
    }
}
