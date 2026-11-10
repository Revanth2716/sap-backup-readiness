package com.revzz.sap.readiness.domain;

/**
 * A readiness finding.
 *
 * <p>Every finding carries the recommended action, because "your backup is
 * old" is not something anybody can act on at 09:00.</p>
 */
public class ReadinessFinding {

    private Severity severity;
    private String category;
    private String systemName;
    private String message;
    private String recommendation;

    public ReadinessFinding() {
    }

    public ReadinessFinding(Severity severity, String category, String systemName,
                            String message, String recommendation) {
        this.severity = severity;
        this.category = category;
        this.systemName = systemName;
        this.message = message;
        this.recommendation = recommendation;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }
}
