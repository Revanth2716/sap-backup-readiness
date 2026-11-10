package com.revzz.sap.readiness.seed;

import com.revzz.sap.readiness.config.AppProperties;
import com.revzz.sap.readiness.domain.BackupRecord;
import com.revzz.sap.readiness.domain.CapacitySample;
import com.revzz.sap.readiness.domain.RestoreTest;
import com.revzz.sap.readiness.domain.SystemProfile;
import com.revzz.sap.readiness.repo.ContinuityRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Random;

/**
 * Seeds a landscape with a continuity story.
 *
 * <p>Five systems, each with the problem a real customer has:</p>
 *
 * <ul>
 *   <li><b>PRD</b> - the backup chain has <em>stopped</em>. The last full
 *       backup failed nine days ago, nothing has run since, and the last
 *       successful full backup is ten days old against an RPO of 24 hours.</li>
 *   <li><b>DEV</b> - the backups are fine and current, but the last restore
 *       test <em>failed</em>.</li>
 *   <li><b>BW</b> - the restore test is 75 days old and the database fills
 *       up in about three weeks.</li>
 *   <li><b>QA</b> - everything is current, and recovery has <em>never</em>
 *       been tested.</li>
 *   <li><b>SOL</b> - healthy, and a restore test eight days ago proves it.</li>
 * </ul>
 *
 * <p>Random noise would prove nothing. Every number here exists because it
 * demonstrates one specific finding.</p>
 */
@Component
@Order(2)
public class ContinuitySeeder implements CommandLineRunner {

    private final JdbcTemplate jdbc;
    private final ContinuityRepository repository;
    private final AppProperties properties;

    public ContinuitySeeder(JdbcTemplate jdbc, ContinuityRepository repository,
                            AppProperties properties) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        if (!properties.isSeedDemoData() || repository.countSystems() > 0) {
            return;
        }
        seedUsers();
        Random random = new Random(20261110L);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime anchor = nightAnchor(now);

        // --- the landscape -------------------------------------------------
        system("PRD", "PRD", "000", "Production ERP", "BASIS.LEAD", true, 24, 4, 2000);
        system("DEV", "DVE", "300", "Development ERP", "REVANTH.BASIS", false, 48, 24, 500);
        system("BW", "BWD", "200", "Analytics and reporting", "PRIVILEGED", false, 48, 12, 1000);
        system("QA", "QAS", "100", "Quality system", "DEVADMIN", false, 48, 8, 800);
        system("SOL", "S4H", "000", "S/4HANA core", "BASIS.LEAD", true, 4, 2, 3000);

        // --- PRD: the whole chain stopped nine days ago ---------------------
        // The failed full backup is the newest attempt, so there is no
        // successful full backup behind it that is newer than the failure.
        dailyFulls("PRD", anchor, 30, random, 9, true, 900);
        incrementals("PRD", now, 12, random, 216, 4);
        restoreTest("PRD", now.minusDays(20), "OK", 95, "BASIS.LEAD",
                "Restored to the DR system in 95 minutes, data verified against the source.");

        // --- DEV: backups fine, the restore test failed ----------------------
        dailyFulls("DEV", anchor, 30, random, 0, false, 240);
        incrementals("DEV", now, 3, random, 4, 4);
        restoreTest("DEV", now.minusDays(5), "FAILED", 210, "REVANTH.BASIS",
                "Restore aborted after 210 minutes - the archive chain was incomplete.");

        // --- BW: the restore test is old and the database is filling up -------
        dailyFulls("BW", anchor, 30, random, 0, false, 700);
        incrementals("BW", now, 3, random, 6, 6);
        restoreTest("BW", now.minusDays(75), "OK", 140, "PRIVILEGED",
                "Restore to the reporting sandbox completed successfully.");

        // --- QA: everything is current, recovery has never been tested -------
        dailyFulls("QA", anchor, 30, random, 0, false, 200);
        incrementals("QA", now, 3, random, 2, 4);
        // deliberately no restore test for QA

        // --- SOL: healthy ---------------------------------------------------
        dailyFulls("SOL", anchor, 30, random, 0, false, 1100);
        incrementals("SOL", now, 3, random, 1, 2);
        restoreTest("SOL", now.minusDays(8), "OK", 65, "BASIS.LEAD",
                "Full restore into the acceptance system verified in 65 minutes.");

        // --- capacity history ------------------------------------------------
        capacity("PRD", 1200, 2, 260);
        capacity("DEV", 300, 1, 120);
        capacity("BW", 700, 6, 90);
        capacity("QA", 400, 1, 60);
        capacity("SOL", 1150, 1, 180);
    }

    /**
     * The most recent 22:00 that is already in the past.
     *
     * <p>Without this the "nightly" backup of the current day could be
     * scheduled in the future, and a backup that has not happened yet would
     * look like a backup that is about to happen - or worse, like a stale
     * one.</p>
     */
    private LocalDateTime nightAnchor(LocalDateTime now) {
        LocalDateTime anchor = now.withHour(22).withMinute(0).withSecond(0).withNano(0);
        if (!anchor.isBefore(now)) {
            anchor = anchor.minusDays(1);
        }
        return anchor;
    }

    private void seedUsers() {
        Object[][] users = {
                {"BASIS.LEAD", "Basis Lead", "A", "IT-Basis"},
                {"REVANTH.BASIS", "Revanth Basis", "A", "IT-Basis"},
                {"DEVADMIN", "Dev Administrator", "A", "IT-Basis"},
                {"PRIVILEGED", "Privileged User", "A", "IT-Basis"},
                {"ANIL.HR", "Anil HR", "A", "HR"},
        };
        for (Object[] user : users) {
            jdbc.update("INSERT INTO SAPUSER (USERID, FULLNAME, UFLAG, DEPT) VALUES (?,?,?,?)",
                    user[0], user[1], user[2], user[3]);
        }
    }

    private void system(String name, String sid, String client, String description, String owner,
                        boolean critical, int rpo, int rto, int maxDbGb) {
        SystemProfile profile = new SystemProfile();
        profile.setName(name);
        profile.setSid(sid);
        profile.setClient(client);
        profile.setDescription(description);
        profile.setOwner(owner);
        profile.setCritical(critical);
        profile.setRpoHours(rpo);
        profile.setRtoHours(rto);
        profile.setMaxDbGb(maxDbGb);
        repository.insertSystem(profile);
    }

    /**
     * One full backup per night.
     *
     * @param anchor        the most recent 22:00 in the past
     * @param days          how many nights back to go
     * @param failedDaysAgo the night whose backup failed (0 = none failed)
     * @param failed        whether a backup failed at all
     * @param sizeGb        the size of the backup
     */
    private void dailyFulls(String system, LocalDateTime anchor, int days, Random random,
                            int failedDaysAgo, boolean failed, int sizeGb) {
        for (int day = 0; day <= days; day++) {
            // When a backup failed and nothing has run since, there is no
            // successful full backup more recent than the failure - that is
            // the whole point of the finding.
            if (failed && failedDaysAgo > 0 && day < failedDaysAgo) {
                continue;
            }
            LocalDateTime started = anchor.minusDays(day);
            boolean thisFailed = failed && day == failedDaysAgo;

            BackupRecord record = new BackupRecord();
            record.setBackupId("BKP" + system + "F" + String.format("%03d", day));
            record.setSystemName(system);
            record.setBackupType(BackupRecord.TYPE_FULL);
            record.setStartedAt(started);
            record.setFinishedAt(started.plusMinutes(45 + random.nextInt(60)));
            record.setDurationMinutes(45 + random.nextInt(60));
            record.setSizeGb(sizeGb);
            record.setStatus(thisFailed ? BackupRecord.STATUS_FAILED : BackupRecord.STATUS_OK);
            record.setMedia("TAPE-ARC-0" + (1 + random.nextInt(4)));
            repository.insertBackup(record);
        }
    }

    /**
     * Incrementals at a fixed interval.
     *
     * @param hoursAgo    the age of the most recent incremental
     * @param everyHours  the interval between them
     */
    private void incrementals(String system, LocalDateTime now, int days, Random random,
                              int hoursAgo, int everyHours) {
        int counter = 0;
        int limit = days * 24;
        for (int offset = hoursAgo; offset <= limit; offset += everyHours) {
            LocalDateTime started = now.minusHours(offset);
            counter++;
            BackupRecord record = new BackupRecord();
            record.setBackupId("BKP" + system + "I" + String.format("%03d", counter));
            record.setSystemName(system);
            record.setBackupType(BackupRecord.TYPE_INCREMENTAL);
            record.setStartedAt(started);
            record.setFinishedAt(started.plusMinutes(6 + random.nextInt(20)));
            record.setDurationMinutes(6 + random.nextInt(20));
            record.setSizeGb(12 + random.nextInt(30));
            record.setStatus(BackupRecord.STATUS_OK);
            record.setMedia("DISK-0" + (1 + random.nextInt(3)));
            repository.insertBackup(record);
        }
    }

    private void restoreTest(String system, LocalDateTime testedAt, String result, int duration,
                             String performedBy, String notes) {
        RestoreTest test = new RestoreTest();
        test.setTestId("RST" + system + String.format("%03d", Math.abs(testedAt.hashCode() % 1000)));
        test.setSystemName(system);
        test.setTestedAt(testedAt);
        test.setResult(result);
        test.setDurationMinutes(duration);
        test.setPerformedBy(performedBy);
        test.setNotes(notes);
        repository.insertRestoreTest(test);
    }

    /** A daily capacity history with a constant daily growth. */
    private void capacity(String system, int startGb, int growthGb, int logGb) {
        LocalDate today = LocalDate.now();
        for (int day = 29; day >= 0; day--) {
            CapacitySample sample = new CapacitySample();
            sample.setSystemName(system);
            sample.setSampleDate(today.minusDays(day));
            sample.setDbSizeGb(startGb + growthGb * (29 - day));
            sample.setLogSizeGb(logGb);
            repository.insertCapacity(sample);
        }
    }
}
