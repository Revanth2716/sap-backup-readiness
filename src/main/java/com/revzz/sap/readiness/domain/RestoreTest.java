package com.revzz.sap.readiness.domain;

import java.time.LocalDateTime;

/**
 * A restore test - the evidence that a restore actually works.
 *
 * <p>This is the record that separates a backup from a recovery plan.</p>
 */
public class RestoreTest {

    public static final String RESULT_OK = "OK";
    public static final String RESULT_FAILED = "FAILED";

    private String testId;
    private String systemName;
    private LocalDateTime testedAt;
    private String result;
    private int durationMinutes;
    private String performedBy;
    private String notes;

    public boolean isSuccessful() {
        return RESULT_OK.equals(result);
    }

    public int ageDays(LocalDateTime now) {
        if (testedAt == null) {
            return 0;
        }
        return (int) java.time.Duration.between(testedAt, now).toDays();
    }

    /** The test date for display - the raw timestamp carries nanoseconds. */
    public String getTestedAtDisplay() {
        if (testedAt == null) {
            return "";
        }
        return testedAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    public String getTestId() {
        return testId;
    }

    public void setTestId(String testId) {
        this.testId = testId;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public LocalDateTime getTestedAt() {
        return testedAt;
    }

    public void setTestedAt(LocalDateTime testedAt) {
        this.testedAt = testedAt;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
