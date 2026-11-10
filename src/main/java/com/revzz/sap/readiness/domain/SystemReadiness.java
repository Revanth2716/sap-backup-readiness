package com.revzz.sap.readiness.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * The readiness of one system, and the reasons behind the score.
 *
 * <p>The score is never the point on its own - it is the findings and the
 * recommended actions that make it actionable. The score exists so that a
 * portfolio of thirty systems can be sorted.</p>
 */
public class SystemReadiness {

    private SystemProfile profile;
    private int score;
    private Severity severity;
    private List<ReadinessFinding> findings = new ArrayList<ReadinessFinding>();
    private BackupRecord lastFullBackup;
    private BackupRecord lastIncremental;
    private BackupRecord lastGoodBackup;
    private RestoreTest lastRestoreTest;
    private CapacityForecast forecast;

    public boolean hasFinding(String category) {
        for (ReadinessFinding finding : findings) {
            if (category.equals(finding.getCategory())) {
                return true;
            }
        }
        return false;
    }

    public int findingCount() {
        return findings.size();
    }

    public SystemProfile getProfile() {
        return profile;
    }

    public void setProfile(SystemProfile profile) {
        this.profile = profile;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public List<ReadinessFinding> getFindings() {
        return findings;
    }

    public void setFindings(List<ReadinessFinding> findings) {
        this.findings = findings;
    }

    public BackupRecord getLastFullBackup() {
        return lastFullBackup;
    }

    public void setLastFullBackup(BackupRecord lastFullBackup) {
        this.lastFullBackup = lastFullBackup;
    }

    public BackupRecord getLastIncremental() {
        return lastIncremental;
    }

    public void setLastIncremental(BackupRecord lastIncremental) {
        this.lastIncremental = lastIncremental;
    }

    /** The newest backup that produced a usable recovery point. */
    public BackupRecord getLastGoodBackup() {
        return lastGoodBackup;
    }

    public void setLastGoodBackup(BackupRecord lastGoodBackup) {
        this.lastGoodBackup = lastGoodBackup;
    }

    public RestoreTest getLastRestoreTest() {
        return lastRestoreTest;
    }

    public void setLastRestoreTest(RestoreTest lastRestoreTest) {
        this.lastRestoreTest = lastRestoreTest;
    }

    public CapacityForecast getForecast() {
        return forecast;
    }

    public void setForecast(CapacityForecast forecast) {
        this.forecast = forecast;
    }
}
