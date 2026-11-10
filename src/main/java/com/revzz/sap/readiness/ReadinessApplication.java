package com.revzz.sap.readiness;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * SAP Backup &amp; Restore Readiness.
 *
 * <p>Answers the three questions a continuity review always asks and a backup
 * dashboard never does:</p>
 *
 * <ol>
 *   <li>Are the backups current, measured against the agreed recovery point
 *       objective rather than against "did the job fire"?</li>
 *   <li>Has recovery actually been proven - and when was it last proven?</li>
 *   <li>Is there room for the data to keep growing?</li>
 * </ol>
 *
 * <p>The result is a per-system readiness score with the findings behind it,
 * so the score is never the only thing anybody has to argue about.</p>
 */
@SpringBootApplication
public class ReadinessApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReadinessApplication.class, args);
    }

    /** Entry point for a WAR deployment on the SAP NetWeaver Java stack. */
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(ReadinessApplication.class);
    }
}
