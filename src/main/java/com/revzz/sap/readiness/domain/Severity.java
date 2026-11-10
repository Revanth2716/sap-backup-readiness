package com.revzz.sap.readiness.domain;

/** Severity of a readiness finding, ordered from most to least urgent. */
public enum Severity {

    CRITICAL(1, "Critical"),
    HIGH(2, "High"),
    MEDIUM(3, "Medium"),
    LOW(4, "Low"),
    INFO(5, "Info");

    private final int rank;
    private final String label;

    Severity(int rank, String label) {
        this.rank = rank;
        this.label = label;
    }

    public int rank() {
        return rank;
    }

    public String label() {
        return label;
    }

    public String cssClass() {
        return name().toLowerCase();
    }
}
