package com.revzz.sap.readiness.domain;

import java.time.LocalDateTime;

/** One backup job. */
public class BackupRecord {

    public static final String TYPE_FULL = "FULL";
    public static final String TYPE_INCREMENTAL = "INCREMENTAL";
    public static final String TYPE_DIFFERENTIAL = "DIFFERENTIAL";

    public static final String STATUS_OK = "OK";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_RUNNING = "RUNNING";

    private String backupId;
    private String systemName;
    private String backupType;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private int durationMinutes;
    private int sizeGb;
    private String status;
    private String media;

    public boolean isFull() {
        return TYPE_FULL.equals(backupType);
    }

    public boolean isSuccessful() {
        return STATUS_OK.equals(status);
    }

    public int ageHours(LocalDateTime now) {
        if (startedAt == null) {
            return 0;
        }
        return (int) java.time.Duration.between(startedAt, now).toHours();
    }

    /** The start time for display - the raw timestamp carries nanoseconds. */
    public String getStartedAtDisplay() {
        return format(startedAt);
    }

    /** The end time for display. */
    public String getFinishedAtDisplay() {
        return format(finishedAt);
    }

    private static String format(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return value.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    public String getBackupId() {
        return backupId;
    }

    public void setBackupId(String backupId) {
        this.backupId = backupId;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getBackupType() {
        return backupType;
    }

    public void setBackupType(String backupType) {
        this.backupType = backupType;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public int getSizeGb() {
        return sizeGb;
    }

    public void setSizeGb(int sizeGb) {
        this.sizeGb = sizeGb;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMedia() {
        return media;
    }

    public void setMedia(String media) {
        this.media = media;
    }
}
