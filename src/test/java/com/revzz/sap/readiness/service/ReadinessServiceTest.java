package com.revzz.sap.readiness.service;

import com.revzz.sap.readiness.domain.BackupRecord;
import com.revzz.sap.readiness.domain.CapacitySample;
import com.revzz.sap.readiness.domain.RestoreTest;
import com.revzz.sap.readiness.domain.Severity;
import com.revzz.sap.readiness.domain.SystemProfile;
import com.revzz.sap.readiness.domain.SystemReadiness;
import com.revzz.sap.readiness.config.AppProperties;
import com.revzz.sap.readiness.repo.ContinuityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The readiness rules, tested one at a time.
 *
 * <p>Each test builds exactly one situation, so that a failure says which
 * rule broke rather than "the score changed".</p>
 */
class ReadinessServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.now();

    private SystemProfile profile;
    private ContinuityRepository repository;

    @BeforeEach
    void setUp() {
        profile = new SystemProfile();
        profile.setName("PRD");
        profile.setSid("PRD");
        profile.setClient("000");
        profile.setDescription("Production ERP");
        profile.setOwner("BASIS.LEAD");
        profile.setCritical(true);
        profile.setRpoHours(24);
        profile.setRtoHours(4);
        profile.setMaxDbGb(2000);
    }

    // -- the rules -----------------------------------------------------------

    @Test
    @DisplayName("a failed full backup is critical even when the score is still high")
    void failedFullBackupIsCritical() {
        ReadinessService service = service(
                backups(full(BackupRecord.STATUS_FAILED, 9 * 24)),
                tests(), samples(1200, 1200));

        SystemReadiness readiness = service.assess(profile);

        assertTrue(readiness.hasFinding("BACKUP_FAILED"));
        assertEquals(Severity.CRITICAL, readiness.getSeverity());
        assertEquals(Severity.CRITICAL, finding(readiness, "BACKUP_FAILED").getSeverity());
    }

    @Test
    @DisplayName("a backup older than the RPO breaches the recovery point objective")
    void staleBackupBreachesTheRpo() {
        ReadinessService service = service(
                backups(full(BackupRecord.STATUS_OK, 48)),
                tests(), samples(1200, 1200));

        SystemReadiness readiness = service.assess(profile);

        assertTrue(readiness.hasFinding("BACKUP_STALE"));
        assertEquals(Severity.HIGH, finding(readiness, "BACKUP_STALE").getSeverity());
        assertTrue(finding(readiness, "BACKUP_STALE").getMessage().contains("24 h"));
    }

    @Test
    @DisplayName("a backup inside the RPO does not breach it")
    void freshBackupIsAccepted() {
        ReadinessService service = service(
                backups(full(BackupRecord.STATUS_OK, 5)),
                tests(), samples(1200, 1200));

        SystemReadiness readiness = service.assess(profile);

        assertFalse(readiness.hasFinding("BACKUP_STALE"));
    }

    @Test
    @DisplayName("recovery that was never tested is a high finding")
    void missingRestoreTestIsAFinding() {
        ReadinessService service = service(
                backups(full(BackupRecord.STATUS_OK, 5)),
                tests(), samples(1200, 1200));

        SystemReadiness readiness = service.assess(profile);

        assertTrue(readiness.hasFinding("NO_RESTORE_TEST"));
        assertEquals(Severity.HIGH, finding(readiness, "NO_RESTORE_TEST").getSeverity());
    }

    @Test
    @DisplayName("a failed restore test is critical")
    void failedRestoreTestIsCritical() {
        ReadinessService service = service(
                backups(full(BackupRecord.STATUS_OK, 5)),
                tests(restore(RestoreTest.RESULT_FAILED, 3)),
                samples(1200, 1200));

        SystemReadiness readiness = service.assess(profile);

        assertTrue(readiness.hasFinding("RESTORE_TEST_FAILED"));
        assertEquals(Severity.CRITICAL, readiness.getSeverity());
    }

    @Test
    @DisplayName("a restore test older than the policy is stale")
    void oldRestoreTestIsStale() {
        ReadinessService service = service(
                backups(full(BackupRecord.STATUS_OK, 5)),
                tests(restore(RestoreTest.RESULT_OK, 75)),
                samples(1200, 1200));

        SystemReadiness readiness = service.assess(profile);

        assertTrue(readiness.hasFinding("RESTORE_TEST_STALE"));
        assertEquals(Severity.HIGH, finding(readiness, "RESTORE_TEST_STALE").getSeverity());
    }

    @Test
    @DisplayName("a recent successful restore test produces no finding at all")
    void healthySystemHasNoFindings() {
        ReadinessService service = service(
                backups(full(BackupRecord.STATUS_OK, 5), incremental(1)),
                tests(restore(RestoreTest.RESULT_OK, 8)),
                samples(1200, 1210));

        SystemReadiness readiness = service.assess(profile);

        assertTrue(readiness.getFindings().isEmpty(),
                "expected no findings but got " + readiness.getFindings());
        assertEquals(100, readiness.getScore());
        assertEquals(Severity.INFO, readiness.getSeverity());
    }

    @Test
    @DisplayName("the severity is the worst finding, not the score band")
    void severityIsTheWorstFindingNotTheScoreBand() {
        // One failed restore test costs 25 points, so the score is 75 and the
        // score band would say "medium". The severity has to say "critical".
        ReadinessService service = service(
                backups(full(BackupRecord.STATUS_OK, 5), incremental(1)),
                tests(restore(RestoreTest.RESULT_FAILED, 3)),
                samples(1200, 1210));

        SystemReadiness readiness = service.assess(profile);

        assertEquals(75, readiness.getScore());
        assertEquals(Severity.CRITICAL, readiness.getSeverity());
    }

    @Test
    @DisplayName("a database that fills up within the warning window is a finding")
    void capacityRiskIsAFinding() {
        // 1290 GB used, growing 10 GB a day, maximum 1400 GB -> 11 days left.
        ReadinessService service = service(
                backups(full(BackupRecord.STATUS_OK, 5)),
                tests(restore(RestoreTest.RESULT_OK, 8)),
                samples(1000, 1290));
        profile.setMaxDbGb(1400);

        SystemReadiness readiness = service.assess(profile);

        assertTrue(readiness.hasFinding("CAPACITY_RISK"));
        assertNotNull(readiness.getForecast());
        assertTrue(readiness.getForecast().isAtRisk());
    }

    @Test
    @DisplayName("a database with plenty of room is not a finding")
    void healthyCapacityIsNotAFinding() {
        ReadinessService service = service(
                backups(full(BackupRecord.STATUS_OK, 5)),
                tests(restore(RestoreTest.RESULT_OK, 8)),
                samples(1200, 1210));
        profile.setMaxDbGb(2000);

        SystemReadiness readiness = service.assess(profile);

        assertFalse(readiness.hasFinding("CAPACITY_RISK"));
        assertFalse(readiness.getForecast().isAtRisk());
    }

    @Test
    @DisplayName("a broken backup chain is reported once, not once per rule")
    void brokenChainIsReportedOnce() {
        ReadinessService service = service(
                backups(full(BackupRecord.STATUS_FAILED, 9 * 24), incremental(9 * 24)),
                tests(), samples(1200, 1200));

        SystemReadiness readiness = service.assess(profile);

        assertTrue(readiness.hasFinding("BACKUP_FAILED"));
        assertTrue(readiness.hasFinding("BACKUP_STALE"));
        assertFalse(readiness.hasFinding("INCREMENTAL_STALE"),
                "the incremental finding would be a second report of the same problem");
        assertFalse(readiness.hasFinding("FULL_BACKUP_STALE"));
    }

    @Test
    @DisplayName("the newest recovery point is the newest successful backup of any type")
    void newestRecoveryPointIsTheNewestSuccessfulBackup() {
        // The repository hands the records over newest first, so the
        // incremental at 1 h is the newest recovery point - not the full
        // backup from 20 hours ago.
        ReadinessService service = service(
                backups(incremental(1), full(BackupRecord.STATUS_OK, 20)),
                tests(restore(RestoreTest.RESULT_OK, 8)),
                samples(1200, 1210));

        SystemReadiness readiness = service.assess(profile);

        assertNotNull(readiness.getLastGoodBackup());
        assertEquals("INCREMENTAL", readiness.getLastGoodBackup().getBackupType());
        assertFalse(readiness.hasFinding("BACKUP_STALE"),
                "the incremental at 1 h is a newer recovery point than the full at 20 h");
    }

    @Test
    @DisplayName("the score never goes below zero")
    void scoreNeverGoesNegative() {
        ReadinessService service = service(
                backups(),
                tests(),
                samples(1000, 1290));
        profile.setMaxDbGb(1400);

        SystemReadiness readiness = service.assess(profile);

        assertTrue(readiness.getScore() >= 0);
    }

    // -- helpers -------------------------------------------------------------

    private ReadinessService service(List<BackupRecord> backups, List<RestoreTest> tests,
                                     List<CapacitySample> samples) {
        repository = mock(ContinuityRepository.class);
        when(repository.findSystem(anyString())).thenReturn(profile);
        when(repository.findBackups(anyString())).thenReturn(backups);
        when(repository.findRestoreTests(anyString())).thenReturn(tests);
        when(repository.findCapacity(anyString())).thenReturn(samples);
        return new ReadinessService(repository, new CapacityService(repository, new AppProperties()),
                new AppProperties());
    }

    private List<BackupRecord> backups(BackupRecord... records) {
        List<BackupRecord> list = new ArrayList<BackupRecord>();
        for (BackupRecord record : records) {
            list.add(record);
        }
        return list;
    }

    private List<RestoreTest> tests(RestoreTest... records) {
        List<RestoreTest> list = new ArrayList<RestoreTest>();
        for (RestoreTest record : records) {
            list.add(record);
        }
        return list;
    }

    private List<CapacitySample> samples(int firstGb, int lastGb) {
        List<CapacitySample> list = new ArrayList<CapacitySample>();
        for (int day = 0; day < 30; day++) {
            CapacitySample sample = new CapacitySample();
            sample.setSystemName("PRD");
            sample.setSampleDate(LocalDate.now().minusDays(29 - day));
            sample.setDbSizeGb(firstGb + (lastGb - firstGb) * day / 29);
            sample.setLogSizeGb(100);
            list.add(sample);
        }
        return list;
    }

    private BackupRecord full(String status, int ageHours) {
        BackupRecord record = new BackupRecord();
        record.setBackupId("BKP-FULL-" + ageHours);
        record.setSystemName("PRD");
        record.setBackupType(BackupRecord.TYPE_FULL);
        record.setStartedAt(NOW.minusHours(ageHours));
        record.setFinishedAt(NOW.minusHours(ageHours - 1));
        record.setDurationMinutes(60);
        record.setSizeGb(900);
        record.setStatus(status);
        record.setMedia("TAPE");
        return record;
    }

    private BackupRecord incremental(int ageHours) {
        BackupRecord record = new BackupRecord();
        record.setBackupId("BKP-INC-" + ageHours);
        record.setSystemName("PRD");
        record.setBackupType(BackupRecord.TYPE_INCREMENTAL);
        record.setStartedAt(NOW.minusHours(ageHours));
        record.setFinishedAt(NOW.minusHours(ageHours));
        record.setDurationMinutes(10);
        record.setSizeGb(20);
        record.setStatus(BackupRecord.STATUS_OK);
        record.setMedia("DISK");
        return record;
    }

    private RestoreTest restore(String result, int ageDays) {
        RestoreTest test = new RestoreTest();
        test.setTestId("RST-001");
        test.setSystemName("PRD");
        test.setTestedAt(NOW.minusDays(ageDays));
        test.setResult(result);
        test.setDurationMinutes(90);
        test.setPerformedBy("BASIS.LEAD");
        test.setNotes("Restored into the DR system.");
        return test;
    }

    private com.revzz.sap.readiness.domain.ReadinessFinding finding(SystemReadiness readiness,
                                                                     String category) {
        for (com.revzz.sap.readiness.domain.ReadinessFinding finding : readiness.getFindings()) {
            if (category.equals(finding.getCategory())) {
                return finding;
            }
        }
        throw new AssertionError("no finding with category " + category);
    }
}
