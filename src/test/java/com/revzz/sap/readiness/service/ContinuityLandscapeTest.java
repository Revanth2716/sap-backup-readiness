package com.revzz.sap.readiness.service;

import com.revzz.sap.readiness.domain.CapacityForecast;
import com.revzz.sap.readiness.domain.SystemProfile;
import com.revzz.sap.readiness.domain.SystemReadiness;
import com.revzz.sap.readiness.domain.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seeded landscape.
 *
 * <p>These tests check the story the demo data has to tell. If the seeding
 * changes and the story changes with it, these tests are the ones that say
 * so first.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ContinuityLandscapeTest {

    @Autowired
    private ReadinessService readinessService;

    @Test
    @DisplayName("the landscape holds five systems")
    void fiveSystems() {
        assertEquals(5, readinessService.systems().size());
        assertEquals(5, readinessService.readiness().size());
    }

    @Test
    @DisplayName("PRD is critical - the backup chain stopped nine days ago")
    void prdIsCritical() {
        SystemReadiness prd = readinessService.assess("PRD");

        assertNotNull(prd);
        assertEquals(Severity.CRITICAL, prd.getSeverity());
        assertTrue(prd.hasFinding("BACKUP_FAILED"),
                "the last full backup of PRD failed and nothing ran since");
        assertTrue(prd.hasFinding("BACKUP_STALE"),
                "the newest recovery point is older than the 24 h RPO");
        assertEquals(25, prd.getScore());
    }

    @Test
    @DisplayName("DEV is critical - the backups are fine but the restore failed")
    void devIsCriticalBecauseTheRestoreFailed() {
        SystemReadiness dev = readinessService.assess("DEV");

        assertNotNull(dev);
        assertEquals(Severity.CRITICAL, dev.getSeverity());
        assertTrue(dev.hasFinding("RESTORE_TEST_FAILED"));
        assertFalse(dev.hasFinding("BACKUP_STALE"),
                "the backups of DEV are current - the restore is the problem");
        assertFalse(dev.hasFinding("NO_RESTORE_TEST"));
    }

    @Test
    @DisplayName("BW is high - the restore test is 75 days old and the database fills up")
    void bwIsHigh() {
        SystemReadiness bw = readinessService.assess("BW");

        assertNotNull(bw);
        assertEquals(Severity.HIGH, bw.getSeverity());
        assertTrue(bw.hasFinding("RESTORE_TEST_STALE"));
        assertTrue(bw.hasFinding("CAPACITY_RISK"));
        assertNotNull(bw.getForecast());
        assertTrue(bw.getForecast().isAtRisk());
        assertTrue(bw.getForecast().getDaysToFull() > 0
                && bw.getForecast().getDaysToFull() <= 30,
                "BW fills up within the warning window, was " + bw.getForecast().getDaysToFull());
    }

    @Test
    @DisplayName("QA is high - recovery has never been tested")
    void qaHasNoRestoreTest() {
        SystemReadiness qa = readinessService.assess("QA");

        assertNotNull(qa);
        assertEquals(Severity.HIGH, qa.getSeverity());
        assertTrue(qa.hasFinding("NO_RESTORE_TEST"));
        assertFalse(qa.hasFinding("BACKUP_STALE"));
        assertFalse(qa.hasFinding("INCREMENTAL_STALE"));
        assertFalse(qa.hasFinding("CAPACITY_RISK"));
    }

    @Test
    @DisplayName("SOL is healthy - a full score and no findings")
    void solIsHealthy() {
        SystemReadiness sol = readinessService.assess("SOL");

        assertNotNull(sol);
        assertEquals(100, sol.getScore());
        assertEquals(Severity.INFO, sol.getSeverity());
        assertTrue(sol.getFindings().isEmpty(),
                "SOL should have no findings but had " + sol.getFindings());
    }

    @Test
    @DisplayName("the landscape is sorted worst first")
    void sortedWorstFirst() {
        List<SystemReadiness> readiness = readinessService.readiness();

        assertEquals("PRD", readiness.get(0).getProfile().getName());
        assertEquals("DEV", readiness.get(1).getProfile().getName());
        assertEquals("SOL", readiness.get(4).getProfile().getName());
    }

    @Test
    @DisplayName("every finding carries a recommended action")
    void everyFindingHasAnAction() {
        for (com.revzz.sap.readiness.domain.ReadinessFinding finding
                : readinessService.allFindings()) {
            assertNotNull(finding.getRecommendation());
            assertFalse(finding.getRecommendation().trim().isEmpty(),
                    "finding " + finding.getCategory() + " has no recommendation");
            assertFalse(finding.getMessage().trim().isEmpty());
        }
    }

    @Test
    @DisplayName("the counts add up to the landscape")
    void countsAddUp() {
        int counted = readinessService.countBySeverity(Severity.CRITICAL)
                + readinessService.countBySeverity(Severity.HIGH)
                + readinessService.countBySeverity(Severity.MEDIUM)
                + readinessService.countBySeverity(Severity.LOW)
                + readinessService.countBySeverity(Severity.INFO);

        assertEquals(readinessService.systems().size(), counted);
        assertEquals(2, readinessService.countBySeverity(Severity.CRITICAL));
        assertEquals(2, readinessService.countBySeverity(Severity.HIGH));
        assertEquals(1, readinessService.countBySeverity(Severity.INFO));
    }

    @Test
    @DisplayName("the report names every system and every finding")
    void reportCoversEverything() {
        String report = readinessService.renderReport();

        assertTrue(report.contains("# Continuity readiness report"));
        for (String system : new String[]{"PRD", "DEV", "BW", "QA", "SOL"}) {
            assertTrue(report.contains(system), "the report does not mention " + system);
        }
        assertTrue(report.contains("BACKUP_FAILED"));
        assertTrue(report.contains("NO_RESTORE_TEST"));
        assertTrue(report.contains("## Recovery evidence"));
        assertTrue(report.contains("## Capacity"));
    }
}
