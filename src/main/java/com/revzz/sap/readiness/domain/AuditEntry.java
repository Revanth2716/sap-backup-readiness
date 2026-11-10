package com.revzz.sap.readiness.domain;

import java.time.LocalDateTime;

/** One line of the portal audit trail. */
public class AuditEntry {

    private String auditId;
    private String username;
    private String action;
    private String detail;
    private LocalDateTime at;

    public String getAuditId() {
        return auditId;
    }

    public void setAuditId(String auditId) {
        this.auditId = auditId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public LocalDateTime getAt() {
        return at;
    }

    public void setAt(LocalDateTime at) {
        this.at = at;
    }

    /** Formatted for display - the raw timestamp carries nanoseconds. */
    public String getAtDisplay() {
        if (at == null) {
            return "";
        }
        return at.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
