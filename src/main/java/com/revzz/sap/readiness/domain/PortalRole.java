package com.revzz.sap.readiness.domain;

/** The portal's own roles - not SAP authorisation roles. */
public enum PortalRole {

    /** Signs in and sees the dashboard. */
    VIEWER,

    /** Everything a viewer sees, plus the system detail and the report. */
    ANALYST,

    /** Everything, including acknowledging findings and exporting the report. */
    OPS_ADMIN;

    public boolean canAnalyse() {
        return this != VIEWER;
    }

    public boolean canOperate() {
        return this == OPS_ADMIN;
    }

    public String cssClass() {
        return name().toLowerCase();
    }
}
