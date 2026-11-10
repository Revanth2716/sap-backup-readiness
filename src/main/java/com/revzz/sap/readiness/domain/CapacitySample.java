package com.revzz.sap.readiness.domain;

/** One daily capacity sample. */
public class CapacitySample {

    private String systemName;
    private java.time.LocalDate sampleDate;
    private int dbSizeGb;
    private int logSizeGb;

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public java.time.LocalDate getSampleDate() {
        return sampleDate;
    }

    public void setSampleDate(java.time.LocalDate sampleDate) {
        this.sampleDate = sampleDate;
    }

    public int getDbSizeGb() {
        return dbSizeGb;
    }

    public void setDbSizeGb(int dbSizeGb) {
        this.dbSizeGb = dbSizeGb;
    }

    public int getLogSizeGb() {
        return logSizeGb;
    }

    public void setLogSizeGb(int logSizeGb) {
        this.logSizeGb = logSizeGb;
    }
}
