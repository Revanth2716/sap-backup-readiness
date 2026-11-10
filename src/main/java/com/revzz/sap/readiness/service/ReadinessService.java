package com.revzz.sap.readiness.service;

import com.revzz.sap.readiness.config.AppProperties;
import com.revzz.sap.readiness.domain.BackupRecord;
import com.revzz.sap.readiness.domain.CapacityForecast;
import com.revzz.sap.readiness.domain.ReadinessFinding;
import com.revzz.sap.readiness.domain.RestoreTest;
import com.revzz.sap.readiness.domain.Severity;
import com.revzz.sap.readiness.domain.SystemProfile;
import com.revzz.sap.readiness.domain.SystemReadiness;
import com.revzz.sap.readiness.repo.ContinuityRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The readiness engine.
 *
 * <p>Three questions, one per system:</p>
 *
 * <ol>
 *   <li><b>Are the backups current?</b> — measured against the agreed
 *       recovery point objective, and against the <em>newest recoverable
 *       backup</em>, not against "did the job fire". A job that ran and
 *       failed is worse than one that never ran, because it looks fine on
 *       the dashboard.</li>
 *   <li><b>Has recovery been proven?</b> — a restore test, and how old it is.
 *       This is the question a backup dashboard never asks, and it is the
 *       one the auditor always does.</li>
 *   <li><b>Is there room?</b> — the capacity forecast.</li>
 * </ol>
 *
 * <p>The score is 100 minus the penalties, and it exists so a portfolio of
 * thirty systems can be sorted. The <em>severity</em> is the worst finding,
 * so a failed restore test can never be hidden behind an average score.</p>
 */
@Service
public class ReadinessService {

    /** Penalty for each finding, and the severity it carries. */
    private static final int PENALTY_NO_BACKUP = 45;
    private static final int PENALTY_FAILED_BACKUP = 45;
    private static final int PENALTY_BACKUP_AGE = 30;
    private static final int PENALTY_FULL_BACKUP_AGE = 15;
    private static final int PENALTY_NO_INCREMENTAL = 15;
    private static final int PENALTY_INCREMENTAL_AGE = 15;
    private static final int PENALTY_NO_RESTORE_TEST = 30;
    private static final int PENALTY_OLD_RESTORE_TEST = 30;
    private static final int PENALTY_FAILED_RESTORE_TEST = 25;
    private static final int PENALTY_CAPACITY = 20;

    private final ContinuityRepository repository;
    private final CapacityService capacityService;
    private final AppProperties properties;

    public ReadinessService(ContinuityRepository repository,
                            CapacityService capacityService,
                            AppProperties properties) {
        this.repository = repository;
        this.capacityService = capacityService;
        this.properties = properties;
    }

    public List<SystemProfile> systems() {
        return repository.findSystems();
    }

    /** The readiness of every system, worst first. */
    public List<SystemReadiness> readiness() {
        List<SystemReadiness> results = new ArrayList<SystemReadiness>();
        for (SystemProfile profile : repository.findSystems()) {
            results.add(assess(profile));
        }
        Collections.sort(results, new Comparator<SystemReadiness>() {
            @Override
            public int compare(SystemReadiness left, SystemReadiness right) {
                int bySeverity = left.getSeverity().rank() - right.getSeverity().rank();
                return bySeverity != 0 ? bySeverity : left.getScore() - right.getScore();
            }
        });
        return results;
    }

    /** The readiness of one system, or null when the system is unknown. */
    public SystemReadiness assess(String systemName) {
        SystemProfile profile = repository.findSystem(systemName);
        return profile == null ? null : assess(profile);
    }

    /** The readiness of one system. */
    public SystemReadiness assess(SystemProfile profile) {
        SystemReadiness readiness = new SystemReadiness();
        readiness.setProfile(profile);

        List<ReadinessFinding> findings = new ArrayList<ReadinessFinding>();
        int penalty = 0;
        LocalDateTime now = LocalDateTime.now();

        // -- 1. are the backups current? -------------------------------------
        List<BackupRecord> backups = repository.findBackups(profile.getName());

        // The newest full backup of any status - this is what tells us
        // whether the last attempt failed.
        BackupRecord lastFullAttempt = firstOfType(backups, BackupRecord.TYPE_FULL);
        // The newest backup that actually produced a usable recovery point.
        BackupRecord lastGoodBackup = firstSuccessful(backups);
        // The newest incremental of any status.
        BackupRecord lastIncremental = firstOfType(backups, BackupRecord.TYPE_INCREMENTAL);

        readiness.setLastFullBackup(lastFullAttempt);
        readiness.setLastIncremental(lastIncremental);
        readiness.setLastGoodBackup(lastGoodBackup);

        // A broken chain is reported once, by its most specific cause. Three
        // overlapping findings for one broken backup job would only make the
        // report longer, not clearer.
        boolean chainBroken = false;

        if (lastFullAttempt != null && !lastFullAttempt.isSuccessful()) {
            chainBroken = true;
            penalty += PENALTY_FAILED_BACKUP;
            BackupRecord lastGoodFull = firstSuccessfulOfType(backups, BackupRecord.TYPE_FULL);
            findings.add(new ReadinessFinding(Severity.CRITICAL, "BACKUP_FAILED",
                    profile.getName(),
                    "The last full backup of " + profile.getName() + " failed ("
                            + lastFullAttempt.getBackupId() + ", "
                            + lastFullAttempt.ageHours(now) + " h ago)"
                            + (lastGoodFull == null ? " and no full backup has succeeded before it."
                            : "; the last successful full backup is "
                            + lastGoodFull.ageHours(now) + " h old."),
                    "Re-run the failed backup and read the backup log - a failed backup that "
                            + "nobody noticed is the normal way this goes wrong."));
        }

        if (lastGoodBackup == null) {
            chainBroken = true;
            penalty += PENALTY_NO_BACKUP;
            findings.add(new ReadinessFinding(Severity.CRITICAL, "NO_BACKUP",
                    profile.getName(),
                    "No successful backup has ever been recorded for " + profile.getName() + ".",
                    "Run a backup and check that it is monitored. Without a recovery point there "
                            + "is no recovery, whatever the job schedule says."));
        } else {
            int age = lastGoodBackup.ageHours(now);
            if (age > profile.getRpoHours()) {
                chainBroken = true;
                penalty += PENALTY_BACKUP_AGE;
                findings.add(new ReadinessFinding(Severity.HIGH, "BACKUP_STALE",
                        profile.getName(),
                        "The newest recoverable backup is " + age + " h old ("
                                + lastGoodBackup.getBackupId() + "), beyond the agreed RPO of "
                                + profile.getRpoHours() + " h.",
                        "Bring the backup schedule back in line with the RPO, and check the job "
                                + "log for why it did not run."));
            }
        }

        if (!chainBroken) {
            if (lastIncremental == null) {
                penalty += PENALTY_NO_INCREMENTAL;
                findings.add(new ReadinessFinding(Severity.MEDIUM, "NO_INCREMENTAL",
                        profile.getName(),
                        "No incremental backup has been recorded for " + profile.getName() + ".",
                        "Schedule the incremental backup - without it the recovery point is a "
                                + "full backup cycle, not an hour."));
            } else {
                int age = lastIncremental.ageHours(now);
                int limit = properties.getPolicy().getMaxIncrementalAgeHours();
                if (age > limit) {
                    penalty += PENALTY_INCREMENTAL_AGE;
                    findings.add(new ReadinessFinding(Severity.HIGH, "INCREMENTAL_STALE",
                            profile.getName(),
                            "The last incremental backup is " + age + " h old (policy: "
                                    + limit + " h).",
                            "Check the incremental job - it is often the first thing to stop when "
                                    + "the media or the log volume runs out."));
                }
            }

            BackupRecord lastGoodFull = firstSuccessfulOfType(backups, BackupRecord.TYPE_FULL);
            if (lastGoodFull == null) {
                penalty += PENALTY_NO_BACKUP;
                findings.add(new ReadinessFinding(Severity.CRITICAL, "NO_FULL_BACKUP",
                        profile.getName(),
                        "No successful full backup has been recorded for " + profile.getName()
                                + " - only incrementals, which cannot start a restore.",
                        "Run a full backup. An incremental chain without a full backup behind it "
                                + "cannot be restored."));
            } else {
                int age = lastGoodFull.ageHours(now);
                int limit = properties.getPolicy().getMaxFullBackupAgeDays() * 24;
                if (age > limit) {
                    penalty += PENALTY_FULL_BACKUP_AGE;
                    findings.add(new ReadinessFinding(Severity.MEDIUM, "FULL_BACKUP_STALE",
                            profile.getName(),
                            "The last successful full backup is " + age + " h old (policy: "
                                    + properties.getPolicy().getMaxFullBackupAgeDays() + " days).",
                            "Schedule the next full backup. The longer a full backup stays old, "
                                    + "the longer a restore takes, because more incrementals have "
                                    + "to be replayed on top of it."));
                }
            }
        }

        // -- 2. has recovery been proven? -----------------------------------
        List<RestoreTest> tests = repository.findRestoreTests(profile.getName());
        RestoreTest lastTest = tests.isEmpty() ? null : tests.get(0);
        readiness.setLastRestoreTest(lastTest);

        if (lastTest == null) {
            penalty += PENALTY_NO_RESTORE_TEST;
            findings.add(new ReadinessFinding(Severity.HIGH, "NO_RESTORE_TEST",
                    profile.getName(),
                    "Recovery has never been tested for " + profile.getName() + ". The backups "
                            + "may be perfectly fine - nobody has proved it.",
                    "Schedule a restore test. A backup that has never been restored is a hope, "
                            + "not a recovery plan, and an auditor will ask."));
        } else {
            int age = lastTest.ageDays(now);
            if (!lastTest.isSuccessful()) {
                penalty += PENALTY_FAILED_RESTORE_TEST;
                findings.add(new ReadinessFinding(Severity.CRITICAL, "RESTORE_TEST_FAILED",
                        profile.getName(),
                        "The last restore test of " + profile.getName() + " failed ("
                                + age + " days ago): " + lastTest.getNotes(),
                        "Fix the restore before the next release - a failed restore means the "
                                + "backup path is broken, not just untested."));
            } else if (age > properties.getPolicy().getMaxRestoreTestAgeDays()) {
                penalty += PENALTY_OLD_RESTORE_TEST;
                findings.add(new ReadinessFinding(Severity.HIGH, "RESTORE_TEST_STALE",
                        profile.getName(),
                        "The last successful restore test was " + age + " days ago (policy: "
                                + properties.getPolicy().getMaxRestoreTestAgeDays() + " days).",
                        "Re-test the restore. The environment changes - a restore that worked a "
                                + "quarter ago proves little about today."));
            }
        }

        // -- 3. is there room? ----------------------------------------------
        CapacityForecast forecast = capacityService.forecast(profile);
        readiness.setForecast(forecast);
        if (forecast != null && forecast.isAtRisk()) {
            penalty += PENALTY_CAPACITY;
            findings.add(new ReadinessFinding(Severity.MEDIUM, "CAPACITY_RISK",
                    profile.getName(),
                    "The database fills up in about " + forecast.getDaysToFull()
                            + " days (" + forecast.getCurrentSizeGb() + " GB of "
                            + forecast.getMaxSizeGb() + " GB, growing "
                            + round1(forecast.getGrowthPerDayGb()) + " GB a day).",
                    "Extend the data volume or plan the cleanup now - extending it in a hurry "
                            + "is how a full database becomes an outage."));
        }

        // -- score and severity ----------------------------------------------
        int score = Math.max(0, 100 - penalty);
        readiness.setScore(score);
        readiness.setFindings(findings);
        readiness.setSeverity(worstSeverity(findings, score));
        return readiness;
    }

    /** The severity of a system is the worst finding it has. */
    private Severity worstSeverity(List<ReadinessFinding> findings, int score) {
        Severity worst = null;
        for (ReadinessFinding finding : findings) {
            if (worst == null || finding.getSeverity().rank() < worst.rank()) {
                worst = finding.getSeverity();
            }
        }
        if (worst != null) {
            return worst;
        }
        if (score >= 80) {
            return Severity.INFO;
        }
        if (score >= 60) {
            return Severity.MEDIUM;
        }
        if (score >= 35) {
            return Severity.HIGH;
        }
        return Severity.CRITICAL;
    }

    /** Every finding across the landscape, most severe first. */
    public List<ReadinessFinding> allFindings() {
        List<ReadinessFinding> findings = new ArrayList<ReadinessFinding>();
        for (SystemReadiness readiness : readiness()) {
            findings.addAll(readiness.getFindings());
        }
        Collections.sort(findings, new Comparator<ReadinessFinding>() {
            @Override
            public int compare(ReadinessFinding left, ReadinessFinding right) {
                int bySeverity = left.getSeverity().rank() - right.getSeverity().rank();
                return bySeverity != 0 ? bySeverity
                        : left.getSystemName().compareTo(right.getSystemName());
            }
        });
        return findings;
    }

    /** How many systems are in each severity band. */
    public int countBySeverity(Severity severity) {
        int count = 0;
        for (SystemReadiness readiness : readiness()) {
            if (readiness.getSeverity() == severity) {
                count++;
            }
        }
        return count;
    }

    /** The average readiness score across the landscape. */
    public int averageScore() {
        List<SystemReadiness> all = readiness();
        if (all.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (SystemReadiness readiness : all) {
            total += readiness.getScore();
        }
        return Math.round((float) total / all.size());
    }

    // -- helpers --------------------------------------------------------------

    private BackupRecord firstOfType(List<BackupRecord> backups, String type) {
        for (BackupRecord backup : backups) {
            if (type.equals(backup.getBackupType())) {
                return backup;
            }
        }
        return null;
    }

    private BackupRecord firstSuccessful(List<BackupRecord> backups) {
        for (BackupRecord backup : backups) {
            if (backup.isSuccessful()) {
                return backup;
            }
        }
        return null;
    }

    private BackupRecord firstSuccessfulOfType(List<BackupRecord> backups, String type) {
        for (BackupRecord backup : backups) {
            if (type.equals(backup.getBackupType()) && backup.isSuccessful()) {
                return backup;
            }
        }
        return null;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /** The continuity report, as a Markdown document. */
    public String renderReport() {
        List<SystemReadiness> all = readiness();
        StringBuilder out = new StringBuilder();
        out.append("# Continuity readiness report\n\n");
        out.append("Generated by SAP Backup & Restore Readiness.\n\n");

        out.append("## Summary\n\n");
        out.append("| System | Description | Owner | Critical | Score | Status | Findings |\n");
        out.append("|---|---|---|---|---|---|---|\n");
        for (SystemReadiness readiness : all) {
            SystemProfile profile = readiness.getProfile();
            out.append("| ").append(profile.getName())
                    .append(" | ").append(profile.getDescription())
                    .append(" | ").append(profile.getOwner())
                    .append(" | ").append(profile.isCritical() ? "yes" : "no")
                    .append(" | ").append(readiness.getScore())
                    .append(" | ").append(readiness.getSeverity())
                    .append(" | ").append(readiness.findingCount())
                    .append(" |\n");
        }
        out.append("\n");

        out.append("## Findings\n\n");
        for (SystemReadiness readiness : all) {
            if (readiness.getFindings().isEmpty()) {
                continue;
            }
            out.append("### ").append(readiness.getProfile().getName())
                    .append(" - ").append(readiness.getProfile().getDescription()).append("\n\n");
            for (ReadinessFinding finding : readiness.getFindings()) {
                out.append("- **").append(finding.getSeverity()).append(" - ")
                        .append(finding.getCategory()).append("**: ")
                        .append(finding.getMessage()).append("  \n  ")
                        .append(finding.getRecommendation()).append("\n");
            }
            out.append("\n");
        }

        out.append("## Recovery evidence\n\n");
        out.append("| System | Newest recovery point | Last full backup | Last incremental | "
                + "Last restore test | RPO | RTO |\n");
        out.append("|---|---|---|---|---|---|---|\n");
        for (SystemReadiness readiness : all) {
            SystemProfile profile = readiness.getProfile();
            out.append("| ").append(profile.getName()).append(" | ")
                    .append(readiness.getLastGoodBackup() == null ? "never"
                            : readiness.getLastGoodBackup().getBackupId())
                    .append(" | ")
                    .append(readiness.getLastFullBackup() == null ? "never"
                            : readiness.getLastFullBackup().getBackupId())
                    .append(" | ")
                    .append(readiness.getLastIncremental() == null ? "never"
                            : readiness.getLastIncremental().getBackupId())
                    .append(" | ")
                    .append(readiness.getLastRestoreTest() == null ? "never tested"
                            : readiness.getLastRestoreTest().getTestId())
                    .append(" | ").append(profile.getRpoHours()).append(" h | ")
                    .append(profile.getRtoHours()).append(" h |\n");
        }
        out.append("\n");

        out.append("## Capacity\n\n");
        out.append("| System | Current | Maximum | Growth per day | Days to full | Status |\n");
        out.append("|---|---|---|---|---|---|\n");
        for (SystemReadiness readiness : all) {
            CapacityForecast forecast = readiness.getForecast();
            if (forecast == null) {
                continue;
            }
            out.append("| ").append(forecast.getSystemName()).append(" | ")
                    .append(forecast.getCurrentSizeGb()).append(" GB | ")
                    .append(forecast.getMaxSizeGb()).append(" GB | ")
                    .append(round1(forecast.getGrowthPerDayGb())).append(" GB | ")
                    .append(forecast.getDaysToFull() == null ? "not growing"
                            : forecast.getDaysToFull() + " days")
                    .append(" | ")
                    .append(forecast.isAtRisk() ? "AT RISK" : "ok")
                    .append(" |\n");
        }
        out.append("\n");

        out.append("---\n\n");
        out.append("> A backup that has never been restored is a hope, not a recovery plan.\n");
        return out.toString();
    }
}
