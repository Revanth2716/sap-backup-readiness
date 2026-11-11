package com.revzz.sap.readiness.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Holds the application closed until the start-up work is finished.
 *
 * <p>Spring Boot starts the web server <em>before</em> the
 * {@code CommandLineRunner}s have run, so for the first few hundred
 * milliseconds the application answers requests with a half-built
 * landscape — a dashboard that shows three systems instead of five, and a
 * score computed over the ones that happen to exist.</p>
 *
 * <p>That window is short, but it is real: a load balancer that probes the
 * application the moment the port opens sees an empty dashboard, and
 * somebody refreshing the browser twice gets two different answers.</p>
 *
 * <p>The gate opens after every seeder has run. The runner below has the
 * highest order, so it is always the last one.</p>
 */
@Component
@Order(100)
public class StartupGate implements CommandLineRunner {

    private final AtomicBoolean ready = new AtomicBoolean(false);

    @Override
    public void run(String... args) {
        ready.set(true);
    }

    /** True once the start-up work is done. */
    public boolean isReady() {
        return ready.get();
    }
}
